package com.cim.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.cim.item.guns.MachineGunItem;

public class ClientRenderHandler {

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof MachineGunItem) {
                event.setCanceled(true);

                GuiGraphics graphics = event.getGuiGraphics();
                int width = event.getWindow().getGuiScaledWidth();
                int height = event.getWindow().getGuiScaledHeight();
                int x = width / 2;
                int y = height / 2;

                // Было: 2x2 пикселя, непрозрачный белый
                // graphics.fill(x - 1, y - 1, x + 1, y + 1, 0xFFFFFFFF);

                // Стало: 1x1 пиксель, 70% непрозрачности (полупрозрачный)
                // 0xB2FFFFFF: B2 = 178 (из 255) альфа, FFFFFF = белый
                graphics.fill(x, y, x + 1, y + 1, 0x80FFFFFF);
            }
        }
    }
}
