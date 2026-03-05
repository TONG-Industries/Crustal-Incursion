package com.cim.capability;


import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.cim.api.energy.IEnergyConnector;
import com.cim.api.energy.IEnergyProvider;
import com.cim.api.energy.IEnergyReceiver;
import com.cim.api.hive.HiveNetworkManager;

public class ModCapabilities {

    public static final Capability<IEnergyProvider> ENERGY_PROVIDER = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<IEnergyReceiver> ENERGY_RECEIVER = CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<IEnergyConnector> ENERGY_CONNECTOR = CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {

        event.register(HiveNetworkManager.class);
        event.register(IEnergyProvider.class);
        event.register(IEnergyReceiver.class);
        event.register(IEnergyConnector.class);
    }
}