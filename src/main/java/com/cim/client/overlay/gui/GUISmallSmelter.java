// com/cim/client/overlay/gui/GUISmallSmelter.java
package com.cim.client.overlay.gui;

import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.block.entity.industrial.casting.SmallSmelterBlockEntity;
import com.cim.item.ModItems;
import com.cim.main.CrustalIncursionMod;
import com.cim.menu.SmallSmelterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GUISmallSmelter extends AbstractContainerScreen<SmallSmelterMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/machine/small_smelter_gui.png");
    private static final int TANK_X = 83;
    private static final int TANK_Y = 8;
    private static final int TANK_W = 32;
    private static final int TANK_H = 54;
    private static final int TEMP_BAR_X = 40;
    private static final int TEMP_BAR_Y = 9;
    private static final int TEMP_BAR_W = 15;
    private static final int TEMP_BAR_H = 51;
    private static final int FLAME_X = 121;
    private static final int FLAME_Y = 35;
    private static final int FLAME_W = 14;
    private static final int FLAME_H = 14;
    private static final int PROGRESS_X = 121;
    private static final int PROGRESS_Y = 7;
    private static final int PROGRESS_W = 16;
    private static final int PROGRESS_H = 3;

    private static final List<ItemStack>[] TIER_ITEMS = new List[6];

    static {
        TIER_ITEMS[0] = Arrays.asList(
                new ItemStack(Items.STICK), new ItemStack(Items.SCAFFOLDING),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.SPRUCE_PLANKS),
                new ItemStack(Items.BIRCH_PLANKS), new ItemStack(Items.JUNGLE_PLANKS),
                new ItemStack(Items.ACACIA_PLANKS), new ItemStack(Items.DARK_OAK_PLANKS),
                new ItemStack(Items.MANGROVE_PLANKS), new ItemStack(Items.CHERRY_PLANKS),
                new ItemStack(Items.BAMBOO_PLANKS), new ItemStack(Items.BAMBOO_MOSAIC),
                new ItemStack(Items.OAK_LOG), new ItemStack(Items.SPRUCE_LOG),
                new ItemStack(Items.BIRCH_LOG), new ItemStack(Items.JUNGLE_LOG),
                new ItemStack(Items.ACACIA_LOG), new ItemStack(Items.DARK_OAK_LOG),
                new ItemStack(Items.MANGROVE_LOG), new ItemStack(Items.CHERRY_LOG),
                new ItemStack(Items.BAMBOO_BLOCK)
        );
        TIER_ITEMS[1] = Arrays.asList(
                new ItemStack(Items.COAL),
                new ItemStack(Items.CHARCOAL),
                new ItemStack(Items.BLAZE_POWDER)
        );
        TIER_ITEMS[2] = Arrays.asList(
                new ItemStack(Items.BLAZE_ROD),
                new ItemStack(Items.MAGMA_CREAM),
                new ItemStack(Items.PORKCHOP)
        );
        TIER_ITEMS[3] = Arrays.asList(
                new ItemStack(Items.COAL_BLOCK)
        );
        TIER_ITEMS[4] = Arrays.asList(
                new ItemStack(Items.LAVA_BUCKET)
        );
        TIER_ITEMS[5] = Arrays.asList(
                new ItemStack(ModItems.MORY_LAH.get()),
                new ItemStack(Items.DRAGON_BREATH)
        );
    }

    public GUISmallSmelter(SmallSmelterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Температурная полоска
        float temp = menu.getTemperature() / 10f;
        float maxTemp = SmallSmelterBlockEntity.MAX_TEMP;
        int filledHeight = (int)((temp / maxTemp) * TEMP_BAR_H);
        if (filledHeight > 0) {
            gui.blit(TEXTURE, x + TEMP_BAR_X, y + TEMP_BAR_Y + (TEMP_BAR_H - filledHeight),
                    177, 19 + (TEMP_BAR_H - filledHeight), TEMP_BAR_W, filledHeight);
        }

        // Индикатор горения — только если burnTime > 0
        if (menu.isBurning() && menu.getBurnTime() > 0) {
            gui.blit(TEXTURE, x + FLAME_X, y + FLAME_Y, 179, 2, FLAME_W, FLAME_H);
        }

        // === ПРОГРЕСС ПЛАВКИ — читаем напрямую из BlockEntity, а не ContainerData ===
        SmallSmelterBlockEntity be = menu.getBlockEntity();
        if (be != null && be.getSmeltMaxProgress() > 0) {
            float progress = be.getSmeltProgress();
            float maxProgress = be.getSmeltMaxProgress();
            // Прогресс идёт всегда, если есть maxProgress (включая шлак и кастомные металлы)
            if (progress > 0 || be.isSmelting() || be.isSlagRecipe() || be.getCurrentRecipe() != null) {
                int fillWidth = (int)((progress / maxProgress) * PROGRESS_W);
                if (fillWidth > 0) {
                    gui.blit(TEXTURE, x + PROGRESS_X, y + PROGRESS_Y, 215, 15, fillWidth, PROGRESS_H);
                }
            }
        }

        // Резервуар металла (32x54)
        renderMetalTank(gui, x + TANK_X, y + TANK_Y, TANK_W, TANK_H);
    }

    private void renderMetalTank(GuiGraphics gui, int x, int y, int width, int height) {
        List<SmallSmelterBlockEntity.MetalStack> metals = menu.getBlockEntity().getMetalStacks();
        if (metals.isEmpty()) return;

        int totalCapacity = SmallSmelterBlockEntity.CAPACITY_UNITS;
        int currentY = y + height;

        for (SmallSmelterBlockEntity.MetalStack stack : metals) {
            int segmentHeight = (int)((stack.amount * height) / (float)totalCapacity);
            if (segmentHeight <= 0) continue;
            int color = stack.metal.getColor();
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;
            gui.setColor(r, g, b, 1.0f);
            gui.blit(TEXTURE, x, currentY - segmentHeight, 194, 19, width, segmentHeight);
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

        // Слот топлива (61,12) — тултип с тирами
        if (isHovering(61, 12, 16, 16, mouseX, mouseY)) {
            renderFuelTooltip(gui, mouseX, mouseY);
        } else if (isHovering(TEMP_BAR_X, TEMP_BAR_Y, TEMP_BAR_W, TEMP_BAR_H, mouseX, mouseY)) {
            renderTemperatureTooltip(gui, mouseX, mouseY);
        } else if (isHovering(FLAME_X, FLAME_Y, FLAME_W, FLAME_H, mouseX, mouseY)) {
            renderFlameTooltip(gui, mouseX, mouseY);
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY)) {
            renderProgressTooltip(gui, mouseX, mouseY);
        } else if (isHovering(TANK_X, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
            renderMetalTankTooltip(gui, mouseX, mouseY);
        } else {
            this.renderTooltip(gui, mouseX, mouseY);
        }
    }

    private void renderFuelTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String[] lines = {
                "§6§lТопливные тиры:",
                "§8Тир 0: §f1°C, §f6.25§7с.",
                "§8Тир 1: §f2°C, §f12.5§7с.",
                "§8Тир 2: §f3°C, §f25§7с.",
                "§8Тир 3: §f4°C, §f40§7с.",
                "§8Тир 4: §f6°C, §f60§7с.",
                "§8Тир 5: §f8°C, §f120§7с."
        };

        int lineHeight = 11;
        int padding = 4;
        int iconSize = 12;
        int iconTextGap = 2;

        int maxTextWidth = 0;
        for (String line : lines) {
            maxTextWidth = Math.max(maxTextWidth, this.font.width(line));
        }

        int tooltipWidth = padding + iconSize + iconTextGap + maxTextWidth + padding;
        int tooltipHeight = lines.length * lineHeight + padding * 2;

        // === ВСЕГДА СЛЕВА ===
        int tooltipX = mouseX - tooltipWidth - 8;
        int tooltipY = mouseY - tooltipHeight / 2;

        // Только вертикальная коррекция, если вылезает за экран
        if (tooltipY < 4) tooltipY = 4;
        if (tooltipY + tooltipHeight > this.height) tooltipY = this.height - tooltipHeight - 4;

        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0100010);
        guiGraphics.fill(tooltipX + 1, tooltipY, tooltipX + tooltipWidth - 1, tooltipY + 1, 0xF0500070);
        guiGraphics.fill(tooltipX + 1, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth - 1, tooltipY + tooltipHeight, 0xF0500070);
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + 1, tooltipY + tooltipHeight, 0xF0500070);
        guiGraphics.fill(tooltipX + tooltipWidth - 1, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0500070);

        long currentSecond = System.currentTimeMillis() / 1000;

        for (int i = 0; i < lines.length; i++) {
            int lineY = tooltipY + padding + i * lineHeight;

            if (i == 0) {
                guiGraphics.drawString(this.font, lines[i], tooltipX + padding, lineY + 2, 0xFFFFFF, true);
            } else {
                int tier = i - 1;

                List<ItemStack> items = TIER_ITEMS[tier];
                if (items != null && !items.isEmpty()) {
                    int itemIndex = (int)((currentSecond + tier) % items.size());
                    ItemStack stack = items.get(itemIndex);

                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(tooltipX + padding, lineY, 100);
                    guiGraphics.pose().scale(0.75f, 0.75f, 1.0f);
                    guiGraphics.renderItem(stack, 0, 0);
                    guiGraphics.renderItemDecorations(this.font, stack, 0, 0);
                    guiGraphics.pose().popPose();
                }

                int textX = tooltipX + padding + iconSize + iconTextGap;
                guiGraphics.drawString(this.font, lines[i], textX, lineY + 2, 0xFFFFFF, true);
            }
        }
    }

    private void renderTemperatureTooltip(GuiGraphics gui, int mx, int my) {
        float temp = menu.getTemperature() / 10f;
        float maxTemp = SmallSmelterBlockEntity.MAX_TEMP;
        int color = getSmoothTemperatureColor(temp / maxTemp);
        Component text = Component.literal(String.format("%.0f / %.0f °C", temp, maxTemp))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        gui.renderTooltip(this.font, text, mx, my);
    }

    private void renderFlameTooltip(GuiGraphics gui, int mx, int my) {
        if (menu.isBurning()) {
            int seconds = menu.getBurnTime() / 20;
            int totalSeconds = menu.getTotalBurnTime() / 20;
            Component text = Component.literal(String.format("§6Осталось: §f%d§7/§f%d сек", seconds, totalSeconds));
            gui.renderTooltip(this.font, text, mx, my);
        } else {
            gui.renderTooltip(this.font, Component.literal("§7Остановлен"), mx, my);
        }
    }

    private void renderProgressTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        int currentTemp = (int)(menu.getTemperature() / 10f);
        SmallSmelterBlockEntity be = menu.getBlockEntity();
        int requiredTemp = be != null ? be.getRequiredTemp() : 0;
        boolean hasEnough = currentTemp >= requiredTemp;
        int tempColor = hasEnough ? 0x00FF00 : (System.currentTimeMillis() / 500 % 2 == 0 ? 0x910000 : 0x808080);
        lines.add(Component.literal(String.format("Температура: %d/%d °C", currentTemp, requiredTemp))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tempColor))));

        if (be != null && be.getSmeltMaxProgress() > 0) {
            float remaining = be.getSmeltMaxProgress() - be.getSmeltProgress();
            float heatPerTick = 0;
            if (be.getCurrentRecipe() != null) {
                heatPerTick = be.getCurrentRecipe().heatConsumption();
            } else if (be.isSlagRecipe()) {
                heatPerTick = be.getSmeltHeatPerTick();
            }
            if (heatPerTick > 0) {
                float seconds = remaining / (heatPerTick * 20.0f);
                lines.add(Component.literal(String.format("Осталось: %.1fс", Math.max(0, seconds)))
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
            }
        }
        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    private void renderMetalTankTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6§lРасплавленные металлы:"));
        List<SmallSmelterBlockEntity.MetalStack> metals = menu.getBlockEntity().getMetalStacks();
        if (metals.isEmpty()) {
            lines.add(Component.literal("§7Пусто"));
        } else {
            boolean showExact = hasShiftDown();
            List<SmallSmelterBlockEntity.MetalStack> displayOrder = new ArrayList<>(metals);
            Collections.reverse(displayOrder);
            for (SmallSmelterBlockEntity.MetalStack stack : displayOrder) {
                int units = stack.amount;
                MetalUnits2.MetalStack converted = MetalUnits2.convertFromUnits(units);
                String name = Component.translatable(stack.metal.getTranslationKey()).getString();
                if (showExact) {
                    lines.add(Component.literal(name + ": " + units + " ед.")
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(stack.metal.getColor()))));
                } else {
                    StringBuilder sb = new StringBuilder();
                    if (converted.blocks() > 0) sb.append(converted.blocks()).append("б ");
                    if (converted.ingots() > 0) sb.append(converted.ingots()).append("сл ");
                    if (converted.nuggets() > 0) sb.append(converted.nuggets()).append("см ");
                    if (sb.length() == 0) sb.append("0");
                    lines.add(Component.literal(name + ": " + sb.toString())
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(stack.metal.getColor()))));
                }
            }
            int total = menu.getBlockEntity().getTotalMetalAmount();
            lines.add(Component.literal(String.format("§7Всего: §f%d§7 ед. / §f%d§7 ед.", total, SmallSmelterBlockEntity.CAPACITY_UNITS)));
            lines.add(Component.literal(showExact ? "§8[Shift] скрыть точное значение" : "§8[Shift] точное значение"));
        }
        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    private int getSmoothTemperatureColor(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        int colorGrey = 0xAAAAAA;
        int colorOrange = 0xFFAA00;
        int colorRed = 0xFF2222;
        if (percent <= 0.3f) return lerpColor(colorGrey, colorOrange, percent / 0.3f);
        else if (percent <= 0.7f) return lerpColor(colorOrange, colorRed, (percent - 0.3f) / 0.4f);
        else return colorRed;
    }

    private int lerpColor(int c1, int c2, float t) {
        int r = (int)(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int)(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int)((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}