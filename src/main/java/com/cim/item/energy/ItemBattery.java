package com.cim.item.energy;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;
import com.cim.api.energy.EnergyCapabilityProvider;

/**
 * @deprecated Используй {@link ModBatteryItem} вместо этого
 */
@Deprecated
public class ItemBattery extends ModBatteryItem {

    public ItemBattery(Properties pProperties, int capacity, int maxReceive, int maxExtract) {
        super(pProperties, capacity, maxReceive, maxExtract);
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new EnergyCapabilityProvider(stack, this.capacity, this.maxReceive, this.maxExtract);
    }
}