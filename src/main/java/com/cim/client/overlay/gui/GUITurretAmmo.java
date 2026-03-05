package com.cim.client.overlay.gui;



import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import com.cim.item.weapons.turrets.TurretChipItem;
import com.cim.menu.TurretLightMenu;
import com.cim.network.ModPacketHandler;
import com.cim.network.packet.turrets.PacketModifyTurretChip;
import com.cim.network.packet.turrets.PacketToggleTurret;
import com.cim.network.packet.turrets.PacketUpdateTurretSettings;

import java.util.ArrayList;
import java.util.List;

public class GUITurretAmmo extends AbstractContainerScreen<TurretLightMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("cim", "textures/gui/turret/turret_light_gui.png");

    // --- СОСТОЯНИЯ GUI ---
    private static final int STATE_NORMAL = 0;
    private static final int STATE_MAIN_MENU = 1;
    private static final int STATE_CHIP_LIST = 2;
    private static final int STATE_ADD_INPUT = 3;
    private static final int STATE_RESULT_MSG = 4;
    private static final int STATE_ATTACK_MODE = 5;
    private static final int STATE_STATS = 6; // 🔥

    // --- ПАЛИТRA (Pastel Terminal) ---
    private static final int COLOR_TEXT    = 0xE0E0E0; // Белый (основной)
    private static final int COLOR_GOOD    = 0x77DD77; // Зеленый (ок/вкл)
    private static final int COLOR_BAD     = 0xFF6961; // Красный (ошибка/выкл/киллы)
    private static final int COLOR_WARN    = 0xFDFD96; // Желтый (зарядка/ввод)
    private static final int COLOR_INFO    = 0xAEC6CF; // Голубой (время/списки)
    private static final int COLOR_OFF     = 0x949494; // Серый (отключено)

    private int uiState = STATE_NORMAL;
    private int selectedIndex = 0;
    private String inputString = "";
    private int cursorTimer = 0;
    private String resultMessage = "";
    private int resultColor = 0xFFFFFF;
    private int resultDuration = 0;
    private int timerPlus = 0, timerMinus = 0, timerCheck = 0, timerLeft = 0, timerRight = 0, timerMenu = 0;
    private static final int PRESS_DURATION = 10;

    public GUITurretAmmo(TurretLightMenu menu, Inventory inventory, Component component) {
        super(menu, inventory, component);
        this.imageWidth = 201;
        this.imageHeight = 188;
    }

    public void handleFeedback(boolean success) {
        this.uiState = STATE_RESULT_MSG;
        this.resultDuration = 40;
        if (success) {
            this.resultMessage = "SUCCESS";
            this.resultColor = COLOR_GOOD;
        } else {
            this.resultMessage = "ERROR 404";
            this.resultColor = COLOR_BAD;
        }
    }


    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        int energy = this.menu.getDataSlot(TurretLightMenu.DATA_ENERGY);
        int maxEnergy = this.menu.getDataSlot(TurretLightMenu.DATA_MAX_ENERGY);
        int status = this.menu.getDataSlot(TurretLightMenu.DATA_STATUS);
        boolean isSwitchedOn = this.menu.getDataSlot(TurretLightMenu.DATA_SWITCH) == 1;
        int bootTimer = this.menu.getDataSlot(TurretLightMenu.DATA_BOOT_TIMER);

        // Анимация кнопок
        if (isSwitchedOn) guiGraphics.blit(TEXTURE, x + 10, y + 62, 204, 103, 10, 32);
        if (timerPlus > 0) { timerPlus--; guiGraphics.blit(TEXTURE, x + 39, y + 62, 221, 171, 15, 15); }
        if (timerMinus > 0) { timerMinus--; guiGraphics.blit(TEXTURE, x + 56, y + 62, 204, 137, 15, 15); }
        if (timerCheck > 0) { timerCheck--; guiGraphics.blit(TEXTURE, x + 22, y + 62, 204, 171, 15, 15); }
        if (timerLeft > 0) { timerLeft--; guiGraphics.blit(TEXTURE, x + 73, y + 62, 221, 137, 15, 15); }
        if (timerRight > 0) { timerRight--; guiGraphics.blit(TEXTURE, x + 90, y + 62, 221, 120, 15, 15); }
        if (timerMenu > 0) { timerMenu--; guiGraphics.blit(TEXTURE, x + 73, y + 79, 221, 154, 15, 15); }

        // Светодиод (LED)
        if (hasChip()) { guiGraphics.blit(TEXTURE, x + 12, y + 52, 221, 113, 6, 6); }

        // Энергия
        if (maxEnergy > 0 && energy > 0) {
            int barHeight = 52;
            int filledHeight = (int) ((long) energy * barHeight / maxEnergy);
            guiGraphics.blit(TEXTURE, x + 180, y + 27 + (barHeight - filledHeight), 204, 27 + (barHeight - filledHeight), 16, filledHeight);
        }

        // --- ЭКРАН ---
        if (energy > 10000 && isSwitchedOn) {
            guiGraphics.blit(TEXTURE, x + 10, y + 32, 0, 196, 95, 16);

            if (bootTimer > 0) {
                drawBootingText(guiGraphics, x + 10, y + 32, 95, 16);
                if (uiState != STATE_NORMAL) uiState = STATE_NORMAL;
            } else {

                switch (uiState) {
                    case STATE_MAIN_MENU:
                        drawMainMenu(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_ATTACK_MODE:
                        drawAttackMode(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    // 🔥 ДОБАВЛЕНО: Отрисовка статистики
                    case STATE_STATS:
                        drawStats(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_CHIP_LIST:
                        if (!hasChip()) { uiState = STATE_MAIN_MENU; break; }
                        drawChipUserList(guiGraphics, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_ADD_INPUT:
                        cursorTimer++;
                        String display = inputString + ((cursorTimer / 10 % 2 == 0) ? "_" : "");
                        if (display.length() > 14) display = display.substring(display.length() - 14);
                        drawCenteredText(guiGraphics, display, 0xFFFF00, x + 10, y + 32, 95, 16);
                        break;

                    case STATE_RESULT_MSG:
                        if (resultDuration > 0) {
                            resultDuration--;
                            drawCenteredText(guiGraphics, resultMessage, resultColor, x + 10, y + 32, 95, 16);
                        } else {
                            if (resultMessage.equals("SUCCESS")) uiState = STATE_CHIP_LIST;
                            else uiState = STATE_ADD_INPUT;
                        }
                        break;

                    default: // STATE_NORMAL
                        drawStatusText(guiGraphics, x + 10, y + 32, 95, 16, status, energy, maxEnergy);
                        break;
                }
            }
        } else {
            uiState = STATE_NORMAL;
        }
    }

    // --- ОТРИСОВКА МЕНЮ ---

    private void drawMainMenu(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        if (selectedIndex < 0) selectedIndex = 2;
        if (selectedIndex > 2) selectedIndex = 0;

        String text = "";
        int color = COLOR_TEXT;

        if (selectedIndex == 0) {
            text = "CHIP CONTROL";
            if (!hasChip()) color = COLOR_OFF; // Серый, если нет чипа
        } else if (selectedIndex == 1) {
            text = "ATTACK MODE";
        } else {
            text = "TURRET STATS";
        }

        // Единый стиль стрелочек
        text = "< " + text + " >";
        drawCenteredText(guiGraphics, text, color, x, y, w, h);
    }


    private void drawAttackMode(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        if (selectedIndex < 0) selectedIndex = 2;
        if (selectedIndex > 2) selectedIndex = 0;

        String name = "";
        int valHostile = this.menu.getDataSlot(TurretLightMenu.DATA_TARGET_HOSTILE);
        int valNeutral = this.menu.getDataSlot(TurretLightMenu.DATA_TARGET_NEUTRAL);
        int valPlayer = this.menu.getDataSlot(TurretLightMenu.DATA_TARGET_PLAYERS);
        boolean isEnabled = false;

        switch (selectedIndex) {
            case 0: name = "HOSTILES"; isEnabled = valHostile == 1; break;
            case 1: name = "NEUTRALS"; isEnabled = valNeutral == 1; break;
            case 2: name = "PLAYERS"; isEnabled = valPlayer == 1; break;
        }

        String symbol = isEnabled ? "[V]" : "[X]";
        int color = isEnabled ? COLOR_GOOD : COLOR_BAD;

        // Стрелочки теперь тоже рисуются тут
        String text = "< " + name + " " + symbol + " >";
        drawCenteredText(guiGraphics, text, color, x, y, w, h);
    }


    // 🔥 ДОБАВЛЕНО: Метод отрисовки статистики
    private void drawStats(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        if (selectedIndex < 0) selectedIndex = 2;
        if (selectedIndex > 2) selectedIndex = 0;

        String text = "";
        int color = COLOR_TEXT;

        switch (selectedIndex) {
            case 0:
                int kills = this.menu.getDataSlot(TurretLightMenu.DATA_KILLS);
                text = "KILLS: " + kills;
                color = COLOR_BAD; // Красный для агрессии
                break;
            case 1:
                int secondsTotal = this.menu.getDataSlot(TurretLightMenu.DATA_LIFETIME);
                int hours = secondsTotal / 3600;
                int minutes = (secondsTotal % 3600) / 60;
                text = String.format("TIME: %dh %dm", hours, minutes);
                color = COLOR_INFO; // Голубой для времени
                break;
            case 2:
                text = "OWNER: [DATA]";
                color = COLOR_WARN; // Желтый для важного
                break;
        }

        // Исправленные стрелочки (как в главном меню)
        text = "< " + text + " >";
        drawCenteredText(guiGraphics, text, color, x, y, w, h);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        double relX = mouseX - x;
        double relY = mouseY - y;

        if (button == 0) {
            boolean hitPower = (relX >= 10 && relX < 20 && relY >= 62 && relY < 94);
            boolean hitMenu  = (relX >= 73 && relX < 88 && relY >= 79 && relY < 94);
            boolean hitCheck = (relX >= 22 && relX < 37 && relY >= 62 && relY < 77);
            boolean hitPlus  = (relX >= 39 && relX < 54 && relY >= 62 && relY < 77);
            boolean hitMinus = (relX >= 56 && relX < 71 && relY >= 62 && relY < 77);
            boolean hitLeft  = (relX >= 73 && relX < 88 && relY >= 62 && relY < 77);
            boolean hitRight = (relX >= 90 && relX < 105 && relY >= 62 && relY < 77);

            if (hitPower || hitMenu || hitCheck || hitPlus || hitMinus || hitLeft || hitRight) {
                playClickSound();
            }

            if (hitMenu)  timerMenu  = PRESS_DURATION;
            if (hitCheck) timerCheck = PRESS_DURATION;
            if (hitPlus)  timerPlus  = PRESS_DURATION;
            if (hitMinus) timerMinus = PRESS_DURATION;
            if (hitLeft)  timerLeft  = PRESS_DURATION;
            if (hitRight) timerRight = PRESS_DURATION;


            if (hitPower) {
                ModPacketHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.SERVER.noArg(),
                        new PacketToggleTurret(this.menu.getPos()));
                return true;
            }

            if (hitMenu) {
                if (uiState == STATE_NORMAL) {
                    uiState = STATE_MAIN_MENU;
                    selectedIndex = hasChip() ? 0 : 1;
                } else {
                    uiState = STATE_NORMAL;
                }
                return true;
            }

            if (uiState != STATE_NORMAL && uiState != STATE_RESULT_MSG) {

                if (hitCheck) {
                    if (uiState == STATE_MAIN_MENU) {
                        if (selectedIndex == 0) {
                            if (hasChip()) { uiState = STATE_CHIP_LIST; selectedIndex = 0; }
                        } else if (selectedIndex == 1) {
                            uiState = STATE_ATTACK_MODE; selectedIndex = 0;
                        } else if (selectedIndex == 2) {
                            // 🔥 ДОБАВЛЕНО: Вход в статистику
                            uiState = STATE_STATS; selectedIndex = 0;
                        }
                    } else if (uiState == STATE_ADD_INPUT) {
                        if (!inputString.isEmpty()) {
                            ModPacketHandler.INSTANCE.send(
                                    net.minecraftforge.network.PacketDistributor.SERVER.noArg(),
                                    new PacketModifyTurretChip(1, inputString));
                        }
                    }
                    return true;
                }

                if (hitLeft || hitRight) {
                    if (uiState == STATE_MAIN_MENU) {
                        // 🔥 ИСПРАВЛЕНО: Теперь 3 пункта меню (0, 1, 2)
                        if (hitLeft) selectedIndex--; else selectedIndex++;
                    }
                    // 🔥 ДОБАВЛЕНО: Листание страниц статистики
                    else if (uiState == STATE_CHIP_LIST || uiState == STATE_ATTACK_MODE || uiState == STATE_STATS) {
                        if (hitLeft) selectedIndex--; else selectedIndex++;
                    }
                    return true;
                }

                if (hitPlus || hitMinus) {
                    if (uiState == STATE_CHIP_LIST) {
                        if (hitPlus) { uiState = STATE_ADD_INPUT; inputString = ""; }
                        else {
                            ModPacketHandler.INSTANCE.send(
                                    net.minecraftforge.network.PacketDistributor.SERVER.noArg(),
                                    new PacketModifyTurretChip(0, String.valueOf(selectedIndex)));
                            if (selectedIndex > 0) selectedIndex--;
                        }
                    } else if (uiState == STATE_ATTACK_MODE) {
                        boolean newValue = hitPlus;
                        ModPacketHandler.INSTANCE.send(
                                net.minecraftforge.network.PacketDistributor.SERVER.noArg(),
                                new PacketUpdateTurretSettings(this.menu.getPos(), selectedIndex, newValue));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }


    // --- ВВОД С КЛАВИАТУРЫ ---
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (uiState == STATE_ADD_INPUT) {
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_') {
                if (inputString.length() < 16) {
                    inputString += codePoint;
                    return true;
                }
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (uiState == STATE_ADD_INPUT) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!inputString.isEmpty()) {
                    inputString = inputString.substring(0, inputString.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!inputString.isEmpty()) {
                    playClickSound();
                    timerCheck = PRESS_DURATION;
                    ModPacketHandler.INSTANCE.send(
                            net.minecraftforge.network.PacketDistributor.SERVER.noArg(),
                            new PacketModifyTurretChip(1, inputString)
                    );
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                uiState = STATE_CHIP_LIST;
                return true;
            }
            if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Хелперы
    private boolean hasChip() {
        ItemStack stack = this.menu.getAmmoContainer().getStackInSlot(9);
        return !stack.isEmpty() && stack.getItem() instanceof TurretChipItem;
    }

    private void drawChipUserList(GuiGraphics guiGraphics, int screenX, int screenY, int w, int h) {
        ItemStack stack = this.menu.getAmmoContainer().getStackInSlot(9);
        List<String> names = new ArrayList<>();
        if (stack.hasTag() && stack.getTag().contains("TurretOwners")) {
            ListTag list = stack.getTag().getList("TurretOwners", Tag.TAG_STRING);
            for (Tag t : list) {
                String s = t.getAsString();
                names.add(s.contains("|") ? s.split("\\|")[1] : s);
            }
        }

        if (selectedIndex < 0) selectedIndex = names.size() - 1;
        if (selectedIndex >= names.size()) selectedIndex = 0;

        String textToShow;
        if (names.isEmpty()) textToShow = "EMPTY LIST";
        else textToShow = (selectedIndex + 1) + "/" + names.size() + " " + names.get(selectedIndex);

        // Стрелочки и тут добавим для красоты
        if (!names.isEmpty()) textToShow = "< " + textToShow + " >";

        drawCenteredText(guiGraphics, textToShow, COLOR_INFO, screenX, screenY, w, h);
    }


    private void drawCenteredText(GuiGraphics guiGraphics, String textStr, int color, int screenX, int screenY, int w, int h) {
        Component text = Component.literal(textStr);
        float scale = 0.7f;
        guiGraphics.pose().pushPose();
        float textX = (screenX + 5) / scale;
        float textY = (screenY + (h - 8 * scale) / 2) / scale;
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(this.font, text, (int)textX, (int)textY, color, false);
        guiGraphics.pose().popPose();
    }

    private void drawBootingText(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        long time = System.currentTimeMillis() / 500;
        String dots = ".".repeat((int) (time % 4));
        drawCenteredText(guiGraphics, "SYSTEM BOOT" + dots, COLOR_TEXT, x, y, w, h);
    }

    private void drawStatusText(GuiGraphics guiGraphics, int x, int y, int w, int h, int status, int energy, int maxEnergy) {
        String msg;
        int color;
        if (status == 1) { msg = "SYSTEM ONLINE"; color = COLOR_GOOD; }
        else if (status >= 200 && status <= 300) { msg = "REPAIRING: " + (status - 200) + "%"; color = COLOR_WARN; }
        else if (status >= 1000) { msg = "RESPAWN: " + ((status - 1000) / 20) + "s"; color = COLOR_BAD; }
        else {
            if (energy < maxEnergy) { msg = "CHARGING..."; color = COLOR_WARN; }
            else { msg = "STANDBY MODE"; color = COLOR_OFF; }
        }
        drawCenteredText(guiGraphics, msg, color, x, y, w, h);
    }

    private void playClickSound() {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, 13, 11, 4210752, false);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);
        renderTooltip(guiGraphics, mouseX, mouseY);
        if (isHovering(180, 27, 16, 52, mouseX, mouseY)) {
            int energy = this.menu.getDataSlot(0);
            int maxEnergy = this.menu.getDataSlot(1);
            guiGraphics.renderTooltip(this.font, Component.literal(String.format("%d / %d HE", energy, maxEnergy)), mouseX, mouseY);
        }
    }
}
