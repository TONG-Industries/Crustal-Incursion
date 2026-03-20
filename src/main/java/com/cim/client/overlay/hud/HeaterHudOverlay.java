package com.cim.client;

import com.cim.main.CrustalIncursionMod;
import com.cim.multiblock.industrial.HeaterBlockEntity;
import com.cim.multiblock.system.IMultiblockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HeaterHudOverlay {

    @SubscribeEvent
    public static void onRenderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity be = mc.level.getBlockEntity(pos);

            if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                be = mc.level.getBlockEntity(part.getControllerPos());
            }

            if (be instanceof HeaterBlockEntity heater) {
                GuiGraphics graphics = event.getGuiGraphics();
                Font font = mc.font;

                int heat = heater.getHeatLevel();
                int maxHeat = heater.getMaxHeat();
                float heatPercent = (float) heat / maxHeat;

                // Получаем плавно перетекающий цвет
                int color = getSmoothTemperatureColor(heatPercent);

                String text = "Температура: " + heat + " / " + maxHeat + " °C";

                int screenWidth = event.getWindow().getGuiScaledWidth();
                int screenHeight = event.getWindow().getGuiScaledHeight();
                int x = screenWidth / 2 + 15;
                int y = screenHeight / 2 + 10;

                int textWidth = font.width(text);
                graphics.fill(x - 2, y - 2, x + textWidth + 2, y + font.lineHeight + 1, 0x80000000);

                graphics.drawString(font, text, x, y, color, true);
            }
        }
    }

    // --- ЛОГИКА ПЛАВНОГО ПЕРЕХОДА ЦВЕТА ---

    private static int getSmoothTemperatureColor(float percent) {
        // Ограничиваем процент от 0.0 до 1.0 на всякий случай
        percent = Math.max(0.0f, Math.min(1.0f, percent));

        int colorGrey = 0xAAAAAA;
        int colorOrange = 0xFFAA00;
        int colorRed = 0xFF5555;

        if (percent <= 0.5f) {
            // От 0% до 50% смешиваем серый и оранжевый
            return lerpColor(colorGrey, colorOrange, percent * 2.0f);
        } else {
            // От 50% до 100% смешиваем оранжевый и красный
            return lerpColor(colorOrange, colorRed, (percent - 0.5f) * 2.0f);
        }
    }

    /**
     * Смешивает два RGB цвета в зависимости от параметра t (от 0.0 до 1.0)
     */
    private static int lerpColor(int color1, int color2, float t) {
        // Вытаскиваем каналы первого цвета (Red, Green, Blue)
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        // Вытаскиваем каналы второго цвета
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        // Интерполируем (смешиваем) каждый канал отдельно
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        // Собираем каналы обратно в один HEX-цвет
        return (r << 16) | (g << 8) | b;
    }
}