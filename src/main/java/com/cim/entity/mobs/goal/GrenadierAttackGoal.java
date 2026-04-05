package com.cim.entity.mobs.goal;

import com.cim.entity.mobs.GrenadierZombieEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class GrenadierAttackGoal extends Goal {

    private final GrenadierZombieEntity zombie;
    private final double speedModifier;
    private final float maxAttackDistanceSq;
    private final float minAttackDistanceSq;

    private int seeTime;
    private int strafingTime = -1;
    private boolean strafingClockwise;
    private boolean strafingBackwards;

    public GrenadierAttackGoal(GrenadierZombieEntity zombie, double speedModifier,
                               float maxAttackDistance, float minAttackDistance) {
        this.zombie = zombie;
        this.speedModifier = speedModifier;
        this.maxAttackDistanceSq = maxAttackDistance * maxAttackDistance;
        this.minAttackDistanceSq = minAttackDistance * minAttackDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.zombie.getTarget();
        return target != null && target.isAlive() && this.zombie.hasGrenades() && this.zombie.canThrowGrenade();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.zombie.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        return this.zombie.hasGrenades();
    }

    @Override
    public void stop() {
        this.zombie.setAggressive(false);
        this.seeTime = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.zombie.getTarget();
        if (target == null) return;

        // Если на кд — просто приближаемся/отходим, но не атакуем
        if (!this.zombie.canThrowGrenade()) {
            this.zombie.setAggressive(false);
            // Продолжаем двигаться, но не бросаем
        }

        double distanceSq = this.zombie.distanceToSqr(target);
        boolean canSee = this.zombie.getSensing().hasLineOfSight(target);
        boolean hasSeen = this.seeTime > 0;

        if (canSee != hasSeen) {
            this.seeTime = 0;
        }

        if (canSee) {
            this.seeTime++;
        } else {
            this.seeTime--;
        }

        // Движение
        if (distanceSq < minAttackDistanceSq && canSee) {
            this.zombie.getNavigation().stop();
            this.strafingTime++;
        } else if (distanceSq > maxAttackDistanceSq) {
            this.zombie.getNavigation().moveTo(target, this.speedModifier);
            this.strafingTime = -1;
        } else {
            this.zombie.getNavigation().stop();
            this.strafingTime++;
        }

        // Стрейфинг
        if (this.strafingTime >= 20) {
            if (this.zombie.getRandom().nextFloat() < 0.3F) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            if (this.zombie.getRandom().nextFloat() < 0.3F) {
                this.strafingBackwards = !this.strafingBackwards;
            }
            this.strafingTime = 0;
        }

        if (this.strafingTime > -1) {
            if (distanceSq > maxAttackDistanceSq * 0.75F) {
                this.strafingBackwards = false;
            } else if (distanceSq < minAttackDistanceSq * 1.5F) {
                this.strafingBackwards = true;
            }

            this.zombie.getMoveControl().strafe(
                    this.strafingBackwards ? -0.5F : 0.5F,
                    this.strafingClockwise ? 0.5F : -0.5F
            );
            this.zombie.lookAt(target, 30.0F, 30.0F);
        } else {
            this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        // Атака только если видим, в зоне, и НЕ на кд
        if (canSee && this.seeTime >= 5 && distanceSq <= maxAttackDistanceSq &&
                distanceSq >= minAttackDistanceSq && this.zombie.canThrowGrenade()) {
            this.zombie.setAggressive(true);
            this.zombie.throwGrenade(target);
        } else {
            this.zombie.setAggressive(false);
        }
    }
}