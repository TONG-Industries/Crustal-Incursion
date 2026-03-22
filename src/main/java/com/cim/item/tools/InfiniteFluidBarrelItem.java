package com.cim.item.tools;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class InfiniteFluidBarrelItem extends Item {

    public InfiniteFluidBarrelItem(Properties properties) {
        super(properties.stacksTo(1)); // Не стакается
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§8Поместите в слот опустошения"));
        tooltip.add(Component.literal("§8настроенной цистерны, чтобы"));
        tooltip.add(Component.literal("§8бесконечно заполнять её."));
        tooltip.add(Component.literal("§dБесконечный источник"));
    }
}
