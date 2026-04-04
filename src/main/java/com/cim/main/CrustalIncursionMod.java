package com.cim.main;


import com.cim.api.fluids.ModFluids;
import com.cim.api.hive.HiveNetworkManager;
import com.cim.api.metallurgy.ModMetallurgy;
import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.event.SlagItem;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import com.cim.api.hive.HiveNetworkManagerProvider;
import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.ModBlockEntities;
import com.cim.capability.ModCapabilities;
import com.cim.entity.ModEntities;
import com.cim.entity.mobs.DepthWormEntity;
import com.cim.entity.weapons.turrets.TurretLightEntity;
import com.cim.event.CrateBreaker;
import com.cim.item.energy.ModBatteryItem;
import com.cim.menu.ModMenuTypes;
import com.cim.network.ModPacketHandler;
import com.cim.sound.ModSounds;
import com.cim.worldgen.biome.ModSurfaceRules;
import com.cim.worldgen.biome.terrablender.ModOverworldRegion;
import com.cim.worldgen.tree.custom.ModFoliagePlacerTypes;
import com.cim.worldgen.tree.custom.ModTrunkPlacerTypes;
import software.bernie.geckolib.GeckoLib;

import com.cim.item.ModItems;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

import java.util.List;

@Mod(CrustalIncursionMod.MOD_ID)
public class CrustalIncursionMod {
    public static final String MOD_ID = "cim";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CrustalIncursionMod() {
        LOGGER.info("Initializing Crustal Incursion...");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModCreativeTabs.register(modEventBus);
        GeckoLib.initialize();
        this.registerCapabilities(modEventBus);
        ResourceRegistry.init();
        ModBlocks.register(modEventBus); // 1. Сначала блоки
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(new CrateBreaker());
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModSounds.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        modEventBus.addListener(this::entityAttributeEvent);
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::addCreative);
        ModTrunkPlacerTypes.register(modEventBus);
        ModFoliagePlacerTypes.register(modEventBus);
        ModFluids.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(new HiveEventHandler());

    }
    private void registerCapabilities(IEventBus modEventBus) {
        modEventBus.addListener(ModCapabilities::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModMetallurgy.init();          // <-- регистрация металлов и рецептов
            ModPacketHandler.register();
            Regions.register(new ModOverworldRegion(new ResourceLocation(MOD_ID, "overworld"), 5));
            SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, "cim", ModSurfaceRules.makeRules());
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Логгирование для отладки
        LOGGER.info("Building creative tab contents for: " + event.getTabKey());
        ResourceRegistry.addCreative(event);
        if (event.getTab() == ModCreativeTabs.CIM_BUILD_TAB.get()) {

            event.accept(ModBlocks.CONCRETE.get());
            event.accept(ModBlocks.CONCRETE_SLAB.get());
            event.accept(ModBlocks.CONCRETE_STAIRS.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_NEW.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_OLD.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get());
            event.accept(ModBlocks.CONCRETE_TILE.get());
            event.accept(ModBlocks.CONCRETE_TILE_SLAB.get());
            event.accept(ModBlocks.CONCRETE_TILE_STAIRS.get());
            event.accept(ModBlocks.CONCRETE_TILE_ALT.get());
            event.accept(ModBlocks.CONCRETE_TILE_ALT_SLAB.get());
            event.accept(ModBlocks.CONCRETE_TILE_ALT_STAIRS.get());
            event.accept(ModBlocks.CONCRETE_TILE_ALT_BLUE.get());
            event.accept(ModBlocks.CONCRETE_TILE_ALT_BLUE_SLAB.get());
            event.accept(ModBlocks.CONCRETE_TILE_ALT_BLUE_STAIRS.get());
            event.accept(ModBlocks.CONCRETE_STRIPPED.get());
            event.accept(ModBlocks.CONCRETE_STRIPPED_SLAB.get());
            event.accept(ModBlocks.CONCRETE_STRIPPED_STAIRS.get());
            event.accept(ModBlocks.CONCRETE_REINFORCED.get());
            event.accept(ModBlocks.CONCRETE_REINFORCED_SLAB.get());
            event.accept(ModBlocks.CONCRETE_REINFORCED_STAIRS.get());
            event.accept(ModBlocks.CONCRETE_REINFORCED_HEAVY.get());
            event.accept(ModBlocks.CONCRETE_REINFORCED_HEAVY_SLAB.get());
            event.accept(ModBlocks.CONCRETE_REINFORCED_HEAVY_STAIRS.get());

            event.accept(ModBlocks.FIREBRICK_BLOCK.get());
            event.accept(ModBlocks.FIREBRICK_SLAB.get());
            event.accept(ModBlocks.FIREBRICK_STAIRS.get());

            event.accept(ModBlocks.REINFORCEDBRICK_BLOCK.get());
            event.accept(ModBlocks.REINFORCEDBRICK_SLAB.get());
            event.accept(ModBlocks.REINFORCEDBRICK_STAIRS.get());

            event.accept(ModBlocks.SEQUOIA_PLANKS.get());
            event.accept(ModBlocks.SEQUOIA_SLAB.get());
            event.accept(ModBlocks.SEQUOIA_STAIRS.get());

            event.accept(ModBlocks.MORY_BLOCK);
            event.accept(ModBlocks.ANTON_CHIGUR);

            event.accept(ModBlocks.CONCRETE_NET.get());
            event.accept(ModBlocks.DECO_STEEL.get());
            event.accept(ModBlocks.DECO_STEEL_DARK.get());
            event.accept(ModBlocks.DECO_STEEL_SMOG.get());
            event.accept(ModBlocks.DECO_LEAD.get());
            event.accept(ModBlocks.DECO_BEAM.get());
            event.accept(ModBlocks.BEAM_BLOCK.get());
            event.accept(ModBlocks.STEEL_PROPS.get());
            // Другие строительные блоки
            event.accept(ModBlocks.CRATE.get());
            event.accept(ModBlocks.CRATE_AMMO.get());


        }


        if (event.getTab() == ModCreativeTabs.CIM_TECH_TAB.get()) {

            event.accept(ModItems.CROWBAR.get());
            event.accept(ModItems.BEAM_PLACER.get());
            event.accept(ModItems.SCREWDRIVER.get());
            event.accept(ModBlocks.SHAFT_WOODEN);
            event.accept(ModBlocks.SHAFT_IRON);
            event.accept(ModBlocks.DRILL_HEAD);
            event.accept(ModBlocks.MOTOR_ELECTRO);
            event.accept(ModBlocks.WIND_GEN_FLUGER);
            event.accept(ModBlocks.GEAR_PORT);
            event.accept(ModBlocks.RCONVERTER);
            event.accept(ModBlocks.ADDER);
            event.accept(ModBlocks.STOPPER);
            event.accept(ModBlocks.TACHOMETER);
            event.accept(ModBlocks.ROTATION_METER);
            event.accept(ModBlocks.SHAFT_PLACER);
            event.accept(ModBlocks.MINING_PORT);

            event.accept(ModItems.WIRE_COIL);
            event.accept(ModBlocks.CONNECTOR);
            event.accept(ModBlocks.MEDIUM_CONNECTOR);
            event.accept(ModBlocks.LARGE_CONNECTOR);
            event.accept(ModBlocks.WIRE_COATED);
            event.accept(ModBlocks.SWITCH);
            event.accept(ModBlocks.CONVERTER_BLOCK);
            event.accept(ModBlocks.MACHINE_BATTERY);
            event.accept(ModItems.ENERGY_CELL_BASIC);


            event.accept(ModItems.CREATIVE_BATTERY);
            List<RegistryObject<Item>> batteriesToAdd = List.of(
                    ModItems.BATTERY,
                    ModItems.BATTERY_ADVANCED,
                    ModItems.BATTERY_LITHIUM,
                    ModItems.BATTERY_TRIXITE
            ); for (RegistryObject<Item> batteryRegObj : batteriesToAdd) {Item item = batteryRegObj.get();if (item instanceof ModBatteryItem batteryItem) {ItemStack emptyStack = new ItemStack(batteryItem);event.accept(emptyStack);ItemStack chargedStack = new ItemStack(batteryItem);ModBatteryItem.setEnergy(chargedStack, batteryItem.getCapacity());event.accept(chargedStack);}}

            event.accept(ModItems.FLUID_IDENTIFIER.get());
            event.accept(ModItems.INFINITE_FLUID_BARREL);
            event.accept(ModItems.PROTECTOR_STEEL);
            event.accept(ModItems.PROTECTOR_LEAD);
            event.accept(ModItems.PROTECTOR_TUNGSTEN);
            event.accept(ModBlocks.FLUID_BARREL);
            event.accept(ModBlocks.BRONZE_FLUID_PIPE);
            event.accept(ModBlocks.STEEL_FLUID_PIPE);
            event.accept(ModBlocks.LEAD_FLUID_PIPE);
            event.accept(ModBlocks.TUNGSTEN_FLUID_PIPE);

            event.accept(ModItems.HEATER_ITEM);
            event.accept(ModBlocks.SMELTER);
            event.accept(ModBlocks.CASTING_POT);
            event.accept(ModBlocks.CASTING_DESCENT);
            event.accept(ModItems.MOLD_NUGGET.get());
            event.accept(ModItems.MOLD_INGOT.get());
            event.accept(ModItems.MOLD_BLOCK.get());
            event.accept(ModItems.POKER.get());
        }


        if (event.getTab() == ModCreativeTabs.CIM_WEAPONS_TAB.get()) {
            event.accept(ModItems.CAST_PICKAXE_IRON);

            event.accept(ModBlocks.DET_MINER);
            event.accept(ModItems.DETONATOR);
            event.accept(ModItems.MULTI_DETONATOR);
            event.accept(ModItems.RANGE_DETONATOR);
            event.accept(ModItems.MORY_LAH);
            event.accept(ModItems.GRENADE);
            event.accept(ModItems.GRENADEHE);
            event.accept(ModItems.GRENADEFIRE);
            event.accept(ModItems.GRENADESMART);
            event.accept(ModItems.GRENADESLIME);
            event.accept(ModItems.GRENADE_IF);
            event.accept(ModItems.GRENADE_IF_HE);
            event.accept(ModItems.GRENADE_IF_SLIME);
            event.accept(ModItems.GRENADE_IF_FIRE);
            event.accept(ModItems.GRENADE_NUC);
            event.accept(ModItems.TURRET_CHIP);
            event.accept(ModItems.TURRET_LIGHT_PORTATIVE_PLACER);
            event.accept(ModItems.MACHINEGUN);
            event.accept(ModBlocks.TURRET_LIGHT_PLACER);
            event.accept(ModItems.AMMO_TURRET);
            event.accept(ModItems.AMMO_TURRET_HOLLOW);
            event.accept(ModItems.AMMO_TURRET_PIERCING);
            event.accept(ModItems.AMMO_TURRET_FIRE);
            event.accept(ModItems.AMMO_TURRET_RADIO);


        }

        if (event.getTab() == ModCreativeTabs.CIM_RECOURSES_TAB.get()) {

            event.accept(ModItems.FIREBRICK.get());
            event.accept(ModItems.REINFORCEDBRICK.get());
            event.accept(ModItems.FUEL_ASH.get());

            if (event.getTab() == ModCreativeTabs.CIM_RECOURSES_TAB.get()) {
                event.accept(ModItems.FIREBRICK.get());
                event.accept(ModItems.REINFORCEDBRICK.get());
                event.accept(ModItems.FUEL_ASH.get());

                // Добавляем демонстрационный шлак для каждого металла (1 слиток = 9 единиц)
                for (Metal metal : MetallurgyRegistry.getAllMetals()) {
                    ItemStack slagStack = SlagItem.createSlag(metal, MetalUnits2.UNITS_PER_INGOT);
                    event.accept(slagStack);
                }
            }
        }

        if (event.getTab() == ModCreativeTabs.CIM_NATURE_TAB.get()) {

            event.accept(ModBlocks.SEQUOIA_BARK.get());
            event.accept(ModBlocks.SEQUOIA_HEARTWOOD.get());
            event.accept(ModBlocks.SEQUOIA_LEAVES.get());
            event.accept(ModBlocks.WASTE_LOG.get());
            event.accept(ModBlocks.NECROSIS_PORTAL.get());
            event.accept(ModBlocks.NECROSIS_TEST.get());
            event.accept(ModBlocks.NECROSIS_TEST2.get());
            event.accept(ModBlocks.NECROSIS_TEST3.get());
            event.accept(ModBlocks.NECROSIS_TEST4.get());
            event.accept(ModBlocks.DIRT_ROUGH.get());
            event.accept(ModBlocks.BASALT_ROUGH.get());
            event.accept(ModItems.DEPTH_WORM_SPAWN_EGG);
            event.accept(ModBlocks.DEPTH_WORM_NEST);
            event.accept(ModBlocks.HIVE_SOIL);
            event.accept(ModBlocks.HIVE_ROOTS.get()); // Обычная версия
            event.accept(ModBlocks.DEPTH_WORM_NEST_DEAD);
            event.accept(ModBlocks.HIVE_SOIL_DEAD);
        }

       }

    // Метод регистрации атрибутов (здоровье, урон и т.д.)
    private void entityAttributeEvent(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntities.DEPTH_WORM.get(), DepthWormEntity.createAttributes().build());
        event.put(ModEntities.TURRET_LIGHT.get(), TurretLightEntity.createAttributes().build());
        event.put(ModEntities.TURRET_LIGHT_LINKED.get(), TurretLightEntity.createAttributes().build());
    }
    @SubscribeEvent
    public static void onEntitySpawn(MobSpawnEvent.FinalizeSpawn event) {
        Level level = (Level) event.getLevel();
        // Если мы в Некрозе
        if (level.dimension().location().getPath().equals("necrosis")) {
            double spawnY = event.getY();
            Player nearestPlayer = level.getNearestPlayer(event.getX(), spawnY, event.getZ(), 128, false);

            // Если игрок далеко по вертикали (больше 50 блоков) - отменяем спавн
            if (nearestPlayer != null && Math.abs(nearestPlayer.getY() - spawnY) > 50) {
                event.setSpawnCancelled(true);
            }
        }
    }
    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<Level> event) {
        if (!event.getObject().isClientSide) {
            event.addCapability(new ResourceLocation("cim", "hive_network_manager"),
                    new HiveNetworkManagerProvider());
            System.out.println("DEBUG: Capability Attached to Level!");
        }
    }


    @Mod.EventBusSubscriber(modid = "cim", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public class HiveEventHandler {
        @SubscribeEvent
        public static void onWorldTick(TickEvent.LevelTickEvent event) {
            // Обязательно проверяем сторону (Server) и фазу (END)
            if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
                HiveNetworkManager manager = HiveNetworkManager.get(serverLevel);
                if (manager != null) {
                    manager.tick(serverLevel);
                }
            }
        }
    }
}