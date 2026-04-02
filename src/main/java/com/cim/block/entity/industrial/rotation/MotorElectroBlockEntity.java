package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.Rotational;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.cim.block.basic.industrial.rotation.MotorElectroBlock;

public class MotorElectroBlockEntity extends BlockEntity implements Rotational {

    // 0.5 оборотов в секунду. В нашей системе это будет базовая скорость.
    private final long speedConstant = 20; // Условные единицы скорости
    private final long torqueConstant = 100; // Постоянный крутящий момент

    public MotorElectroBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOTOR_ELECTRO_BE.get(), pos, state);
    }

    @Override
    public long getSpeed() { return speedConstant; }

    @Override
    public long getTorque() { return torqueConstant; }

    @Override
    public void setSpeed(long speed) {
        // Для мотора скорость задается внутренне, но метод нужен для интерфейса
    }

    @Override
    public boolean isSource() { return true; } // Это источник! [cite: 16]

    @Override
    public Direction[] getPropagationDirections() {
        // Энергия выходит только с той стороны, куда смотрит вал (FACING)
        return new Direction[]{ getBlockState().getValue(MotorElectroBlock.FACING) };
    }

    @Override
    public long getMaxSpeed() { return 256; }
    @Override
    public long getMaxTorque() { return 1024; }
}
