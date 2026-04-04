package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.Rotational;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ShaftBlockEntity extends BlockEntity implements Rotational {

    private long speed = 0;
    private long lastSyncedSpeed = 0; // Запоминаем, что мы отправляли клиенту в последний раз

    public ShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAFT_BE.get(), pos, state);
    }

    // ... твой конструктор и переменные ...

    @Override
    public void setSpeed(long speed) {
        if (this.speed != speed) {
            this.speed = speed;
            setChanged(); // Отмечаем чанк как грязный для сохранения NBT

            // Проверяем, достаточно ли изменилась скорость для отправки пакета
            if (shouldSyncSpeed()) {
                this.lastSyncedSpeed = this.speed;
                // ВАЖНО: Отправляем пакет клиенту, чтобы Flywheel обновил скорость
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }
            }
        }
    }

    // Умный фильтр отправки пакетов
    private boolean shouldSyncSpeed() {
        // Обязательно синхронизируем при старте или полной остановке
        if (this.speed == 0 && this.lastSyncedSpeed != 0) return true;
        if (this.speed != 0 && this.lastSyncedSpeed == 0) return true;

        // Вычисляем разницу по модулю
        long diff = Math.abs(this.speed - this.lastSyncedSpeed);

        // Порог: 5% от последней отправленной скорости (но не меньше 2 единиц)
        long threshold = Math.max(2, Math.abs(this.lastSyncedSpeed) / 20);

        return diff >= threshold;
    }

    // --- ОБЯЗАТЕЛЬНАЯ СИНХРОНИЗАЦИЯ NBT ---
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", this.speed);
        tag.putLong("LastSyncedSpeed", this.lastSyncedSpeed);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        this.speed = tag.getLong("Speed");
        this.lastSyncedSpeed = tag.getLong("LastSyncedSpeed");
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag() {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag();
        tag.putLong("Speed", this.speed);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            com.cim.api.rotation.KineticNetwork net = com.cim.api.rotation.KineticNetworkManager.get((net.minecraft.server.level.ServerLevel) level)
                    .getNetworkFor(worldPosition);

            // Если сеть найдена в памяти, просто забираем её скорость для визуала
            if (net != null) {
                this.speed = net.getSpeed();
                this.lastSyncedSpeed = this.speed;
                // Отправляем пакет на клиент, чтобы вал начал крутиться сразу после загрузки чанка
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                net.requestRecalculation();
            }
        }
    }
    // --- КОНЕЦ БЛОКА СИНХРОНИЗАЦИИ ---

    @Override
    public Direction[] getPropagationDirections() {
        Direction facing = getBlockState().getValue(ShaftBlock.FACING);
        return new Direction[]{facing, facing.getOpposite()};
    }

    @Override
    public long getMaxSpeed() {
        if (getBlockState().getBlock() instanceof ShaftBlock shaft) {
            return (long) (shaft.getMaterial().baseSpeed() * shaft.getDiameter().speedMod);
        }
        return 256;
    }

    @Override
    public long getMaxTorque() {
        if (getBlockState().getBlock() instanceof ShaftBlock shaft) {
            return (long) (shaft.getMaterial().baseTorque() * shaft.getDiameter().torqueMod);
        }
        return 1024;
    }

    @Override
    public long getInertiaContribution() {
        if (getBlockState().getBlock() instanceof ShaftBlock shaft) {
            return (long) (shaft.getMaterial().baseInertia() * shaft.getDiameter().inertiaMod);
        }
        return 5;
    }

    @Override
    public long getFrictionContribution() {
        if (getBlockState().getBlock() instanceof ShaftBlock shaft) {
            return (long) shaft.getMaterial().baseFriction();
        }
        return 1;
    }

    @Override
    public long getMaxTorqueTolerance() { return getMaxTorque(); }

    @Override
    public long getSpeed() { return speed; }

    @Override
    public long getTorque() {
        // Вал сам по себе не генерирует крутящий момент
        return 0;
    }

    @Override
    public boolean isSource() { return false; } // Вал — это просто передатчик
}