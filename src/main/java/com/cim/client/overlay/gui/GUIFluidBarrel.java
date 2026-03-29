package com.cim.client.overlay.gui;

import com.cim.network.ModPacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
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
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

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

        // --- ОТРИСОВКА КАСТОМНЫХ ТУЛТИПОВ ---
        this.renderCustomTooltips(graphics, mouseX, mouseY);
    }

    private void renderCustomTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int relX = mouseX - this.leftPos;
        int relY = mouseY - this.topPos;

        // 1. Тултип для резервуара с жидкостью (x: 71, y: 39, ширина: 16, высота: 52)
        if (relX >= 71 && relX < 105 && relY >= 39 && relY < 91) {
            List<Component> tooltip = new ArrayList<>();
            FluidStack fluid = menu.getFluid();

            if (fluid.isEmpty()) {
                tooltip.add(Component.literal("Пусто").withStyle(ChatFormatting.GRAY));
            } else {
                // Название жидкости (берется локализованное название от Forge)
                tooltip.add(fluid.getDisplayName());
                // Количество
                tooltip.add(Component.literal(fluid.getAmount() + " / " + menu.getCapacity() + " mB").withStyle(ChatFormatting.GRAY));
            }
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }

        // 2. Тултип для кнопки режима (x: 80, y: 95, размер: 15x15)
        if (relX >= 80 && relX < 95 && relY >= 95 && relY < 110) {
            List<Component> tooltip = new ArrayList<>();
            String modeName = switch (menu.getMode()) {
                case 0 -> "§aВход / Выход (Оба)";
                case 1 -> "§bТолько Вход";
                case 2 -> "§6Только Выход";
                case 3 -> "§cОтключено";
                default -> "Неизвестно";
            };
            tooltip.add(Component.literal("Режим:"));
            tooltip.add(Component.literal(modeName));
            graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int x = this.leftPos;
        int y = this.topPos;

        // 1. Рисуем фон
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // 2. Рисуем иконку режима
        int mode = menu.getMode();
        graphics.blit(TEXTURE, x + 80, y + 95, 177, mode * 16, 15, 15);

        // 3. Рисуем защитника (если есть)
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

        // 4. Рисуем жидкость (x71 y39)
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

        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int startY = y + (maxFluidHeight - fluidHeight);
        int width = 34;

        // === ИДЕАЛЬНЫЙ ТАЙЛИНГ С ОБРЕЗКОЙ ===
        for (int i = 0; i < width; i += 16) {
            int drawWidth = Math.min(width - i, 16);

            for (int j = 0; j < fluidHeight; j += 16) {
                int drawHeight = Math.min(fluidHeight - j, 16);

                int drawX = x + i;
                int drawY = startY + fluidHeight - j - drawHeight;

                // Вычисляем, какую именно часть текстуры нужно "вырезать"
                float minU = sprite.getU0();
                float maxU = minU + (sprite.getU1() - minU) * ((float) drawWidth / 16.0F);

                float minV = sprite.getV0();
                float maxV = minV + (sprite.getV1() - minV) * ((float) drawHeight / 16.0F);

                // Рисуем полигон с точными цветами и обрезанной текстурой (Bottom-Left, Bottom-Right, Top-Right, Top-Left)
                bufferBuilder.vertex(matrix, drawX, drawY + drawHeight, 0).uv(minU, maxV).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, drawX + drawWidth, drawY + drawHeight, 0).uv(maxU, maxV).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, drawX + drawWidth, drawY, 0).uv(maxU, minV).color(r, g, b, a).endVertex();
                bufferBuilder.vertex(matrix, drawX, drawY, 0).uv(minU, minV).color(r, g, b, a).endVertex();
            }
        }
        Tesselator.getInstance().end();

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Клик по кнопке режима (x80 y95, размер 15x15)
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