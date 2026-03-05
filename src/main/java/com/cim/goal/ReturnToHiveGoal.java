package com.cim.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.cim.api.hive.HiveNetworkManager;
import com.cim.api.hive.HiveNetworkMember;
import com.cim.block.entity.hive.DepthWormNestBlockEntity;
import com.cim.entity.mobs.DepthWormEntity;

import java.util.EnumSet;

public class ReturnToHiveGoal extends Goal {
    private final DepthWormEntity worm;
    private BlockPos targetPos;
    private int nextSearchTick;

    public ReturnToHiveGoal(DepthWormEntity worm) {
        this.worm = worm;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = worm.getTarget();
        if (target != null && target.isAlive()) return false;
        if (worm.tickCount < nextSearchTick) return false;

        BlockPos home = worm.getHomePos();
        if (home != null) {
            HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
            BlockEntity be = worm.level().getBlockEntity(home);
            if (be instanceof HiveNetworkMember member && member.getNetworkId() != null) {
                if (manager != null && manager.hasFreeNest(member.getNetworkId(), worm.level())) {
                    this.targetPos = home;
                    return true;
                }
            }
            worm.setHomePos(null);
        }

        this.targetPos = findNearestEntry();
        if (this.targetPos != null) {
            worm.setHomePos(this.targetPos);
        }
        return this.targetPos != null;
    }

    private BlockPos findNearestEntry() {
        BlockPos wormPos = worm.blockPosition();
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return null;

        BlockPos bestNest = null;
        double bestNestDist = Double.MAX_VALUE;
        BlockPos bestSoil = null;
        double bestSoilDist = Double.MAX_VALUE;
        int radius = 12;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = wormPos.offset(x, y, z);
                    double d = worm.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    BlockEntity be = worm.level().getBlockEntity(p);
                    if (be instanceof HiveNetworkMember member && member.getNetworkId() != null) {
                        if (manager.hasFreeNest(member.getNetworkId(), worm.level())) {
                            if (be instanceof DepthWormNestBlockEntity) {
                                if (d < bestNestDist) {
                                    bestNestDist = d;
                                    bestNest = p.immutable();
                                }
                            } else {
                                if (d < bestSoilDist) {
                                    bestSoilDist = d;
                                    bestSoil = p.immutable();
                                }
                            }
                        }
                    }
                }
            }
        }
        // Сначала возвращаем гнездо, если нашли, иначе – почву
        return bestNest != null ? bestNest : bestSoil;
    }

    @Override
    public void start() {}

    @Override
    public void tick() {
        if (targetPos == null) return;

        worm.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 1.0D);

        if (worm.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5) < 2.5D) {
            HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
            BlockEntity be = worm.level().getBlockEntity(targetPos);

            if (be instanceof HiveNetworkMember member && member.getNetworkId() != null) {
                if (manager != null && manager.hasFreeNest(member.getNetworkId(), worm.level())) {
                    CompoundTag tag = new CompoundTag();
                    worm.saveWithoutId(tag);
                    if (manager.addWormToNetwork(member.getNetworkId(), tag, targetPos, worm.level())) {
                        worm.discard();
                    } else {
                        this.nextSearchTick = worm.tickCount + 100;
                        this.targetPos = null;
                        worm.setHomePos(null);
                    }
                } else {
                    this.nextSearchTick = worm.tickCount + 100;
                    this.targetPos = null;
                    worm.setHomePos(null);
                }
            } else {
                this.nextSearchTick = worm.tickCount + 100;
                this.targetPos = null;
                worm.setHomePos(null);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetPos != null &&
                (worm.getTarget() == null || !worm.getTarget().isAlive());
    }
}