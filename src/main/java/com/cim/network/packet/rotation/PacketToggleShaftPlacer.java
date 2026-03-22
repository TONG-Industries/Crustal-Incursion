package com.cim.network.packet.rotation;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.cim.block.entity.industrial.rotation.ShaftPlacerBlockEntity;

import java.util.function.Supplier;

public class PacketToggleShaftPlacer {
    private final BlockPos pos;

    public PacketToggleShaftPlacer(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketToggleShaftPlacer msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketToggleShaftPlacer decode(FriendlyByteBuf buf) {
        return new PacketToggleShaftPlacer(buf.readBlockPos());
    }

    public static void handle(PacketToggleShaftPlacer msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (be instanceof ShaftPlacerBlockEntity placer) {
                    placer.togglePower();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}