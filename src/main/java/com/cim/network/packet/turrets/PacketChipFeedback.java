package com.cim.network.packet.turrets;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.cim.client.overlay.gui.GUITurretAmmo;

import java.util.function.Supplier;

public class PacketChipFeedback {
    private final boolean success;

    public PacketChipFeedback(boolean success) {
        this.success = success;
    }

    public PacketChipFeedback(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // Выполняем на клиенте
            net.minecraft.client.gui.screens.Screen currentScreen = Minecraft.getInstance().screen;
            if (currentScreen instanceof GUITurretAmmo gui) {
                gui.handleFeedback(success);
            }
        });
        context.setPacketHandled(true);
    }
}
