package com.cim.client.overlay.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import com.cim.main.CrustalIncursionMod;
import com.cim.menu.ShaftPlacerMenu;
import com.cim.network.ModPacketHandler;
import com.cim.network.packet.rotation.PacketToggleShaftPlacer;

public class GUIShaftPlacer extends AbstractContainerScreen<ShaftPlacerMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/machine/shaft_placer_gui.png");

    public GUIShaftPlacer(ShaftPlacerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 188;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // Энергобар (без изменений)
        int energy = menu.getEnergy();
        int maxEnergy = menu.getMaxEnergy();
        if (maxEnergy > 0) {
            int barHeight = 52;
            int filledHeight = (int) ((long) energy * barHeight / maxEnergy);
            guiGraphics.blit(TEXTURE,
                    x + 120, y + 28 + (barHeight - filledHeight),
                    177, 33 + (barHeight - filledHeight),
                    16, filledHeight);
        }

        // Кнопка включения
        if (menu.isSwitchedOn()) {
            guiGraphics.blit(TEXTURE, x + 45, y + 43, 177, 0, 10, 32);
        }

        // Светодиоды
        if (menu.canPlaceNext()) {
            guiGraphics.blit(TEXTURE, x + 64, y + 34, 177, 86, 6, 6);
        }
        if (menu.hasDrillHead()) {
            guiGraphics.blit(TEXTURE, x + 72, y + 34, 177, 86, 6, 6);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);

        // Тултип энергии
        if (isHovering(120, 28, 16, 52, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.literal(menu.getEnergy() + " / " + menu.getMaxEnergy() + " HE"), mouseX, mouseY);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 4210752, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            double relX = mouseX - x;
            double relY = mouseY - y;

            // Клик по кнопке питания (x45, y43, размер 10x32)
            if (relX >= 45 && relX < 55 && relY >= 43 && relY < 75) {
                playClickSound();
                ModPacketHandler.INSTANCE.sendToServer(new PacketToggleShaftPlacer(menu.getPos()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}