package com.cim.client.overlay.gui;

import com.cim.api.metallurgy.system.MetalUnits2;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GUISmelter extends AbstractContainerScreen<SmelterMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            CrustalIncursionMod.MOD_ID, "textures/gui/machine/smelter_gui.png");

    private static final int TOP_PROGRESS_X = 95;
    private static final int TOP_PROGRESS_Y = 7;
    private static final int BOTTOM_PROGRESS_X = 95;
    private static final int BOTTOM_PROGRESS_Y = 39;
    private static final int PROGRESS_WIDTH = 70;
    private static final int PROGRESS_HEIGHT = 3;

    private static final int TANK_X = 33;
    private static final int TANK_Y = 8;
    private static final int TANK_WIDTH = 48;
    private static final int TANK_HEIGHT = 70;

    public GUISmelter(SmelterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 184;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Температура
        int temp = menu.getTemperature();
        int maxTemp = SmelterBlockEntity.MAX_TEMP;
        int barHeight = 51;
        int fillHeight = (temp * barHeight) / maxTemp;
        if (fillHeight > 0) {
            gui.blit(TEXTURE, x + 12, y + 17 + (barHeight - fillHeight),
                    176, 4 + (barHeight - fillHeight), 15, fillHeight);
        }

        // Прогресс верхнего ряда
        if (menu.isTopSmelting() || menu.hasTopRecipe()) {
            int progress = menu.getTopProgress();
            int maxProgress = menu.getTopMaxProgress();
            int fillWidth = maxProgress > 0 ? (progress * PROGRESS_WIDTH) / maxProgress : 0;
            if (fillWidth > 0) {
                gui.blit(TEXTURE, x + TOP_PROGRESS_X, y + TOP_PROGRESS_Y, 176, 0, fillWidth, PROGRESS_HEIGHT);
            }
        }

        // Прогресс нижнего ряда
        if (menu.isBottomSmelting() || menu.hasBottomRecipe()) {
            int progress = menu.getBottomProgress();
            int maxProgress = menu.getBottomMaxProgress();
            int fillWidth = maxProgress > 0 ? (progress * PROGRESS_WIDTH) / maxProgress : 0;
            if (fillWidth > 0) {
                gui.blit(TEXTURE, x + BOTTOM_PROGRESS_X, y + BOTTOM_PROGRESS_Y, 176, 0, fillWidth, PROGRESS_HEIGHT);
            }
        }

        renderMetalTank(gui, x + TANK_X, y + TANK_Y, TANK_WIDTH, TANK_HEIGHT);
    }

    private void renderMetalTank(GuiGraphics gui, int x, int y, int width, int height) {
        List<SmelterBlockEntity.MetalStack> metals = menu.getBlockEntity().getMetalStacks();
        if (metals.isEmpty()) return;

        int totalCapacity = SmelterBlockEntity.TANK_CAPACITY;
        int currentY = y + height;

        for (SmelterBlockEntity.MetalStack stack : metals) {
            int segmentHeight = (int)((stack.amount * height) / (float)totalCapacity);
            if (segmentHeight <= 0) continue;

            int color = stack.metal.getColor();
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            gui.setColor(r, g, b, 1.0f);
            gui.blit(TEXTURE, x, currentY - segmentHeight, 50, 185, width, segmentHeight);
            gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.fill(x, currentY - segmentHeight, x + width, currentY - segmentHeight + 1, 0x40FFFFFF);
            currentY -= segmentHeight;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (this.isHovering(12, 17, 15, 51, mouseX, mouseY)) {
            renderTemperatureTooltip(gui, mouseX, mouseY);
        } else if (this.isHovering(TOP_PROGRESS_X, TOP_PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT, mouseX, mouseY)) {
            renderProgressTooltip(gui, mouseX, mouseY, true);
        } else if (this.isHovering(BOTTOM_PROGRESS_X, BOTTOM_PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT, mouseX, mouseY)) {
            renderProgressTooltip(gui, mouseX, mouseY, false);
        } else if (this.isHovering(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY)) {
            renderMetalTankTooltip(gui, mouseX, mouseY);
        } else {
            this.renderTooltip(gui, mouseX, mouseY);
        }
    }

    private void renderTemperatureTooltip(GuiGraphics gui, int mx, int my) {
        int temp = menu.getTemperature();
        float percent = temp / 1600f;
        int color = getTempColor(percent);
        Component text = Component.literal(String.format("%d / %d °C", temp, 1600))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        gui.renderTooltip(this.font, text, mx, my);
    }

    private void renderProgressTooltip(GuiGraphics gui, int mx, int my, boolean isTop) {
        List<Component> lines = new ArrayList<>();
        int currentTemp = menu.getTemperature();
        int requiredTemp = isTop ? menu.getRequiredTempTop() : menu.getRequiredTempBottom();
        int progress = isTop ? menu.getTopProgress() : menu.getBottomProgress();
        int maxProgress = isTop ? menu.getTopMaxProgress() : menu.getBottomMaxProgress();

        boolean hasEnough = currentTemp >= requiredTemp;
        int tempColor = hasEnough ? 0x00FF00 : (System.currentTimeMillis() / 500 % 2 == 0 ? 0x910000 : 0x808080);
        lines.add(Component.literal(String.format("Температура: %d/%d °C", currentTemp, requiredTemp))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tempColor))));

        if (maxProgress > 0) {
            int remaining = maxProgress - progress;
            int heatPerTick = isTop ? menu.getTopHeatPerTick() : menu.getBottomHeatPerTick();
            if (heatPerTick <= 0) heatPerTick = 10; // запасное значение
            float seconds = remaining / (heatPerTick * 20.0f);
            lines.add(Component.literal(String.format("Осталось: %.1fс", Math.max(0, seconds)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }
    private void renderMetalTankTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6§lРасплавленные металлы:"));

        List<SmelterBlockEntity.MetalStack> metals = menu.getBlockEntity().getMetalStacks();
        if (metals.isEmpty()) {
            lines.add(Component.literal("§7Пусто"));
        } else {
            boolean showExact = hasShiftDown();
            
            List<SmelterBlockEntity.MetalStack> displayOrder = new ArrayList<>(metals);
            Collections.reverse(displayOrder);

            for (SmelterBlockEntity.MetalStack stack : displayOrder) {
                int units = stack.amount;
                MetalUnits2.MetalStack converted = MetalUnits2.convertFromUnits(units);
                String name = Component.translatable(stack.metal.getTranslationKey()).getString();

                if (showExact) {
                    lines.add(Component.literal(name + ": " + units + " ед.")
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(stack.metal.getColor()))));
                } else {
                    StringBuilder sb = new StringBuilder();
                    if (converted.blocks() > 0) sb.append(converted.blocks()).append("блоки ");
                    if (converted.ingots() > 0) sb.append(converted.ingots()).append("слитки ");
                    if (converted.nuggets() > 0) sb.append(converted.nuggets()).append("самородки ");
                    if (sb.length() == 0) sb.append("0");
                    lines.add(Component.literal(name + ": " + sb.toString())
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(stack.metal.getColor()))));
                }
            }
            int total = menu.getBlockEntity().getTotalMetalAmount();
            int maxBlocks = menu.getBlockEntity().getBlockCapacity();
            if (showExact) {
                lines.add(Component.literal(String.format("§7Всего: §f%d§7 ед. / §f%d§7 ед.", total, maxBlocks * 81)));
            } else {
                MetalUnits2.MetalStack totalConv = MetalUnits2.convertFromUnits(total);
                lines.add(Component.literal(String.format("§7Всего: §f%dб, %dсл, %dсм §8/ %d блоков",
                        totalConv.blocks(), totalConv.ingots(), totalConv.nuggets(), maxBlocks)));
            }
            lines.add(Component.literal(showExact ? "§8[Shift] скрыть точное значение" : "§8[Shift] точное значение"));
        }
        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    private int getTempColor(float percent) {
        if (percent < 0.3f) return 0xAAAAAA;
        if (percent < 0.7f) return 0xFFAA00;
        return 0xFF2222;
    }
}