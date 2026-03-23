package com.cim.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.cim.main.CrustalIncursionMod;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CrustalIncursionMod.MOD_ID);


    public static final RegistryObject<MenuType<MachineBatteryMenu>> MACHINE_BATTERY_MENU =
            MENUS.register("machine_battery_menu", () -> IForgeMenuType.create(MachineBatteryMenu::new));

    public static final RegistryObject<MenuType<MotorElectroMenu>> MOTOR_ELECTRO_MENU =
            MENUS.register("motor_electro_menu", () -> IForgeMenuType.create(MotorElectroMenu::new));

    public static final RegistryObject<MenuType<TurretLightMenu>> TURRET_AMMO_MENU =
            MENUS.register("turret_ammo", () -> IForgeMenuType.create((windowId, inv, data) -> {
                // Вызываем конструктор: TurretLightMenu(int, Inventory, FriendlyByteBuf)
                return new TurretLightMenu(windowId, inv, data);
            }));

    public static final RegistryObject<MenuType<HeaterMenu>> HEATER_MENU =
            MENUS.register("heater_menu", () -> IForgeMenuType.create(HeaterMenu::create));

    public static final RegistryObject<MenuType<ShaftPlacerMenu>> SHAFT_PLACER_MENU =
            MENUS.register("shaft_placer_menu",
                    () -> IForgeMenuType.create(ShaftPlacerMenu::new));

    public static final RegistryObject<MenuType<SmelterMenu>> SMELTER_MENU =
            MENUS.register("smelter_menu", () -> IForgeMenuType.create(SmelterMenu::create));

    public static final RegistryObject<MenuType<MiningPortMenu>> MINING_PORT_MENU =
            MENUS.register("mining_port_menu",
                    () -> IForgeMenuType.create(MiningPortMenu::new));

    public static final RegistryObject<MenuType<FluidBarrelMenu>> FLUID_BARREL_MENU =
            MENUS.register("fluid_barrel_menu",
                    () -> IForgeMenuType.create(FluidBarrelMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}