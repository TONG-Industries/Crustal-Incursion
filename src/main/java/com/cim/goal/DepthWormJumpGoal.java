package com.cim.goal;


import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import com.cim.entity.mobs.DepthWormEntity;

import java.util.EnumSet;

public class DepthWormJumpGoal extends Goal {
    private final DepthWormEntity worm;
    private LivingEntity target;
    private final double speedModifier;
    private final float jumpRangeMin, jumpRangeMax;
    private int jumpTimer;
    private boolean jumpPerformed;         // новый флаг
    private final int PREPARE_TIME = 30;

    public DepthWormJumpGoal(DepthWormEntity worm, double speedModifier, float jumpRangeMin, float jumpRangeMax) {
        this.worm = worm;
        this.speedModifier = speedModifier;
        this.jumpRangeMin = jumpRangeMin;
        this.jumpRangeMax = jumpRangeMax;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.worm.getTarget();
        if (this.target == null || !this.target.isAlive()) return false;
        double dist = this.worm.distanceTo(this.target);
        return dist >= this.jumpRangeMin && dist <= this.jumpRangeMax;
    }

    @Override
    public boolean canContinueToUse() {
        // продолжаем, пока не выполнили прыжок и таймер ещё идёт
        return !jumpPerformed && jumpTimer > 0;
    }

    @Override
    public void start() {
        this.jumpTimer = PREPARE_TIME;
        this.jumpPerformed = false;
        this.worm.setAttacking(true);
        this.worm.getNavigation().stop();
        this.worm.hasImpulse = true;
    }

    @Override
    public void stop() {
        this.target = null;
        this.worm.setAttacking(false);
        this.jumpPerformed = false;
    }

    @Override
    public void tick() {
        if (this.target == null || !this.target.isAlive()) {
            // цель умерла – прерываем гол
            this.worm.setAttacking(false);
            this.jumpTimer = 0;
            this.jumpPerformed = true;  // чтобы гол завершился
            return;
        }

        double dist = this.worm.distanceTo(this.target);
        if (dist > this.jumpRangeMax + 2.0F) {
            this.worm.setAttacking(false);
            this.jumpTimer = 0;
            this.jumpPerformed = true;
            return;
        }

        this.worm.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

        if (--this.jumpTimer <= 0 && !jumpPerformed) {
            doJump();
            jumpPerformed = true;
            this.worm.ignoreFallDamageTicks = 30;
            // после прыжка гол завершится на следующем тике (canContinueToUse → false)
        }
    }

    private void doJump() {
        Vec3 targetPos = this.target.position().add(0, this.target.getBbHeight() * 0.5, 0);
        Vec3 toTarget = targetPos.subtract(this.worm.position());

        // Поворачиваем червя мордой к цели
        double yaw = Math.atan2(toTarget.z, toTarget.x) * (180 / Math.PI) - 90;
        this.worm.setYRot((float) yaw);
        this.worm.yHeadRot = (float) yaw;
        this.worm.yBodyRot = (float) yaw;
        this.worm.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z, 30.0F, 30.0F);

        double horizontalDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double speed = 0.8 + (horizontalDist * 0.1);
        Vec3 velocity = toTarget.normalize().scale(speed);
        double verticalBoost = 0.15 + (horizontalDist * 0.04);
        this.worm.setDeltaMovement(velocity.x, verticalBoost, velocity.z);
        this.worm.setFlying(true);
        this.worm.ignoreFallDamageTicks = 30;
    }

}