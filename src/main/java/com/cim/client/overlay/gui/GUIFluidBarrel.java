package com.cim.client.overlay.gui;

import com.cim.network.ModPacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import com.cim.main.CrustalIncursionMod;
import com.cim.menu.FluidBarrelMenu;
import com.cim.item.ModItems;

public class GUIFluidBarrel extends AbstractContainerScreen<FluidBarrelMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/storage/fluid_tank_gui.png");

    public GUIFluidBarrel(FluidBarrelMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 176;
        this.imageHeight = 196;
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

        int mode = menu.getMode();
        graphics.blit(TEXTURE, x + 80, y + 95, 177, mode * 16, 15, 15);

        ItemStack protectorStack = menu.getSlot(16).getItem();
        if (!protectorStack.isEmpty()) {
            Item item = protectorStack.getItem();
            int vOffset = -1;
            if (item == ModItems.PROTECTOR_STEEL.get()) {
                vOffset = 197;
            } else if (item == ModItems.PROTECTOR_LEAD.get()) {
                vOffset = 214;
            } else if (item == ModItems.PROTECTOR_TUNGSTEN.get()) {
                vOffset = 231;
            }
            if (vOffset != -1) {
                graphics.blit(TEXTURE, x + 39, y + 6, 0, vOffset, 118, 16);
            }
        }

        // 4. Рисуем жидкость (новое расположение: x71 y39)
        renderFluid(graphics, x + 71, y + 39);
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

        int drawY = y + (maxFluidHeight - fluidHeight);
        graphics.blit(x, drawY, 0, 34, fluidHeight, sprite);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Клик по новой кнопке режима (x80 y95, размер 15x15)
            if (isMouseOver(mouseX, mouseY, 80, 95, 15, 15)) {
                playSound();
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