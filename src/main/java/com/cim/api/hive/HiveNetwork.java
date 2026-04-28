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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

    private static final int RESERVE_POINTS = 10;
    private static final int MIN_WORMS_FOR_RESERVE = 6;
    private int rootsBuilt = 0;
    private int soilBuilt = 0;

    private static final int ROOTS_FOR_SOIL = 3;
    private static final int SOIL_FOR_NEST = 3;

    private final Map<Direction, Integer> growthMomentum = new EnumMap<>(Direction.class);
    private BlockPos lastSuccessfulExpansion = null;
    private int consecutiveSuccesses = 0;

    private int pulsePhase = 0;
    private static final int PULSE_CYCLE = 200;

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

    public enum DevelopmentScenario {
        STARTUP, RAPID_GROWTH, EXPAND_TERRITORY, BUILD_NESTS,
        CONSOLIDATE, AGGRESSIVE_PUSH, DEFENSIVE_BUILDUP, SURVIVAL
    }

    private DevelopmentScenario currentScenario = DevelopmentScenario.STARTUP;
    public int activeWorms = 0;
    private transient int spawnRecursionDepth = 0;
    private static final int MAX_SPAWN_RECURSION = 3;
    private long lastExpansionAttempt = 0;
    private static final long EXPANSION_COOLDOWN = 100;
    private long lastRootAttempt = 0;
    private static final long ROOT_COOLDOWN = 120;
    private long lastStuckLog = 0;

    public HiveNetwork(UUID id) {
        this.id = id;
        for (Direction dir : Direction.values()) {
            growthMomentum.put(dir, 0);
        }
    }

    public boolean isActive() { return killsPool > 0; }
    public boolean isAwakened() { return isAwakened; }

    public int getMaxPoints() {
        return Math.max(50, wormCounts.size() * 50);
    }

    public boolean canSpendReserve() {
        return getTotalWormsIncludingActive(null) >= MIN_WORMS_FOR_RESERVE;
    }

    // ⭐ Убран автоспавн брутальных червей — они теперь эволюционируют из обычных
    public void onSuccessfulExpansion(Level level, BlockPos expansionPos) {
        // Ничего не делаем — бруталы не спавнятся автоматически
    }

    public int getAvailablePoints() {
        int totalWorms = getTotalWormsIncludingActive(null);
        if (totalWorms >= MIN_WORMS_FOR_RESERVE) {
            return Math.max(0, killsPool - RESERVE_POINTS);
        }
        return killsPool;
    }

    public void addPoints(int points, Level level) {
        boolean wasInactive = !isActive();
        killsPool = Math.min(getMaxPoints(), killsPool + points);

        if (!isAwakened && killsPool >= AWAKEN_THRESHOLD) {
            isAwakened = true;
            currentState = HiveState.EXPANSION;
            currentScenario = DevelopmentScenario.STARTUP;
            lastFedTime = level.getGameTime();
            System.out.println("[Hive " + id + "] ⚡ AWAKENED! Max cap: " + getMaxPoints());
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
        if (pos.equals(lastSuccessfulExpansion)) {
            consecutiveSuccesses = 0;
        }
    }

    public int getTotalWorms(Level level) {
        int total = 0;
        for (BlockPos nestPos : wormCounts.keySet()) {
            if (level != null && !level.isLoaded(nestPos)) continue;
            BlockEntity be = level != null ? level.getBlockEntity(nestPos) : null;
            if (be instanceof DepthWormNestBlockEntity nest) total += nest.getStoredWormsCount();
        }
        return total;
    }

    public boolean isDead(Level level) {
        return getTotalWorms(level) == 0 && activeWorms <= 0 && members.isEmpty();
    }

    public boolean isAbandoned(Level level) {
        return getTotalWorms(level) == 0 && activeWorms <= 0 && !members.isEmpty() && killsPool <= 0 && !wormCounts.isEmpty();
    }

    public void addActiveWorms(int count) { activeWorms += count; }
    public void removeActiveWorm() { if (activeWorms > 0) activeWorms--; }

    public int getTotalWormsIncludingActive(Level level) {
        int stored = getTotalWorms(level);
        int maxPossible = wormCounts.size() * 3;
        if (activeWorms > maxPossible) activeWorms = maxPossible;
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

    private void processHealing(Level level) {
        if (killsPool <= 0) return;
        int available = getAvailablePoints();

        for (BlockPos nestPos : wormCounts.keySet()) {
            if (available <= 0 && canSpendReserve()) return;
            if (!level.isLoaded(nestPos)) continue;
            BlockEntity be = level.getBlockEntity(nestPos);
            if (be instanceof DepthWormNestBlockEntity nest) {
                while ((available > 0 || !canSpendReserve()) && nest.hasInjuredWorms()) {
                    if (nest.healOneWorm()) {
                        killsPool--;
                        available = getAvailablePoints();
                    } else break;
                }
            }
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

        pulsePhase = (int) (level.getGameTime() % PULSE_CYCLE);

        if (level.getGameTime() % 200 == 0) {
            int totalWorms = getTotalWormsIncludingActive(level);
            boolean hasReserve = totalWorms >= MIN_WORMS_FOR_RESERVE;
            System.out.println("[Hive " + id + "] State: " + currentState + " | Scenario: " + currentScenario +
                    " | Points: " + killsPool + "/" + getMaxPoints() +
                    " (Available: " + getAvailablePoints() + ", Reserve: " + (hasReserve ? "YES" : "NO") + ")" +
                    " | Worms: " + totalWorms + "/" + MIN_WORMS_FOR_RESERVE + "+" + activeWorms +
                    " | Nests: " + wormCounts.size() +
                    " | Members: " + members.size() +
                    " | Momentum: " + getDominantDirection());
        }

        if (level.getGameTime() % 40 == 0) makeDecisions(level);
    }

    private void makeDecisions(Level level) {
        if (currentState == HiveState.DEAD || currentState == HiveState.DORMANT) return;

        processHealing(level);

        int totalWorms = getTotalWormsIncludingActive(level);
        int nests = wormCounts.size();
        int maxCapacity = nests * 3;
        boolean needMoreNests = totalWorms >= maxCapacity - 1 && nests < 8;
        int soilCount = Math.max(0, members.size() - nests);

        boolean povertyTrap = soilCount >= SOIL_FOR_NEST * 2 && nests <= 2 && getAvailablePoints() < 15;

        if (currentState == HiveState.STARVATION) {
            executeDefensiveBuildup(level, totalWorms, nests);
            return;
        }

        if (povertyTrap) {
            if (currentScenario != DevelopmentScenario.CONSOLIDATE) {
                currentScenario = DevelopmentScenario.CONSOLIDATE;
                System.out.println("[Hive " + id + "] 🚨 POVERTY TRAP detected! Switching to CONSOLIDATE. " +
                        "Soil=" + soilCount + " Nests=" + nests + " Points=" + getAvailablePoints());
            }
            if (killsPool >= 15 && soilCount >= SOIL_FOR_NEST) {
                boolean upgraded = tryUpgradeSoilToNestEmergency(level);
                if (upgraded) {
                    System.out.println("[Hive " + id + "] 🏗️ Emergency nest built! Escaping poverty trap.");
                }
            }
            return;
        }

        if (level.getGameTime() % 100 == 0 || currentScenario == null) {
            analyzeAndChooseScenario(level);
        }

        long time = level.getGameTime();
        if (time - lastRootAttempt > ROOT_COOLDOWN) {
            if (tryBuildRootsSmart(level)) lastRootAttempt = time;
        }

        if (needMoreNests && soilCount >= SOIL_FOR_NEST && getAvailablePoints() >= 15) {
            boolean upgraded = tryUpgradeSoilToNest(level, true);
            if (upgraded) {
                soilBuilt = 0;
                rootsBuilt = 0;
                if (level.getRandom().nextFloat() < 0.3f) {
                    tryPlaceRootsOnBlock(level, lastSuccessfulExpansion);
                }
                return;
            }
        }

        if (time - lastExpansionAttempt > EXPANSION_COOLDOWN) {
            boolean expanded = tryExpandBiological(level);
            lastExpansionAttempt = time;

            if (expanded) {
                if (level.getRandom().nextFloat() < 0.3f) {
                    tryPlaceRootsOnBlock(level, lastSuccessfulExpansion);
                }
            }

            if (!expanded && needMoreNests && (time - lastStuckLog) > 200) {
                lastStuckLog = time;
                System.out.println("[Hive " + id + "] BUILD_NESTS stuck: cannot expand. " +
                        "Available=" + getAvailablePoints() + " SoilCount=" + soilCount +
                        " Members=" + members.size() + " Nests=" + nests);
            }
        }

        if (totalWorms < maxCapacity && getAvailablePoints() >= 10) {
            spawnNewWormOptimally(level);
        }
    }

    private boolean tryUpgradeSoilToNestEmergency(Level level) {
        if (wormCounts.size() >= 8) return false;
        if (killsPool < 15) return false;

        int totalWorms = getTotalWormsIncludingActive(level);
        int maxCapacity = wormCounts.size() * 3;
        int soilCount = Math.max(0, members.size() - wormCounts.size());
        boolean emergency = soilCount >= SOIL_FOR_NEST * 2 || totalWorms >= maxCapacity - 1;
        if (!emergency) return false;

        HiveNetworkManager manager = HiveNetworkManager.get(level);
        List<ScoredPosition> candidates = new ArrayList<>();

        for (BlockPos soilPos : members) {
            if (wormCounts.containsKey(soilPos)) continue;
            if (!level.isLoaded(soilPos)) continue;

            BlockEntity be = level.getBlockEntity(soilPos);
            if (!(be instanceof HiveSoilBlockEntity soil)) continue;
            if (soil.getNetworkId() == null || !soil.getNetworkId().equals(this.id)) continue;

            boolean tooClose = false;
            int nearbyNests = 0;
            for (BlockPos nestPos : wormCounts.keySet()) {
                double dist = Math.sqrt(soilPos.distSqr(nestPos));
                if (dist < 2.0) {
                    tooClose = true;
                    break;
                }
                if (dist < 5.0) nearbyNests++;
            }
            if (tooClose) continue;

            double score = nearbyNests * 30.0 + level.getRandom().nextDouble() * 10;
            candidates.add(new ScoredPosition(soilPos, score));
        }

        if (candidates.isEmpty()) return false;

        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        BlockPos pos = candidates.get(0).pos;

        if (manager != null) manager.removeNode(this.id, pos, level);

        level.setBlock(pos, ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState(), 3);

        BlockEntity newBe = ModBlockEntities.DEPTH_WORM_NEST.get().create(pos,
                ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState());

        if (!(newBe instanceof DepthWormNestBlockEntity nest)) return false;
        nest.setNetworkId(this.id);
        level.setBlockEntity(newBe);

        if (manager != null) manager.addNode(this.id, pos, true);

        wormCounts.put(pos, 0);
        members.add(pos);

        nestsBuiltTotal++;
        killsPool -= 15;

        lastSuccessfulExpansion = pos;
        consecutiveSuccesses++;

        System.out.println("[Hive " + id + "] 🆘 EMERGENCY Nest upgraded at " + pos +
                " (cost 15, remaining: " + killsPool + ")");
        return true;
    }

    private void executeDefensiveBuildup(Level level, int totalWorms, int nests) {
        if (totalWorms < nests * 2 && killsPool >= 10) {
            spawnNewWormOptimally(level);
        }
    }

    private boolean tryPlaceRootsOnBlock(Level level, BlockPos basePos) {
        if (basePos == null) return false;
        if (getAvailablePoints() < 2 && killsPool < 2) return false;

        List<BlockPos> candidates = new ArrayList<>();
        BlockPos above = basePos.above();
        if (level.getBlockState(above).isAir() && isValidRootPlacement(above)) {
            candidates.add(above);
        }

        BlockPos below = basePos.below();
        if (level.getBlockState(below).isAir() && isValidRootPlacement(below)) {
            candidates.add(below);
        }

        if (candidates.isEmpty()) return false;

        BlockPos target = candidates.get(level.getRandom().nextInt(candidates.size()));
        boolean hanging = target.getY() < basePos.getY();

        BlockState rootState = ModBlocks.HIVE_ROOTS.get().defaultBlockState()
                .setValue(com.cim.block.basic.necrosis.hive.HiveRootsBlock.HANGING, hanging);
        level.setBlock(target, rootState, 3);

        killsPool -= 2;
        rootsBuilt++;
        onSuccessfulExpansion(level, basePos);
        System.out.println("[Hive " + id + "] 🌿 Roots placed " + (hanging ? "hanging down" : "growing up") +
                " at " + target + " on " + basePos);
        return true;
    }

    private boolean tryBuildRootsSmart(Level level) {
        if (getAvailablePoints() < 2 || rootsBuilt >= ROOTS_FOR_SOIL) return false;

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

        return tryPlaceRootsOnBlock(level, bestNest);
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

    private boolean isValidRootPlacement(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (members.contains(pos.relative(dir))) return true;
        }
        return false;
    }

    private boolean tryExpandBiological(Level level) {
        int available = getAvailablePoints();
        if (available < 5 && killsPool < 5) return false;
        if (members.isEmpty()) return false;

        int totalWorms = getTotalWormsIncludingActive(level);
        int nests = wormCounts.size();
        int maxCapacity = nests * 3;

        boolean needMoreNests = totalWorms >= maxCapacity - 1 && nests < 8;
        boolean needMoreSoil = members.size() < nests * 4;

        if (!needMoreNests && !needMoreSoil) return false;

        BlockPos target = findBiologicalExpansionTarget(level);
        if (target == null) return false;

        placeHiveSoil(level, target);
        onSuccessfulExpansion(level, target);
        if (lastSuccessfulExpansion != null) {
            Direction dir = getDirectionFromTo(lastSuccessfulExpansion, target);
            if (dir != null) {
                growthMomentum.merge(dir, 1, Integer::sum);
                consecutiveSuccesses++;
            }
        }
        lastSuccessfulExpansion = target;

        killsPool -= 5;
        soilBuilt++;

        System.out.println("[Hive " + id + "] 🌱 Biological expansion to " + target +
                " (momentum: " + getDominantDirection() + ", streak: " + consecutiveSuccesses + ")");
        return true;
    }

    private BlockPos findBiologicalExpansionTarget(Level level) {
        Set<BlockPos> candidates = new HashSet<>();

        for (BlockPos member : members) {
            for (Direction dir : Direction.values()) {
                BlockPos p = member.relative(dir);
                if (members.contains(p)) continue;
                if (!isWithinExpansionLimit(p)) continue;
                if (!canPlaceSoilAt(level, p)) continue;
                candidates.add(p.immutable());
            }
        }

        if (candidates.isEmpty()) return null;

        List<ScoredPosition> scored = new ArrayList<>();
        for (BlockPos pos : candidates) {
            double score = calculateBiologicalScore(level, pos);
            scored.add(new ScoredPosition(pos, score));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));

        int topN = Math.min(3, scored.size());
        int choice = level.getRandom().nextInt(topN);
        return scored.get(choice).pos;
    }

    private double calculateBiologicalScore(Level level, BlockPos pos) {
        double score = 0.0;

        int neighborMembers = 0;
        int neighborNests = 0;
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (members.contains(n)) {
                neighborMembers++;
                if (wormCounts.containsKey(n)) neighborNests++;
            }
        }
        score += neighborMembers * 25.0;
        score += neighborNests * 40.0;

        if (lastSuccessfulExpansion != null) {
            Direction dir = getDirectionFromTo(lastSuccessfulExpansion, pos);
            if (dir != null) {
                int momentum = growthMomentum.getOrDefault(dir, 0);
                score += momentum * 15.0;
                if (consecutiveSuccesses > 2) {
                    score += consecutiveSuccesses * 5.0;
                }
            }
        }

        if (hiveCenter != null) {
            double dist = Math.sqrt(pos.distSqr(hiveCenter));
            if (dist < 3) {
                score -= 20;
            } else if (dist > maxExpansionRadius * 0.7) {
                score -= dist * 2;
            } else {
                score += 10;
            }
        }

        if (hiveCenter != null) {
            int yDiff = Math.abs(pos.getY() - hiveCenter.getY());
            score -= yDiff * 3;
        }

        if (pulsePhase < PULSE_CYCLE / 2) {
            if (hiveCenter != null) {
                double dist = Math.sqrt(pos.distSqr(hiveCenter));
                score += dist * 2;
            }
        } else {
            score += neighborMembers * 10;
        }

        score += level.getRandom().nextDouble() * 10;

        return score;
    }

    private Direction getDirectionFromTo(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > Math.abs(dz)) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else if (Math.abs(dz) > 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return null;
    }

    private String getDominantDirection() {
        return growthMomentum.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().getName() + "(" + e.getValue() + ")")
                .orElse("none");
    }

    private boolean canPlaceSoilAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (!state.getFluidState().isEmpty()) return false;

        if (state.is(ModBlocks.HIVE_SOIL.get()) || state.is(ModBlocks.DEPTH_WORM_NEST.get()) ||
                state.is(ModBlocks.HIVE_SOIL_DEAD.get()) || state.is(ModBlocks.DEPTH_WORM_NEST_DEAD.get())) {
            return false;
        }

        if (!state.isAir()) {
            return state.getDestroySpeed(level, pos) >= 0;
        }

        BlockPos below = pos.below();
        boolean hasSolidBelow = !level.getBlockState(below).isAir() && level.getBlockState(below).getFluidState().isEmpty();
        boolean belowIsMember = members.contains(below);

        int neighborMembers = 0;
        for (Direction dir : Direction.values()) {
            if (members.contains(pos.relative(dir))) neighborMembers++;
        }

        return hasSolidBelow || belowIsMember || neighborMembers >= 2;
    }

    private void placeHiveSoil(Level level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.HIVE_SOIL.get().defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HiveSoilBlockEntity soil) {
            soil.setNetworkId(this.id);
            HiveNetworkManager manager = HiveNetworkManager.get(level);
            if (manager != null) manager.addNode(this.id, pos, false);
        }
    }

    private boolean tryUpgradeSoilToNest(Level level, boolean needSpaceForNewWorm) {
        if (getAvailablePoints() < 15) return false;
        if (wormCounts.size() >= 8) return false;

        int totalWorms = getTotalWormsIncludingActive(level);
        int maxCapacity = wormCounts.size() * 3;

        if (!needSpaceForNewWorm && totalWorms < maxCapacity) return false;

        HiveNetworkManager manager = HiveNetworkManager.get(level);

        List<ScoredPosition> candidates = new ArrayList<>();

        for (BlockPos soilPos : members) {
            if (wormCounts.containsKey(soilPos)) continue;
            if (!level.isLoaded(soilPos)) continue;

            BlockEntity be = level.getBlockEntity(soilPos);
            if (!(be instanceof HiveSoilBlockEntity soil)) continue;
            if (soil.getNetworkId() == null || !soil.getNetworkId().equals(this.id)) continue;

            boolean tooClose = false;
            int nearbyNests = 0;
            for (BlockPos nestPos : wormCounts.keySet()) {
                double dist = Math.sqrt(soilPos.distSqr(nestPos));
                if (dist < 2.0) {
                    tooClose = true;
                    break;
                }
                if (dist < 5.0) nearbyNests++;
            }
            if (tooClose) continue;

            double score = nearbyNests * 20.0;
            score += level.getRandom().nextDouble() * 5;

            candidates.add(new ScoredPosition(soilPos, score));
        }

        if (candidates.isEmpty()) return false;

        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        BlockPos pos = candidates.get(0).pos;

        if (manager != null) manager.removeNode(this.id, pos, level);

        level.setBlock(pos, ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState(), 3);

        BlockEntity newBe = ModBlockEntities.DEPTH_WORM_NEST.get().create(pos,
                ModBlocks.DEPTH_WORM_NEST.get().defaultBlockState());

        if (!(newBe instanceof DepthWormNestBlockEntity nest)) return false;
        nest.setNetworkId(this.id);
        level.setBlockEntity(newBe);

        if (manager != null) manager.addNode(this.id, pos, true);

        wormCounts.put(pos, 0);
        members.add(pos);

        nestsBuiltTotal++;
        onSuccessfulExpansion(level, pos);
        killsPool -= 15;

        lastSuccessfulExpansion = pos;
        consecutiveSuccesses++;

        System.out.println("[Hive " + id + "] 🏠 Nest upgraded at " + pos + " (score: " + candidates.get(0).score + ")");
        return true;
    }

    private void analyzeAndChooseScenario(Level level) {
        int totalWorms = getTotalWormsIncludingActive(level);
        int nests = wormCounts.size();
        int maxCapacity = nests * 3;
        int soilCount = Math.max(0, members.size() - nests);

        boolean povertyTrap = soilCount >= SOIL_FOR_NEST * 2 && nests <= 2 && getAvailablePoints() < 15;

        DevelopmentScenario newScenario = currentScenario;

        if (povertyTrap) {
            newScenario = DevelopmentScenario.CONSOLIDATE;
        } else if (totalWorms >= maxCapacity - 1 && nests < 8 && getAvailablePoints() >= 15) {
            newScenario = DevelopmentScenario.BUILD_NESTS;
        } else if (threatLevel > 15) {
            newScenario = DevelopmentScenario.DEFENSIVE_BUILDUP;
        } else if (totalWorms < 4 && getAvailablePoints() >= 15 && nests > 0) {
            newScenario = DevelopmentScenario.RAPID_GROWTH;
        } else if (nests == 1 && totalWorms < 3) {
            newScenario = DevelopmentScenario.STARTUP;
        } else if (consecutiveSuccesses > 5 && getAvailablePoints() > 20) {
            newScenario = DevelopmentScenario.EXPAND_TERRITORY;
        } else {
            newScenario = DevelopmentScenario.CONSOLIDATE;
        }

        if (newScenario != currentScenario) {
            currentScenario = newScenario;
            lastScenarioChange = level.getGameTime();
            consecutiveSuccesses = 0;
            System.out.println("[Hive " + id + "] 📊 Scenario changed to: " + newScenario);
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

            if (bestNest != null && getAvailablePoints() >= 10) {
                BlockEntity be = level.getBlockEntity(bestNest);
                if (be instanceof DepthWormNestBlockEntity nest) {
                    CompoundTag newWorm = new CompoundTag();
                    newWorm.putFloat("Health", 15.0F);
                    newWorm.putInt("Kills", 0);
                    newWorm.putInt("RawKills", 0);
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

    private static class ScoredPosition {
        final BlockPos pos;
        final double score;

        ScoredPosition(BlockPos pos, double score) {
            this.pos = pos;
            this.score = score;
        }
    }

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
        tag.putInt("ActiveWorms", activeWorms);
        tag.putInt("PulsePhase", pulsePhase);
        tag.putInt("ConsecutiveSuccesses", consecutiveSuccesses);

        if (lastSuccessfulExpansion != null) {
            tag.putLong("LastExpansion", lastSuccessfulExpansion.asLong());
        }

        CompoundTag momentumTag = new CompoundTag();
        for (Map.Entry<Direction, Integer> entry : growthMomentum.entrySet()) {
            momentumTag.putInt(entry.getKey().getName(), entry.getValue());
        }
        tag.put("GrowthMomentum", momentumTag);

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
        net.activeWorms = tag.getInt("ActiveWorms");
        net.pulsePhase = tag.getInt("PulsePhase");
        net.consecutiveSuccesses = tag.getInt("ConsecutiveSuccesses");

        if (tag.contains("LastExpansion")) {
            net.lastSuccessfulExpansion = BlockPos.of(tag.getLong("LastExpansion"));
        }

        CompoundTag momentumTag = tag.getCompound("GrowthMomentum");
        for (Direction dir : Direction.values()) {
            if (momentumTag.contains(dir.getName())) {
                net.growthMomentum.put(dir, momentumTag.getInt(dir.getName()));
            }
        }

        try { net.currentScenario = DevelopmentScenario.valueOf(tag.getString("Scenario")); } catch (Exception ignored) {}
        try { net.currentState = HiveState.valueOf(tag.getString("CurrentState")); } catch (Exception ignored) {}

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