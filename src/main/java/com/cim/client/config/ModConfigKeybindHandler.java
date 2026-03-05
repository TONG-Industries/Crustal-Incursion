package com.cim.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import com.cim.client.overlay.gui.GUIMultiDetonator;
import com.cim.item.activators.MultiDetonatorItem;
import com.cim.network.ModPacketHandler;
import com.cim.network.packet.guns.PacketUnloadGun;

// УДАЛИЛИ АННОТАЦИЮ @Mod.EventBusSubscriber
public class ModConfigKeybindHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ModKeyBindings.UNLOAD_KEY.consumeClick()) {
            ModPacketHandler.INSTANCE.sendToServer(new PacketUnloadGun());
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;

        // Проверяем, что игрок существует и никакой GUI не открыт
        if (player == null || minecraft.screen != null) {
            return;
        }

        // Используем GLFW код клавиши R
        if (event.getKey() == GLFW.GLFW_KEY_R && event.getAction() == GLFW.GLFW_PRESS) {
            ItemStack mainItem = player.getMainHandItem();
            ItemStack offItem = player.getOffhandItem();

            // Проверяем предмет в основной руке
            if (mainItem.getItem() instanceof MultiDetonatorItem) {
                minecraft.setScreen(new GUIMultiDetonator(mainItem));
                // НЕ вызываем event.setCanceled(true); - это вызовет крах!
                return;
            }

            // Проверяем предмет в руке со щитом
            if (offItem.getItem() instanceof MultiDetonatorItem) {
                minecraft.setScreen(new GUIMultiDetonator(offItem));
                // НЕ вызываем event.setCanceled(true); - это вызовет крах!
                return;
            }
        }
    }

}
