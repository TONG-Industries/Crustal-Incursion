package com.cim.multiblock.system;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;

public interface IMultiblockController {

    MultiblockStructureHelper getStructureHelper();

    PartRole getPartRole(BlockPos localOffset);

    @Nullable
    default VoxelShape getCustomMasterVoxelShape(BlockState state) {
        return null;
    }
}