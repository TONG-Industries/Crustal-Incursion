package com.cim.network.packet.fluids;

import com.cim.item.tools.FluidIdentifierItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClearFluidHistoryPacket {
    public ClearFluidHistoryPacket() {}
    public ClearFluidHistoryPacket(FriendlyByteBuf buf) {}
    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof FluidIdentifierItem)) {
                    stack = player.getOffhandItem();
                }
                if (stack.getItem() instanceof FluidIdentifierItem) {
                    stack.getOrCreateTag().remove("RecentFluids");
                }
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
