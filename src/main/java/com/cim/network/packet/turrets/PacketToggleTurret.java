package com.cim.network.packet.turrets;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.cim.block.entity.weapons.TurretLightPlacerBlockEntity;

import java.util.function.Supplier;

public class PacketToggleTurret {
    private final BlockPos pos;

    public PacketToggleTurret(BlockPos pos) {
        this.pos = pos;
    }

    public PacketToggleTurret(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // ЛОГИКА НА СЕРВЕРЕ
            ServerPlayer player = context.getSender();
            if (player != null) {
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof TurretLightPlacerBlockEntity turretBE) {
                    turretBE.togglePower(); // <--- Метод, который мы создадим ниже
                }
            }
        });
        return true;
    }
}
