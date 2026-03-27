package com.cim.item.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class FluidIdentifierItem extends Item {
    public FluidIdentifierItem(Properties pProperties) {
        super(pProperties.stacksTo(1)); // Идентификатор не стакается
    }

    // ==========================================
    // ФИКС 1: РАЗРЕШАЕМ ШИФТ+ПКМ ПО ТРУБЕ
    // ==========================================
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        // Если игрок на шифте кликает по нашей трубе - говорим игре:
        // "Не открывай предмет, заставь сработать метод use() самой трубы!"
        return level.getBlockState(pos).getBlock() instanceof com.cim.block.basic.fluids.FluidPipeBlock;
    }

    // ==========================================
    // ФИКС 2: БЛОКИРУЕМ GUI ПРИ КЛИКЕ ПО БЛОКУ
    // ==========================================
    @Override
    public InteractionResult useOn(UseOnContext pContext) {
        // Если мы кликнули по трубе (а её метод use на клиенте вдруг вернул PASS из-за рассинхрона),
        // мы принудительно гасим клик здесь, возвращая SUCCESS.
        // Это не даст игре дойти до метода use() в воздухе и открыть GUI.
        if (pContext.getLevel().getBlockState(pContext.getClickedPos()).getBlock() instanceof com.cim.block.basic.fluids.FluidPipeBlock) {
            return InteractionResult.SUCCESS;
        }
        return super.useOn(pContext);
    }

    // ==========================================
    // ОТКРЫТИЕ GUI (Только при клике в воздух)
    // ==========================================
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