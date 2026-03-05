package com.cim.client.overlay.gui;


import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import com.cim.main.CrustalIncursionMod;
import com.cim.menu.MotorElectroMenu;
import com.cim.network.ModPacketHandler;
import com.cim.network.packet.rotation.PacketToggleMotor;
import com.cim.network.packet.rotation.PacketToggleMotorMode;

public class GUIMotorElectro extends AbstractContainerScreen<MotorElectroMenu> {
    private static final ResourceLocation TEXTURE =  new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/machine/motor_electro_gui.png");

    public GUIMotorElectro(MotorElectroMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 180;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // 1. Базовый фон
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // 2. Энергобар (Вертикальный) - твой код рабочий
        int energy = menu.getEnergy();
        int maxEnergy = menu.getMaxEnergy();
        if (maxEnergy > 0) {
            int barHeight = 52;
            int filledHeight = (int) ((long) energy * barHeight / maxEnergy);
            guiGraphics.blit(TEXTURE,
                    x + 123, y + 5 + (barHeight - filledHeight),
                    187, 30 + (barHeight - filledHeight),
                    16, filledHeight);
        }

        // 3. Кнопка питания (вкл/выкл)
        // Место: x47, y35
        // Текстура выкл (по умолчанию на фоне): предполагаем x187/y101 для вкл, значит выкл это стандартный фон
        if (menu.isSwitchedOn()) {
            // Текстура ON: x187, y101 (из твоего запроса) | Размер: 10x32
            guiGraphics.blit(TEXTURE, x + 47, y + 35, 187, 101, 10, 32);
        }
        // Если выключена, используется текстура по умолчанию на базовом фоне (0,0)

        // 4. Кнопка режима (генератор/мотор)
        // Место: x47, y69
        // Текстура режима генератора (нажата): x187, y83 | Размер: 10x17
        // Если режим генератора активен, рендерим "нажатую" текстуру поверх стандартной
        if (menu.isGeneratorMode()) {
            guiGraphics.blit(TEXTURE, x + 47, y + 69, 187, 83, 10, 17);
        }

        // 5. Прогресс-бар вращения (Горизонтальный, СЛЕВА НАПРАВО)
// Место: x59, y35 | Текстура: x204, y49 | Размер: 52x16
        int rotVal = menu.getRotationValue();
        if (rotVal > 0) {
            int barWidth = 52;
            // Используем float для расчета, чтобы избежать потерь при делении
            // 100000.0f — это твой максимум. Если в режиме мотора там всего 5000,
            // полоска будет 2.6 пикселя (почти незаметна).
            float ratio = (float) rotVal / 2500.0f;
            int filledWidth = (int) (ratio * barWidth);

            // Ограничиваем, чтобы не вылезло за рамки
            if (filledWidth > barWidth) filledWidth = barWidth;
            if (filledWidth < 1) filledWidth = 1; // Рисуем хотя бы 1 пиксель, если rotVal > 0

            // Используем blit с указанием размера файла текстуры (256, 256)
            guiGraphics.blit(TEXTURE,
                    x + 59, y + 35,      // Позиция на экране
                    204, 49,             // U, V на текстуре
                    filledWidth, 16,     // Ширина, Высота куска
                    256, 256             // Ширина, Высота всего файла текстуры
            );
        }
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);

        // Тултип для энергии
        if (isHovering(123, 5, 16, 52, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.literal(menu.getEnergy() + " / " + menu.getMaxEnergy() + " HE"), mouseX, mouseY);
        }

        // Тултип для вращения
        if (isHovering(59, 35, 52, 16, mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.literal("Rotation Power: " + menu.getRotationValue()), mouseX, mouseY);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 8, 6, 4210752, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            double relX = mouseX - x;
            double relY = mouseY - y;

            // Клик по кнопке ПИТАНИЯ (x47, y35)
            if (relX >= 47 && relX < 57 && relY >= 35 && relY < 67) {
                playClickSound();
                PacketToggleMotor packet = new PacketToggleMotor(menu.getPos());
                ModPacketHandler.INSTANCE.sendToServer(packet);
                return true;
            }

            // Клик по кнопке РЕЖИМА (x47, y69)
            if (relX >= 47 && relX < 57 && relY >= 69 && relY < 86) {
                playClickSound();
                ModPacketHandler.INSTANCE.sendToServer(new PacketToggleMotorMode(menu.getPos()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}