package com.cim.api.hive;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class HiveNetworkManagerProvider implements ICapabilitySerializable<CompoundTag> {
    private final HiveNetworkManager manager = new HiveNetworkManager();
    private final LazyOptional<HiveNetworkManager> holder = LazyOptional.of(() -> manager);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return HiveNetworkManager.HIVE_NETWORK_MANAGER.orEmpty(cap, holder);
    }

    @Override
    public CompoundTag serializeNBT() {
        return manager.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        manager.deserializeNBT(nbt);
    }
}