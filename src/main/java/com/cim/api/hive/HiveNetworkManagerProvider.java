package com.cim.api.hive;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HiveNetworkManagerProvider implements ICapabilitySerializable<CompoundTag> {

    // Сама капабилити
    public static final Capability<HiveNetworkManager> HIVE_NETWORK_MANAGER = CapabilityManager.get(new CapabilityToken<>(){});

    // Экземпляр менеджера и его ленивая обертка
    private final HiveNetworkManager instance = new HiveNetworkManager();
    private final LazyOptional<HiveNetworkManager> optional = LazyOptional.of(() -> instance);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // Проверяем, ту ли капабилити у нас просят
        return HIVE_NETWORK_MANAGER.orEmpty(cap, optional);
    }

    @Override
    public CompoundTag serializeNBT() {
        return instance.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        instance.deserializeNBT(nbt);
    }
}
