package com.cim.item;

import com.cim.item.energy.EnergyCellItem;
import com.cim.item.rotation.*;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.cim.block.basic.ModBlocks;
import com.cim.item.activators.DetonatorItem;
import com.cim.item.activators.MultiDetonatorItem;
import com.cim.item.activators.RangeDetonatorItem;
import com.cim.item.energy.ItemCreativeBattery;
import com.cim.item.energy.ModBatteryItem;
import com.cim.item.guns.MachineGunItem;
import com.cim.item.mobs.DepthWormSpawnEggItem;
import com.cim.item.weapons.ammo.AmmoTurretItem;
import com.cim.item.weapons.turrets.TurretChipItem;
import com.cim.item.weapons.turrets.TurretLightPortativePlacer;
import com.cim.item.weapons.turrets.TurretLightPlacerBlockItem;
import com.cim.main.CrustalIncursionMod;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CrustalIncursionMod.MOD_ID);


    //ОБЫЧНЫЕ ПРЕДМЕТЫ
    public static final RegistryObject<Item> DEPTH_WORM_SPAWN_EGG = ITEMS.register("depth_worm_spawn_egg",
            () -> new DepthWormSpawnEggItem(new Item.Properties()));


    //ИНСТРУМЕНТЫ
    public static final RegistryObject<Item> SCREWDRIVER = ITEMS.register("screwdriver",
            () -> new ScrewdriverItem(new Item.Properties().stacksTo(1).durability(256)));
    public static final RegistryObject<Item> CROWBAR = ITEMS.register("crowbar",
            () -> new Item(new Item.Properties().stacksTo(1).durability(256)));// Прочность как у железных инструментов
    public static final RegistryObject<Item> RANGE_DETONATOR = ITEMS.register("range_detonator",
            () -> new RangeDetonatorItem(new Item.Properties()));
    public static final RegistryObject<Item> MULTI_DETONATOR = ITEMS.register("multi_detonator",
            () -> new MultiDetonatorItem(new Item.Properties()));
    public static final RegistryObject<Item> DETONATOR = ITEMS.register("detonator",
            () -> new DetonatorItem(new Item.Properties()));

    //ОРУЖИЕ
    public static final RegistryObject<Item> MACHINEGUN = ITEMS.register("machinegun",
            () -> new MachineGunItem(new Item.Properties()));
    public static final RegistryObject<Item> TURRET_CHIP = ITEMS.register("turret_chip",
            () -> new TurretChipItem(new Item.Properties()));
    public static final RegistryObject<Item> TURRET_LIGHT_PORTATIVE_PLACER = ITEMS.register("turret_light_portative_placer",
            () -> new TurretLightPortativePlacer(new Item.Properties().stacksTo(1)));

    //БЛОК-АЙТЕМЫ
    public static final RegistryObject<Item> MOTOR_ELECTRO_ITEM = ITEMS.register("motor_electro",
            () -> new MotorElectroBlockItem(ModBlocks.MOTOR_ELECTRO.get(), new Item.Properties()));
    public static final RegistryObject<Item> WIND_GEN_FLUGER = ITEMS.register("wind_gen_fluger",
            () -> new WindGenFlugerBlockItem(ModBlocks.WIND_GEN_FLUGER.get(), new Item.Properties()));
    public static final RegistryObject<Item> SHAFT_IRON_ITEM = ITEMS.register("shaft_iron",
            () -> new ShaftBlockItem(ModBlocks.SHAFT_IRON.get(), new Item.Properties()));
    public static final RegistryObject<Item> SHAFT_WOODEN_ITEM = ITEMS.register("shaft_wooden",
            () -> new ShaftBlockItem(ModBlocks.SHAFT_WOODEN.get(), new Item.Properties()));
    public static final RegistryObject<Item> TURRET_LIGHT_PLACER_ITEM = ITEMS.register("turret_light_placer",
            () -> new TurretLightPlacerBlockItem(ModBlocks.TURRET_LIGHT_PLACER.get(), new Item.Properties()));
    public static final RegistryObject<Item> DRILL_HEAD_ITEM = ITEMS.register("drill_head_item",
            () -> new DrillHeadItem(ModBlocks.DRILL_HEAD.get(), new Item.Properties()));


    //ПАТОРОНЫ
    public static final RegistryObject<Item> AMMO_TURRET = ITEMS.register("ammo_turret",
            () -> new AmmoTurretItem(new Item.Properties(), 8.0f, 3.0f, false));
    public static final RegistryObject<Item> AMMO_TURRET_PIERCING = ITEMS.register("ammo_turret_piercing",
            () -> new AmmoTurretItem(new Item.Properties(), 12.0f, 3.0f, true));
    public static final RegistryObject<Item> AMMO_TURRET_HOLLOW = ITEMS.register("ammo_turret_hollow",
            () -> new AmmoTurretItem(new Item.Properties(), 8.0f, 3.0f, false));
    public static final RegistryObject<Item> AMMO_TURRET_FIRE = ITEMS.register("ammo_turret_fire",
            () -> new AmmoTurretItem(new Item.Properties(), 6.0f, 3.0f, false));
    public static final RegistryObject<Item> AMMO_TURRET_RADIO = ITEMS.register("ammo_turret_radio",
            () -> new AmmoTurretItem(new Item.Properties(), 9.0f, 3.0f, false));


    //БАТАРЕИ

    public static final RegistryObject<Item> ENERGY_CELL_BASIC = ITEMS.register("energy_cell_basic",
            () -> new EnergyCellItem(new Item.Properties().stacksTo(1),
                    1_000_000L,     // capacity
                    5_000L,         // chargingSpeed
                    5_000L));       // unchargingSpeed

    public static final RegistryObject<Item> CREATIVE_BATTERY = ITEMS.register("battery_creative",
            () -> new ItemCreativeBattery(new Item.Properties()));
    public static final RegistryObject<Item> BATTERY = ITEMS.register("battery",
            () -> new ModBatteryItem(new Item.Properties(), 5000, 100, 100));
    public static final RegistryObject<Item> BATTERY_ADVANCED = ITEMS.register("battery_advanced",
            () -> new ModBatteryItem(new Item.Properties(), 20000, 500, 500));
    public static final RegistryObject<Item> BATTERY_LITHIUM = ITEMS.register("battery_lithium",
            () -> new ModBatteryItem(new Item.Properties(), 250000, 1000, 1000));
    public static final RegistryObject<Item> BATTERY_TRIXITE = ITEMS.register("battery_trixite",
            () -> new ModBatteryItem(new Item.Properties(), 5000000, 40000, 200000));
}