package com.cim.client.overlay.gui;

import com.cim.item.tools.FluidIdentifierItem;
import com.cim.main.CrustalIncursionMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class GUIFluidIdentifier extends Screen {

    private static final ResourceLocation TEXTURE = new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/item/fluid_identifier_gui.png");
    private final ItemStack identifierStack;

    // Размеры
    private final int imageWidth = 153;
    private final int imageHeight = 229;
    private int leftPos, topPos;

    // Списки данных
    private List<String> recentFluids;
    private List<String> favorites;
    private List<String> displayList = new ArrayList<>();

    // Скролл
    private float scrollAmount = 0f;
    private boolean isScrolling = false;
    private boolean isClearButtonPressed = false;

    // Поиск
    private EditBox searchBox;

    public GUIFluidIdentifier(ItemStack stack) {
        super(Component.literal("Fluid Identifier"));
        this.identifierStack = stack;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        this.recentFluids = FluidIdentifierItem.getRecentFluids(identifierStack);
        this.favorites = FluidIdentifierItem.getFavorites(identifierStack);

        // Настройка строки поиска (координаты х39 у9, размер 64х15)
        this.searchBox = new EditBox(this.font, this.leftPos + 40, this.topPos + 12, 64, 15, Component.literal("Search"));
        this.searchBox.setBordered(false); // Убираем стандартную рамку MC
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setResponder(text -> updateFluidList());
        this.addRenderableWidget(this.searchBox);

        updateFluidList();
    }

    private void updateFluidList() {
        displayList.clear();
        displayList.add("none");

        String search = searchBox.getValue().toLowerCase();

        // 1. Избранное
        for (String fav : favorites) {
            String displayName = fav.replace("minecraft:", "").replace("cim:", "");
            if (displayName.toLowerCase().contains(search)) displayList.add(fav);
        }

        // 2. Остальные жидкости
        for (net.minecraft.world.level.material.Fluid fluid : ForgeRegistries.FLUIDS.getValues()) {
            // Пропускаем пустоту
            if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) continue;

            // Оставляем ТОЛЬКО источники, отсекая текучие (flowing) версии
            if (!fluid.defaultFluidState().isSource()) continue;

            ResourceLocation fluidLoc = ForgeRegistries.FLUIDS.getKey(fluid);
            if (fluidLoc == null) continue;

            String id = fluidLoc.toString();
            String displayName = id.replace("minecraft:", "").replace("cim:", "");

            if (!favorites.contains(id) && displayName.toLowerCase().contains(search)) {
                displayList.add(id);
            }
        }
        scrollAmount = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int x = this.leftPos;
        int y = this.topPos;

        // 1. Основной фон
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // 2. Кнопка очистки истории (x105, y33)
        if (isClearButtonPressed) {
            graphics.blit(TEXTURE, x + 105, y + 33, 154, 80, 12, 33); // Темная версия
        }

        // 3. Недавние жидкости (2 ряда по 5)
        renderRecentFluids(graphics, x, y);

        // 4. Основной список с маской (ножницами)
        renderScrollableList(graphics, x, y, mouseX, mouseY);

        // 5. Ползунок
        renderScrollBar(graphics, x, y);

        super.render(graphics, mouseX, mouseY, partialTick); // Отрисует EditBox
    }

    private void renderRecentFluids(GuiGraphics graphics, int x, int y) {
        for (int i = 0; i < recentFluids.size(); i++) {
            if (i >= 10) break; // Максимум 10
            int row = i / 5;
            int col = i % 5;
            int drawX = x + 22 + (col * 16);
            int drawY = y + 33 + (row * 17); // 17 чтобы был отступ в 1 пиксель между рядами (у33 и у50)

            ItemStack dummy = new ItemStack(identifierStack.getItem());
            dummy.getOrCreateTag().putString("SelectedFluid", recentFluids.get(i));
            graphics.renderItem(dummy, drawX, drawY);
        }
    }

    private void renderScrollableList(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int listX = x + 22;
        int listY = y + 75;
        int listWidth = 99;
        int listHeight = 141;

        // Включаем обрезку рендера
        graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);

        int maxScroll = Math.max(0, (displayList.size() * 19) - listHeight);
        int currentOffset = (int) (scrollAmount * maxScroll);

        for (int i = 0; i < displayList.size(); i++) {
            String fluidId = displayList.get(i);
            int entryY = listY + (i * 19) - currentOffset;

            // Рендерим только видимые плашки
            if (entryY + 19 >= listY && entryY <= listY + listHeight) {
                boolean isFav = favorites.contains(fluidId);

                // Плашка (х154 у20 если избранное, иначе х154 у0)
                int vOffset = isFav ? 20 : 0;
                graphics.blit(TEXTURE, listX, entryY, 154, vOffset, 99, 19);

                // Иконка: Сдвигаем на 1 вправо и 1 вниз (было +2, +2, стало +3, +3)
                ItemStack dummy = new ItemStack(identifierStack.getItem());
                dummy.getOrCreateTag().putString("SelectedFluid", fluidId);

                graphics.pose().pushPose();
                graphics.pose().translate(listX + 3, entryY + 3, 0);
                graphics.pose().scale(13f/16f, 13f/16f, 1f);
                graphics.renderItem(dummy, 0, 0);
                graphics.pose().popPose();

                // Название жидкости
                String displayName = fluidId.equals("none") ? "Ничего" : fluidId.replace("minecraft:", "").replace("cim:", "");

                // Цвет жидкости (с хардкодом лавы)
                int color = 0xFFDDDDDD; // Светло-серый по умолчанию
                if (fluidId.equals("minecraft:lava")) {
                    color = 0xFFFF5500; // Оранжевый
                } else if (!fluidId.equals("none")) {
                    var fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(fluidId));
                    if (fluid != null) {
                        int tint = IClientFluidTypeExtensions.of(fluid).getTintColor();
                        if (tint != -1 && tint != 0xFFFFFFFF) color = tint;
                    }
                }

                graphics.drawString(this.font, displayName, listX + 20, entryY + 5, color, false);
            }
        }

        graphics.disableScissor();
    }

    private void renderScrollBar(GuiGraphics graphics, int x, int y) {
        int trackHeight = 141 - 15; // Высота пути ползунка минус сам ползунок
        int thumbY = y + 75 + (int)(scrollAmount * trackHeight);
        graphics.blit(TEXTURE, x + 123, thumbY, 215, 80, 8, 15);
    }

    // --- ЛОГИКА МЫШИ (КЛИКИ И СКРОЛЛ) ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, (displayList.size() * 19) - 141);
        if (maxScroll > 0) {
            scrollAmount -= (float) (delta * 19 / maxScroll); // Крутим по 1 элементу (19px)
            scrollAmount = Math.max(0f, Math.min(1f, scrollAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int x = this.leftPos;
        int y = this.topPos;

        // 1. Клик по кнопке очистки истории (x105, y33, размер 12x33)
        if (mouseX >= x + 105 && mouseX <= x + 105 + 12 && mouseY >= y + 33 && mouseY <= y + 33 + 33) {
            com.cim.network.ModPacketHandler.INSTANCE.sendToServer(new com.cim.network.packet.fluids.ClearFluidHistoryPacket());
            // Обновляем визуально сразу
            this.recentFluids.clear();
            this.isClearButtonPressed = true; // Для отрисовки нажатой кнопки
            // Можно запустить таймер или просто сбросить в false в tick(), но для простоты оставим так (сбросится при переоткрытии)
            return true;
        }

        // 2. Клик по недавним жидкостям (х22, у33 и у50)
        for (int i = 0; i < recentFluids.size(); i++) {
            if (i >= 10) break;
            int row = i / 5;
            int col = i % 5;
            int drawX = x + 22 + (col * 16);
            int drawY = y + 33 + (row * 17);

            if (mouseX >= drawX && mouseX <= drawX + 16 && mouseY >= drawY && mouseY <= drawY + 16) {
                String clickedFluid = recentFluids.get(i);
                com.cim.network.ModPacketHandler.INSTANCE.sendToServer(new com.cim.network.packet.fluids.SelectFluidPacket(clickedFluid));
                this.minecraft.player.closeContainer(); // Закрываем GUI
                return true;
            }
        }

        // 3. Клик по основному списку
        int listX = x + 22;
        int listY = y + 75;
        int listWidth = 99;
        int listHeight = 141;

        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            int maxScroll = Math.max(0, (displayList.size() * 19) - listHeight);
            int currentOffset = (int) (scrollAmount * maxScroll);

            // Вычисляем, на какую по счету плашку нажали
            int clickedIndex = (int) ((mouseY - listY + currentOffset) / 19);

            if (clickedIndex >= 0 && clickedIndex < displayList.size()) {
                String clickedFluid = displayList.get(clickedIndex);
                int entryY = listY + (clickedIndex * 19) - currentOffset;

                // Проверяем, кликнули ли по звездочке (x88, y4 на плашке, размер примерно 10x10)
                if (mouseX >= listX + 88 && mouseX <= listX + 97 && mouseY >= entryY + 4 && mouseY <= entryY + 14) {
                    // Игнорируем звездочку для пункта "Ничего"
                    if (!clickedFluid.equals("none")) {
                        com.cim.network.ModPacketHandler.INSTANCE.sendToServer(new com.cim.network.packet.fluids.ToggleFavoriteFluidPacket(clickedFluid));
                        // Обновляем локально для мгновенной реакции GUI
                        if (favorites.contains(clickedFluid)) favorites.remove(clickedFluid);
                        else favorites.add(clickedFluid);
                        updateFluidList();
                    }
                    return true;
                } else {
                    // Кликнули по самой плашке -> выбираем жидкость
                    com.cim.network.ModPacketHandler.INSTANCE.sendToServer(new com.cim.network.packet.fluids.SelectFluidPacket(clickedFluid));
                    this.minecraft.player.closeContainer(); // Закрываем GUI
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
