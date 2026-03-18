package com.cim.client.overlay.gui;

import com.cim.network.ModPacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import com.cim.main.CrustalIncursionMod;
import com.cim.menu.FluidBarrelMenu;

public class GUIFluidBarrel extends AbstractContainerScreen<FluidBarrelMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/storage/fluid_tank_gui.png");

    public GUIFluidBarrel(FluidBarrelMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = -9999;
        this.inventoryLabelX = -9999;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos;
        int y = this.topPos;

        // 1. Рисуем фон
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // 2. Рисуем кнопку режима (х151 у34)
        int mode = menu.getMode();
        graphics.blit(TEXTURE, x + 151, y + 34, 176, mode * 18, 18, 18);

        // 3. Рисуем жидкость (х71 у17, размер 34х52)
        renderFluid(graphics, x + 71, y + 17);
    }

    private void renderFluid(GuiGraphics graphics, int x, int y) {
        FluidStack fluid = menu.getFluid();
        if (fluid.isEmpty()) return;

        int capacity = menu.getCapacity();
        int maxFluidHeight = 52;
        int fluidHeight = (int) (maxFluidHeight * ((float) fluid.getAmount() / capacity));
        if (fluidHeight <= 0) return;

        IClientFluidTypeExtensions clientProps = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation stillTexture = clientProps.getStillTexture(fluid);
        if (stillTexture == null) return;

        TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        int color = clientProps.getTintColor(fluid);

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        RenderSystem.setShaderColor(r, g, b, a);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

        // Блиттим жидкость (снизу вверх)
        int drawY = y + (maxFluidHeight - fluidHeight);
        graphics.blit(x, drawY, 0, 34, fluidHeight, sprite);

        // Возвращаем цвет в норму
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Клик по кнопке режима
            if (isMouseOver(mouseX, mouseY, 151, 34, 18, 18)) {
                playSound();
                // Отправляем пакет на сервер!
                com.cim.network.ModPacketHandler.INSTANCE.sendToServer(
                        new com.cim.network.packet.fluids.UpdateBarrelModeC2SPacket(menu.blockEntity.getBlockPos())
                );
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int sizeX, int sizeY) {
        return (mouseX >= this.leftPos + x && mouseX <= this.leftPos + x + sizeX &&
                mouseY >= this.topPos + y && mouseY <= this.topPos + y + sizeY);
    }

    private void playSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}
