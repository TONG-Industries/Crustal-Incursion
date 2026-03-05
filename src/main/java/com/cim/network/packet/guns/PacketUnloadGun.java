package com.cim.network.packet.guns;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import com.cim.item.guns.MachineGunItem;

import java.util.function.Supplier;

public class PacketUnloadGun {

    public PacketUnloadGun() {}

    public PacketUnloadGun(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (stack.getItem() instanceof MachineGunItem gun) {
                    gun.unloadGun(player, stack); // Вызываем метод разрядки
                    player.inventoryMenu.broadcastChanges(); // Обновляем инвентарь на клиенте
                }
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
