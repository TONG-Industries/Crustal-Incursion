package com.cim.event;

import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class LiquidMetalItem extends Item {
    public LiquidMetalItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("MetalId")) {
            ResourceLocation metalId = new ResourceLocation(tag.getString("MetalId"));
            int amount = tag.getInt("Amount");

            Optional<Metal> metalOpt = MetallurgyRegistry.get(metalId);
            String metalName = metalOpt.map(m -> Component.translatable(m.getTranslationKey()).getString())
                    .orElse(metalId.getPath());

            tooltip.add(Component.literal("§7Металл: §f" + metalName));

            MetalUnits2.MetalStack units = MetalUnits2.convertFromUnits(amount);
            StringBuilder content = new StringBuilder("§7Количество: §f");
            if (units.blocks() > 0) content.append(units.blocks()).append(" блоков ");
            if (units.ingots() > 0) content.append(units.ingots()).append(" слитков ");
            if (units.nuggets() > 0) content.append(units.nuggets()).append(" самородков");
            tooltip.add(Component.literal(content.toString().trim()));
        } else {
            tooltip.add(Component.literal("§8Расплавленный металл").withStyle(ChatFormatting.GRAY));
        }
    }
}