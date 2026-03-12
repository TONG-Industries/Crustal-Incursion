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
        return wormCounts.isEmpty() && members.isEmpty();
    }

    public boolean isAbandoned() {
        return wormCounts.isEmpty() && !members.isEmpty();
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
            System.out.println("[Hive Tick] Network " + this.id + " | State: " + currentState +
                    " | Points: " + killsPool + " | Nodes: " + members.size() +
                    " | Worms: " + getTotalWorms() + " | Threat: " + threatLevel);

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
        int worms = getTotalWorms();
        int territory = members.size();
        return territory > 0 ? (worms * 100) / territory : 0;
    }

    private HiveState determineOptimalState() {
        if (killsPool <= 0) return HiveState.STARVATION;
        if (threatLevel > 20) return HiveState.DEFENSIVE;

        int injuredNests = 0;
        for (int count : wormCounts.values()) {
            if (count > 0 && threatLevel > 10) injuredNests++;
        }
        if (injuredNests > wormCounts.size() / 2) return HiveState.RECOVERY;

        if (getTotalWorms() >= 4 && threatLevel > 5) return HiveState.AGGRESSIVE;
        if (expansionPressure > 30) return HiveState.EXPANSION;

        return getTotalWorms() > 2 ? HiveState.AGGRESSIVE : HiveState.EXPANSION;
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
            currentState = (getTotalWorms() >= 4) ? HiveState.AGGRESSIVE : HiveState.EXPANSION;
        }
    }

    private void handleAggressive(Level level) {
        int readyWorms = getTotalWorms();
        int maxCapacity = wormCounts.size() * 3;
        int softLimit = Math.max(6, wormCounts.size() * 2);

        // ПРИОРИТЕТ: Строим гнездо если приближаемся к лимиту
        if (readyWorms >= softLimit && killsPool >= 15) {
            System.out.println("[Hive] Approaching capacity (" + readyWorms + "/" + maxCapacity + "). Building nest first.");
            if (buildNewNestIfPossible(level)) return;
        }

        // ЖЁСТКИЙ ЛИМИТ
        if (readyWorms >= maxCapacity) {
            System.out.println("[Hive] At max capacity (" + readyWorms + "/" + maxCapacity + "). Cannot spawn more.");
            if (killsPool >= 15) buildNewNestIfPossible(level);
            return;
        }

        // Накопление червей
        if (readyWorms < 4 && killsPool >= 10) {
            if (hasSpaceForMoreWorms()) {
                spawnNewWormOptimally(level);
            } else {
                System.out.println("[Hive] No space for new worm. Need to expand first.");
            }
            return;
        }

        // Атака
        if (readyWorms >= 3) {
            executeCoordinatedAttack(level);
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

        BlockPos bestNest = findNestNeedingExpansion(level);
        if (bestNest == null) return;

        BlockPos target = findAdjacentSpot(level, bestNest);
        if (target != null && canPlaceSoilSafely(level, target, this.id)) {
            placeHiveSoil(level, target, this.id);
            killsPool -= 5;
            System.out.println("[Hive] Expansion: soil at " + target + " adjacent to " + bestNest);
        }
    }

    private BlockPos findAdjacentSpot(Level level, BlockPos center) {
        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : horizontal) {
            BlockPos target = center.relative(dir);
            if (canPlaceSoilSafely(level, target, this.id)) return target;
        }
        for (Direction dir : new Direction[]{Direction.UP, Direction.DOWN}) {
            BlockPos target = center.relative(dir);
            if (canPlaceSoilSafely(level, target, this.id)) return target;
        }
        for (BlockPos member : new ArrayList<>(members)) {
            if (wormCounts.containsKey(member)) continue;
            for (Direction dir : Direction.values()) {
                BlockPos target = member.relative(dir);
                if (canPlaceSoilSafely(level, target, this.id)) return target;
            }
        }
        return null;
    }

    private boolean canPlaceSoilSafely(Level level, BlockPos pos, UUID networkId) {
        if (!isValidExpansionTarget(level, pos)) return false;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof HiveNetworkMember member) {
                if (networkId.equals(member.getNetworkId())) return true;
            }
        }
        return false;
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
        BlockPos bestNest = null;
        int minWorms = Integer.MAX_VALUE;

        for (Map.Entry<BlockPos, Integer> entry : wormCounts.entrySet()) {
            if (entry.getValue() < minWorms && entry.getValue() < 3) {
                BlockEntity be = level.getBlockEntity(entry.getKey());
                if (be instanceof DepthWormNestBlockEntity nest) {
                    bestNest = entry.getKey();
                    minWorms = entry.getValue();
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
                killsPool -= 10;
                System.out.println("[Hive] New worm spawned at " + bestNest + ". Points remaining: " + killsPool);
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

        return net;
    }
}