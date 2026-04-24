package com.cim.client.overlay.hud;

import com.cim.block.entity.industrial.MillstoneBlockEntity;
import com.cim.main.CrustalIncursionMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MillstoneHudOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof MillstoneBlockEntity millstone)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 + 12;
        int y = screenHeight / 2 + 4;

        ItemStack input = millstone.getInputStack();
        ItemStack result = millstone.getResultStack();
        boolean processing = millstone.isProcessing();
        int current = millstone.getCurrentGrinds();
        int required = millstone.getRequiredGrinds();
        int remaining = millstone.getRemainingGrinds();

        String mainText;
        int mainColor;
        String subText = null;
        int subColor = 0xAAAAAA;

        if (!result.isEmpty()) {
            // Готово к сбору
            mainText = "✓ " + result.getHoverName().getString();
            mainColor = 0x55FF55; // Зелёный
            subText = "ПКМ чтобы забрать";

        } else if (processing) {
            // В процессе помола
            mainText = String.format("%d/%d оборотов", current, required);

            // Цвет от прогресса: серый -> оранжевый -> зелёный
            float progress = (float) current / required;
            mainColor = getProgressColor(progress);

            if (remaining > 0) {
                subText = "Осталось: " + remaining;
            }

        } else if (!input.isEmpty()) {
            // Есть вход, ждёт начала (редкий кейс)
            mainText = input.getHoverName().getString();
            mainColor = 0xFFAA00;
            subText = "ПКМ для помола";

        } else {
            // Пусто
            mainText = "Жернова пусты";
            mainColor = 0xAAAAAA;
            subText = "Положите минерал";
        }

        // Рендер основного текста
        int textWidth = font.width(mainText);

        // Корректировка если вылезает за экран
        if (x + textWidth + 4 > screenWidth) {
            x = screenWidth / 2 - textWidth - 12;
        }

        // Фон под текст
        graphics.fill(x - 3, y - 2, x + textWidth + 3, y + font.lineHeight + 2, 0x90000000);
        graphics.drawString(font, mainText, x, y, mainColor, true);

        // Дополнительная строка (если есть)
        if (subText != null) {
            int subWidth = font.width(subText);
            int subY = y + font.lineHeight + 3;

            // Расширяем фон если нужно
            int maxWidth = Math.max(textWidth, subWidth);
            graphics.fill(x - 3, y - 2, x + maxWidth + 3, subY + font.lineHeight + 2, 0x90000000);

            graphics.drawString(font, subText, x, subY, subColor, true);
        }

        // Иконка предмета слева от текста (опционально)
        if (!result.isEmpty()) {
            graphics.renderItem(result, x - 20, y - 2);
        } else if (!input.isEmpty()) {
            graphics.renderItem(input, x - 20, y - 2);
        }
    }

    private static int getProgressColor(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        int grey = 0xAAAAAA;
        int orange = 0xFFAA00;
        int green = 0x55FF55;

        if (percent <= 0.5f) {
            return lerpColor(grey, orange, percent * 2.0f);
        } else {
            return lerpColor(orange, green, (percent - 0.5f) * 2.0f);
        }
    }

    private static int lerpColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}