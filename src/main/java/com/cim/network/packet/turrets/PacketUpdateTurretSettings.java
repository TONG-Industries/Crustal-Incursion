package com.cim.network.packet.turrets;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.cim.block.entity.weapons.TurretLightPlacerBlockEntity;

import java.util.function.Supplier;

public class PacketUpdateTurretSettings {
    private final BlockPos pos;
    private final int settingIndex; // 0=Hostile, 1=Neutral, 2=Players
    private final boolean value;

    public PacketUpdateTurretSettings(BlockPos pos, int settingIndex, boolean value) {
        this.pos = pos;
        this.settingIndex = settingIndex;
        this.value = value;
    }

    public PacketUpdateTurretSettings(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.settingIndex = buf.readInt();
        this.value = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(settingIndex);
        buf.writeBoolean(value);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                BlockEntity be = player.level().getBlockEntity(pos);
                if (be instanceof TurretLightPlacerBlockEntity turretBE) {
                    // Метод нужно добавить в BlockEntity!
                    // Он должен менять значения в ContainerData
                    turretBE.updateAttackSetting(settingIndex, value);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
