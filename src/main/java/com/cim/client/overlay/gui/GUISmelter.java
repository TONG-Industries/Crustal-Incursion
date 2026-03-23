package com.cim.client.overlay.gui;

import com.cim.main.CrustalIncursionMod;
import com.cim.menu.SmelterMenu;
import com.cim.multiblock.industrial.SmelterBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class GUISmelter extends AbstractContainerScreen<SmelterMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            CrustalIncursionMod.MOD_ID, "textures/gui/machine/smelter_gui.png");

    public GUISmelter(SmelterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 184;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Фон GUI (176x184)
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // === ПОЛОСКА ТЕМПЕРАТУРЫ (x12 y17, 15x51, текстура x175 y4) ===
        int temp = menu.getTemperature();
        int maxTemp = SmelterBlockEntity.MAX_TEMP;
        int barHeight = 51;
        int fillHeight = (temp * barHeight) / maxTemp;

        if (fillHeight > 0) {
            // Рисуем снизу вверх: y + 17 + (51 - fillHeight)
            gui.blit(TEXTURE, x + 12, y + 17 + (barHeight - fillHeight),
                    175, 4 + (barHeight - fillHeight), 15, fillHeight);
        }

        // === ПРОГРЕСС ВЕРХНЕГО РЯДА (x95 y7, 70x3, текстура x175 y0) ===
        if (menu.isSmeltingTop()) {
            int progress = menu.getProgressTop(); // 0-100
            int fillWidth = (progress * 70) / 100;
            gui.blit(TEXTURE, x + 95, y + 7, 175, 0, fillWidth, 3);
        }

        // === ПРОГРЕСС НИЖНЕГО РЯДА (x95 y39, 70x3) ===
        if (menu.isSmeltingBottom()) {
            int progress = menu.getProgressBottom();
            int fillWidth = (progress * 70) / 100;
            gui.blit(TEXTURE, x + 95, y + 39, 175, 0, fillWidth, 3);
        }

        // === ЖИДКОСТНОЙ БУФЕР (x33 y8, 48x70) ===
        renderFluidTank(gui, x + 33, y + 8, 48, 70);
    }

    private void renderFluidTank(GuiGraphics gui, int x, int y, int width, int height) {
        List<FluidStack> fluids = menu.getBlockEntity().getFluidStacks();
        if (fluids.isEmpty()) return;

        int totalCapacity = SmelterBlockEntity.TANK_CAPACITY;
        int totalAmount = Math.max(fluids.stream().mapToInt(FluidStack::getAmount).sum(), 1);

        // Масштабируем к высоте бака
        int currentY = y + height;

        for (FluidStack fluid : fluids) {
            int segmentHeight = (int)((fluid.getAmount() * height) / (float)totalCapacity);
            if (segmentHeight < 2) segmentHeight = 2; // минимальная высота для видимости

            int color = getFluidColor(fluid);

            // Рисуем сегмент
            gui.fill(x, currentY - segmentHeight, x + width, currentY, 0xFF000000 | color);

            // Блик сверху
            gui.fill(x, currentY - segmentHeight, x + width, currentY - segmentHeight + 1, 0x60FFFFFF);

            // Тень снизу
            gui.fill(x, currentY - 1, x + width, currentY, 0x30000000);

            currentY -= segmentHeight;
        }
    }

    private int getFluidColor(FluidStack stack) {
        // Цвета для металлов (замени на свои жидкости)
        if (stack.getFluid().getFluidType().getDescriptionId().contains("iron")) return 0xA19D94; // серебристый
        if (stack.getFluid().getFluidType().getDescriptionId().contains("copper")) return 0xB87333; // медный
        if (stack.getFluid().getFluidType().getDescriptionId().contains("gold")) return 0xFFD700;   // золотой
        if (stack.getFluid().isSame(net.minecraft.world.level.material.Fluids.LAVA)) return 0xFF4500;
        if (stack.getFluid().isSame(net.minecraft.world.level.material.Fluids.WATER)) return 0x3C44AA;
        return 0xC0C0C0; // дефолтный серый
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // Стандартные подписи (название и "инвентарь")
        gui.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        gui.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Тултип температуры
        if (this.isHovering(12, 17, 15, 51, mouseX, mouseY)) {
            int temp = menu.getTemperature();
            float percent = temp / 1600f;
            int color = getTempColor(percent);
            Component text = Component.literal(String.format("%d / %d °C", temp, 1600))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
            gui.renderTooltip(this.font, text, mouseX, mouseY);
        }

        // Тултип жидкостей
        if (this.isHovering(33, 8, 48, 70, mouseX, mouseY)) {
            renderFluidTooltip(gui, mouseX, mouseY);
        } else {
            this.renderTooltip(gui, mouseX, mouseY);
        }
    }

    private void renderFluidTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6§lРасплавленные металлы:"));

        List<FluidStack> fluids = menu.getBlockEntity().getFluidStacks();
        if (fluids.isEmpty()) {
            lines.add(Component.literal("§7Пусто"));
        } else {
            for (FluidStack fluid : fluids) {
                int mb = fluid.getAmount();
                String name = fluid.getDisplayName().getString();

                if (hasShiftDown()) {
                    // Точные мб
                    lines.add(Component.literal(String.format("§f%s: §e%d mB", name, mb)));
                } else {
                    // Формат слитков/блоков: 1 блок = 1000мб, 1 слиток = 111мб, 1 самородок = 12мб
                    int blocks = mb / 1000;
                    int rem = mb % 1000;
                    int ingots = rem / 111;
                    int nuggets = (rem % 111) / 12;

                    StringBuilder sb = new StringBuilder("§f" + name + ": §e");
                    if (blocks > 0) sb.append(blocks).append(" блоков ");
                    if (ingots > 0) sb.append(ingots).append(" слитков ");
                    if (nuggets > 0 || (blocks == 0 && ingots == 0)) sb.append(nuggets).append(" самородков");
                    lines.add(Component.literal(sb.toString().trim()));
                }
            }
            int total = fluids.stream().mapToInt(FluidStack::getAmount).sum();
            int max = SmelterBlockEntity.TANK_CAPACITY;
            lines.add(Component.literal(String.format("§7Всего: %d/%d mB", total, max)));
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    private static int getTempColor(float percent) {
        percent = Math.max(0, Math.min(1, percent));
        if (percent < 0.3f) return 0xAAAAAA;
        if (percent < 0.7f) return 0xFFAA00;
        return 0xFF2222;
    }
}