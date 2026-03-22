package com.cim.client.overlay.gui;

import com.cim.main.CrustalIncursionMod;
import com.cim.menu.HeaterMenu;
import com.cim.multiblock.industrial.HeaterBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GUIHeater extends AbstractContainerScreen<HeaterMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            CrustalIncursionMod.MOD_ID, "textures/gui/machine/heater_gui.png");

    // Предметы для анимации каждого тира (меняются каждую секунду)
    private static final List<ItemStack>[] TIER_ITEMS = new List[6];

    static {
        // Тир 0: дешевое топливо
        TIER_ITEMS[0] = Arrays.asList(
                new ItemStack(Items.STICK),
                new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.SPRUCE_PLANKS),
                new ItemStack(Items.BIRCH_PLANKS),
                new ItemStack(Items.ACACIA_PLANKS)
        );

        // Тир 1: обычное топливо
        TIER_ITEMS[1] = Arrays.asList(
                new ItemStack(Items.COAL),
                new ItemStack(Items.CHARCOAL)
        );

        // Тир 2: blaze rod (и шуточный porkchop из оригинала)
        TIER_ITEMS[2] = Arrays.asList(
                new ItemStack(Items.BLAZE_ROD),
                new ItemStack(Items.PORKCHOP)
        );

        // Тир 3: блок угля
        TIER_ITEMS[3] = Arrays.asList(
                new ItemStack(Blocks.COAL_BLOCK)
        );

        // Тир 4: ведро лавы
        TIER_ITEMS[4] = Arrays.asList(
                new ItemStack(Items.LAVA_BUCKET)
        );

        // Тир 5: специальное
        TIER_ITEMS[5] = Arrays.asList(
                new ItemStack(Items.NETHER_STAR),
                new ItemStack(Items.DRAGON_BREATH) // Для визуала
        );
    }

    public GUIHeater(HeaterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // === ИСПРАВЛЕННАЯ ПОЛОСКА НАГРЕВА: 15x51 ===
        int temp = menu.getTemperature();
        int maxTemp = HeaterBlockEntity.MAX_TEMP;
        int barWidth = 15;  // Было 16
        int barHeight = 51; // Было 52
        int filledHeight = (int) ((long) temp * barHeight / maxTemp);

        if (filledHeight > 0) {
            guiGraphics.blit(TEXTURE,
                    x + 64, y + 9 + (barHeight - filledHeight),
                    177, 19 + (barHeight - filledHeight),
                    barWidth, filledHeight);
        }

        // Индикатор работы
        if (menu.isBurning()) {
            guiGraphics.blit(TEXTURE, x + 104, y + 25, 177, 0, 18, 18);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Название убрано по просьбе
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Если наведены на слот топлива - рисуем кастомный тултип
        if (this.isHovering(85, 12, 16, 16, mouseX, mouseY)) {
            renderFuelTooltip(guiGraphics, mouseX, mouseY);
        } else {
            // Обычные тултипы
            renderStandardTooltips(guiGraphics, mouseX, mouseY, x, y);
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderStandardTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int y) {
        // Тултип температуры (область 15x51) с цветом
        if (this.isHovering(64, 9, 15, 51, mouseX, mouseY)) {
            int temp = menu.getTemperature();
            int maxTemp = HeaterBlockEntity.MAX_TEMP;
            float percent = (float) temp / maxTemp;
            int color = getSmoothTemperatureColor(percent);

            Component tempText = Component.literal(String.format("%d / %d °C", temp, maxTemp))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));

            guiGraphics.renderTooltip(this.font, tempText, mouseX, mouseY);
        }

        // Тултип индикатора работы (без изменений)
        if (this.isHovering(104, 25, 18, 18, mouseX, mouseY)) {
            if (menu.isBurning()) {
                int seconds = menu.getBurnTime() / 20;
                int totalSeconds = menu.getTotalBurnTime() / 20;
                Component timeText = Component.literal(
                        String.format("§6Осталось: §f%d§7/§f%d сек", seconds, totalSeconds)
                );
                guiGraphics.renderTooltip(this.font, timeText, mouseX, mouseY);
            } else {
                guiGraphics.renderTooltip(this.font, Component.literal("§7Остановлен"), mouseX, mouseY);
            }
        }
    }

    private void renderFuelTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Данные для отображения
        String[] lines = {
                "§6§lТопливные тиры:",
                "§8Тир 0: §f1°C, §f5§7с.",
                "§8Тир 1: §f2°C, §f7.5§7с.",
                "§8Тир 2: §f3°C, §f20§7с.",
                "§8Тир 3: §f4°C, §f30§7с.",
                "§8Тир 4: §f6°C, §f40§7с.",
                "§8Тир 5: §f10°C, §f60§7с."
        };

        int lineHeight = 11; // Высота строки с небольшим отступом
        int padding = 4;
        int iconSize = 12; // Масштаб 0.75 от 16
        int iconTextGap = 2; // 2 пикселя между иконкой и текстом

        // Вычисляем размеры тултипа
        int maxTextWidth = 0;
        for (String line : lines) {
            maxTextWidth = Math.max(maxTextWidth, this.font.width(line));
        }

        int tooltipWidth = padding + iconSize + iconTextGap + maxTextWidth + padding;
        int tooltipHeight = lines.length * lineHeight + padding * 2;

        // Позиция (со смещением от курсора)
        int tooltipX = mouseX + 8;
        int tooltipY = mouseY - tooltipHeight / 2;

        // Корректировка границ экрана
        if (tooltipX + tooltipWidth > this.width) tooltipX = mouseX - tooltipWidth - 8;
        if (tooltipY < 4) tooltipY = 4;
        if (tooltipY + tooltipHeight > this.height) tooltipY = this.height - tooltipHeight - 4;

        // Фон тултипа (стандартный стиль Minecraft)
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0100010);
        guiGraphics.fill(tooltipX + 1, tooltipY, tooltipX + tooltipWidth - 1, tooltipY + 1, 0xF0500070);
        guiGraphics.fill(tooltipX + 1, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth - 1, tooltipY + tooltipHeight, 0xF0500070);
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + 1, tooltipY + tooltipHeight, 0xF0500070);
        guiGraphics.fill(tooltipX + tooltipWidth - 1, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF0500070);

        // Текущее время для анимации (1 секунда = 1 кадр)
        long currentSecond = System.currentTimeMillis() / 1000;

        // Рисуем строки
        for (int i = 0; i < lines.length; i++) {
            int lineY = tooltipY + padding + i * lineHeight;

            if (i == 0) {
                // Заголовок - без иконки, просто текст
                guiGraphics.drawString(this.font, lines[i], tooltipX + padding, lineY + 2, 0xFFFFFF, true);
            } else {
                int tier = i - 1; // Тир 0 = индекс 1

                // Рисуем анимированную иконку слева
                List<ItemStack> items = TIER_ITEMS[tier];
                if (items != null && !items.isEmpty()) {
                    // Выбираем предмет на основе времени + tier для разности фаз
                    int itemIndex = (int)((currentSecond + tier) % items.size());
                    ItemStack stack = items.get(itemIndex);

                    // Масштабируем до 12x12 (0.75 от 16)
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(tooltipX + padding, lineY, 100); // Z=100 чтобы быть поверх фона
                    guiGraphics.pose().scale(0.75f, 0.75f, 1.0f);
                    guiGraphics.renderItem(stack, 0, 0);
                    guiGraphics.renderItemDecorations(this.font, stack, 0, 0);
                    guiGraphics.pose().popPose();
                }

                // Рисуем текст справа от иконки с отступом 2px
                int textX = tooltipX + padding + iconSize + iconTextGap;
                guiGraphics.drawString(this.font, lines[i], textX, lineY + 2, 0xFFFFFF, true);
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