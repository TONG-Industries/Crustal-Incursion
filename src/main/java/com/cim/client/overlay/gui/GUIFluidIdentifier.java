package com.cim.client.overlay.gui;

import com.cim.api.fluids.ModFluids;
import com.cim.item.tools.FluidIdentifierItem;
import com.cim.main.CrustalIncursionMod;
import com.cim.network.ModPacketHandler;
import com.cim.network.packet.fluids.ClearFluidHistoryPacket;
import com.cim.network.packet.fluids.SelectFluidPacket;
import com.cim.network.packet.fluids.ToggleFavoriteFluidPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class GUIFluidIdentifier extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/item/fluid_identifier_gui.png");
    private final ItemStack identifierStack;

    private static final int IMAGE_WIDTH = 153;
    private static final int IMAGE_HEIGHT = 229;
    private int leftPos, topPos;

    private final List<String> recentFluids = new ArrayList<>();
    private final List<String> favorites = new ArrayList<>();
    private final List<String> displayList = new ArrayList<>();

    private float scrollAmount = 0f;
    private int timerClear = 0;
    private int timerSearch = 0;
    private int cursorTimer = 0;
    private static final int PRESS_DURATION = 10;
    private static final int COLOR_INFO = 0xAEC6CF;
    private static final int COLOR_HAZARDOUS = 0xFFFF5555;
    private static final int COLOR_RADIOACTIVE = 0xFF55FF55;

    private EditBox searchBox;

    public GUIFluidIdentifier(ItemStack stack) {
        super(Component.literal("Fluid Identifier"));
        this.identifierStack = stack;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;

        this.recentFluids.clear();
        this.recentFluids.addAll(FluidIdentifierItem.getRecentFluids(identifierStack));
        this.favorites.clear();
        this.favorites.addAll(FluidIdentifierItem.getFavorites(identifierStack));

        this.searchBox = new EditBox(this.font, this.leftPos + 40, this.topPos + 12, 64, 15, Component.empty());
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(16);
        this.searchBox.setTextColor(0x00FFFFFF);
        this.searchBox.setFocused(true);
        this.searchBox.setResponder(text -> updateFluidList());

        // НЕ добавляем в renderables чтобы избежать двойного рендера
        // this.addRenderableWidget(this.searchBox);

        updateFluidList();
    }

    @Override
    public void tick() {
        super.tick();
        if (timerClear > 0) timerClear--;
        if (timerSearch > 0) timerSearch--;
        cursorTimer++;
    }

    private String getFluidSearchString(Fluid fluid) {
        StringBuilder sb = new StringBuilder();

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id != null) {
            String path = id.getPath().toLowerCase();
            sb.append(id.toString().toLowerCase()).append(" ");
            sb.append(path).append(" ");

            // Добавляем части пути для поиска (например "hydrogen_peroxide" -> "hydrogen" "peroxide")
            for (String part : path.split("_")) {
                sb.append(part).append(" ");
            }
        }

        sb.append(fluid.getFluidType().getDescription().getString().toLowerCase()).append(" ");

        FluidStack stack = new FluidStack(fluid, 1000);
        int corrosivity = com.cim.api.fluids.FluidPropertyHelper.getCorrosivity(stack);
        int radioactivity = com.cim.api.fluids.FluidPropertyHelper.getRadioactivity(stack);
        int temperature = com.cim.api.fluids.FluidPropertyHelper.getTemperature(stack);

        if (corrosivity > 0) {
            sb.append("corrosive acid кислота коррозия едкий ");
            if (corrosivity >= 2) sb.append("strong сильный ");
        }
        if (radioactivity > 0) {
            sb.append("radioactive radiation радиоактивный радиация ");
            if (radioactivity >= 2) sb.append("nuclear ядерный ");
        }
        if (temperature > 500) sb.append("hot heat горячий пар steam ");
        if (temperature < 273) sb.append("cold ice холодный лед ");

        int baseTemp = fluid.getFluidType().getTemperature();
        if (baseTemp > 1000) sb.append("lava магма magma ");
        if (fluid.getFluidType().getDensity() < 0) sb.append("gas газ пар steam ");

        return sb.toString();
    }

    private void updateFluidList() {
        displayList.clear();
        String search = searchBox.getValue().toLowerCase().trim();

        // 1. Добавляем все избранные жидкости (включая "none", если оно там)
        for (String fav : favorites) {
            if (fav.equals("none")) {
                displayList.add(fav); // "none" добавляем как есть
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(fav));
            if (fluid == null || !fluid.defaultFluidState().isSource()) continue;
            if (search.isEmpty() || getFluidSearchString(fluid).contains(search)) {
                displayList.add(fav);
            }
        }

        // 2. Если "none" НЕ в избранном, но подходит под поиск – добавляем его (после избранных)
        boolean noneInFavorites = favorites.contains("none");
        boolean shouldAddNone = !noneInFavorites && (search.isEmpty() || "none ничего пусто empty".contains(search));
        if (shouldAddNone) {
            displayList.add("none");
        }

        // 3. Добавляем все остальные жидкости, кроме уже добавленных
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY || !fluid.defaultFluidState().isSource()) continue;
            String id = BuiltInRegistries.FLUID.getKey(fluid).toString();
            if (displayList.contains(id)) continue;
            if (search.isEmpty() || getFluidSearchString(fluid).contains(search)) {
                displayList.add(id);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int x = this.leftPos;
        int y = this.topPos;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(TEXTURE, x, y, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        if (timerSearch > 0) graphics.blit(TEXTURE, x + 22, y + 9, 167, 80, 15, 15);
        if (timerClear > 0) graphics.blit(TEXTURE, x + 105, y + 33, 154, 80, 12, 33);

        renderRecentFluids(graphics, x, y, mouseX, mouseY);
        renderScrollableList(graphics, x, y, mouseX, mouseY);
        renderScrollBar(graphics, x, y);

        // Рисуем текст и курсор вручную, без вызова searchBox.render()
        String content = searchBox.getValue();
        boolean focused = searchBox.isFocused();
        String cursorSymbol = (focused && (cursorTimer / 10 % 2 == 0)) ? "_" : "";
        String fullText = content + cursorSymbol;
        if (this.font.width(fullText) > 60) {
            fullText = this.font.plainSubstrByWidth(fullText, 60, true);
        }
        graphics.drawString(this.font, fullText, searchBox.getX(), searchBox.getY(), COLOR_INFO, false);
    }

    private void renderRecentFluids(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < recentFluids.size(); i++) {
            if (i >= 10) break;
            String fluidId = recentFluids.get(i);
            if (fluidId == null || fluidId.isEmpty() || fluidId.equals("null")) continue; // ← добавить эту проверку

            int drawX = x + 22 + ((i % 5) * 16);
            int drawY = y + 33 + ((i / 5) * 17);

            renderFluidIcon(graphics, fluidId, drawX, drawY);

            if (mouseX >= drawX && mouseX < drawX + 16 && mouseY >= drawY && mouseY < drawY + 16) {
                Component tooltip = getFluidDisplayName(fluidId);
                // Дополнительная защита: если компонент пустой, подставить заглушку
                if (tooltip.getString().trim().isEmpty()) {
                    tooltip = Component.literal("Unknown");
                }
                graphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
            }
        }
    }

    private Component getFluidDisplayName(String fluidId) {
        if (fluidId.equals("none")) {
            return Component.literal("Ничего");
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(fluidId));
        if (fluid != null) {
            return fluid.getFluidType().getDescription();
        }
        return Component.literal(fluidId.replace("minecraft:", "").replace("cim:", ""));
    }

    private void renderScrollableList(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int listX = x + 22;
        int listY = y + 75;
        graphics.enableScissor(listX, listY, listX + 99, listY + 141);

        int maxScroll = Math.max(0, (displayList.size() * 19) - 141);
        int currentOffset = (int) (scrollAmount * maxScroll);
        String selectedFluid = FluidIdentifierItem.getSelectedFluid(identifierStack);

        for (int i = 0; i < displayList.size(); i++) {
            String fluidId = displayList.get(i);
            int entryY = listY + (i * 19) - currentOffset;

            if (entryY + 19 < listY || entryY > listY + 141) continue;

            boolean isFav = favorites.contains(fluidId);
            boolean isCurrent = fluidId.equals(selectedFluid);
            int vOffset = isCurrent ? (isFav ? 60 : 40) : (isFav ? 20 : 0);

            graphics.blit(TEXTURE, listX, entryY, 154, vOffset, 99, 19);

            // Иконка теперь рисуется для всех, включая "none"
            renderFluidIcon(graphics, fluidId, listX + 3, entryY + 3);

            if (!fluidId.equals("none")) {
                Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(fluidId));
                if (fluid != null) {
                    FluidStack stack = new FluidStack(fluid, 1000);
                    int cx = listX + 88;
                    if (com.cim.api.fluids.FluidPropertyHelper.getCorrosivity(stack) > 0) {
                        graphics.fill(cx, entryY + 4, cx + 2, entryY + 6, COLOR_HAZARDOUS);
                        cx -= 3;
                    }
                    if (com.cim.api.fluids.FluidPropertyHelper.getRadioactivity(stack) > 0) {
                        graphics.fill(cx, entryY + 4, cx + 2, entryY + 6, COLOR_RADIOACTIVE);
                    }
                }
            }

            Component name = getFluidDisplayName(fluidId);
            graphics.drawString(this.font, name, listX + 20, entryY + 5, getFluidColor(fluidId), false);
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = this.leftPos;
        int y = this.topPos;

        // Поиск
        if (mouseX >= x + 22 && mouseX <= x + 37 && mouseY >= y + 9 && mouseY <= y + 24) {
            timerSearch = PRESS_DURATION;
            playClickSound();
            this.searchBox.setFocused(true);
            return true;
        }

        // Очистка
        if (mouseX >= x + 105 && mouseX <= x + 117 && mouseY >= y + 33 && mouseY <= y + 66) {
            timerClear = PRESS_DURATION;
            playClickSound();
            ModPacketHandler.INSTANCE.sendToServer(new ClearFluidHistoryPacket());
            this.recentFluids.clear();
            return true;
        }

        // Клик по списку
        int listX = x + 22;
        int listY = y + 75;
        if (mouseX >= listX && mouseX <= listX + 99 && mouseY >= listY && mouseY <= listY + 141) {
            int maxScroll = Math.max(0, (displayList.size() * 19) - 141);
            int currentOffset = (int) (scrollAmount * maxScroll);
            int clickedIndex = (int) ((mouseY - listY + currentOffset) / 19);

            if (clickedIndex >= 0 && clickedIndex < displayList.size()) {
                String fluid = displayList.get(clickedIndex);
                int entryY = listY + (clickedIndex * 19) - currentOffset;
                playClickSound();

                // Клик по звездочке (избранное)
                if (mouseX >= listX + 88 && mouseX <= listX + 97 && mouseY >= entryY + 4 && mouseY <= entryY + 14) {
                    toggleFavorite(fluid);
                } else {
                    selectFluid(fluid);
                }
                return true;
            }
        }

        // Клик по недавним
        for (int i = 0; i < recentFluids.size(); i++) {
            int drawX = x + 22 + ((i % 5) * 16);
            int drawY = y + 33 + ((i / 5) * 17);
            if (mouseX >= drawX && mouseX < drawX + 16 && mouseY >= drawY && mouseY < drawY + 16) {
                playClickSound();
                selectFluid(recentFluids.get(i));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectFluid(String fluid) {
        ModPacketHandler.INSTANCE.sendToServer(new SelectFluidPacket(fluid));
        identifierStack.getOrCreateTag().putString("SelectedFluid", fluid);

        // Добавляем ВСЕ выбранные жидкости (включая "none") в историю
        recentFluids.remove(fluid);
        recentFluids.add(0, fluid);
        if (recentFluids.size() > 10) recentFluids.remove(10);
    }

    private void toggleFavorite(String fluid) {
        ModPacketHandler.INSTANCE.sendToServer(new ToggleFavoriteFluidPacket(fluid));
        if (favorites.contains(fluid)) favorites.remove(fluid);
        else favorites.add(fluid);
        updateFluidList();
    }

    private void renderFluidIcon(GuiGraphics graphics, String id, int x, int y) {
        ItemStack dummy;
        if (id.equals("none")) {
            // Для "ничего" используем сам предмет идентификатора без NBT
            dummy = new ItemStack(identifierStack.getItem());
        } else {
            // Для жидкости подкладываем её ID в NBT, чтобы предмет отрисовал её иконку
            dummy = new ItemStack(identifierStack.getItem());
            dummy.getOrCreateTag().putString("SelectedFluid", id);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(13f / 16f, 13f / 16f, 1f);
        graphics.renderItem(dummy, 0, 0);
        graphics.pose().popPose();
    }

    private int getFluidColor(String id) {
        if (id.equals("none")) return 0xFFAAAAAA; // серый
        if (id.equals("minecraft:lava")) return 0xFFFF5500;
        Fluid fluid = BuiltInRegistries.FLUID.get(new ResourceLocation(id));
        return (fluid != null) ? IClientFluidTypeExtensions.of(fluid).getTintColor() : 0xFFDDDDDD;
    }

    private void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void renderScrollBar(GuiGraphics graphics, int x, int y) {
        int thumbY = y + 75 + (int) (scrollAmount * (141 - 15));
        graphics.blit(TEXTURE, x + 123, thumbY, 215, 80, 8, 15);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int ms = Math.max(0, (displayList.size() * 19) - 141);
        if (ms > 0) {
            scrollAmount = Math.max(0f, Math.min(1f, scrollAmount - (float) (d * 19 / ms)));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int m) {
        return searchBox.charTyped(c, m);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        return searchBox.keyPressed(k, s, m) || super.keyPressed(k, s, m);
    }
    @Override
    public boolean isPauseScreen() {
        return false; // Мир не должен застывать
    }
}