package com.cim.client.overlay.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import com.cim.main.CrustalIncursionMod;
import com.cim.menu.MachineBatteryMenu;
import com.cim.network.ModPacketHandler;
import com.cim.network.packet.energy.UpdateBatteryC2SPacket;
import com.cim.util.EnergyFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Полностью переделанный GUI для энергохранилища.
 *
 * Элементы:
 * 1. Панели IN/OUT скоростей (отображаются если есть хотя бы 1 ячейка)
 * 2. 8 слотов зарядки (4 input + 4 output)
 * 3. 8 слотов разрядки (4 input + 4 output)
 * 4. Шкала буфера энергии
 * 5. Светодиоды заполненных ячеек
 * 6. Кнопка режима (1 кнопка, без редстоуна)
 * 7. Кнопка приоритета
 */
public class GUIMachineBattery extends AbstractContainerScreen<MachineBatteryMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/storage/gui_battery.png");

    // Цвет надписей скоростей
    private static final int SPEED_TEXT_COLOR = 0xAEC6CF;

    public GUIMachineBattery(MachineBatteryMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);

        // Увеличиваем высоту GUI на 28 пикселей (инвентарь сдвинут вниз)
        this.imageWidth = 176;
        this.imageHeight = 194; // было 166, +28
    }

    @Override
    protected void init() {
        super.init();
        // Убираем рендеринг заголовка и плашки "Инвентарь"
        this.titleLabelX = -9999;
        this.inventoryLabelX = -9999; // Убираем плашку "Инвентарь"
    }

    // --- ОСНОВНОЙ РЕНДЕР ---

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(pGuiGraphics);
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = this.leftPos;
        int y = this.topPos;

        // Основная текстура GUI
        pGuiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // Рендерим элементы
        renderSpeedPanels(pGuiGraphics, x, y);
        renderEnergyBar(pGuiGraphics, x, y);
        renderCellLEDs(pGuiGraphics, x, y);
        renderModeButton(pGuiGraphics, x, y);
        renderPriorityButton(pGuiGraphics, x, y);
    }


    // --- 1. ПАНЕЛИ СКОРОСТЕЙ IN / OUT ---

    private void renderSpeedPanels(GuiGraphics graphics, int x, int y) {
        int filledCells = menu.getFilledCellCount();
        if (filledCells <= 0) return; // Не рендерим если нет ячеек

        // Левая панель (IN) - координаты рендера: x8, y7, размер 76x16
        // Текстура панели на атласе: x177, y117
        graphics.blit(TEXTURE, x + 8, y + 7, 177, 117, 76, 16);

        // Правая панель (OUT) - координаты рендера: x92, y7, размер 76x16
        graphics.blit(TEXTURE, x + 92, y + 7, 177, 117, 76, 16);

        // Текст скоростей поверх панелей
        long chargingSpeed = menu.getChargingSpeed();
        long unchargingSpeed = menu.getUnchargingSpeed();

        // IN: скорость зарядки в JE/S (тик * 20)
        String inText = "IN: " + EnergyFormatter.format(chargingSpeed * 20) + " JE/S";
        // OUT: скорость разрядки в JE/S
        String outText = "OUT: " + EnergyFormatter.format(unchargingSpeed * 20) + " JE/S";

        // Рендер текста по центру панелей
        int inTextWidth = this.font.width(inText);
        int outTextWidth = this.font.width(outText);

        // Панель IN: x8..x84 (ширина 76)
        graphics.drawString(this.font, inText, x + 8 + (76 - inTextWidth) / 2, y + 7 + 4, SPEED_TEXT_COLOR, false);
        // Панель OUT: x92..x168 (ширина 76)
        graphics.drawString(this.font, outText, x + 93 + (76 - outTextWidth) / 2, y + 7 + 4, SPEED_TEXT_COLOR, false);
    }

    // --- 4. ШКАЛА БУФЕРА ЭНЕРГИИ ---

    private void renderEnergyBar(GuiGraphics graphics, int x, int y) {
        long energy = menu.getEnergy();
        long maxEnergy = menu.getMaxEnergy();

        if (energy > 0 && maxEnergy > 0) {
            int totalHeight = 52;
            int barHeight = (int) (totalHeight * ((double) energy / maxEnergy));
            if (barHeight > totalHeight) barHeight = totalHeight;

            // Рендер: x62, y37. Текстура шкалы как раньше: u=176, v=(52-barHeight)
            graphics.blit(TEXTURE, x + 62, y + 37 + (totalHeight - barHeight), 176, totalHeight - barHeight, 52, barHeight);
        }
    }

    // --- 5. СВЕТОДИОДЫ ЯЧЕЕК ---

    private void renderCellLEDs(GuiGraphics graphics, int x, int y) {
        // Координаты рендера 4-х светодиодов (размер 6x6)
        int[][] ledPositions = {
                {67, 29},  // Слот 0
                {75, 29},  // Слот 1
                {97, 29},  // Слот 2
                {105, 29}  // Слот 3
        };

        // Текстура зелёного светодиода: x177, y141, размер 6x6
        for (int i = 0; i < 4; i++) {
            if (!menu.blockEntity.isCellEmpty(i)) {
                graphics.blit(TEXTURE, x + ledPositions[i][0], y + ledPositions[i][1], 177, 141, 6, 6);
            }
            // Красные уже нарисованы на фоновой текстуре
        }
    }

    // --- 6. КНОПКА РЕЖИМА ---

    private void renderModeButton(GuiGraphics graphics, int x, int y) {
        int mode = menu.getMode();
        int vForMode = getModeTextureV(mode);
        // Рендер: x64, y94, размер 15x15
        graphics.blit(TEXTURE, x + 64, y + 94, 177, vForMode, 15, 15);
    }

    private int getModeTextureV(int mode) {
        return switch (mode) {
            case 0 -> 69;  // BOTH: x177, y69
            case 1 -> 53;  // INPUT: x177, y53
            case 2 -> 85;  // OUTPUT: x177, y85
            case 3 -> 101; // DISABLED: x177, y101
            default -> 69;
        };
    }

    // --- 7. КНОПКА ПРИОРИТЕТА ---

    private void renderPriorityButton(GuiGraphics graphics, int x, int y) {
        int priorityOrdinal = menu.getPriorityOrdinal();
        int vForPriority = getPriorityTextureV(priorityOrdinal);
        // Рендер: x97, y94, размер 15x15
        graphics.blit(TEXTURE, x + 97, y + 94, 193, vForPriority, 15, 15);
    }

    private int getPriorityTextureV(int ordinal) {
        return switch (ordinal) {
            case 0 -> 53;  // LOW: x193, y53
            case 1 -> 69;  // NORMAL: x193, y69
            case 2 -> 85;  // HIGH: x193, y85
            default -> 53;
        };
    }

    // --- ТУЛТИПЫ ---

    @Override
    protected void renderTooltip(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        super.renderTooltip(pGuiGraphics, pMouseX, pMouseY);

        // Тултип для шкалы энергии (x62, y37, 52x52)
        if (isMouseOver(pMouseX, pMouseY, 62, 37, 52, 52)) {
            List<Component> tooltip = new ArrayList<>();

            long energy = menu.getEnergy();
            long maxEnergy = menu.getMaxEnergy();
            long delta = menu.getEnergyDelta();

            String energyStr = EnergyFormatter.format(energy);
            String maxEnergyStr = EnergyFormatter.format(maxEnergy);
            tooltip.add(Component.literal(energyStr + " / " + maxEnergyStr + " HE"));

            String deltaText = (delta >= 0 ? "+" : "") + EnergyFormatter.formatRate(delta);
            ChatFormatting deltaColor = delta > 0 ? ChatFormatting.GREEN : (delta < 0 ? ChatFormatting.RED : ChatFormatting.YELLOW);
            tooltip.add(Component.literal(deltaText).withStyle(deltaColor));

            long deltaPerSecond = delta * 20;
            String deltaPerSecondText = (deltaPerSecond >= 0 ? "+" : "") + EnergyFormatter.formatWithUnit(deltaPerSecond, "HE/s");
            tooltip.add(Component.literal(deltaPerSecondText).withStyle(deltaColor));

            pGuiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), pMouseX, pMouseY);
        }

        // Тултип для кнопки режима (x64, y94, 15x15)
        if (isMouseOver(pMouseX, pMouseY, 64, 94, 15, 15)) {
            List<Component> tooltip = new ArrayList<>();

            int mode = menu.getMode();
            String modeKey = switch (mode) {
                case 0 -> "both";
                case 1 -> "input";
                case 2 -> "output";
                case 3 -> "locked";
                default -> "both";
            };

            String titleKey = "gui.smogline.battery.mode." + modeKey;
            String descKey = "gui.smogline.battery.mode." + modeKey + ".desc";

            tooltip.add(Component.translatable(titleKey).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));

            pGuiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), pMouseX, pMouseY);
        }

        // Тултип для кнопки приоритета (x97, y94, 15x15)
        if (isMouseOver(pMouseX, pMouseY, 97, 94, 15, 15)) {
            List<Component> tooltip = new ArrayList<>();

            int priorityOrdinal = menu.getPriorityOrdinal();
            String priorityKey = "gui.smogline.battery.priority." + priorityOrdinal;

            tooltip.add(Component.translatable(priorityKey));
            tooltip.add(Component.translatable("gui.smogline.battery.priority.recommended").withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(priorityKey + ".desc").withStyle(ChatFormatting.GRAY));

            pGuiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), pMouseX, pMouseY);
        }

        // Тултип для панели IN (x8, y7, 76x16)
        if (isMouseOver(pMouseX, pMouseY, 8, 7, 76, 16)) {
            int filledCells = menu.getFilledCellCount();
            if (filledCells > 0) {
                List<Component> tooltip = new ArrayList<>();
                long speed = menu.getChargingSpeed();
                tooltip.add(Component.literal("§aСкорость зарядки: " + EnergyFormatter.format(speed) + " HE/t"));
                tooltip.add(Component.literal("§7(" + EnergyFormatter.format(speed * 20) + " HE/s)").withStyle(ChatFormatting.GRAY));
                pGuiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), pMouseX, pMouseY);
            }
        }

        // Тултип для панели OUT (x92, y7, 76x16)
        if (isMouseOver(pMouseX, pMouseY, 92, 7, 76, 16)) {
            int filledCells = menu.getFilledCellCount();
            if (filledCells > 0) {
                List<Component> tooltip = new ArrayList<>();
                long speed = menu.getUnchargingSpeed();
                tooltip.add(Component.literal("§cСкорость разрядки: " + EnergyFormatter.format(speed) + " HE/t"));
                tooltip.add(Component.literal("§7(" + EnergyFormatter.format(speed * 20) + " HE/s)").withStyle(ChatFormatting.GRAY));
                pGuiGraphics.renderTooltip(this.font, tooltip, Optional.empty(), pMouseX, pMouseY);
            }
        }
    }

    // --- ОБРАБОТКА КЛИКОВ ---

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0) {
            // Кнопка режима (x64, y94, 15x15) -> buttonId = 0
            if (isMouseOver(pMouseX, pMouseY, 64, 94, 15, 15)) {
                playSound();
                ModPacketHandler.INSTANCE.sendToServer(new UpdateBatteryC2SPacket(this.menu.blockEntity.getBlockPos(), 0));
                return true;
            }
            // Кнопка приоритета (x97, y94, 15x15) -> buttonId = 1
            if (isMouseOver(pMouseX, pMouseY, 97, 94, 15, 15)) {
                playSound();
                ModPacketHandler.INSTANCE.sendToServer(new UpdateBatteryC2SPacket(this.menu.blockEntity.getBlockPos(), 1));
                return true;
            }
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    // --- ХЕЛПЕРЫ ---

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int sizeX, int sizeY) {
        return (mouseX >= this.leftPos + x && mouseX <= this.leftPos + x + sizeX &&
                mouseY >= this.topPos + y && mouseY <= this.topPos + y + sizeY);
    }

    private void playSound() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }
}