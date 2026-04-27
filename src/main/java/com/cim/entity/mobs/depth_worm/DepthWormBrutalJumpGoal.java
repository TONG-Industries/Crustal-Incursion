package com.cim.entity.mobs.depth_worm;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class DepthWormBrutalJumpGoal extends Goal {
    private final DepthWormBrutalEntity worm;
    private final float jumpRangeMin, jumpRangeMax;

    private LivingEntity target;
    private int prepareTimer;
    private boolean jumpPerformed;
    private static final int PREPARE_TIME = 10; // 0.5 сек

    // Ракетная баллистика
    private static final double MAX_HORIZONTAL_SPEED = 3.5;
    private static final double MAX_VERTICAL_SPEED = 2.0;
    private static final double GRAVITY = 0.08; // стандартная гравитация LivingEntity

    // Анти-застревание
    private int jumpTickCounter = 0;
    private static final int MAX_JUMP_TICKS = 60; // 3 сек максимум
    private int noMovementTicks = 0;
    private Vec3 lastJumpPos = Vec3.ZERO;

    public DepthWormBrutalJumpGoal(DepthWormBrutalEntity worm, double speedModifier, float jumpRangeMin, float jumpRangeMax) {
        this.worm = worm;
        this.jumpRangeMin = jumpRangeMin;
        this.jumpRangeMax = jumpRangeMax;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        this.target = this.worm.getTarget();
        if (this.target == null || !this.target.isAlive()) return false;
        if (this.worm.isImpaling() || this.worm.isPreparingJump()) return false;

        double dist = this.worm.distanceTo(this.target);
        return dist >= this.jumpRangeMin && dist <= this.jumpRangeMax;
    }

    @Override
    public boolean canContinueToUse() {
        if (!jumpPerformed) {
            return prepareTimer > 0 && target != null && target.isAlive();
        }
        // Зависли в воздухе слишком долго
        if (jumpTickCounter > MAX_JUMP_TICKS) return false;
        // Приземлились и не насаживаем — мгновенный сброс
        if (worm.onGround() && !worm.isImpaling()) return false;
        return true;
    }

    @Override
    public void start() {
        this.prepareTimer = PREPARE_TIME;
        this.jumpPerformed = false;
        this.jumpTickCounter = 0;
        this.noMovementTicks = 0;
        this.worm.setPreparingJump(true);
        this.worm.getNavigation().stop();
        this.worm.setAttacking(true);
    }

    @Override
    public void stop() {
        this.target = null;
        this.worm.setPreparingJump(false);
        this.worm.setAttacking(false);
        this.worm.setFlying(false);
        this.jumpPerformed = false;
        this.jumpTickCounter = 0;
        this.noMovementTicks = 0;
        this.worm.getNavigation().stop();

        // Если остались висеть в воздухе без жертвы — сбрасываем вниз
        if (!worm.onGround() && !worm.isImpaling()) {
            Vec3 v = this.worm.getDeltaMovement();
            this.worm.setDeltaMovement(v.multiply(0.5, -0.3, 0.5));
        }
    }

    @Override
    public void tick() {
        if (this.target == null || !this.target.isAlive()) {
            abortPrepare();
            return;
        }

        if (!jumpPerformed) {
            // Фаза прицеливания (0.5 сек)
            if (this.worm.distanceTo(this.target) > this.jumpRangeMax + 6.0F) {
                abortPrepare();
                return;
            }

            this.worm.getLookControl().setLookAt(this.target, 30.0F, 30.0F);

            if (--this.prepareTimer <= 0) {
                if (tryExecuteJump()) {
                    jumpPerformed = true;
                    lastJumpPos = this.worm.position();
                } else {
                    abortPrepare();
                }
            }
        } else {
            // Фаза полёта
            jumpTickCounter++;

            // Анти-застревание: если не двигаемся — считаем тики
            Vec3 cur = this.worm.position();
            if (cur.distanceToSqr(lastJumpPos) < 0.0025) { // 0.05 блока
                if (++noMovementTicks > 8) return; // canContinueToUse сбросит в следующем тике
            } else {
                noMovementTicks = 0;
                lastJumpPos = cur;
            }

            checkMidAirCollision();
        }
    }

    private void abortPrepare() {
        this.prepareTimer = 0;
        this.worm.setPreparingJump(false);
        this.worm.setAttacking(false);
        this.jumpPerformed = true;
    }

    // =====================================================================
    // БАЛЛИСТИКА (адаптировано с TurretLightComputer)
    // =====================================================================

    private boolean tryExecuteJump() {
        Vec3 wormPos = this.worm.position();
        Vec3 targetPos = this.target.position();
        Vec3 targetVel = this.target.getDeltaMovement();

        // 1. Предсказание позиции цели (итеративное, как у турели)
        double flatDist = Math.sqrt(
                (targetPos.x - wormPos.x) * (targetPos.x - wormPos.x) +
                        (targetPos.z - wormPos.z) * (targetPos.z - wormPos.z)
        );

        double t = solveFlightTime(flatDist, targetPos.y - wormPos.y);
        if (t < 0) return false;

        // 2 итерации упреждения — достаточно для моба
        Vec3 predictedPos = targetPos;
        for (int i = 0; i < 2; i++) {
            predictedPos = targetPos.add(targetVel.x * t, 0, targetVel.z * t);
            double newFlat = Math.sqrt(
                    (predictedPos.x - wormPos.x) * (predictedPos.x - wormPos.x) +
                            (predictedPos.z - wormPos.z) * (predictedPos.z - wormPos.z)
            );
            t = solveFlightTime(newFlat, predictedPos.y - wormPos.y);
            if (t < 0) return false;
        }

        double dx = predictedPos.x - wormPos.x;
        double dy = (predictedPos.y + this.target.getBbHeight() * 0.5) - (wormPos.y + this.worm.getBbHeight() * 0.3);
        double dz = predictedPos.z - wormPos.z;

        // 2. Расчёт вектора запуска
        Vec3 velocity = calculateLaunchVelocity(dx, dy, dz, t);
        if (velocity == null) return false;

        // 3. Проверка точной траектории на столкновения
        if (!isTrajectoryClear(wormPos, velocity, t)) {
            return false;
        }

        // 4. Применение
        double yaw = Math.atan2(dz, dx) * (180 / Math.PI) - 90;
        this.worm.setYRot((float) yaw);
        this.worm.yHeadRot = (float) yaw;
        this.worm.yBodyRot = (float) yaw;

        this.worm.setDeltaMovement(velocity);
        this.worm.setFlying(true);
        this.worm.hasImpulse = true;
        this.worm.ignoreFallDamageTicks = 60;

        this.worm.setPreparingJump(false);
        this.worm.setAttacking(false);

        return true;
    }

    /** Подбирает время полёта так, чтобы вертикальная скорость не превысила лимит */
    private double solveFlightTime(double horizontalDist, double dy) {
        double t = Math.max(3.0, horizontalDist / MAX_HORIZONTAL_SPEED);
        double vy = dy / t + 0.5 * GRAVITY * t;

        // Если нужно слишком сильно вверх — увеличиваем t (более пологая дуга)
        while (vy > MAX_VERTICAL_SPEED && t < 80.0) {
            t += 1.0;
            vy = dy / t + 0.5 * GRAVITY * t;
        }

        if (vy > MAX_VERTICAL_SPEED * 1.3) return -1; // недостижимо
        return t;
    }

    /** Расчёт скорости: x = vx*t, y = vy*t - 0.5*g*t² */
    private Vec3 calculateLaunchVelocity(double dx, double dy, double dz, double t) {
        if (t <= 0) return null;
        double vx = dx / t;
        double vz = dz / t;
        double vy = dy / t + 0.5 * GRAVITY * t;

        double hSpeed = Math.sqrt(vx * vx + vz * vz);
        if (hSpeed > MAX_HORIZONTAL_SPEED * 1.1) return null;
        if (vy > MAX_VERTICAL_SPEED * 1.2 || vy < -MAX_VERTICAL_SPEED) return null;

        return new Vec3(vx, vy, vz);
    }

    /** Точная проверка траектории по параболе с шагом 0.5 блока */
    private boolean isTrajectoryClear(Vec3 start, Vec3 velocity, double flightTime) {
        int steps = (int) (flightTime * 2.0) + 1;
        for (int i = 0; i <= steps; i++) {
            double t = (i / (double) steps) * flightTime;
            double x = start.x + velocity.x * t;
            double z = start.z + velocity.z * t;
            double y = start.y + velocity.y * t - 0.5 * GRAVITY * t * t;

            AABB box = new AABB(x - 0.3, y, z - 0.3, x + 0.3, y + 0.6, z + 0.3);
            if (!worm.level().noCollision(box)) {
                return false;
            }
        }
        return true;
    }

    // =====================================================================
    // КОЛЛИЗИЯ С ЦЕЛЬЮ
    // =====================================================================

    private void checkMidAirCollision() {
        if (this.worm.isImpaling()) return;

        AABB wormBox = this.worm.getBoundingBox().inflate(0.4);
        if (wormBox.intersects(this.target.getBoundingBox())) {
            executeImpaleOrBounce();
        }
    }

    private void executeImpaleOrBounce() {
        int armor = this.target.getArmorValue();

        if (armor < 12) {
            this.target.hurt(this.worm.damageSources().mobAttack(this.worm), 10.0F);
            this.worm.setImpaledTarget(this.target);

            Vec3 vel = this.worm.getDeltaMovement();
            this.worm.setDeltaMovement(vel.scale(0.85).add(0, 0.05, 0));
        } else {
            this.target.hurt(this.worm.damageSources().mobAttack(this.worm), 5.0F);

            Vec3 bounce = this.worm.getLookAngle().scale(-0.6).add(0, 0.4, 0);
            this.worm.setDeltaMovement(bounce);
            this.worm.setFlying(false);
        }
    }
}