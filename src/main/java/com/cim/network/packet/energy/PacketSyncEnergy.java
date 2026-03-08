package com.cim.network.packet.energy;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import com.cim.client.handler.ClientEnergySyncHandler;

import java.util.function.Supplier;

public class PacketSyncEnergy {
    private final int containerId;
    private final long energy;
    private final long maxEnergy;
    private final long delta;
    private final long chargingSpeed;
    private final long unchargingSpeed;
    private final int filledCellCount;

    public PacketSyncEnergy(int containerId, long energy, long maxEnergy, long delta,
                            long chargingSpeed, long unchargingSpeed, int filledCellCount) {
        this.containerId = containerId;
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.delta = delta;
        this.chargingSpeed = chargingSpeed;
        this.unchargingSpeed = unchargingSpeed;
        this.filledCellCount = filledCellCount;
    }

    public static void encode(PacketSyncEnergy msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.containerId);
        buffer.writeLong(msg.energy);
        buffer.writeLong(msg.maxEnergy);
        buffer.writeLong(msg.delta);
        buffer.writeLong(msg.chargingSpeed);
        buffer.writeLong(msg.unchargingSpeed);
        buffer.writeInt(msg.filledCellCount);
    }

    public static PacketSyncEnergy decode(FriendlyByteBuf buffer) {
        return new PacketSyncEnergy(
                buffer.readInt(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readInt()
        );
    }

    public static void handle(PacketSyncEnergy msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientEnergySyncHandler.handle(
                        msg.containerId,
                        msg.energy,
                        msg.maxEnergy,
                        msg.delta,
                        msg.chargingSpeed,
                        msg.unchargingSpeed,
                        msg.filledCellCount
                );
            });
        });
        ctx.get().setPacketHandled(true);
    }
}