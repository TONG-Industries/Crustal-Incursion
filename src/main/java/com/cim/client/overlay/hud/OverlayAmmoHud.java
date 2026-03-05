package com.cim.client.overlay.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.registries.ForgeRegistries;
import com.cim.item.guns.MachineGunItem;

public class OverlayAmmoHud {

    // Текстура заднего фона для плашки (опционально, если нарисуешь)
    // private static final ResourceLocation BG_TEXTURE = new ResourceLocation(RefStrings.MODID, "textures/gui/ammo_hud_bg.png");

    public static final IGuiOverlay HUD_AMMO = (ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // Проверяем, что в руках пулемет
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof MachineGunItem machineGun)) {
            return;
        }

        // Получаем данные
        int currentAmmo = machineGun.getAmmo(stack);
        String loadedId = machineGun.getLoadedAmmoID(stack);
        int maxAmmo = 25; // 24 в ленте + 1 в патроннике

        // Настройки позиционирования (Правый нижний угол)
        int x = screenWidth - 16; // Отступ справа
        int y = screenHeight - 16; // Отступ снизу

        // --- РЕНДЕР ИКОНКИ ПАТРОНА ---
        // Рисуем иконку только если патроны есть > 0.
        // Или если хочешь видеть "призрак" последнего патрона даже при 0, убери проверку currentAmmo > 0
        if (currentAmmo > 0 && loadedId != null && !loadedId.isEmpty()) {
            Item ammoItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(loadedId));
            if (ammoItem != null) {
                // Рисуем иконку предмета (самого патрона)
                // Сдвигаем влево, чтобы не наезжала на текст (зависит от длины строки)
                // x - 80 (примерно) дает место для длинной надписи
                guiGraphics.renderItem(new ItemStack(ammoItem), x - 85, y - 8);
            }
        }

        // --- РЕНДЕР ТЕКСТА ---
        // Формат: "24 + 1 / 25"
        String text;
        if (currentAmmo > 1) {
            // Есть патроны в ленте + 1 в стволе
            // (currentAmmo - 1) - это лента
            text = (currentAmmo - 1) + " + 1 / " + maxAmmo;
        } else if (currentAmmo == 1) {
            // Лента пуста, 1 в стволе
            text = "0 + 1 / " + maxAmmo;
        } else {
            // Полный ноль
            text = "0 / " + maxAmmo;
        }

        // Цвет текста: Красный, если патронов мало (< 5), иначе Белый
        int color = (currentAmmo < 5) ? 0xFF5555 : 0xFFFFFF;

        // Рисуем текст (с тенью)
        // Выравнивание по правому краю: вычисляем ширину текста и сдвигаем X влево
        int textWidth = mc.font.width(text);
        guiGraphics.drawString(mc.font, text, x - textWidth, y - 6, color, true);
    };

}
