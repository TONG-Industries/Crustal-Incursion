package com.cim.client.overlay.hud;

import com.cim.item.tools.CastPickaxeIronItem;
import com.cim.main.CrustalIncursionMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID, value = Dist.CLIENT)
public class CastPickaxeIronHud {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Pre event) {
        // Можно проверять конкретный оверлей, например: if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof CastPickaxeIronItem pickaxe)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Если игрок заряжает (удерживает ПКМ)
        if (player.isUsingItem() && player.getItemInHand(player.getUsedItemHand()) == stack) {
            int useTicks = player.getTicksUsingItem();
            float progress = Math.min(1.0f, useTicks / (float) CastPickaxeIronItem.CHARGE_TICKS);

            renderChargeBar(graphics, screenWidth, screenHeight, progress);
        }
        // Если есть кулдаун (после полного заряда) — показываем перезарядку
        else if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            float cooldownPercent = player.getCooldowns().getCooldownPercent(stack.getItem(), 0);
            renderCooldownBar(graphics, screenWidth, screenHeight, cooldownPercent);
        }
    }

    private static void renderChargeBar(GuiGraphics graphics, int screenWidth, int screenHeight, float progress) {
        int barWidth = 20;
        int barHeight = 1;
        int x = (screenWidth - barWidth) / 2;
        int y = screenHeight / 2 + 7;

        // Заполнение: от оранжевого (0%) к зеленому (100%)
        int fillWidth = (int)(barWidth * progress);
        int color = progress >= 1.0f ? 0xFF00FF00 : 0xFFFFAA00;

        graphics.fill(x, y, x + fillWidth, y + barHeight, color);

        // Текстура или блик (опционально)
        graphics.fill(x, y, x + fillWidth, y + 1, 0xFFFFFFFF & (progress >= 1.0f ? 0x88FFFFFF : 0x44FFFFFF));
    }

    private static void renderCooldownBar(GuiGraphics graphics, int screenWidth, int screenHeight, float cooldownPercent) {
        int barWidth = 20;
        int barHeight = 1;
        int x = (screenWidth - barWidth) / 2;
        int y = screenHeight / 2 + 7;

        // Заполнение (уменьшается от 100% до 0%)
        int fillWidth = (int)(barWidth * (1.0f - cooldownPercent));
        int color = 0xFFFFFFFF; // Белый для кулдауна

        graphics.fill(x, y, x + fillWidth, y + barHeight, color);
    }
}