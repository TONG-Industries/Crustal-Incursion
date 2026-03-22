package com.cim.client.overlay.hud;

import com.cim.main.CrustalIncursionMod;
import com.cim.multiblock.industrial.HeaterBlockEntity;
import com.cim.multiblock.system.IMultiblockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (be == null) return;

        // Ищем нагреватель (контроллер)
        HeaterBlockEntity heater = null;
        if (be instanceof IMultiblockPart part) {
            BlockPos controllerPos = part.getControllerPos();
            if (controllerPos != null) {
                BlockEntity controller = mc.level.getBlockEntity(controllerPos);
                if (controller instanceof HeaterBlockEntity h) {
                    heater = h;
                }
            }
        } else if (be instanceof HeaterBlockEntity h) {
            heater = h;
        }

        if (heater == null) return;

        // Проверяем, что данные актуальны (не 0 если нагреватель работал)
        int temp = heater.getTemperature();

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int maxTemp = HeaterBlockEntity.MAX_TEMP;
        float tempPercent = (float) temp / maxTemp;
        int color = getSmoothTemperatureColor(tempPercent);

        String tempText = String.format("%d / %d °C", temp, maxTemp);
        int textWidth = font.width(tempText);

        int screenWidth = event.getWindow().getGuiScaledWidth();
        int screenHeight = event.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 + 12;
        int y = screenHeight / 2 + 4;

        if (x + textWidth + 4 > screenWidth) {
            x = screenWidth / 2 - textWidth - 12;
        }

        // Фон
        graphics.fill(x - 3, y - 2, x + textWidth + 3, y + font.lineHeight + 2, 0x90000000);
        graphics.drawString(font, tempText, x, y, color, true);

        if (heater.isBurning()) {
            String status = "§6● §fНагрев";
            int statusWidth = font.width(status);
            int statusY = y + font.lineHeight + 3;

            if (statusWidth > textWidth) {
                graphics.fill(x - 3, y - 2, x + statusWidth + 3, statusY + font.lineHeight + 2, 0x90000000);
            }

            graphics.drawString(font, status, x, statusY, 0xFFAA00, true);
        }
    }

    // --- ЛОГИКА ПЛАВНОГО ПЕРЕХОДА ЦВЕТА ---

    private static int getSmoothTemperatureColor(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));

        int colorGrey = 0xAAAAAA;    // Холодный (0-30%)
        int colorOrange = 0xFFAA00;  // Теплый (30-70%)
        int colorRed = 0xFF2222;     // Горячий (70-100%)

        if (percent <= 0.3f) {
            // От серого к оранжевому
            return lerpColor(colorGrey, colorOrange, percent / 0.3f);
        } else if (percent <= 0.7f) {
            // От оранжевого к красному
            return lerpColor(colorOrange, colorRed, (percent - 0.3f) / 0.4f);
        } else {
            // Критическая температура - ярко-красный с пульсацией (можно добавить later)
            return colorRed;
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