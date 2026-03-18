package com.cim.goal;

import com.cim.api.hive.HiveNetwork;
import com.cim.api.hive.HiveNetworkManager;
import com.cim.api.hive.HiveNetworkMember;
import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.hive.DepthWormNestBlockEntity;
import com.cim.block.entity.hive.HiveSoilBlockEntity;
import com.cim.entity.mobs.DepthWormEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

public class ReturnToHiveGoal extends Goal {
    private final DepthWormEntity worm;
    private BlockPos targetPos;
    private int nextSearchTick;
    private boolean targetIsSoil = false;
    private int soilEntryAttempts = 0;
    private static final int MAX_SOIL_ATTEMPTS = 20;

    public ReturnToHiveGoal(DepthWormEntity worm) {
        this.worm = worm;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = worm.getTarget();
        if (target != null && target.isAlive()) return false;
        if (worm.tickCount < nextSearchTick) return false;

        nextSearchTick = worm.tickCount + 10 + worm.getRandom().nextInt(10);

        BlockPos boundNest = worm.getBoundNestPos();
        if (boundNest != null && isValidEntryPoint(boundNest)) {
            this.targetPos = boundNest;
            this.targetIsSoil = isSoil(boundNest);
            return true;
        }

        BlockPos entry = findNearestEntryPoint();
        if (entry != null) {
            this.targetPos = entry;
            this.targetIsSoil = isSoil(entry);
            worm.bindToNest(findNearestNest(entry));
            return true;
        }
        return false;
    }

    private boolean isValidEntryPoint(BlockPos pos) {
        BlockEntity be = worm.level().getBlockEntity(pos);
        if (be instanceof DepthWormNestBlockEntity nest) return !nest.isFull() && nest.getNetworkId() != null;
        if (be instanceof HiveSoilBlockEntity soil) {
            UUID netId = soil.getNetworkId();
            if (netId == null) return false;
            HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
            if (manager != null) {
                HiveNetwork network = manager.getNetwork(netId);
                return network != null && !network.wormCounts.isEmpty();
            }
        }
        return false;
    }

    private boolean isSoil(BlockPos pos) {
        return worm.level().getBlockState(pos).is(ModBlocks.HIVE_SOIL.get());
    }

    private BlockPos findNearestNest(BlockPos entryPos) {
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return entryPos;
        BlockEntity be = worm.level().getBlockEntity(entryPos);
        if (!(be instanceof HiveNetworkMember member) || member.getNetworkId() == null) return entryPos;

        HiveNetwork network = manager.getNetwork(member.getNetworkId());
        if (network == null) return entryPos;

        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        for (BlockPos nestPos : network.wormCounts.keySet()) {
            double dist = entryPos.distSqr(nestPos);
            if (dist < minDist) { minDist = dist; nearest = nestPos; }
        }
        return nearest != null ? nearest : entryPos;
    }

    private BlockPos findNearestEntryPoint() {
        BlockPos wormPos = worm.blockPosition();
        BlockPos bestEntry = null;
        double bestDist = Double.MAX_VALUE;
        int radius = 20;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = wormPos.offset(x, y, z);
                    BlockEntity be = worm.level().getBlockEntity(p);
                    if (be instanceof DepthWormNestBlockEntity nest && !nest.isFull()) {
                        double d = worm.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                        if (d < bestDist) { bestDist = d; bestEntry = p.immutable(); }
                    }
                }
            }
        }

        if (bestEntry == null) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -8; y <= 8; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos p = wormPos.offset(x, y, z);
                        if (isValidSoilEntry(p)) {
                            double d = worm.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                            if (d < bestDist * 1.5) { bestDist = d; bestEntry = p.immutable(); }
                        }
                    }
                }
            }
        }
        return bestEntry;
    }

    private boolean isValidSoilEntry(BlockPos pos) {
        BlockEntity be = worm.level().getBlockEntity(pos);
        if (!(be instanceof HiveSoilBlockEntity soil) || soil.getNetworkId() == null) return false;
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return false;
        HiveNetwork network = manager.getNetwork(soil.getNetworkId());
        return network != null && !network.wormCounts.isEmpty();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        if (targetIsSoil && soilEntryAttempts > MAX_SOIL_ATTEMPTS) {
            this.targetPos = null;
            this.targetIsSoil = false;
            this.soilEntryAttempts = 0;
            return;
        }

        if (targetIsSoil) soilEntryAttempts++;

        double targetX = targetPos.getX() + 0.5;
        double targetY = targetPos.getY() + (targetIsSoil ? 0.5 : 0.8);
        double targetZ = targetPos.getZ() + 0.5;
        double distSq = worm.distanceToSqr(targetX, targetY, targetZ);

        if (targetIsSoil && distSq < 2.0D) {
            Vec3 downPull = new Vec3(0, -0.3, 0);
            worm.setDeltaMovement(worm.getDeltaMovement().add(downPull));
            worm.getLookControl().setLookAt(targetX, targetY, targetZ, 30.0F, 30.0F);
            if (worm.getY() < targetY - 1.0) enterNetwork(worm.blockPosition());
            return;
        }

        if (distSq < 4.0D) {
            worm.getNavigation().stop();
            Vec3 pull = new Vec3(targetX - worm.getX(), targetY - worm.getY(), targetZ - worm.getZ()).normalize().scale(0.15);
            worm.setDeltaMovement(worm.getDeltaMovement().add(pull));
            worm.getLookControl().setLookAt(targetX, targetY, targetZ, 30.0F, 30.0F);
        } else {
            worm.getNavigation().moveTo(targetX, targetY, targetZ, 1.2D);
            worm.getLookControl().setLookAt(targetX, targetY + 0.5, targetZ);
        }

        if (!targetIsSoil && distSq < 1.5D) enterNetwork(targetPos);
    }

    private void enterNetwork(BlockPos entryPos) {
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return;

        BlockEntity be = worm.level().getBlockEntity(entryPos);
        UUID netId = (be instanceof HiveNetworkMember member) ? member.getNetworkId() : null;
        if (netId == null) return;

        HiveNetwork network = manager.getNetwork(netId);
        if (network == null) return;

        int kills = worm.getKills();
        if (kills > 0) network.addPoints(kills, worm.level());

        BlockPos boundNest = worm.getBoundNestPos();
        BlockPos actualNest = (boundNest == null || targetIsSoil) ? findNearestNest(entryPos) : boundNest;

        CompoundTag tag = new CompoundTag();
        worm.saveWithoutId(tag);
        tag.putInt("Kills", 0);
        tag.putLong("BoundNest", actualNest.asLong());

        boolean success = manager.addWormToNetwork(netId, tag, entryPos, worm.level());

        if (success) {
            // ⭐ ИСПРАВЛЕНО: Безопасно снимаем червя с глобального пула
            network.removeActiveWorm();
            worm.discard();
            System.out.println("[Hive] Worm returned via " + (targetIsSoil ? "soil" : "nest") + ". Active remaining: " + network.activeWorms);
        } else {
            this.targetPos = null;
            this.soilEntryAttempts = 0;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPos == null) return false;
        if (targetIsSoil) {
            if (soilEntryAttempts > MAX_SOIL_ATTEMPTS) return false;
            return worm.getY() > targetPos.getY() - 2.0 && worm.getTarget() == null;
        }
        return isValidEntryPoint(targetPos) && worm.getTarget() == null;
    }

    @Override
    public void stop() {
        this.targetPos = null;
        this.targetIsSoil = false;
        worm.getNavigation().stop();
    }
}