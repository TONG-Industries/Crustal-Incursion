package com.cim.item.tools;

import com.cim.api.metallurgy.system.Metal;
import com.cim.block.entity.industrial.casting.CastingPotBlockEntity;
import com.cim.event.HotItemHandler;
import com.cim.event.SlagItem;
import com.cim.multiblock.industrial.SmelterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Кочерга - универсальный инструмент для работы с горячими металлами.
 */
public class PokerItem extends Item {

    public PokerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        InteractionHand hand = context.getHand();

        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        ItemStack poker = player.getItemInHand(hand);

        // === ОБРАБОТКА ЛИТЕЙНОГО КОТЛА ===
        if (be instanceof CastingPotBlockEntity pot) {
            return handleCastingPot(level, pos, player, hand, pot, poker);
        }

        // === ОБРАБОТКА ПЛАВИЛЬНИ ===
        if (be instanceof SmelterBlockEntity smelter) {
            return handleSmelter(level, pos, player, smelter, poker);
        }

        return InteractionResult.PASS;
    }

    /**
     * Обработка литейного котла
     */
    private InteractionResult handleCastingPot(Level level, BlockPos pos, Player player, InteractionHand hand,
                                               CastingPotBlockEntity pot, ItemStack poker) {

        // Shift + ПКМ - сброс ВСЕГО содержимого (кроме формы!)
        if (player.isShiftKeyDown()) {
            return dumpCastingPotContents(level, pos, player, pot, poker);
        }

        // Обычный ПКМ - извлечение содержимого по приоритету
        if (pot.hasSlag()) {
            return extractSlagFromPot(level, pos, player, hand, pot, poker);
        }

        if (!pot.getOutputItem().isEmpty()) {
            return extractHotItemFromPot(level, pos, player, hand, pot, poker);
        }

        // Нечего доставать
        player.displayClientMessage(Component.literal("§7Котёл пуст или содержит жидкий металл"), true);
        return InteractionResult.PASS;
    }

    /**
     * Сброс содержимого котла в виде шлака
     * ВАЖНО: форма НЕ выбрасывается!
     */
    private InteractionResult dumpCastingPotContents(Level level, BlockPos pos, Player player,
                                                     CastingPotBlockEntity pot, ItemStack poker) {
        boolean dumped = false;

        // Шлак
        if (pot.hasSlag()) {
            ItemStack slag = pot.extractSlag();
            if (!slag.isEmpty()) {
                ensureSlagIsHot(slag);
                popResourceSafe(level, pos, slag);
                dumped = true;
            }
        }

        // Жидкий металл → шлак (только если есть металл)
        if (pot.getStoredUnits() > 0 && pot.getCurrentMetal() != null) {
            ItemStack slag = SlagItem.createSlag(pot.getCurrentMetal(), pot.getStoredUnits());
            ensureSlagIsHot(slag);
            popResourceSafe(level, pos, slag);
            pot.clearMetal();
            dumped = true;
        }

        // Горячий предмет
        if (!pot.getOutputItem().isEmpty()) {
            ItemStack item = pot.takeOutput();
            popResourceSafe(level, pos, item);
            dumped = true;
        }

        // ФОРМА НЕ ВЫБРАСЫВАЕТСЯ! Она остаётся в котле.

        if (dumped) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 0.8f);
            damagePoker(poker, player, 1);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    /**
     * Извлечение шлака из котла
     */
    private InteractionResult extractSlagFromPot(Level level, BlockPos pos, Player player, InteractionHand hand,
                                                 CastingPotBlockEntity pot, ItemStack poker) {
        ItemStack slag = pot.extractSlag();
        if (slag.isEmpty()) return InteractionResult.PASS;

        ensureSlagIsHot(slag);

        if (!player.getInventory().add(slag)) {
            player.drop(slag, false);
        }

        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
        damagePoker(poker, player, 1);
        return InteractionResult.CONSUME;
    }

    /**
     * Извлечение горячего предмета из котла
     */
    private InteractionResult extractHotItemFromPot(Level level, BlockPos pos, Player player, InteractionHand hand,
                                                    CastingPotBlockEntity pot, ItemStack poker) {
        ItemStack item = pot.takeOutput();
        if (item.isEmpty()) return InteractionResult.PASS;

        // Кочергой можно достать даже горячий
        if (HotItemHandler.isHot(item)) {
            int temp = HotItemHandler.getTemperature(item);
            player.displayClientMessage(
                    Component.literal(String.format("§6Достали горячий предмет! %d°C", temp)),
                    true
            );
        }

        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }

        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        damagePoker(poker, player, 1);
        return InteractionResult.CONSUME;
    }

    /**
     * Обработка плавильни
     */
    private InteractionResult handleSmelter(Level level, BlockPos pos, Player player,
                                            SmelterBlockEntity smelter, ItemStack poker) {

        // Shift + ПКМ - сброс металла в шлак
        if (player.isShiftKeyDown()) {
            return dumpSmelterContents(level, pos, player, smelter, poker);
        }

        // Обычное взаимодействие - открытие GUI
        // Возвращаем PASS чтобы открылся GUI (или обработался другой код)
        return InteractionResult.PASS;
    }

    /**
     * Сброс металла из плавильни в виде шлака
     */
    private InteractionResult dumpSmelterContents(Level level, BlockPos pos, Player player,
                                                  SmelterBlockEntity smelter, ItemStack poker) {
        if (!smelter.hasMetal()) {
            player.displayClientMessage(Component.literal("§7В плавильне нет металла"), true);
            return InteractionResult.PASS;
        }

        List<ItemStack> slagItems = smelter.dumpMetalAsSlag();

        for (ItemStack slag : slagItems) {
            ensureSlagIsHot(slag);
            popResourceSafe(level, pos, slag);
        }

        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 0.8f);
        damagePoker(poker, player, slagItems.size());

        player.displayClientMessage(
                Component.literal(String.format("§6Сброшено %d единиц шлака", slagItems.size())),
                true
        );

        return InteractionResult.CONSUME;
    }

    /**
     * Убеждается что шлак горячий
     */
    private void ensureSlagIsHot(ItemStack slag) {
        if (HotItemHandler.isHot(slag)) return;

        int meltingPoint = 1000;
        if (slag.getTag() != null) {
            if (slag.getTag().contains(SlagItem.TAG_MELTING_POINT)) {
                meltingPoint = slag.getTag().getInt(SlagItem.TAG_MELTING_POINT);
            } else if (slag.getTag().contains("MeltingPoint")) {
                meltingPoint = slag.getTag().getInt("MeltingPoint");
            }
        }

        HotItemHandler.setHot(slag, meltingPoint, false);
    }

    /**
     * Безопасное выбрасывание предмета
     */
    private void popResourceSafe(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, pos, stack);
        }
    }

    /**
     * Наносит урон кочерге
     */
    private void damagePoker(ItemStack poker, Player player, int amount) {
        if (player.getAbilities().instabuild) return;

        poker.hurtAndBreak(amount, player, (p) -> {
            p.broadcastBreakEvent(player.getUsedItemHand());
        });
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isRepairable(ItemStack stack) {
        return true;
    }
}