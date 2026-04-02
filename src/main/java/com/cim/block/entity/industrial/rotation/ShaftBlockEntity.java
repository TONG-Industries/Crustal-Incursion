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

    public ShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAFT_BE.get(), pos, state);
    }

    @Override
    public long getSpeed() { return speed; }

    @Override
    public long getTorque() {
        // Вал сам по себе не генерирует крутящий момент
        return 0;
    }

    // ... твой конструктор и переменные ...

    @Override
    public void setSpeed(long speed) {
        if (this.speed != speed) {
            this.speed = speed;
            setChanged(); // Сохраняем изменения в чанк

            // ВАЖНО: Отправляем пакет обновления на клиент (Flywheel)!
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    // --- НАЧАЛО БЛОКА СИНХРОНИЗАЦИИ ---
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", this.speed);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        this.speed = tag.getLong("Speed");
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
    // --- КОНЕЦ БЛОКА СИНХРОНИЗАЦИИ ---

    @Override
    public Direction[] getPropagationDirections() {
        Direction facing = getBlockState().getValue(ShaftBlock.FACING);
        return new Direction[]{facing, facing.getOpposite()};
    }

    @Override
    public long getMaxSpeed() { return 256; } // Можно вынести в конфиг
    @Override
    public long getMaxTorque() { return 1024; }

    @Override
    public boolean isSource() { return false; } // Вал — это просто передатчик
}