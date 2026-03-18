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

public class SelectFluidPacket {
    private final String fluidId;

    public SelectFluidPacket(String fluidId) { this.fluidId = fluidId; }
    public SelectFluidPacket(FriendlyByteBuf buf) { this.fluidId = buf.readUtf(); }
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
                    tag.putString("SelectedFluid", fluidId);

                    // Если выбрали не "Ничего", добавляем в недавние (максимум 10)
                    if (!fluidId.equals("none")) {
                        ListTag recents = tag.getList("RecentFluids", Tag.TAG_STRING);
                        // Удаляем дубликат, чтобы перенести его на первое место
                        for (int i = 0; i < recents.size(); i++) {
                            if (recents.getString(i).equals(fluidId)) {
                                recents.remove(i);
                                break;
                            }
                        }
                        recents.add(0, StringTag.valueOf(fluidId));
                        while (recents.size() > 10) {
                            recents.remove(recents.size() - 1);
                        }
                        tag.put("RecentFluids", recents);
                    }
                }
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
