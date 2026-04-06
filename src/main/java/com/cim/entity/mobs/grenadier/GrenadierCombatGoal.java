package com.cim.entity.mobs.grenadier;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class GrenadierCombatGoal extends Goal {
    private final GrenadierZombieEntity zombie;

    // Дистанции
    private static final double PANIC_DIST_SQ = 7.0 * 7.0;   // < 7 блоков = паника
    private static final double SAFE_DIST_SQ = 10.0 * 10.0;  // > 10 блоков = безопасно
    private static final float RETREAT_SPEED = 1.4f;
    private static final float CHASE_SPEED = 1.0f;

    public GrenadierCombatGoal(GrenadierZombieEntity zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = zombie.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = zombie.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void stop() {
        // При остановке goal'а сбрасываем состояние
        zombie.setInRetreatMode(false);
        zombie.clearHiddenGrenade();
        if (!zombie.hasGrenades()) {
            zombie.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    @Override
    public void tick() {
        LivingEntity target = zombie.getTarget();
        if (target == null) return;

        double distSq = zombie.distanceToSqr(target);
        boolean hasGrenades = zombie.hasGrenades();

        // Если гранат нет - просто очищаем руки и выходим (ближний бой делает ZombieAttackGoal)
        if (!hasGrenades) {
            if (!zombie.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
                zombie.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            zombie.setInRetreatMode(false);
            return;
        }

        // --- ЛОГИКА ГРЕНАДЁРА ---

        if (zombie.isInRetreatMode()) {
            // Мы в режиме отступления (паника)
            if (distSq >= SAFE_DIST_SQ) {
                // Достаточно далеко - выходим из паники, достаём гранату
                zombie.setInRetreatMode(false);
                zombie.restoreGrenadeInHand();
            } else {
                // Продолжаем убегать, граната спрятана
                retreatFrom(target);
                zombie.getLookControl().setLookAt(target, 30f, 30f);
            }
        } else {
            // Не в панике
            if (distSq < PANIC_DIST_SQ) {
                // Слишком близко! Паникуем, убираем гранату из рук
                zombie.setInRetreatMode(true);
                zombie.hideGrenadeForRetreat();
                retreatFrom(target);
            } else {
                // Нормальная дистанция - достаём гранату и атакуем
                zombie.restoreGrenadeInHand();
                zombie.getLookControl().setLookAt(target, 30f, 30f);

                // Кидаем гранату если можно и видим цель
                if (zombie.canThrowGrenade() && zombie.getSensing().hasLineOfSight(target)) {
                    zombie.throwGrenade(target);
                }

                // Поддерживаем дистанцию: если слишком далеко (>15) - подходим, иначе стоим на месте
                if (distSq > 225.0) { // 15^2
                    zombie.getNavigation().moveTo(target, CHASE_SPEED);
                } else {
                    zombie.getNavigation().stop();
                }
            }
        }
    }

    private void retreatFrom(LivingEntity target) {
        // Ищем точку подальше от цели
        Vec3 awayPos = DefaultRandomPos.getPosAway(zombie, 16, 7, target.position());
        if (awayPos != null) {
            zombie.getNavigation().moveTo(awayPos.x, awayPos.y, awayPos.z, RETREAT_SPEED);
        } else {
            // Fallback: просто в противоположную сторону
            Vec3 dir = zombie.position().subtract(target.position()).normalize().scale(8);
            Vec3 pos = zombie.position().add(dir);
            zombie.getNavigation().moveTo(pos.x, pos.y, pos.z, RETREAT_SPEED);
        }
    }
}