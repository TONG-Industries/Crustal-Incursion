package com.cim.client.overlay.gui;

import com.cim.api.metal.Metal;
import com.cim.api.metal.MetalUnits;
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

    // Цвет для мигания недостаточной температуры
    private static final int LOW_TEMP_COLOR = 0x910000;
    private static final int OK_TEMP_COLOR = 0x00FF00;
    private static final int GRAY_COLOR = 0x808080;

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

        // Прогресс верхний
        if (menu.isSmeltingTop() || menu.hasTopRecipe()) {
            int progress = menu.getProgressTop();
            int maxProgress = menu.getMaxProgressTop();
            int fillWidth = maxProgress > 0 ? (progress * PROGRESS_WIDTH) / maxProgress : 0;
            if (fillWidth > 0 || menu.hasTopRecipe()) {
                gui.blit(TEXTURE, x + TOP_PROGRESS_X, y + TOP_PROGRESS_Y, 176, 0, fillWidth, PROGRESS_HEIGHT);
            }
        }

        // Прогресс нижний
        if (menu.isSmeltingBottom() || menu.hasBottomRecipe()) {
            int progress = menu.getProgressBottom();
            int maxProgress = menu.getMaxProgressBottom();
            int fillWidth = maxProgress > 0 ? (progress * PROGRESS_WIDTH) / maxProgress : 0;
            if (fillWidth > 0 || menu.hasBottomRecipe()) {
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

        metals.sort((a, b) -> Integer.compare(b.amount, a.amount));

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
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Пусто
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, delta);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Тултип общей температуры
        if (this.isHovering(12, 17, 15, 51, mouseX, mouseY)) {
            renderTemperatureTooltip(gui, mouseX, mouseY);
        }
        // Тултип верхнего прогресса (сплавы)
        else if (this.isHovering(TOP_PROGRESS_X, TOP_PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT, mouseX, mouseY)) {
            renderProgressTooltip(gui, mouseX, mouseY, true);
        }
        // Тултип нижнего прогресса (обычная плавка)
        else if (this.isHovering(BOTTOM_PROGRESS_X, BOTTOM_PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT, mouseX, mouseY)) {
            renderProgressTooltip(gui, mouseX, mouseY, false);
        }
        // Тултип буфера металлов - ВСЕ МЕТАЛЛЫ ЦВЕТНЫМИ
        else if (this.isHovering(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY)) {
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
        int progress = isTop ? menu.getProgressTop() : menu.getProgressBottom();
        int maxProgress = isTop ? menu.getMaxProgressTop() : menu.getMaxProgressBottom();

        // Температура с мигающей индикацией если недостаточно
        boolean hasEnoughTemp = currentTemp >= requiredTemp;
        int tempColor;
        if (hasEnoughTemp) {
            tempColor = OK_TEMP_COLOR;
        } else {
            tempColor = (System.currentTimeMillis() / 500 % 2 == 0) ? LOW_TEMP_COLOR : GRAY_COLOR;
        }

        lines.add(Component.literal(String.format("Температура: %d/%d °C", currentTemp, requiredTemp))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tempColor))));

        // Время плавки
        if (maxProgress > 0) {
            int remaining = maxProgress - progress;
            float seconds = remaining / 400.0f;
            lines.add(Component.literal(String.format("Осталось: %.1fс", Math.max(0, seconds)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    // Общий тултип для всего танка с цветными металлами
    private void renderMetalTankTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6§lРасплавленные металлы:"));

        List<SmelterBlockEntity.MetalStack> metals = menu.getBlockEntity().getMetalStacks();
        if (metals.isEmpty()) {
            lines.add(Component.literal("§7Пусто"));
        } else {
            boolean showExact = hasShiftDown();

            for (SmelterBlockEntity.MetalStack stack : metals) {
                int mb = stack.amount;
                Metal metal = stack.metal;
                int color = metal.getColor();
                String name = Component.translatable(metal.getTranslationKey()).getString();

                if (showExact) {
                    // Точное значение в мб
                    Component metalLine = Component.literal(name + ": ")
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)))
                            .append(Component.literal(mb + " мб")
                                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
                    lines.add(metalLine);
                } else {
                    // Конвертация с автоматическим переносом
                    MetalUnits.MetalStack amount = MetalUnits.convert(mb);

                    // Переносим самородки в слитки
                    int finalIngots = amount.ingots() + (amount.nuggets() / 9);
                    int finalNuggets = amount.nuggets() % 9;

                    // Переносим слитки в блоки
                    int finalBlocks = amount.blocks() + (finalIngots / 9);
                    finalIngots = finalIngots % 9;

                    // Строим строку объема ПОЛНЫМИ словами
                    StringBuilder volumeSb = new StringBuilder();
                    boolean first = true;

                    if (finalBlocks > 0) {
                        volumeSb.append(finalBlocks).append(" ").append(decline(finalBlocks, "блок", "блока", "блоков"));
                        first = false;
                    }
                    if (finalIngots > 0) {
                        if (!first) volumeSb.append(", ");
                        volumeSb.append(finalIngots).append(" ").append(decline(finalIngots, "слиток", "слитка", "слитков"));
                        first = false;
                    }
                    if (finalNuggets > 0) {
                        if (!first) volumeSb.append(", ");
                        volumeSb.append(finalNuggets).append(" ").append(decline(finalNuggets, "самородок", "самородка", "самородков"));
                        first = false;
                    }

                    if (first) {
                        volumeSb.append("< 1 самородка");
                    }

                    Component metalLine = Component.literal(name + ": ")
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)))
                            .append(Component.literal(volumeSb.toString())
                                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
                    lines.add(metalLine);
                }
            }

            // Общая заполненность
            int total = menu.getBlockEntity().getTotalMetalAmount();
            int maxBlocks = menu.getBlockEntity().getBlockCapacity();

            if (showExact) {
                lines.add(Component.literal(String.format("§7Всего: §f%d§7 мб / §f%d§7 мб", total, maxBlocks * 1000)));
            } else {
                MetalUnits.MetalStack totalAmount = MetalUnits.convert(total);

                int finalTotalIngots = totalAmount.ingots() + (totalAmount.nuggets() / 9);
                int finalTotalNuggets = totalAmount.nuggets() % 9;
                int finalTotalBlocks = totalAmount.blocks() + (finalTotalIngots / 9);
                finalTotalIngots = finalTotalIngots % 9;

                StringBuilder totalSb = new StringBuilder("§7Всего: §f");
                boolean first = true;

                if (finalTotalBlocks > 0) {
                    totalSb.append(finalTotalBlocks).append(" ").append(decline(finalTotalBlocks, "блок", "блока", "блоков"));
                    first = false;
                }
                if (finalTotalIngots > 0) {
                    if (!first) totalSb.append(", ");
                    totalSb.append(finalTotalIngots).append(" ").append(decline(finalTotalIngots, "слиток", "слитка", "слитков"));
                    first = false;
                }
                if (finalTotalNuggets > 0) {
                    if (!first) totalSb.append(", ");
                    totalSb.append(finalTotalNuggets).append(" ").append(decline(finalTotalNuggets, "самородок", "самородка", "самородков"));
                    first = false;
                }
                if (first) {
                    totalSb.append("0 блоков");
                }

                totalSb.append(" §8/ ").append(maxBlocks).append(" ").append(decline(maxBlocks, "блок", "блока", "блоков"));
                lines.add(Component.literal(totalSb.toString()));
            }

            lines.add(Component.literal(showExact ? "§8[Shift] скрыть точное значение" : "§8[Shift] точное значение"));
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    // Склонение слов
    private String decline(int count, String one, String two, String five) {
        int absCount = Math.abs(count) % 100;
        int lastDigit = absCount % 10;

        if (absCount >= 11 && absCount <= 19) return five;
        if (lastDigit == 1) return one;
        if (lastDigit >= 2 && lastDigit <= 4) return two;
        return five;
    }

    private static int getTempColor(float percent) {
        percent = Math.max(0, Math.min(1, percent));
        if (percent < 0.3f) return 0xAAAAAA;
        if (percent < 0.7f) return 0xFFAA00;
        return 0xFF2222;
    }
}