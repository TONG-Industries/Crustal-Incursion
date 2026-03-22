package com.cim.network.packet.rotation;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity;

import java.util.function.Supplier;

// Пакет
public class PacketToggleMotor {
    private final BlockPos pos;

    public PacketToggleMotor(BlockPos pos) { this.pos = pos; }
    public PacketToggleMotor(FriendlyByteBuf buf) { this.pos = buf.readBlockPos(); }
    public void encode(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Level level = player.level();
                if (level.getBlockEntity(pos) instanceof MotorElectroBlockEntity be) {
                    be.togglePower();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}