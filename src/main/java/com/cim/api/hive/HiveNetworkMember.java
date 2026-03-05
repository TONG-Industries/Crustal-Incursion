package com.cim.api.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

public interface HiveNetworkMember {
    @Nullable UUID getNetworkId();
    void setNetworkId(@Nullable UUID id);
    BlockPos getBlockPos();
    Level getLevel();
}