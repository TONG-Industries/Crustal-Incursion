package com.cim.api.fluids;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "cim")
public class FluidNetworkTickHandler {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.level.isClientSide) return;

        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {

            FluidNetworkManager manager = FluidNetworkManager.get(serverLevel);

            // 1. Даем сетям тик (для перекачки жидкостей)
            manager.tick();

            // 2. Отладка: выводим в консоль статус раз в 3 секунды (60 тиков)
            if (serverLevel.getGameTime() % 60 == 0) {
                manager.debugLog();
            }
        }
    }
}