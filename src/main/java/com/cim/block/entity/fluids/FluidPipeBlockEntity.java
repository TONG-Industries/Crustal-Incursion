package com.cim.block.entity.fluids;

import com.cim.block.entity.ModBlockEntities; // Импортируй свой класс
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FluidPipeBlockEntity extends BlockEntity {

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        // Замени на свою будущую регистрацию FLUID_PIPE_BE
        super(ModBlockEntities.FLUID_PIPE_BE.get(), pos, state);
    }
}