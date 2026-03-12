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
    private boolean targetIsSoil = false; // НОВОЕ: Цель — почва, не гнездо

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

        // Проверяем привязку к гнезду
        BlockPos boundNest = worm.getBoundNestPos();
        if (boundNest != null && isValidEntryPoint(boundNest)) {
            this.targetPos = boundNest;
            this.targetIsSoil = isSoil(boundNest);
            return true;
        }

        // Ищем ближайшую точку входа (гнездо ИЛИ почву)
        BlockPos entry = findNearestEntryPoint();
        if (entry != null) {
            this.targetPos = entry;
            this.targetIsSoil = isSoil(entry);
            worm.bindToNest(findNearestNest(entry)); // Привязываем к ближайшему гнезду
            return true;
        }

        return false;
    }

    // НОВОЕ: Проверка валидности точки входа (гнездо или почва сети)
    private boolean isValidEntryPoint(BlockPos pos) {
        BlockEntity be = worm.level().getBlockEntity(pos);
        if (be instanceof DepthWormNestBlockEntity nest) {
            return !nest.isFull() && nest.getNetworkId() != null;
        }
        if (be instanceof HiveSoilBlockEntity soil) {
            UUID netId = soil.getNetworkId();
            if (netId == null) return false;
            // Проверяем, есть ли в сети свободное гнездо
            HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
            if (manager != null) {
                HiveNetwork network = manager.getNetwork(netId);
                return network != null && !network.wormCounts.isEmpty();
            }
        }
        return false;
    }

    // НОВОЕ: Это почва?
    private boolean isSoil(BlockPos pos) {
        return worm.level().getBlockState(pos).is(ModBlocks.HIVE_SOIL.get());
    }

    // НОВОЕ: Найти ближайшее гнездо к точке входа
    private BlockPos findNearestNest(BlockPos entryPos) {
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return entryPos;

        BlockEntity be = worm.level().getBlockEntity(entryPos);
        if (!(be instanceof HiveNetworkMember member)) return entryPos;

        UUID netId = member.getNetworkId();
        if (netId == null) return entryPos;

        HiveNetwork network = manager.getNetwork(netId);
        if (network == null) return entryPos;

        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;

        for (BlockPos nestPos : network.wormCounts.keySet()) {
            double dist = entryPos.distSqr(nestPos);
            if (dist < minDist) {
                minDist = dist;
                nearest = nestPos;
            }
        }

        return nearest != null ? nearest : entryPos;
    }

    // НОВОЕ: Поиск ближайшей точки входа (гнездо ИЛИ почва)
    private BlockPos findNearestEntryPoint() {
        BlockPos wormPos = worm.blockPosition();
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return null;

        BlockPos bestEntry = null;
        double bestDist = Double.MAX_VALUE;
        int radius = 20; // Увеличили радиус для поиска почвы

        // Сначала ищем гнезда (приоритет)
        for (int x = -radius; x <= radius; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos p = wormPos.offset(x, y, z);
                    BlockEntity be = worm.level().getBlockEntity(p);

                    if (be instanceof DepthWormNestBlockEntity nest) {
                        if (!nest.isFull()) {
                            double d = worm.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                            if (d < bestDist) {
                                bestDist = d;
                                bestEntry = p.immutable();
                            }
                        }
                    }
                }
            }
        }

        // Если не нашли гнездо — ищем почву сети
        if (bestEntry == null) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -8; y <= 8; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos p = wormPos.offset(x, y, z);

                        if (isValidSoilEntry(p)) {
                            double d = worm.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                            if (d < bestDist * 1.5) { // Почва может быть чуть дальше чем гнездо
                                bestDist = d;
                                bestEntry = p.immutable();
                            }
                        }
                    }
                }
            }
        }

        return bestEntry;
    }

    // НОВОЕ: Проверка подходит ли почва для входа
    private boolean isValidSoilEntry(BlockPos pos) {
        BlockEntity be = worm.level().getBlockEntity(pos);
        if (!(be instanceof HiveSoilBlockEntity soil)) return false;

        UUID netId = soil.getNetworkId();
        if (netId == null) return false;

        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return false;

        HiveNetwork network = manager.getNetwork(netId);
        // Почва валидна если в сети есть куда девать червя
        return network != null && !network.wormCounts.isEmpty();
    }

    @Override
    public void tick() {
        if (targetPos == null) return;

        double targetX = targetPos.getX() + 0.5;
        double targetY = targetPos.getY() + (targetIsSoil ? 0.5 : 0.8); // Для почвы — центр блока
        double targetZ = targetPos.getZ() + 0.5;

        double distSq = worm.distanceToSqr(targetX, targetY, targetZ);

        // Для почвы — "проваливание" сквозь блок
        if (targetIsSoil && distSq < 2.0D) {
            // Ускоряем падение вниз через почву
            Vec3 downPull = new Vec3(0, -0.3, 0);
            worm.setDeltaMovement(worm.getDeltaMovement().add(downPull));
            worm.getLookControl().setLookAt(targetX, targetY, targetZ, 30.0F, 30.0F);

            // Если достаточно глубоко "провалился" — телепортируем в гнездо
            if (worm.getY() < targetY - 1.0) {
                enterNetwork(worm.blockPosition());
            }
            return;
        }

        // Обычное приближение
        if (distSq < 4.0D) {
            worm.getNavigation().stop();
            Vec3 pull = new Vec3(targetX - worm.getX(), targetY - worm.getY(), targetZ - worm.getZ())
                    .normalize()
                    .scale(0.15);
            worm.setDeltaMovement(worm.getDeltaMovement().add(pull));
            worm.getLookControl().setLookAt(targetX, targetY, targetZ, 30.0F, 30.0F);
        } else {
            worm.getNavigation().moveTo(targetX, targetY, targetZ, 1.2D);
            worm.getLookControl().setLookAt(targetX, targetY + 0.5, targetZ);
        }

        // Вход в гнездо
        if (!targetIsSoil && distSq < 1.5D) {
            enterNetwork(targetPos);
        }
    }

    // НОВОЕ: Общая логика входа в сеть
    private void enterNetwork(BlockPos entryPos) {
        HiveNetworkManager manager = HiveNetworkManager.get(worm.level());
        if (manager == null) return;

        BlockEntity be = worm.level().getBlockEntity(entryPos);
        UUID netId = null;

        if (be instanceof HiveNetworkMember member) {
            netId = member.getNetworkId();
        }

        if (netId == null) return;

        HiveNetwork network = manager.getNetwork(netId);
        if (network == null) return;

        // НОВОЕ: Уменьшаем счётчик активных червяков
        BlockPos boundNest = worm.getBoundNestPos();
        if (boundNest != null) {
            network.removeActiveWorm(boundNest);
        }

        // Начисляем очки
        int kills = worm.getKills();
        network.killsPool = Math.min(50, network.killsPool + kills);
        System.out.println("[Hive] Worm returned to network via " + (targetIsSoil ? "soil" : "nest") +
                ". Points: " + network.killsPool + " | Active remaining: " +
                network.activeWormCounts.getOrDefault(boundNest, 0));

        // Сохраняем данные червя
        CompoundTag tag = new CompoundTag();
        worm.saveWithoutId(tag);
        tag.putInt("Kills", 0);
        tag.putLong("BoundNest", targetPos.asLong());
        tag.putBoolean("EnteredViaSoil", targetIsSoil); // Метка для статистики

        // Отправляем в ближайшее свободное гнездо сети
        if (manager.addWormToNetwork(netId, tag, entryPos, worm.level())) {
            worm.discard();
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPos == null) return false;

        // Для почвы — продолжаем пока не "провалился" достаточно
        if (targetIsSoil) {
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