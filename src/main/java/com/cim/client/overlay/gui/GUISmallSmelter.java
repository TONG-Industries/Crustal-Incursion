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
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            CrustalIncursionMod.MOD_ID, "textures/gui/machine/smelter_small_gui.png");

    // Координаты элементов GUI
    private static final int HEAT_BAR_X = 40;
    private static final int HEAT_BAR_Y = 9;
    private static final int HEAT_BAR_WIDTH = 15;
    private static final int HEAT_BAR_HEIGHT = 51;

    private static final int TANK_X = 83;
    private static final int TANK_Y = 8;
    private static final int TANK_WIDTH = 32;
    private static final int TANK_HEIGHT = 54;

    private static final int PROGRESS_X = 121;
    private static final int PROGRESS_Y = 7;
    private static final int PROGRESS_WIDTH = 16;
    private static final int PROGRESS_HEIGHT = 3;

    private static final int BURN_INDICATOR_X = 121;
    private static final int BURN_INDICATOR_Y = 35;
    private static final int BURN_INDICATOR_SIZE = 14;
    
    private static final int BURN_TOOLTIP_X = 104;
    private static final int BURN_TOOLTIP_Y = 25;
    private static final int BURN_TOOLTIP_WIDTH = 18;
    private static final int BURN_TOOLTIP_HEIGHT = 18;

    private static final List<ItemStack>[] TIER_ITEMS = new List[6];

    static {
        // Тир 0
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
                new ItemStack(Items.BAMBOO_BLOCK), new ItemStack(Items.CRAFTING_TABLE),
                new ItemStack(Items.CHEST), new ItemStack(Items.BARREL),
                new ItemStack(Items.WOODEN_SWORD), new ItemStack(Items.WOODEN_PICKAXE),
                new ItemStack(Items.WOODEN_AXE), new ItemStack(Items.WOODEN_SHOVEL),
                new ItemStack(Items.WOODEN_HOE), new ItemStack(Items.BOW),
                new ItemStack(Items.CROSSBOW), new ItemStack(Items.FISHING_ROD),
                new ItemStack(Items.CAMPFIRE), new ItemStack(Items.SOUL_CAMPFIRE),
                new ItemStack(Items.TORCH), new ItemStack(Items.SOUL_TORCH)
        );

        // Тир 1
        TIER_ITEMS[1] = Arrays.asList(
                new ItemStack(Items.COAL),
                new ItemStack(Items.CHARCOAL),
                new ItemStack(Items.BLAZE_POWDER)
        );

        // Тир 2
        TIER_ITEMS[2] = Arrays.asList(
                new ItemStack(Items.BLAZE_ROD),
                new ItemStack(Items.MAGMA_CREAM),
                new ItemStack(Items.PORKCHOP)
        );

        // Тир 3
        TIER_ITEMS[3] = Arrays.asList(
                new ItemStack(Items.COAL_BLOCK)
        );

        // Тир 4
        TIER_ITEMS[4] = Arrays.asList(
                new ItemStack(Items.LAVA_BUCKET)
        );

        // Тир 5
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

        // Основная текстура
        gui.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // === Полоска нагрева ===
        float temp = menu.getTemperatureFloat();
        float maxTemp = SmallSmelterBlockEntity.MAX_TEMP;
        int filledHeight = (int) ((temp / maxTemp) * HEAT_BAR_HEIGHT);

        if (filledHeight > 0) {
            gui.blit(TEXTURE,
                    x + HEAT_BAR_X, y + HEAT_BAR_Y + (HEAT_BAR_HEIGHT - filledHeight),
                    177, 19 + (HEAT_BAR_HEIGHT - filledHeight),
                    HEAT_BAR_WIDTH, filledHeight);
        }

        // === Индикатор горения ===
        if (menu.isBurning()) {
            gui.blit(TEXTURE, x + BURN_INDICATOR_X, y + BURN_INDICATOR_Y, 177, 0, BURN_INDICATOR_SIZE, BURN_INDICATOR_SIZE);
        }

        // === Прогресс плавки ===
        if (menu.isSmelting()) {
            int progress = menu.getSmeltProgress();
            int maxProgress = menu.getSmeltMaxProgress();
            int fillWidth = maxProgress > 0 ? (progress * PROGRESS_WIDTH) / maxProgress : 0;
            if (fillWidth > 0) {
                gui.blit(TEXTURE, x + PROGRESS_X, y + PROGRESS_Y, 176, 0, fillWidth, PROGRESS_HEIGHT);
            }
        }

        // === Буфер металла ===
        renderMetalTank(gui, x + TANK_X, y + TANK_Y, TANK_WIDTH, TANK_HEIGHT);
    }

    private void renderMetalTank(GuiGraphics gui, int x, int y, int width, int height) {
        var metals = menu.getBlockEntity().getMetalStacks();
        if (metals.isEmpty()) return;

        int totalCapacity = SmallSmelterBlockEntity.TANK_CAPACITY;
        int currentY = y + height;

        for (var stack : metals) {
            int segmentHeight = (int) ((stack.amount * height) / (float) totalCapacity);
            if (segmentHeight <= 0) continue;

            int color = stack.metal.getColor();
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            gui.setColor(r, g, b, 1.0f);
            gui.blit(TEXTURE, x, currentY - segmentHeight, 194, 19, width, segmentHeight);
            gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);

            // Блик сверху сегмента
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

        // Тултип температуры
        if (this.isHovering(HEAT_BAR_X, HEAT_BAR_Y, HEAT_BAR_WIDTH, HEAT_BAR_HEIGHT, mouseX, mouseY)) {
            renderTemperatureTooltip(gui, mouseX, mouseY);
        }
        // Тултип прогресса плавки
        else if (this.isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT, mouseX, mouseY)) {
            renderProgressTooltip(gui, mouseX, mouseY);
        }
        // Тултип индикатора горения (как у нагревателя — область побольше)
        else if (this.isHovering(BURN_TOOLTIP_X, BURN_TOOLTIP_Y, BURN_TOOLTIP_WIDTH, BURN_TOOLTIP_HEIGHT, mouseX, mouseY)) {
            renderBurnTooltip(gui, mouseX, mouseY);
        }
        // Тултип топливного слота
        else if (this.isHovering(61, 12, 16, 16, mouseX, mouseY)) {
            renderFuelTooltip(gui, mouseX, mouseY);
        }
        // Тултип буфера металла
        else if (this.isHovering(TANK_X, TANK_Y, TANK_WIDTH, TANK_HEIGHT, mouseX, mouseY)) {
            renderMetalTankTooltip(gui, mouseX, mouseY);
        }
        else {
            this.renderTooltip(gui, mouseX, mouseY);
        }
    }

    private void renderTemperatureTooltip(GuiGraphics gui, int mx, int my) {
        float temp = menu.getTemperatureFloat();
        float maxTemp = SmallSmelterBlockEntity.MAX_TEMP;
        float percent = temp / maxTemp;
        int color = getSmoothTemperatureColor(percent);

        Component tempText = Component.literal(String.format("%.0f / %.0f °C", temp, maxTemp))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));

        gui.renderTooltip(this.font, tempText, mx, my);
    }

    private void renderProgressTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        int currentTemp = (int) menu.getTemperatureFloat();
        int requiredTemp = menu.getRequiredTemp();
        int progress = menu.getSmeltProgress();
        int maxProgress = menu.getSmeltMaxProgress();

        boolean hasEnough = currentTemp >= requiredTemp;
        int tempColor = hasEnough ? 0x00FF00 : (System.currentTimeMillis() / 500 % 2 == 0 ? 0x910000 : 0x808080);
        lines.add(Component.literal(String.format("Температура: %d/%d °C", currentTemp, requiredTemp))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(tempColor))));

        if (maxProgress > 0) {
            int remaining = maxProgress - progress;
            int heatPerTick = menu.getHeatPerTick();
            if (heatPerTick <= 0) heatPerTick = 10;
            float seconds = remaining / (heatPerTick * 20.0f);
            lines.add(Component.literal(String.format("Осталось: %.1fс", Math.max(0, seconds)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xAAAAAA))));
        }

        gui.renderComponentTooltip(this.font, lines, mx, my);
    }

    private void renderBurnTooltip(GuiGraphics gui, int mx, int my) {
        if (menu.isBurning()) {
            int seconds = menu.getBurnTime() / 20;
            int totalSeconds = menu.getTotalBurnTime() / 20;

            Component timeText = Component.literal(
                    String.format("§6Осталось: §f%d§7/§f%d сек", seconds, totalSeconds)
            );
            gui.renderTooltip(this.font, timeText, mx, my);
        } else {
            gui.renderTooltip(this.font, Component.literal("§7Остановлен"), mx, my);
        }
    }

    private void renderMetalTankTooltip(GuiGraphics gui, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§6§lРасплавленные металлы:"));

        var metals = menu.getBlockEntity().getMetalStacks();
        if (metals.isEmpty()) {
            lines.add(Component.literal("§7Пусто"));
        } else {
            boolean showExact = hasShiftDown();

            var displayOrder = new ArrayList<>(metals);
            Collections.reverse(displayOrder);

            for (var stack : displayOrder) {
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

    private void renderFuelTooltip(GuiGraphics gui, int mx, int my) {
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

        int tooltipX = mx + 8;
        int tooltipY = my - tooltipHeight / 2;

        if (tooltipX + tooltipWidth > this.width) tooltipX = mx - tooltipWidth - 8;
        if (tooltipY < 4) tooltipY = 4;
        if (tooltipY + tooltipHeight > this.height) tooltipY = this.height - tooltipHeight - 4;

        // Фон тултипа
        gui.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0100010);
        gui.fill(tooltipX + 1, tooltipY, tooltipX + tooltipWidth - 1, tooltipY + 1, 0xF0500070);
        gui.fill(tooltipX + 1, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth - 1, tooltipY + tooltipHeight, 0xF0500070);
        gui.fill(tooltipX, tooltipY, tooltipX + 1, tooltipY + tooltipHeight, 0xF0500070);
        gui.fill(tooltipX + tooltipWidth - 1, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0500070);

        long currentSecond = System.currentTimeMillis() / 1000;

        for (int i = 0; i < lines.length; i++) {
            int lineY = tooltipY + padding + i * lineHeight;

            if (i == 0) {
                gui.drawString(this.font, lines[i], tooltipX + padding, lineY + 2, 0xFFFFFF, true);
            } else {
                int tier = i - 1;

                List<ItemStack> items = TIER_ITEMS[tier];
                if (items != null && !items.isEmpty()) {
                    int itemIndex = (int) ((currentSecond + tier) % items.size());
                    ItemStack stack = items.get(itemIndex);

                    gui.pose().pushPose();
                    gui.pose().translate(tooltipX + padding, lineY, 100);
                    gui.pose().scale(0.75f, 0.75f, 1.0f);
                    gui.renderItem(stack, 0, 0);
                    gui.renderItemDecorations(this.font, stack, 0, 0);
                    gui.pose().popPose();
                }

                int textX = tooltipX + padding + iconSize + iconTextGap;
                gui.drawString(this.font, lines[i], textX, lineY + 2, 0xFFFFFF, true);
            }
        }
    }

    private static int getSmoothTemperatureColor(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        int colorGrey = 0xAAAAAA;
        int colorOrange = 0xFFAA00;
        int colorRed = 0xFF2222;

        if (percent <= 0.3f) {
            return lerpColor(colorGrey, colorOrange, percent / 0.3f);
        } else if (percent <= 0.7f) {
            return lerpColor(colorOrange, colorRed, (percent - 0.3f) / 0.4f);
        } else {
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