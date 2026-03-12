package com.cim.api.hive;

import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.hive.DepthWormNestBlockEntity;
import com.cim.block.entity.hive.HiveSoilBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class HiveNetwork {
    public final UUID id;
    public final Set<BlockPos> members = new HashSet<>();
    public final Map<BlockPos, Integer> wormCounts = new HashMap<>();
    public final Map<BlockPos, List<CompoundTag>> nestWormData = new HashMap<>();
    public int killsPool = 0;
    private long lastFedTime = 0;

    public enum HiveState {
        EXPANSION, DEFENSIVE, AGGRESSIVE, RECOVERY, STARVATION
    }

    private HiveState currentState = HiveState.EXPANSION;
    private int threatLevel = 0;
    private int expansionPressure = 0;
    private long lastStateChange = 0;
    private int successfulAttacks = 0;
    private final Set<BlockPos> dangerZones = new HashSet<>();

    public HiveNetwork(UUID id) {
        this.id = id;
    }

    public void addMember(BlockPos pos, boolean isNest) {
        members.add(pos);
        if (isNest) {
            wormCounts.put(pos, 0);
            nestWormData.put(pos, new ArrayList<>());
        }
    }

    public void clearNestWormData(BlockPos nestPos) {
        List<CompoundTag> data = nestWormData.get(nestPos);
        if (data != null) data.clear();
        wormCounts.put(nestPos, 0);
    }

    public void removeMember(BlockPos pos) {
        members.remove(pos);
        wormCounts.remove(pos);
        nestWormData.remove(pos);
    }

    public List<CompoundTag> getNestWormData(BlockPos nestPos) {
        return nestWormData.getOrDefault(nestPos, new ArrayList<>());
    }

    public void addWormDataToNest(BlockPos nestPos, CompoundTag wormData) {
        List<CompoundTag> data = nestWormData.computeIfAbsent(nestPos, k -> new ArrayList<>());
        if (data.size() >= 3) data.clear();
        data.add(wormData);
        wormCounts.put(nestPos, data.size());
    }

    public boolean isNest(Level level, BlockPos pos) {
        return members.contains(pos) && level.isLoaded(pos) &&
                level.getBlockState(pos).is(ModBlocks.DEPTH_WORM_NEST.get());
    }

    public int getTotalWorms() {
        int total = 0;
        for (List<CompoundTag> worms : nestWormData.values()) {
            total += worms.size();
        }
        return total;
    }

    public boolean isDead() {
        boolean noStoredWorms = getTotalWorms() == 0;
        boolean noActiveWorms = activeWormCounts.isEmpty() ||
                activeWormCounts.values().stream().allMatch(count -> count == 0);
        boolean noMembers = members.isEmpty();

        // Сеть мертва только если нет вообще ничего
        return noStoredWorms && noActiveWorms && noMembers;
    }

    public boolean isAbandoned() {
        boolean noStoredWorms = getTotalWorms() == 0;
        boolean noActiveWorms = activeWormCounts.isEmpty() ||
                activeWormCounts.values().stream().allMatch(count -> count == 0);
        boolean hasMembers = !members.isEmpty();

        // Брошенная сеть - есть блоки, но нет червяков вообще
        // НО: если есть killsPool, сеть ещё жива и может спавнить новых!
        boolean noResources = killsPool <= 0;

        return noStoredWorms && noActiveWorms && hasMembers && noResources;
    }
    // В классе HiveNetwork добавь:
    public final Map<BlockPos, Integer> activeWormCounts = new HashMap<>(); // Черви "на улице" по гнездам

    // Методы для работы с активными червями:
    public void addActiveWorm(BlockPos nestPos) {
        activeWormCounts.merge(nestPos, 1, Integer::sum);
        System.out.println("[Hive] Worm went active from nest " + nestPos +
                " | Active: " + activeWormCounts.get(nestPos) + " | Stored: " + wormCounts.getOrDefault(nestPos, 0));
    }

    public void removeActiveWorm(BlockPos nestPos) {
        activeWormCounts.merge(nestPos, -1, (old, delta) -> Math.max(0, old + delta));
        if (activeWormCounts.getOrDefault(nestPos, 0) <= 0) {
            activeWormCounts.remove(nestPos);
        }
    }

    // Общее количество червяков (в гнезде + на улице)
    public int getTotalWormsIncludingActive() {
        int stored = getTotalWorms();
        int active = activeWormCounts.values().stream().mapToInt(Integer::intValue).sum();
        return stored + active;
    }

    // Получить количество по конкретному гнезду
    public int getWormsFromNest(BlockPos nestPos) {
        int stored = wormCounts.getOrDefault(nestPos, 0);
        int active = activeWormCounts.getOrDefault(nestPos, 0);
        return stored + active;
    }

    public void update(Level level) {
        if (level.isClientSide) return;

        if (wormCounts.isEmpty()) {
            if (members.isEmpty()) return;
            if (currentState != HiveState.STARVATION) {
                System.out.println("[Hive " + id + "] No nests - network abandoned");
                currentState = HiveState.STARVATION;
            }
            return;
        }

        if (level.getGameTime() % 40 == 0) {
            int active = activeWormCounts.values().stream().mapToInt(Integer::intValue).sum();
            System.out.println("[Hive Tick] Network " + this.id + " | State: " + currentState +
                    " | Points: " + killsPool + " | Nodes: " + members.size() +
                    " | Stored: " + getTotalWorms() + " | Active: " + active +
                    " | Total: " + getTotalWormsIncludingActive() + " | Threat: " + threatLevel);

            if (members.isEmpty()) {
                System.out.println("[Hive Tick] ERROR: Member list empty!");
                return;
            }
            makeDecisions(level);
        }
    }

    private void makeDecisions(Level level) {
        if (killsPool <= 0 && !members.isEmpty()) {
            enterStarvationMode(level);
            return;
        }
        if (killsPool <= 0) return;

        if (level.getGameTime() % 100 == 0) {
            analyzeSituation(level);
        }

        switch (currentState) {
            case STARVATION -> handleStarvation(level);
            case DEFENSIVE -> handleDefensive(level);
            case RECOVERY -> handleRecovery(level);
            case AGGRESSIVE -> handleAggressive(level);
            case EXPANSION -> handleExpansion(level);
        }
    }

    private void analyzeSituation(Level level) {
        threatLevel = calculateThreatLevel(level);
        expansionPressure = calculateExpansionPressure(level);

        HiveState newState = determineOptimalState();
        if (newState != currentState) {
            currentState = newState;
            lastStateChange = level.getGameTime();
            System.out.println("[Hive " + id + "] State changed to: " + currentState);
        }
    }

    private int calculateThreatLevel(Level level) {
        int threats = 0;
        for (BlockPos pos : members) {
            if (!level.isLoaded(pos)) continue;
            AABB area = new AABB(pos).inflate(20);
            List<Player> players = level.getEntitiesOfClass(Player.class, area,
                    p -> !p.isCreative() && !p.isSpectator());
            threats += players.size() * 10;

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DepthWormNestBlockEntity nest && nest.hasInjuredWorms()) {
                threats += 5;
            }
        }
        return threats;
    }

    private int calculateExpansionPressure(Level level) {
        int worms = getTotalWormsIncludingActive(); // Все черви, не только в гнездах
        int territory = members.size();
        return territory > 0 ? (worms * 100) / territory : 0;
    }

    private HiveState determineOptimalState() {
        int totalWorms = getTotalWormsIncludingActive();
        int nests = wormCounts.size();
        int maxCapacity = nests * 3;

        if (killsPool <= 0) return HiveState.STARVATION;
        if (threatLevel > 20) return HiveState.DEFENSIVE;

        // Проверка раненых гнезд
        int injuredNests = 0;
        for (int count : wormCounts.values()) {
            if (count > 0 && threatLevel > 10) injuredNests++;
        }
        if (injuredNests > nests / 2) return HiveState.RECOVERY;

        // Если есть очки и место для червяков - AGGRESSIVE (спавн)
        if (killsPool >= 10 && totalWorms < maxCapacity) {
            return HiveState.AGGRESSIVE;
        }

        // Если много червяков и угроза - тоже AGGRESSIVE (атака)
        if (totalWorms >= 4 && threatLevel > 5) {
            return HiveState.AGGRESSIVE;
        }

        // Если много очков но нет места - нужно расширяться
        if (killsPool >= 20 && totalWorms >= maxCapacity - 1 && nests < 8) {
            return HiveState.EXPANSION;
        }

        // Мало червяков - спавним если есть очки
        if (totalWorms < 4 && killsPool >= 10) {
            return HiveState.AGGRESSIVE;
        }

        // По умолчанию - расширяем территорию умеренно
        return HiveState.EXPANSION;
    }

    private void enterStarvationMode(Level level) {
        if (currentState != HiveState.STARVATION) {
            currentState = HiveState.STARVATION;
            System.out.println("[Hive " + id + "] CRITICAL HUNGER! Survival mode activated.");
        }
    }

    private void handleStarvation(Level level) {
        if (killsPool > 0) {
            System.out.println("[Hive " + id + "] Nutrition restored! Exiting starvation.");
            currentState = HiveState.RECOVERY;
        }
    }

    private void handleDefensive(Level level) {
        List<BlockPos> threatenedNests = new ArrayList<>();

        for (BlockPos pos : new ArrayList<>(wormCounts.keySet())) {
            if (!level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof DepthWormNestBlockEntity nest)) continue;

            AABB dangerZone = new AABB(pos).inflate(15);
            List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, dangerZone,
                    e -> e instanceof Player && !((Player)e).isCreative() && e.isAlive());

            if (!enemies.isEmpty() && !nest.getStoredWorms().isEmpty()) {
                threatenedNests.add(pos);
            }
        }

        if (!threatenedNests.isEmpty()) {
            BlockPos defendPos = threatenedNests.get(0);
            BlockEntity be = level.getBlockEntity(defendPos);
            if (be instanceof DepthWormNestBlockEntity nest && !nest.getStoredWorms().isEmpty()) {
                AABB dangerZone = new AABB(defendPos).inflate(15);
                List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, dangerZone,
                        e -> e instanceof Player && !((Player)e).isCreative());

                if (!enemies.isEmpty()) {
                    LivingEntity target = enemies.get(0);
                    nest.releaseWorms(defendPos, target);
                    killsPool -= 2;
                    System.out.println("[Hive] Emergency defense at nest " + defendPos);
                    return;
                }
            }
        }
    }

    private void handleRecovery(Level level) {
        List<DepthWormNestBlockEntity> injuredNests = new ArrayList<>();

        for (BlockPos pos : new ArrayList<>(wormCounts.keySet())) {
            if (!level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DepthWormNestBlockEntity nest && nest.hasInjuredWorms()) {
                injuredNests.add(nest);
            }
        }

        for (DepthWormNestBlockEntity nest : injuredNests) {
            if (killsPool < 1) break;
            if (nest.healOneWorm()) {
                killsPool -= 1;
                System.out.println("[Hive] Healed worm at " + nest.getBlockPos());
                return;
            }
        }

        if (injuredNests.isEmpty()) {
            int total = getTotalWormsIncludingActive();
            currentState = (total >= 4) ? HiveState.AGGRESSIVE : HiveState.EXPANSION;
        }
    }

    private void handleAggressive(Level level) {
        int readyWorms = getTotalWorms(); // В гнездах
        int activeWorms = activeWormCounts.values().stream().mapToInt(Integer::intValue).sum();
        int totalWorms = readyWorms + activeWorms;

        int nests = wormCounts.size();
        int maxCapacity = nests * 3;

        // ПРИОРИТЕТ 1: Спавним червяков если есть место и очки
        if (killsPool >= 10 && totalWorms < maxCapacity && readyWorms < nests) {
            // Не более 1 червяка за тик, оставляем очки на ядро
            spawnNewWormOptimally(level);

            // Если мало места осталось - сразу пытаемся построить ядро
            if (totalWorms + 1 >= maxCapacity - 2 && killsPool >= 15) {
                System.out.println("[Hive] Space running out after spawn, trying to build nest...");
                if (buildNewNestIfPossible(level)) {
                    currentState = HiveState.EXPANSION; // На следующий тик расширим территорию под новое ядро
                    return;
                }
            }
            return; // Потратили 10 очков, выходим
        }

        // ПРИОРИТЕТ 2: Строим новое ядро если приближаемся к лимиту
        int softLimit = Math.max(6, nests * 2);
        if (totalWorms >= softLimit && killsPool >= 15) {
            System.out.println("[Hive] Approaching capacity (" + totalWorms + "/" + maxCapacity + "), building nest...");
            if (buildNewNestIfPossible(level)) {
                // Ядро построено, расширяем территорию под него
                currentState = HiveState.EXPANSION;
                return;
            }
        }

        // ПРИОРИТЕТ 3: Атака (если есть готовые черви)
        if (readyWorms >= 3 && killsPool >= 2) {
            executeCoordinatedAttack(level);
            return;
        }

        // Если нечего делать - копим или расширяемся чуть-чуть
        if (killsPool > 30 && totalWorms < maxCapacity) {
            // Много очков - можно позволить себе расширение территории
            currentState = HiveState.EXPANSION;
        }
    }

    private boolean hasSpaceForMoreWorms() {
        return getTotalWorms() < wormCounts.size() * 3;
    }

    private void executeCoordinatedAttack(Level level) {
        LivingEntity target = findBestTarget(level);
        if (target == null) return;

        List<BlockPos> nearbyNests = findNestsNearTarget(level, target.blockPosition(), 25);
        int released = 0;

        for (BlockPos nestPos : nearbyNests) {
            if (released >= 2) break;
            BlockEntity be = level.getBlockEntity(nestPos);
            if (be instanceof DepthWormNestBlockEntity nest && !nest.getStoredWorms().isEmpty()) {
                nest.releaseWorms(nestPos, target);
                released++;
            }
        }

        if (released > 0) {
            killsPool = Math.max(0, killsPool - released);
            successfulAttacks++;
            System.out.println("[Hive] Coordinated attack! Released: " + released +
                    " towards " + target.getName().getString());
        }
    }

    private void handleExpansion(Level level) {
        if (killsPool < 5) return;

        int totalWorms = getTotalWormsIncludingActive();
        int nests = wormCounts.size();
        int totalNodes = members.size();

        // Проверяем соотношение территория/черви
        int wormsPerNode = totalNodes > 0 ? (totalWorms * 100) / totalNodes : 0;

        // Если территории уже много - не расширяемся, копим на червяков/ядра
        if (wormsPerNode < 15 && totalNodes > nests * 3) {
            System.out.println("[Hive] Enough territory (" + totalNodes + " nodes for " + totalWorms + " worms). Saving points.");
            return;
        }

        // Максимум 1 расширение за раз, потом переключаемся на накопление
        if (level.getGameTime() % 200 == 0) { // Реже - каждые 10 секунд
            BlockPos bestNest = findNestNeedingExpansion(level);
            if (bestNest == null) return;

            BlockPos target = findAdjacentSpot(level, bestNest);
            if (target != null && canPlaceSoilSafely(level, target, this.id)) {
                placeHiveSoil(level, target, this.id);
                killsPool -= 5;
                System.out.println("[Hive] Expansion: soil at " + target + " | Points left: " + killsPool);

                // После расширения - пробуем перейти в AGGRESSIVE для спавна червяков
                if (killsPool >= 10 && totalWorms < nests * 3) {
                    currentState = HiveState.AGGRESSIVE;
                }
            }
        }
    }

    private BlockPos findAdjacentSpot(Level level, BlockPos center) {
        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        Direction[] vertical = {Direction.UP, Direction.DOWN};

        // Приоритет 1: Горизонтальные направления
        for (Direction dir : horizontal) {
            BlockPos target = center.relative(dir);
            if (canPlaceSoilSafely(level, target, this.id)) return target;
        }

        // Приоритет 2: Вертикальные (вверх/вниз) только если горизонталь занята
        for (Direction dir : vertical) {
            BlockPos target = center.relative(dir);
            if (canPlaceSoilSafely(level, target, this.id)) return target;
        }

        // Приоритет 3: Расширение от других членов сети (тоже с приоритетом горизонтали)
        for (BlockPos member : new ArrayList<>(members)) {
            if (wormCounts.containsKey(member)) continue; // Пропускаем гнезда - от них уже проверили

            // Сначала горизонталь от члена сети
            for (Direction dir : horizontal) {
                BlockPos target = member.relative(dir);
                if (canPlaceSoilSafely(level, target, this.id)) return target;
            }
            // Потом вертикаль
            for (Direction dir : vertical) {
                BlockPos target = member.relative(dir);
                if (canPlaceSoilSafely(level, target, this.id)) return target;
            }
        }
        return null;
    }

    private boolean canPlaceSoilSafely(Level level, BlockPos pos, UUID networkId) {
        if (!isValidExpansionTarget(level, pos)) return false;

        // Проверяем соседей - должен быть хотя бы один член сети рядом (включая диагонали для воздуха)
        boolean hasNetworkNeighbor = false;

        // Прямые соседи (6 направлений)
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof HiveNetworkMember member && networkId.equals(member.getNetworkId())) {
                hasNetworkNeighbor = true;
                break;
            }
        }

        // Дополнительно: проверяем "опору" снизу для строительства в воздухе
        if (!hasNetworkNeighbor) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            // Можно строить в воздухе только если есть опора снизу (или это уже часть улья)
            if (!belowState.isAir() || level.getBlockEntity(below) instanceof HiveNetworkMember) {
                // Проверяем диагональные соседи для "мостов"
                for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                    BlockPos diagonal = pos.relative(dir).below();
                    BlockEntity be = level.getBlockEntity(diagonal);
                    if (be instanceof HiveNetworkMember member && networkId.equals(member.getNetworkId())) {
                        hasNetworkNeighbor = true;
                        break;
                    }
                }
            }
        }

        return hasNetworkNeighbor;
    }

    private BlockPos findNestNeedingExpansion(Level level) {
        BlockPos bestNest = null;
        int minNeighbors = Integer.MAX_VALUE;

        for (BlockPos nestPos : wormCounts.keySet()) {
            if (!level.isLoaded(nestPos)) continue;
            int neighbors = countNetworkNeighbors(level, nestPos);
            if (neighbors < minNeighbors) {
                minNeighbors = neighbors;
                bestNest = nestPos;
            }
        }
        return bestNest;
    }

    private int countNetworkNeighbors(Level level, BlockPos pos) {
        int count = 0;
        for (Direction dir : Direction.values()) {
            BlockPos check = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(check);
            if (be instanceof HiveNetworkMember member && this.id.equals(member.getNetworkId())) {
                count++;
            }
        }
        return count;
    }

    private boolean isValidExpansionTarget(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() &&
                !state.is(ModBlocks.HIVE_SOIL.get()) &&
                !state.is(ModBlocks.DEPTH_WORM_NEST.get()) &&
                !state.is(ModBlocks.HIVE_SOIL_DEAD.get()) &&
                !state.is(ModBlocks.DEPTH_WORM_NEST_DEAD.get()) &&
                state.getDestroySpeed(level, pos) >= 0;
    }

    private void placeHiveSoil(Level level, BlockPos pos, UUID networkId) {
        level.setBlock(pos, ModBlocks.HIVE_SOIL.get().defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HiveSoilBlockEntity soil) {
            soil.setNetworkId(networkId);
            HiveNetworkManager manager = HiveNetworkManager.get(level);
            if (manager != null) manager.addNode(networkId, pos, false);
        }
    }

    private boolean buildNewNestIfPossible(Level level) {
        if (wormCounts.size() >= 8 || killsPool < 15) return false;

        // Ищем подходящую почву вплотную к гнездам
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos soilPos : new ArrayList<>(members)) {
            if (wormCounts.containsKey(soilPos)) continue;
            boolean adjacentToNest = false;
            for (Direction dir : Direction.values()) {
                if (wormCounts.containsKey(soilPos.relative(dir))) {
                    adjacentToNest = true;
                    break;
                }
            }
            if (adjacentToNest && isGoodNestLocation(level, soilPos)) {
                candidates.add(soilPos);
            }
        }

        if (!candidates.isEmpty()) {
            BlockPos chosen = candidates.get(level.random.nextInt(candidates.size()));
            upgradeSoilToNest(level, chosen);
            return true;
        }

        // Создаём новую почву вплотную к гнезду
        for (BlockPos nestPos : wormCounts.keySet()) {
            if (!level.isLoaded(nestPos)) continue;
            for (Direction dir : Direction.values()) {
                BlockPos target = nestPos.relative(dir);
                if (canPlaceSoilSafely(level, target, this.id)) {
                    placeHiveSoil(level, target, this.id);
                    killsPool -= 5;
                    if (killsPool >= 15) {
                        upgradeSoilToNest(level, target);
                        return true;
                    }
                    return false;
                }
            }
        }
        return false;
    }

    private boolean isGoodNestLocation(Level level, BlockPos pos) {
        // Не слишком близко к другим гнездам
        for (BlockPos nestPos : wormCounts.keySet()) {
            if (pos.distSqr(nestPos) < 25) return false;
        }
        // Не слишком далеко
        boolean hasNearbyNest = false;
        for (BlockPos nestPos : wormCounts.keySet()) {
            if (pos.distSqr(nestPos) <= 225) {
                hasNearbyNest = true;
                break;
            }
        }
        if (!hasNearbyNest) return false;
        // Безопасное место
        AABB dangerZone = new AABB(pos).inflate(10);
        List<Player> threats = level.getEntitiesOfClass(Player.class, dangerZone,
                p -> !p.isCreative() && !p.isSpectator());
        return threats.isEmpty();
    }

    private void upgradeSoilToNest(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof HiveSoilBlockEntity soil)) {
            System.out.println("[Hive] ERROR: No soil at " + pos + " for upgrade");
            return;
        }

        UUID soilNetId = soil.getNetworkId();
        if (soilNetId == null) {
            System.out.println("[Hive] ERROR: Soil has no network ID at " + pos);
            return;
        }
        if (!soilNetId.equals(this.id)) {
            System.out.println("[Hive] ERROR: Soil belongs to different network " + soilNetId + " vs " + this.id);
            return;
        }
        if (wormCounts.size() >= 8) {
            System.out.println("[Hive] Maximum nest count reached (" + wormCounts.size() + ")");
            return;
        }
        if (!hasNetworkNeighbor(level, pos)) {
            System.out.println("[Hive] ERROR: Cannot upgrade isolated soil at " + pos);
            return;
        }

        final UUID preservedId = this.id;
        System.out.println("[Hive] Upgrading soil at " + pos + " to nest for network " + preservedId);

        members.remove(pos);

        level.setBlock(pos, ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState(), 3);

        BlockEntity newBe = ModBlockEntities.DEPTH_WORM_NEST.get().create(pos,
                ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState());

        if (!(newBe instanceof DepthWormNestBlockEntity nest)) {
            System.out.println("[Hive] CRITICAL ERROR: Failed to create nest BlockEntity at " + pos);
            return;
        }

        nest.setNetworkId(preservedId);
        level.setBlockEntity(nest);

        BlockEntity verify = level.getBlockEntity(pos);
        if (!(verify instanceof DepthWormNestBlockEntity) ||
                !preservedId.equals(((DepthWormNestBlockEntity)verify).getNetworkId())) {
            System.out.println("[Hive] CRITICAL ERROR: UUID not preserved after upgrade at " + pos);
            return;
        }

        wormCounts.put(pos, 0);
        nestWormData.put(pos, new ArrayList<>());
        members.add(pos);

        killsPool -= 15;

        System.out.println("[Hive] New nest at " + pos + " for network " + preservedId +
                ". Total nests: " + wormCounts.size());

        HiveNetworkManager manager = HiveNetworkManager.get(level);
        if (manager != null) {
            manager.addNode(preservedId, pos, true);
        }
    }

    private boolean hasNetworkNeighbor(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof HiveNetworkMember member && this.id.equals(member.getNetworkId())) {
                return true;
            }
        }
        return false;
    }

    private void spawnNewWormOptimally(Level level) {
        // Находим гнездо с минимальным количеством червяков (включая активных)
        BlockPos bestNest = null;
        int minWorms = Integer.MAX_VALUE;
        int nests = wormCounts.size();

        for (Map.Entry<BlockPos, Integer> entry : wormCounts.entrySet()) {
            BlockPos nestPos = entry.getKey();
            int stored = entry.getValue();
            int active = activeWormCounts.getOrDefault(nestPos, 0);
            int totalAtNest = stored + active;

            // Максимум 3 червяка на гнездо ВСЕГО (stored + active)
            if (totalAtNest < minWorms && totalAtNest < 3) {
                BlockEntity be = level.getBlockEntity(nestPos);
                if (be instanceof DepthWormNestBlockEntity nest && !nest.isFull()) {
                    bestNest = nestPos;
                    minWorms = totalAtNest;
                }
            }
        }

        if (bestNest != null && killsPool >= 10) {
            BlockEntity be = level.getBlockEntity(bestNest);
            if (be instanceof DepthWormNestBlockEntity nest) {
                CompoundTag newWorm = new CompoundTag();
                newWorm.putFloat("Health", 15.0F);
                newWorm.putInt("Kills", 0);

                nest.addWormTag(newWorm);
                addWormDataToNest(bestNest, newWorm);
                wormCounts.put(bestNest, wormCounts.getOrDefault(bestNest, 0) + 1);

                killsPool -= 10;

                int totalNow = getTotalWormsIncludingActive();
                System.out.println("[Hive] Spawned worm at " + bestNest +
                        " | Stored: " + wormCounts.get(bestNest) +
                        " | Total network: " + totalNow + "/" + (nests * 3) +
                        " | Points: " + killsPool);
            }
        } else if (bestNest == null && killsPool >= 15) {
            // Нет места в существующих гнездах - нужно новое ядро
            System.out.println("[Hive] No space in existing nests, need to build new core!");
            if (buildNewNestIfPossible(level)) {
                // Пробуем снова на следующем тике
            }
        }
    }


    private LivingEntity findBestTarget(Level level) {
        LivingEntity bestTarget = null;
        double bestScore = -1;

        for (BlockPos pos : members) {
            if (!level.isLoaded(pos)) continue;
            AABB area = new AABB(pos).inflate(30);
            List<LivingEntity> potential = level.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e instanceof Player && !((Player)e).isCreative() && !((Player)e).isSpectator() && e.isAlive());

            for (LivingEntity target : potential) {
                double dist = pos.distSqr(target.blockPosition());
                double score = 1000.0 / (dist + 1);
                if (potential.size() == 1) score *= 1.5;

                if (score > bestScore) {
                    bestScore = score;
                    bestTarget = target;
                }
            }
        }
        return bestTarget;
    }

    private List<BlockPos> findNestsNearTarget(Level level, BlockPos targetPos, double maxDist) {
        List<BlockPos> result = new ArrayList<>();
        double maxDistSq = maxDist * maxDist;

        for (BlockPos nestPos : wormCounts.keySet()) {
            if (nestPos.distSqr(targetPos) <= maxDistSq) {
                BlockEntity be = level.getBlockEntity(nestPos);
                if (be instanceof DepthWormNestBlockEntity nest && !nest.getStoredWorms().isEmpty()) {
                    result.add(nestPos);
                }
            }
        }
        result.sort(Comparator.comparingDouble(p -> p.distSqr(targetPos)));
        return result;
    }

    public boolean hasAnyLoadedChunk(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;
        for (BlockPos pos : members) {
            ChunkPos chunkPos = new ChunkPos(pos);
            if (!chunkMap.getPlayers(chunkPos, false).isEmpty()) return true;
        }
        return false;
    }

    public void updateWormCount(BlockPos nestPos, int delta) {
        wormCounts.merge(nestPos, delta, Integer::sum);
        if (wormCounts.get(nestPos) < 0) wormCounts.put(nestPos, 0);
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putInt("KillsPool", this.killsPool);
        tag.putLong("LastFed", this.lastFedTime);
        tag.putString("CurrentState", this.currentState.name());
        tag.putInt("ThreatLevel", this.threatLevel);
        tag.putInt("ExpansionPressure", this.expansionPressure);
        tag.putLong("LastStateChange", this.lastStateChange);
        tag.putInt("SuccessfulAttacks", this.successfulAttacks);

        ListTag membersList = new ListTag();
        for (BlockPos p : members) {
            CompoundTag pTag = NbtUtils.writeBlockPos(p);
            pTag.putBoolean("IsNest", wormCounts.containsKey(p));
            if (wormCounts.containsKey(p)) {
                pTag.putInt("WormCount", wormCounts.get(p));
                ListTag wormDataList = new ListTag();
                List<CompoundTag> wormData = nestWormData.get(p);
                if (wormData != null) wormDataList.addAll(wormData);
                pTag.put("WormData", wormDataList);
            }
            membersList.add(pTag);
        }
        tag.put("Members", membersList);

        ListTag dangerList = new ListTag();
        for (BlockPos p : dangerZones) {
            dangerList.add(NbtUtils.writeBlockPos(p));
        }
        tag.put("DangerZones", dangerList);

        ListTag activeList = new ListTag();
        for (Map.Entry<BlockPos, Integer> entry : activeWormCounts.entrySet()) {
            CompoundTag activeTag = NbtUtils.writeBlockPos(entry.getKey());
            activeTag.putInt("Count", entry.getValue());
            activeList.add(activeTag);
        }
        tag.put("ActiveWorms", activeList);

        return tag;

    }

    public static HiveNetwork fromNBT(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        HiveNetwork net = new HiveNetwork(id);

        net.killsPool = tag.getInt("KillsPool");
        net.lastFedTime = tag.getLong("LastFed");

        try {
            net.currentState = HiveState.valueOf(tag.getString("CurrentState"));
        } catch (IllegalArgumentException e) {
            net.currentState = HiveState.EXPANSION;
        }

        net.threatLevel = tag.getInt("ThreatLevel");
        net.expansionPressure = tag.getInt("ExpansionPressure");
        net.lastStateChange = tag.getLong("LastStateChange");
        net.successfulAttacks = tag.getInt("SuccessfulAttacks");

        ListTag membersList = tag.getList("Members", 10);
        for (int i = 0; i < membersList.size(); i++) {
            CompoundTag pTag = membersList.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(pTag);
            boolean isNest = pTag.getBoolean("IsNest");

            net.members.add(pos);
            if (isNest) {
                net.wormCounts.put(pos, pTag.getInt("WormCount"));
                ListTag wormDataList = pTag.getList("WormData", 10);
                List<CompoundTag> wormData = new ArrayList<>();
                for (int j = 0; j < wormDataList.size(); j++) {
                    wormData.add(wormDataList.getCompound(j));
                }
                net.nestWormData.put(pos, wormData);
            }
        }

        ListTag dangerList = tag.getList("DangerZones", 10);
        for (int i = 0; i < dangerList.size(); i++) {
            net.dangerZones.add(NbtUtils.readBlockPos(dangerList.getCompound(i)));
        }

        if (tag.contains("ActiveWorms")) {
            ListTag activeList = tag.getList("ActiveWorms", 10);
            for (int i = 0; i < activeList.size(); i++) {
                CompoundTag activeTag = activeList.getCompound(i);
                BlockPos pos = NbtUtils.readBlockPos(activeTag);
                int count = activeTag.getInt("Count");
                if (count > 0) {
                    net.activeWormCounts.put(pos, count);
                }
            }
        }

        return net;
    }
}