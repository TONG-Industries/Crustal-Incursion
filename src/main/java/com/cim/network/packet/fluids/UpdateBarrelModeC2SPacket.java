package com.cim.network.packet.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.cim.block.entity.fluids.FluidBarrelBlockEntity;

import java.util.function.Supplier;

public class UpdateBarrelModeC2SPacket {
    private final BlockPos pos;

    public UpdateBarrelModeC2SPacket(BlockPos pos) {
        this.pos = pos;
    }

    public UpdateBarrelModeC2SPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FluidBarrelBlockEntity barrel) {
                    barrel.changeMode(); // Вызываем наш новый метод
                }
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
