package com.cim.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@Mod.EventBusSubscriber(modid = "cim", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HotItemHandler {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;
        boolean hasHotItem = false;

        // Остывание раз в 10 тиков (0.5 сек) вместо каждые 2 тика
        boolean shouldCool = player.level().getGameTime() % 10 == 0;
        boolean inventoryChanged = false;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.hasTag() && stack.getTag().contains("HotTime")) {
                int hotTime = stack.getTag().getInt("HotTime");

                if (hotTime > 0) {
                    if (shouldCool) {
                        hotTime -= 5; // Отнимаем сразу 5 единиц за раз
                        if (hotTime < 0) hotTime = 0;
                        stack.getTag().putInt("HotTime", hotTime);
                        inventoryChanged = true;
                    }
                    hasHotItem = true;
                } else {
                    // Остыл - чистим теги
                    stack.removeTagKey("HotTime");
                    stack.removeTagKey("HotTimeMax");
                    inventoryChanged = true;
                }
            }
        }

        // Поджог раз в секунду
        if (hasHotItem && player.level().getGameTime() % 20 == 0) {
            player.setSecondsOnFire(2);
        }

        // Синхронизируем инвентарь только если реально были изменения
        if (inventoryChanged) {
            player.getInventory().setChanged();
        }
    }

    // Тултип с процентом
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.hasTag() && stack.getTag().contains("HotTime")) {
            int hotTime = stack.getTag().getInt("HotTime");
            int maxTime = stack.getTag().getInt("HotTimeMax");
            if (maxTime == 0) maxTime = 200;

            int percent = (int)((hotTime / (float)maxTime) * 100);
            event.getToolTip().add(Component.literal("§6§lРАСКАЛЁННЫЙ! §7" + percent + "%"));
        }
    }
    // Подсказка в тултипе "РАСКАЛЁННЫЙ!"

}