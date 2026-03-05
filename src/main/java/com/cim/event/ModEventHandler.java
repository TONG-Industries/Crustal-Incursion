package com.cim.event;


import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import com.cim.item.ModItems;
import com.cim.item.tags.AmmoRegistry;
import com.cim.item.weapons.ammo.AmmoTurretItem;
import com.cim.main.CrustalIncursionMod;
import com.cim.network.ModPacketHandler;

/**
 * Обработчик FML событий для инициализации сетевых каналов
 * В Forge 1.20.1 каналы ДОЛЖНЫ регистрироваться на FMLCommonSetupEvent,
 * а не в статических блоках или на клиенте!
 */
@Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventHandler {



    // В ModEventHandler.java
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModPacketHandler.register();

            // Регистрируем патроны, беря данные ПРЯМО ИЗ ПРЕДМЕТОВ
            // Метод registerFromItem сам достанет урон/скорость из AmmoTurretItem
            registerAmmo(ModItems.AMMO_TURRET.get(), "20mm_turret");
            registerAmmo(ModItems.AMMO_TURRET_PIERCING.get(), "20mm_turret");
            registerAmmo(ModItems.AMMO_TURRET_HOLLOW.get(), "20mm_turret");
            registerAmmo(ModItems.AMMO_TURRET_FIRE.get(), "20mm_turret");
            registerAmmo(ModItems.AMMO_TURRET_RADIO.get(), "20mm_turret");
        });
    }

    // Вспомогательный метод (добавь его в ModEventHandler или вызови AmmoRegistry напрямую)
    private static void registerAmmo(Item item, String caliber) {
        // Проверяем, что это наш кастомный патрон
        if (item instanceof AmmoTurretItem ammoItem) {
            AmmoRegistry.register(
                    item,
                    caliber,
                    ammoItem.getDamage(),   // Берем из предмета!
                    ammoItem.getSpeed(),    // Берем из предмета!
                    ammoItem.isPiercing()   // Берем из предмета!
            );
        }
    }

}