package com.cim.item;

import com.cim.entity.ModEntities;
import com.cim.entity.weapons.grenades.GrenadeIfType;
import com.cim.entity.weapons.grenades.GrenadeType;
import com.cim.item.energy.EnergyCellItem;
import com.cim.item.food.FoodZamaz;
import com.cim.event.SlagItem;
import com.cim.item.mobs.MoryLahItem;
import com.cim.item.energy.WireCoilItem;
import com.cim.item.rotation.*;
import com.cim.item.tools.*;
import com.cim.item.weapons.grenades.GrenadeIfItem;
import com.cim.item.weapons.grenades.GrenadeItem;
import com.cim.item.weapons.grenades.GrenadeNucItem;
import com.cim.multiblock.system.MultiblockBlockItem;
import net.minecraft.world.effect.MobEffects;
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


    public static final RegistryObject<Item> SLAG = ITEMS.register("slag",
            () -> new SlagItem(new Item.Properties()));
    public static final RegistryObject<Item> POKER = ITEMS.register("poker",
            () -> new PokerItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(128)));
    //ИНСТРУМЕНТЫ
    public static final RegistryObject<Item> SCREWDRIVER = ITEMS.register("screwdriver",
            () -> new ScrewdriverItem(new Item.Properties().stacksTo(1).durability(256)));

    public static final RegistryObject<Item> CROWBAR = ITEMS.register("crowbar",
            () -> new Item(new Item.Properties().stacksTo(1).durability(256)));
    // Прочность как у железных инструментов
    public static final RegistryObject<Item> RANGE_DETONATOR = ITEMS.register("range_detonator",
            () -> new RangeDetonatorItem(new Item.Properties()));

    public static final RegistryObject<Item> MULTI_DETONATOR = ITEMS.register("multi_detonator",
            () -> new MultiDetonatorItem(new Item.Properties()));

    public static final RegistryObject<Item> DETONATOR = ITEMS.register("detonator",
            () -> new DetonatorItem(new Item.Properties()));

    public static final RegistryObject<Item> FIREBRICK = ITEMS.register("firebrick",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> REINFORCEDBRICK = ITEMS.register("reinforcedbrick",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> CAST_PICKAXE_IRON = ITEMS.register("cast_pickaxe_iron",
            () -> new CastPickaxeIronItem(new Item.Properties()));

    public static final RegistryObject<Item> MOLD_INGOT = ITEMS.register("mold_ingot",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> MOLD_NUGGET = ITEMS.register("mold_nugget",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> MOLD_BLOCK= ITEMS.register("mold_block",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> WIRE_COIL = ITEMS.register("wire_coil",
            () -> new WireCoilItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BEAM_PLACER = ITEMS.register("beam_placer",
            () -> new BeamPlacerItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> FLUID_IDENTIFIER = ITEMS.register("fluid_identifier",
            () -> new FluidIdentifierItem(new Item.Properties()));

    public static final RegistryObject<Item> FUEL_ASH = ITEMS.register("fuel_ash",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> PROTECTOR_LEAD = ITEMS.register("protector_lead",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PROTECTOR_STEEL = ITEMS.register("protector_steel",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PROTECTOR_TUNGSTEN = ITEMS.register("protector_tungsten",
            () -> new Item(new Item.Properties().stacksTo(1)));


    public static final RegistryObject<Item> INFINITE_FLUID_BARREL = ITEMS.register("infinite_fluid_barrel",
            () -> new com.cim.item.tools.InfiniteFluidBarrelItem(new Item.Properties()));



    //ОРУЖИЕ
    public static final RegistryObject<Item> MACHINEGUN = ITEMS.register("machinegun",
            () -> new MachineGunItem(new Item.Properties()));

    public static final RegistryObject<Item> TURRET_CHIP = ITEMS.register("turret_chip",
            () -> new TurretChipItem(new Item.Properties()));

    public static final RegistryObject<Item> TURRET_LIGHT_PORTATIVE_PLACER = ITEMS.register("turret_light_portative_placer",
            () -> new TurretLightPortativePlacer(new Item.Properties().stacksTo(1)));





    //БЛОК-АЙТЕМЫ
//    public static final RegistryObject<Item> MOTOR_ELECTRO_ITEM = ITEMS.register("motor_electro",
//            () -> new MotorElectroBlockItem(ModBlocks.MOTOR_ELECTRO.get(), new Item.Properties()));
//
//    public static final RegistryObject<Item> WIND_GEN_FLUGER = ITEMS.register("wind_gen_fluger",
//            () -> new WindGenFlugerBlockItem(ModBlocks.WIND_GEN_FLUGER.get(), new Item.Properties()));
//
//    public static final RegistryObject<Item> SHAFT_IRON_ITEM = ITEMS.register("shaft_iron",
//            () -> new ShaftBlockItem(ModBlocks.SHAFT_IRON.get(), new Item.Properties()));
//
//    public static final RegistryObject<Item> SHAFT_WOODEN_ITEM = ITEMS.register("shaft_wooden",
//            () -> new ShaftBlockItem(ModBlocks.SHAFT_WOODEN.get(), new Item.Properties()));
//
  public static final RegistryObject<Item> TURRET_LIGHT_PLACER_ITEM = ITEMS.register("turret_light_placer",
           () -> new TurretLightPlacerBlockItem(ModBlocks.TURRET_LIGHT_PLACER.get(), new Item.Properties()));
//
//    public static final RegistryObject<Item> DRILL_HEAD_ITEM = ITEMS.register("drill_head_item",
//            () -> new DrillHeadItem(ModBlocks.DRILL_HEAD.get(), new Item.Properties()));

    public static final RegistryObject<Item> HEATER_ITEM = ITEMS.register("heater",
            () -> new MultiblockBlockItem(ModBlocks.HEATER.get(), new Item.Properties()));




    //ПАТРОНЫ
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




    //ГРАНАТЫ
    public static final RegistryObject<Item> GRENADE = ITEMS.register("grenade",
            () -> new GrenadeItem(new Item.Properties(), GrenadeType.STANDARD, ModEntities.GRENADE_PROJECTILE));

    public static final RegistryObject<Item> GRENADEHE = ITEMS.register("grenadehe",
            () -> new GrenadeItem(new Item.Properties(), GrenadeType.HE, ModEntities.GRENADEHE_PROJECTILE));

    public static final RegistryObject<Item> GRENADEFIRE = ITEMS.register("grenadefire",
            () -> new GrenadeItem(new Item.Properties(), GrenadeType.FIRE, ModEntities.GRENADEFIRE_PROJECTILE));

    public static final RegistryObject<Item> GRENADESLIME = ITEMS.register("grenadeslime",
            () -> new GrenadeItem(new Item.Properties(), GrenadeType.SLIME, ModEntities.GRENADESLIME_PROJECTILE));

    public static final RegistryObject<Item> GRENADESMART = ITEMS.register("grenadesmart",
            () -> new GrenadeItem(new Item.Properties(), GrenadeType.SMART, ModEntities.GRENADESMART_PROJECTILE));

    public static final RegistryObject<Item> GRENADE_IF = ITEMS.register("grenade_if",
            () -> new GrenadeIfItem(new Item.Properties(), GrenadeIfType.GRENADE_IF, ModEntities.GRENADE_IF_PROJECTILE));

    public static final RegistryObject<Item> GRENADE_IF_HE = ITEMS.register("grenade_if_he",
            () -> new GrenadeIfItem(new Item.Properties(), GrenadeIfType.GRENADE_IF_HE, ModEntities.GRENADE_IF_HE_PROJECTILE));

    public static final RegistryObject<Item> GRENADE_IF_SLIME = ITEMS.register("grenade_if_slime",
            () -> new GrenadeIfItem(new Item.Properties(), GrenadeIfType.GRENADE_IF_SLIME, ModEntities.GRENADE_IF_SLIME_PROJECTILE));

    public static final RegistryObject<Item> GRENADE_IF_FIRE = ITEMS.register("grenade_if_fire",
            () -> new GrenadeIfItem(new Item.Properties(), GrenadeIfType.GRENADE_IF_FIRE, ModEntities.GRENADE_IF_FIRE_PROJECTILE));

    public static final RegistryObject<Item> GRENADE_NUC = ITEMS.register("grenade_nuc",
            () -> new GrenadeNucItem(new Item.Properties(), ModEntities.GRENADE_NUC_PROJECTILE));

    public static final RegistryObject<Item> MORY_LAH = ITEMS.register("mory_lah",
            () -> new MoryLahItem(new Item.Properties()));




    //ЕДА
    public static final RegistryObject<Item> MORY_FOOD = ITEMS.register("mory_food",
            () -> new FoodZamaz(new FoodZamaz.Builder()
                    .nutrition(4)
                    .saturation(2.0F)));
    public static final RegistryObject<Item> COFFEE = ITEMS.register("coffee",
            () -> new FoodZamaz(new FoodZamaz.Builder()
                    .nutrition(2)
                    .saturation(0.5F)
                    .alwaysEat()
                    .eatDuration(16)
                    .effect(MobEffects.MOVEMENT_SPEED, 30, 1)));





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