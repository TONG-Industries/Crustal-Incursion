package com.cim.network;


import com.cim.network.packet.fluids.ClearFluidHistoryPacket;
import com.cim.network.packet.fluids.SelectFluidPacket;
import com.cim.network.packet.fluids.ToggleFavoriteFluidPacket;
import com.cim.network.packet.fluids.UpdateBarrelModeC2SPacket;
import com.cim.network.packet.rotation.PacketToggleRetractMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import com.cim.main.CrustalIncursionMod;
import com.cim.network.packet.activators.ClearPointPacket;
import com.cim.network.packet.activators.DetonateAllPacket;
import com.cim.network.packet.activators.SetActivePointPacket;
import com.cim.network.packet.activators.SyncPointPacket;
import com.cim.network.packet.energy.PacketSyncEnergy;
import com.cim.network.packet.energy.UpdateBatteryC2SPacket;
import com.cim.network.packet.guns.PacketReloadGun;
import com.cim.network.packet.guns.PacketShoot;
import com.cim.network.packet.guns.PacketUnloadGun;
import com.cim.network.packet.rotation.PacketToggleMotor;
import com.cim.network.packet.rotation.PacketToggleMotorMode;
import com.cim.network.packet.rotation.PacketToggleShaftPlacer;
import com.cim.network.packet.turrets.PacketChipFeedback;
import com.cim.network.packet.turrets.PacketModifyTurretChip;
import com.cim.network.packet.turrets.PacketToggleTurret;
import com.cim.network.packet.turrets.PacketUpdateTurretSettings;

public class ModPacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CrustalIncursionMod.MOD_ID, "main_channel"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;


        INSTANCE.registerMessage(id++, UpdateBatteryC2SPacket.class, UpdateBatteryC2SPacket::toBytes, UpdateBatteryC2SPacket::new, (msg, ctx) -> msg.handle(ctx));

        INSTANCE.registerMessage(id++,
                PacketSyncEnergy.class,
                PacketSyncEnergy::encode,
                PacketSyncEnergy::decode,
                PacketSyncEnergy::handle
        );

        INSTANCE.registerMessage(id++, UpdateBarrelModeC2SPacket.class,
                UpdateBarrelModeC2SPacket::toBytes,
                UpdateBarrelModeC2SPacket::new,
                UpdateBarrelModeC2SPacket::handle);

        INSTANCE.registerMessage(id++,
                PacketReloadGun.class,
                PacketReloadGun::toBytes,
                PacketReloadGun::new,
                PacketReloadGun::handle
        );

        // === ДОБАВЬТЕ ЭТОТ ПАКЕТ ДЛЯ СТРЕЛЬБЫ ===
        INSTANCE.registerMessage(id++,
                PacketShoot.class,
                PacketShoot::toBytes,
                PacketShoot::new,
                PacketShoot::handle
        );

        INSTANCE.registerMessage(id++,
                PacketUnloadGun.class,
                PacketUnloadGun::toBytes,
                PacketUnloadGun::new,
                PacketUnloadGun::handle
        );

        INSTANCE.registerMessage(id++,
                PacketToggleMotor.class,
                PacketToggleMotor::encode,
                PacketToggleMotor::new,
                PacketToggleMotor::handle
        );

        INSTANCE.registerMessage(id++,
                PacketToggleMotorMode.class,
                PacketToggleMotorMode::encode,
                PacketToggleMotorMode::new,
                PacketToggleMotorMode::handle
        );

        INSTANCE.registerMessage(id++,
                PacketModifyTurretChip.class,
                PacketModifyTurretChip::toBytes,
                PacketModifyTurretChip::new,
                PacketModifyTurretChip::handle
        );

        INSTANCE.registerMessage(id++,
                PacketChipFeedback.class,
                PacketChipFeedback::toBytes,
                PacketChipFeedback::new,
                PacketChipFeedback::handle
        );

        INSTANCE.registerMessage(id++,
                PacketUpdateTurretSettings.class,
                PacketUpdateTurretSettings::toBytes,
                PacketUpdateTurretSettings::new,
                PacketUpdateTurretSettings::handle
        );

        INSTANCE.registerMessage(id++,
                PacketToggleTurret.class,
                PacketToggleTurret::toBytes,
                PacketToggleTurret::new,
                PacketToggleTurret::handle
        );

        INSTANCE.registerMessage(id++,
                DetonateAllPacket.class,
                DetonateAllPacket::encode,
                DetonateAllPacket::decode,
                DetonateAllPacket::handle
        );

        INSTANCE.registerMessage(id++,
                SetActivePointPacket.class,
                SetActivePointPacket::encode,
                SetActivePointPacket::decode,
                SetActivePointPacket::handle
        );

        INSTANCE.registerMessage(id++,
                ClearPointPacket.class,
                ClearPointPacket::encode,
                ClearPointPacket::decode,
                ClearPointPacket::handle
        );

        INSTANCE.registerMessage(id++,
                SyncPointPacket.class,
                SyncPointPacket::encode,
                SyncPointPacket::decode,
                SyncPointPacket::handle
        );

        INSTANCE.registerMessage(id++,
                PacketToggleShaftPlacer.class,
                PacketToggleShaftPlacer::encode,
                PacketToggleShaftPlacer::decode,
                PacketToggleShaftPlacer::handle
        );
        INSTANCE.registerMessage(id++,
                PacketToggleRetractMode.class,
                PacketToggleRetractMode::encode,
                PacketToggleRetractMode::decode,
                PacketToggleRetractMode::handle);

        INSTANCE.registerMessage(id++,
                ClearFluidHistoryPacket.class,
                ClearFluidHistoryPacket::toBytes,
                ClearFluidHistoryPacket::new,
                ClearFluidHistoryPacket::handle);

        INSTANCE.registerMessage(id++,
                ToggleFavoriteFluidPacket.class,
                ToggleFavoriteFluidPacket::toBytes,
                ToggleFavoriteFluidPacket::new,
                ToggleFavoriteFluidPacket::handle);

        INSTANCE.registerMessage(id++,
                SelectFluidPacket.class,
                SelectFluidPacket::toBytes,
                SelectFluidPacket::new,
                SelectFluidPacket::handle);
    }
}
