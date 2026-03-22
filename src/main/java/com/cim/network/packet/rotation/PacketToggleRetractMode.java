package com.cim.network.packet.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.cim.block.entity.industrial.rotation.ShaftPlacerBlockEntity;

import java.util.function.Supplier;

public class PacketToggleRetractMode {
    private final BlockPos pos;

    public PacketToggleRetractMode(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketToggleRetractMode msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketToggleRetractMode decode(FriendlyByteBuf buf) {
        return new PacketToggleRetractMode(buf.readBlockPos());
    }

    public static void handle(PacketToggleRetractMode msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Level level = player.level();
                BlockEntity be = level.getBlockEntity(msg.pos);
                if (be instanceof ShaftPlacerBlockEntity placer) {
                    placer.toggleRetractMode();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}