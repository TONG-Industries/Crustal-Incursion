package com.cim.network.packet.fluids;

import com.cim.item.tools.FluidIdentifierItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleFavoriteFluidPacket {
    private final String fluidId;

    public ToggleFavoriteFluidPacket(String fluidId) { this.fluidId = fluidId; }
    public ToggleFavoriteFluidPacket(FriendlyByteBuf buf) { this.fluidId = buf.readUtf(); }
    public void toBytes(FriendlyByteBuf buf) { buf.writeUtf(fluidId); }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof FluidIdentifierItem)) stack = player.getOffhandItem();

                if (stack.getItem() instanceof FluidIdentifierItem) {
                    CompoundTag tag = stack.getOrCreateTag();
                    ListTag favorites = tag.getList("Favorites", Tag.TAG_STRING);
                    boolean found = false;

                    for (int i = 0; i < favorites.size(); i++) {
                        if (favorites.getString(i).equals(fluidId)) {
                            favorites.remove(i);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        favorites.add(StringTag.valueOf(fluidId));
                    }
                    tag.put("Favorites", favorites);
                }
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}