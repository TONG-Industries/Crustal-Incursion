package com.cim.item.tools;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class FluidIdentifierItem extends Item {
    public FluidIdentifierItem(Properties pProperties) {
        super(pProperties.stacksTo(1)); // Идентификатор не стакается
    }

    // Открываем GUI при ПКМ в воздух (только на клиенте)
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            openScreen(player.getItemInHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @OnlyIn(Dist.CLIENT)
    private void openScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new com.cim.client.overlay.gui.GUIFluidIdentifier(stack));
    }

    // --- УТИЛИТЫ ДЛЯ ЧТЕНИЯ NBT ---
    public static String getSelectedFluid(ItemStack stack) {
        if (!stack.hasTag()) return "none";
        return stack.getTag().getString("SelectedFluid");
    }

    public static List<String> getRecentFluids(ItemStack stack) {
        List<String> list = new ArrayList<>();
        if (stack.hasTag() && stack.getTag().contains("RecentFluids")) {
            ListTag listTag = stack.getTag().getList("RecentFluids", Tag.TAG_STRING);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(listTag.getString(i));
            }
        }
        return list;
    }

    public static List<String> getFavorites(ItemStack stack) {
        List<String> list = new ArrayList<>();
        if (stack.hasTag() && stack.getTag().contains("Favorites")) {
            ListTag listTag = stack.getTag().getList("Favorites", Tag.TAG_STRING);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(listTag.getString(i));
            }
        }
        return list;
    }
}
