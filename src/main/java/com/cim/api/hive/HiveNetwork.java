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
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class HiveNetwork {
    public final UUID id;
    public final Set<BlockPos> members = new HashSet<>();
    public final Map<BlockPos, Integer> wormCounts = new HashMap<>();
    public int killsPool = 1;

    private long lastFedTime = 0;
    private long starvationStartTime = -1;
    private static final int DAILY_UPKEEP = 1;
    private static final int UPKEEP_INTERVAL = 24000 * 3;
    private static final int STARVATION_DEATH_TIME = 24000 * 7;

    private boolean isAwakened = false;
    private static final int AWAKEN_THRESHOLD = 2;

    private int rootsBuilt = 0;
    private int soilBuilt = 0;
    private static final int ROOTS_FOR_SOIL = 3;
    private static final int SOIL_FOR_NEST = 3;

    public enum HiveState { DORMANT, EXPANSION, DEFENSIVE, AGGRESSIVE, RECOVERY, STARVATION, DEAD }
    private HiveState currentState = HiveState.DORMANT;

    private int threatLevel = 0;
    private long lastStateChange = 0;
    private int successfulAttacks = 0;

    public int targetWormCount = 6;
    public int targetNestCount = 2;
    public long lastScenarioChange = 0;
    public int wormsSpawnedTotal = 0;
    public int nestsBuiltTotal = 0;

    public int maxExpansionRadius = 8;
    public BlockPos hiveCenter = null;

    public enum DevelopmentScenario { STARTUP, RAPID_GROWTH, EXPAND_TERRITORY, BUILD_NESTS, CONSOLIDATE, AGGRESSIVE_PUSH, DEFENSIVE_BUILDUP, SURVIVAL }
    private DevelopmentScenario currentScenario = DevelopmentScenario.STARTUP;

    // ⭐ ИСПРАВЛЕНО: Глобальный счетчик вместо баганой Map. Черви принадлежат всей сети!
    public int activeWorms = 0;

    private transient int spawnRecursionDepth = 0;
    private static final int MAX_SPAWN_RECURSION = 3;

    private long lastExpansionAttempt = 0;
    private static final long EXPANSION_COOLDOWN = 100;
    private long lastRootAttempt = 0;
    private static final long ROOT_COOLDOWN = 120;

    public HiveNetwork(UUID id) {
        this.id = id;
    }

    public boolean isActive() { return killsPool > 0; }
    public boolean isAwakened() { return isAwakened; }

    public void addPoints(int points, Level level) {
        boolean wasInactive = !isActive();
        killsPool = Math.min(50, killsPool + points);

        if (!isAwakened && killsPool >= AWAKEN_THRESHOLD) {
            isAwakened = true;
            currentState = HiveState.EXPANSION;
            currentScenario = DevelopmentScenario.STARTUP;
            lastFedTime = level.getGameTime();
            System.out.println("[Hive " + id + "] ⚡ AWAKENED with " + killsPool + " points!");
        }
        if (wasInactive && isActive() && isAwakened) {
            currentState = HiveState.EXPANSION;
        }
    }

    public void addMember(BlockPos pos, boolean isNest) {
        members.add(pos);
        if (isNest) {
            wormCounts.put(pos, 0);
            if (hiveCenter == null) hiveCenter = pos.immutable();
        }
    }

    public void removeMember(BlockPos pos) {
        members.remove(pos);
        wormCounts.remove(pos);
    }

    public int getTotalWorms(Level level) {
        int total = 0;
        for (BlockPos nestPos : wormCounts.keySet()) {
            if (level.isLoaded(nestPos)) {
                BlockEntity be = level.getBlockEntity(nestPos);
                if (be instanceof DepthWormNestBlockEntity nest) {
                    total += nest.getStoredWormsCount();
                }
            }
        }
        return total;
    }

    public boolean isDead(Level level) {
        return getTotalWorms(level) == 0 && activeWorms <= 0 && members.isEmpty();
    }

    public boolean isAbandoned(Level level) {
        return getTotalWorms(level) == 0 && activeWorms <= 0 && !members.isEmpty() && killsPool <= 0 && !wormCounts.isEmpty();
    }

    // ⭐ ИСПРАВЛЕНО: Безопасное управление активными червями
    public void addActiveWorms(int count) {
        activeWorms += count;
    }

    public void removeActiveWorm() {
        if (activeWorms > 0) activeWorms--;
    }

    public int getTotalWormsIncludingActive(Level level) {
        int stored = getTotalWorms(level);
        // Защита от потенциальных рассинхронов
        int maxPossible = wormCounts.size() * 3;
        if (activeWorms > maxPossible) {
            activeWorms = maxPossible;
        }
        return stored + activeWorms;
    }

    public boolean isWithinExpansionLimit(BlockPos pos) {
        if (hiveCenter == null) return true;
        return Math.sqrt(pos.distSqr(hiveCenter)) <= maxExpansionRadius;
    }

    private void processHunger(Level level) {
        long currentTime = level.getGameTime();
        long timeSinceLastFed = currentTime - lastFedTime;

        if (timeSinceLastFed >= UPKEEP_INTERVAL && killsPool > 0) {
            int daysPassed = (int) (timeSinceLastFed / UPKEEP_INTERVAL);
            int upkeep = Math.min(daysPassed * DAILY_UPKEEP, killsPool);
            killsPool -= upkeep;
            lastFedTime = currentTime;

            if (isAwakened && killsPool < AWAKEN_THRESHOLD) {
                isAwakened = false;
                currentState = HiveState.DORMANT;
            }
        }

        if (killsPool <= 0) {
            if (starvationStartTime < 0) starvationStartTime = currentTime;
            if (currentTime - starvationStartTime > STARVATION_DEATH_TIME) {
                dieFromStarvation(level);
            } else if (currentState != HiveState.STARVATION && currentState != HiveState.DEAD) {
                currentState = HiveState.STARVATION;
                currentScenario = DevelopmentScenario.SURVIVAL;
            }
        } else {
            starvationStartTime = -1;
        }
    }

    private void dieFromStarvation(Level level) {
        System.out.println("[Hive " + id + "] ☠️ COLONY DIED FROM STARVATION!");
        currentState = HiveState.DEAD;

        for (BlockPos pos : new ArrayList<>(members)) {
            if (!level.isLoaded(pos)) continue;
            BlockState current = level.getBlockState(pos);
            if (current.is(ModBlocks.HIVE_SOIL.get())) {
                level.setBlock(pos, ModBlocks.HIVE_SOIL_DEAD.get().defaultBlockState(), 3);
            } else if (current.is(ModBlocks.DEPTH_WORM_NEST.get())) {
                level.setBlock(pos, ModBlocks.DEPTH_WORM_NEST_DEAD.get().defaultBlockState(), 3);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof DepthWormNestBlockEntity nest) nest.releaseWormsAndNotify();
            }
        }
        members.clear();
        wormCounts.clear();
        activeWorms = 0;
    }

    public void update(Level level) {
        if (level.isClientSide || currentState == HiveState.DEAD) return;

        int hungerTickRate = isAwakened ? 100 : 600;
        if (level.getGameTime() % hungerTickRate == 0) processHunger(level);

        if (!isAwakened) return;
        if (!hasAnyLoadedChunk(level)) return;

        if (level.getGameTime() % 200 == 0) {
            System.out.println("[Hive " + id + "] State: " + currentState + " | Scenario: " + currentScenario +
                    " | Points: " + killsPool + " | Worms: " + getTotalWorms(level) + "(stored) + " + activeWorms + "(active)" +
                    " | Nests: " + wormCounts.size() + " | Members: " + members.size());
        }

        if (level.getGameTime() % 40 == 0) makeDecisions(level);
    }

    private void makeDecisions(Level level) {
        if (currentState == HiveState.DEAD || currentState == HiveState.DORMANT) return;
        if (currentState == HiveState.STARVATION) {
            executeDefensiveBuildup(level, getTotalWormsIncludingActive(level), wormCounts.size());
            return;
        }

        if (level.getGameTime() % 100 == 0 || currentScenario == null) analyzeAndChooseScenario(level);
        executeScenario(level);

        long time = level.getGameTime();

        if (time - lastRootAttempt > ROOT_COOLDOWN) {
            if (tryBuildRootsSmart(level)) lastRootAttempt = time;
        }

        if (time - lastExpansionAttempt > EXPANSION_COOLDOWN) {
            tryExpandSmart(level);
            lastExpansionAttempt = time;
        }

        int totalWorms = getTotalWormsIncludingActive(level);
        int maxCapacity = wormCounts.size() * 3;
        // ⭐ ИСПРАВЛЕНО: Гнезда апгрейдятся только если реально кончается место
        if (totalWorms >= maxCapacity - 1 && wormCounts.size() < 8 && soilBuilt >= SOIL_FOR_NEST && killsPool >= 15) {
            if (tryUpgradeSoilToNest(level, true)) {
                soilBuilt = 0;
                rootsBuilt = 0;
            }
        }
    }

    private void executeDefensiveBuildup(Level level, int totalWorms, int nests) {
        if (totalWorms < nests * 2 && killsPool >= 10) spawnNewWormOptimally(level);
    }

    // ⭐ ИСПРАВЛЕНО: Корни растут только вверх/вниз
    private boolean tryBuildRootsSmart(Level level) {
        if (killsPool < 2 || rootsBuilt >= ROOTS_FOR_SOIL) return false;

        int rootCount = 0;
        int surfaceCount = 0;

        for (BlockPos pos : members) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.HIVE_ROOTS.get())) rootCount++;
            else if (state.is(ModBlocks.HIVE_SOIL.get()) || state.is(ModBlocks.DEPTH_WORM_NEST.get())) surfaceCount++;
        }

        if (surfaceCount > 0 && (double) rootCount / surfaceCount > 0.25) return false;

        BlockPos bestNest = findNestWithFewestRoots(level);
        if (bestNest == null) return false;

        List<BlockPos> candidates = new ArrayList<>();
        BlockPos above = bestNest.above();
        BlockPos below = bestNest.below();

        if (level.getBlockState(above).isAir() && isValidRootPlacement(level, above)) candidates.add(above);
        if (level.getBlockState(below).isAir() && isValidRootPlacement(level, below)) candidates.add(below);

        if (candidates.isEmpty()) return false;

        BlockPos target = candidates.get(level.getRandom().nextInt(candidates.size()));
        boolean hanging = target.getY() > bestNest.getY();

        if (ModBlocks.HIVE_ROOTS != null) {
            BlockState rootState = ModBlocks.HIVE_ROOTS.get().defaultBlockState()
                    .setValue(com.cim.block.basic.necrosis.hive.HiveRootsBlock.HANGING, hanging);
            level.setBlock(target, rootState, 3);
            killsPool -= 2;
            rootsBuilt++;
            return true;
        }
        return false;
    }

    private BlockPos findNestWithFewestRoots(Level level) {
        BlockPos best = null;
        int minRoots = Integer.MAX_VALUE;
        for (BlockPos nestPos : wormCounts.keySet()) {
            if (!level.isLoaded(nestPos)) continue;
            int nearbyRoots = 0;
            for (int x = -3; x <= 3; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -3; z <= 3; z++) {
                        if (level.getBlockState(nestPos.offset(x, y, z)).is(ModBlocks.HIVE_ROOTS.get())) nearbyRoots++;
                    }
                }
            }
            if (nearbyRoots < minRoots) {
                minRoots = nearbyRoots;
                best = nestPos;
            }
        }
        return best;
    }

    private boolean isValidRootPlacement(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (members.contains(pos.relative(dir))) return true;
        }
        return false;
    }

    private boolean tryExpandSmart(Level level) {
        if (killsPool < 5 || members.isEmpty()) return false;

        int totalWorms = getTotalWormsIncludingActive(level);
        int nests = wormCounts.size();
        boolean needMoreNests = totalWorms >= (nests * 3) - 1 && nests < 8;
        boolean needMoreSoil = members.size() < nests * 4;

        if (!needMoreNests && !needMoreSoil && !(needMoreNests && soilBuilt < SOIL_FOR_NEST)) return false;

        BlockPos expansionTarget = findExpansionTarget(level);
        if (expansionTarget != null) {
            level.setBlock(expansionTarget, ModBlocks.HIVE_SOIL.get().defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(expansionTarget);
            if (be instanceof HiveSoilBlockEntity soil) {
                soil.setNetworkId(this.id);
                HiveNetworkManager.get(level).addNode(this.id, expansionTarget, false);
            }
            killsPool -= 5;
            soilBuilt++;
            return true;
        }
        return false;
    }

    // ⭐ ИСПРАВЛЕНО: Растем вокруг гнезд (кластерами), а не одной линией
    private BlockPos findExpansionTarget(Level level) {
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos member : members) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos target = member.relative(dir);
                if (!members.contains(target) && isValidExpansionTarget(level, target) && isWithinExpansionLimit(target)) {
                    if (!candidates.contains(target)) candidates.add(target);
                }
            }
        }

        if (candidates.isEmpty()) return null;

        candidates.sort((p1, p2) -> {
            int score1 = calculatePlacementScore(p1);
            int score2 = calculatePlacementScore(p2);
            return Integer.compare(score1, score2);
        });

        return candidates.get(0); // Берем блок с наименьшим (лучшим) счетом
    }

    private int calculatePlacementScore(BlockPos pos) {
        int nestNeighbors = 0;
        int soilNeighbors = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (wormCounts.containsKey(neighbor)) nestNeighbors++;
            else if (members.contains(neighbor)) soilNeighbors++;
        }
        double dist = hiveCenter != null ? Math.sqrt(pos.distSqr(hiveCenter)) : 0;
        // Приоритет: около гнезд (очень сильно), около другой почвы (плотность), ближе к центру
        return (int) (dist - (nestNeighbors * 50) - (soilNeighbors * 10));
    }

    private boolean isValidExpansionTarget(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0 &&
                !state.is(ModBlocks.HIVE_SOIL.get()) && !state.is(ModBlocks.DEPTH_WORM_NEST.get());
    }

    private boolean tryUpgradeSoilToNest(Level level, boolean needSpaceForNewWorm) {
        if (killsPool < 15 || wormCounts.size() >= 8) return false;

        int totalWorms = getTotalWormsIncludingActive(level);
        if (!needSpaceForNewWorm && totalWorms < wormCounts.size() * 3) return false;

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos soilPos : members) {
            if (wormCounts.containsKey(soilPos)) continue;

            boolean tooClose = false;
            for (BlockPos nestPos : wormCounts.keySet()) {
                if (soilPos.distSqr(nestPos) < 4) { tooClose = true; break; }
            }
            if (!tooClose && isWithinExpansionLimit(soilPos)) candidates.add(soilPos);
        }

        if (candidates.isEmpty()) return false;

        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(hiveCenter)));

        BlockPos pos = candidates.get(0);
        members.remove(pos);
        level.setBlock(pos, ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState(), 3);
        BlockEntity newBe = ModBlockEntities.DEPTH_WORM_NEST.get().create(pos, ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState());
        if (newBe instanceof DepthWormNestBlockEntity nest) nest.setNetworkId(this.id);
        level.setBlockEntity(newBe);

        wormCounts.put(pos, 0);
        members.add(pos);
        nestsBuiltTotal++;
        killsPool -= 15;
        HiveNetworkManager.get(level).addNode(this.id, pos, true);
        return true;
    }

    private void analyzeAndChooseScenario(Level level) {
        int totalWorms = getTotalWormsIncludingActive(level);
        int nests = wormCounts.size();

        DevelopmentScenario newScenario = currentScenario;

        if (totalWorms >= (nests * 3) - 1 && nests < 8 && killsPool >= 15) newScenario = DevelopmentScenario.BUILD_NESTS;
        else if (threatLevel > 15) newScenario = DevelopmentScenario.DEFENSIVE_BUILDUP;
        else if (totalWorms < 4 && killsPool >= 15 && nests > 0) newScenario = DevelopmentScenario.RAPID_GROWTH;
        else if (killsPool > 30 && totalWorms >= 4) newScenario = DevelopmentScenario.AGGRESSIVE_PUSH;
        else if (nests == 1 && totalWorms < 3) newScenario = DevelopmentScenario.STARTUP;

        if (newScenario != currentScenario) {
            currentScenario = newScenario;
            lastScenarioChange = level.getGameTime();
        }
    }

    private void executeScenario(Level level) {
        int totalWorms = getTotalWormsIncludingActive(level);
        int maxCapacity = wormCounts.size() * 3;

        if (currentScenario == DevelopmentScenario.AGGRESSIVE_PUSH && getTotalWorms(level) >= 2 && killsPool >= 3) {
            executeCoordinatedAttack(level);
        } else if (totalWorms < maxCapacity && killsPool >= 10) {
            spawnNewWormOptimally(level);
        }
    }

    private void spawnNewWormOptimally(Level level) {
        if (spawnRecursionDepth >= MAX_SPAWN_RECURSION) return;
        spawnRecursionDepth++;

        try {
            BlockPos bestNest = null;
            int minWorms = Integer.MAX_VALUE;

            for (BlockPos nestPos : wormCounts.keySet()) {
                if (!level.isLoaded(nestPos)) continue;
                BlockEntity be = level.getBlockEntity(nestPos);
                if (be instanceof DepthWormNestBlockEntity nest && !nest.isFull()) {
                    int count = nest.getStoredWormsCount();
                    if (count < minWorms) {
                        minWorms = count;
                        bestNest = nestPos;
                    }
                }
            }

            if (bestNest != null && killsPool >= 10) {
                BlockEntity be = level.getBlockEntity(bestNest);
                if (be instanceof DepthWormNestBlockEntity nest) {
                    CompoundTag newWorm = new CompoundTag();
                    newWorm.putFloat("Health", 15.0F);
                    newWorm.putInt("Kills", 0);
                    newWorm.putLong("BoundNest", bestNest.asLong());
                    nest.addWormTag(newWorm);
                    wormCounts.put(bestNest, nest.getStoredWormsCount());
                    wormsSpawnedTotal++;
                    killsPool -= 10;
                }
            }
        } finally {
            spawnRecursionDepth = 0;
        }
    }

    private void executeCoordinatedAttack(Level level) { /* ... Без изменений ... */ }

    public boolean hasAnyLoadedChunk(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        ChunkMap chunkMap = serverLevel.getChunkSource().chunkMap;
        for (BlockPos pos : members) {
            if (!chunkMap.getPlayers(new ChunkPos(pos), false).isEmpty()) return true;
        }
        return false;
    }

    public void updateWormCount(BlockPos nestPos, int delta) {
        wormCounts.merge(nestPos, delta, Integer::sum);
        if (wormCounts.get(nestPos) < 0) wormCounts.put(nestPos, 0);
    }

    // ⭐ ИСПРАВЛЕНО: Упрощенное сохранение (теперь сохраняем ActiveWorms как int)
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putInt("KillsPool", killsPool);
        tag.putLong("LastFed", lastFedTime);
        tag.putLong("StarvationStart", starvationStartTime);
        tag.putBoolean("IsAwakened", isAwakened);
        tag.putInt("RootsBuilt", rootsBuilt);
        tag.putInt("SoilBuilt", soilBuilt);
        tag.putString("CurrentState", currentState.name());
        tag.putString("Scenario", currentScenario.name());
        tag.putInt("TargetWorms", targetWormCount);
        tag.putInt("TargetNests", targetNestCount);
        tag.putInt("MaxRadius", maxExpansionRadius);
        tag.putInt("ActiveWorms", activeWorms); // Новое сохранение
        if (hiveCenter != null) tag.putLong("HiveCenter", hiveCenter.asLong());

        ListTag membersList = new ListTag();
        for (BlockPos p : members) {
            CompoundTag pTag = NbtUtils.writeBlockPos(p);
            pTag.putBoolean("IsNest", wormCounts.containsKey(p));
            if (wormCounts.containsKey(p)) pTag.putInt("WormCount", wormCounts.get(p));
            membersList.add(pTag);
        }
        tag.put("Members", membersList);
        return tag;
    }

    public static HiveNetwork fromNBT(CompoundTag tag) {
        HiveNetwork net = new HiveNetwork(tag.getUUID("Id"));
        net.killsPool = Math.max(1, tag.getInt("KillsPool"));
        net.lastFedTime = tag.getLong("LastFed");
        net.starvationStartTime = tag.getLong("StarvationStart");
        net.isAwakened = tag.getBoolean("IsAwakened");
        net.rootsBuilt = tag.getInt("RootsBuilt");
        net.soilBuilt = tag.getInt("SoilBuilt");
        net.activeWorms = tag.getInt("ActiveWorms"); // Новая загрузка

        try { net.currentScenario = DevelopmentScenario.valueOf(tag.getString("Scenario")); } catch (Exception e) {}
        try { net.currentState = HiveState.valueOf(tag.getString("CurrentState")); } catch (Exception e) {}

        net.targetWormCount = tag.getInt("TargetWorms") == 0 ? 6 : tag.getInt("TargetWorms");
        net.targetNestCount = tag.getInt("TargetNests") == 0 ? 2 : tag.getInt("TargetNests");
        net.maxExpansionRadius = tag.getInt("MaxRadius") == 0 ? 8 : tag.getInt("MaxRadius");
        if (tag.contains("HiveCenter")) net.hiveCenter = BlockPos.of(tag.getLong("HiveCenter"));

        ListTag membersList = tag.getList("Members", 10);
        for (int i = 0; i < membersList.size(); i++) {
            CompoundTag pTag = membersList.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(pTag);
            net.members.add(pos);
            if (pTag.getBoolean("IsNest")) {
                net.wormCounts.put(pos, pTag.getInt("WormCount"));
                if (net.hiveCenter == null) net.hiveCenter = pos.immutable();
            }
        }
        return net;
    }
}