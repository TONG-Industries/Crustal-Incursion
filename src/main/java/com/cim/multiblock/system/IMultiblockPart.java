package com.cim.multiblock.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import java.util.Set;

public interface IMultiblockPart {

    @Nullable
    BlockPos getControllerPos();

    void setControllerPos(BlockPos pos);

    void setPartRole(PartRole role);

    PartRole getPartRole();

    void setAllowedClimbSides(Set<Direction> sides);

    Set<Direction> getAllowedClimbSides();
}