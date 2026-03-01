package razchexlitiel.cim.main;

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
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import razchexlitiel.cim.api.energy.EnergyNetworkManager;
import razchexlitiel.cim.api.hive.HiveNetworkManagerProvider;
import razchexlitiel.cim.block.basic.ModBlocks;
import razchexlitiel.cim.block.entity.ModBlockEntities;
import razchexlitiel.cim.capability.ModCapabilities;
import razchexlitiel.cim.entity.ModEntities;
import razchexlitiel.cim.entity.mobs.DepthWormEntity;
import razchexlitiel.cim.entity.weapons.turrets.TurretLightEntity;
import razchexlitiel.cim.event.CrateBreaker;
import razchexlitiel.cim.item.fekal_electric.ModBatteryItem;
import razchexlitiel.cim.menu.ModMenuTypes;
import razchexlitiel.cim.network.ModPacketHandler;
import razchexlitiel.cim.sound.ModSounds;
import razchexlitiel.cim.worldgen.biome.ModSurfaceRules;
import razchexlitiel.cim.worldgen.biome.terrablender.ModOverworldRegion;
import razchexlitiel.cim.worldgen.tree.custom.ModFoliagePlacerTypes;
import razchexlitiel.cim.worldgen.tree.custom.ModTrunkPlacerTypes;
import software.bernie.geckolib.GeckoLib;

import razchexlitiel.cim.item.ModItems;
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
        registerCapabilities(modEventBus);
        ModTrunkPlacerTypes.register(modEventBus);
        ModFoliagePlacerTypes.register(modEventBus);

    }
    private void registerCapabilities(IEventBus modEventBus) {
        modEventBus.addListener(ModCapabilities::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModPacketHandler.register();
            Regions.register(new ModOverworldRegion(new ResourceLocation(MOD_ID, "overworld"), 5));
            SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, "cim", ModSurfaceRules.makeRules());
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel level = event.getServer().overworld(); // или через все миры
            EnergyNetworkManager.get(level).tick();
        }
    }

    @SubscribeEvent
    public static void onServerWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).rebuildAllNetworks();
        }
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Логгирование для отладки
        LOGGER.info("Building creative tab contents for: " + event.getTabKey());

        if (event.getTab() == ModCreativeTabs.CIM_BUILD_TAB.get()) {
            // Concrete (обычный)
            event.accept(ModBlocks.CONCRETE.get());
            event.accept(ModBlocks.CONCRETE_SLAB.get());
            event.accept(ModBlocks.CONCRETE_STAIRS.get());

            // Concrete Red
            event.accept(ModBlocks.CONCRETE_RED.get());
            event.accept(ModBlocks.CONCRETE_RED_SLAB.get());
            event.accept(ModBlocks.CONCRETE_RED_STAIRS.get());

            // Concrete Blue
            event.accept(ModBlocks.CONCRETE_BLUE.get());
            event.accept(ModBlocks.CONCRETE_BLUE_SLAB.get());
            event.accept(ModBlocks.CONCRETE_BLUE_STAIRS.get());

            // Concrete Green
            event.accept(ModBlocks.CONCRETE_GREEN.get());
            event.accept(ModBlocks.CONCRETE_GREEN_SLAB.get());
            event.accept(ModBlocks.CONCRETE_GREEN_STAIRS.get());

            // Concrete Hazard New
            event.accept(ModBlocks.CONCRETE_HAZARD_NEW.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get());

            // Concrete Hazard Old
            event.accept(ModBlocks.CONCRETE_HAZARD_OLD.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get());
            event.accept(ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get());

            // Другие строительные блоки
            event.accept(ModBlocks.CRATE.get());
            event.accept(ModBlocks.CRATE_AMMO.get());
        }


        if (event.getTab() == ModCreativeTabs.CIM_TECH_TAB.get()) {

            event.accept(ModBlocks.SHAFT_WOODEN);
            event.accept(ModBlocks.SHAFT_IRON);
            event.accept(ModBlocks.MOTOR_ELECTRO);
            event.accept(ModBlocks.WIND_GEN_FLUGER);
            event.accept(ModBlocks.GEAR_PORT);
            event.accept(ModBlocks.RCONVERTER);
            event.accept(ModBlocks.ADDER);
            event.accept(ModBlocks.STOPPER);
            event.accept(ModBlocks.TACHOMETER);
            event.accept(ModBlocks.ROTATION_METER);

            event.accept(ModItems.CREATIVE_BATTERY);
            event.accept(ModBlocks.MACHINE_BATTERY);
            event.accept(ModBlocks.MACHINE_BATTERY_LITHIUM);
            event.accept(ModBlocks.WIRE_COATED);
            event.accept(ModBlocks.SWITCH);
            event.accept(ModBlocks.CONVERTER_BLOCK);

            List<RegistryObject<Item>> batteriesToAdd = List.of(
                    ModItems.BATTERY,
                    ModItems.BATTERY_ADVANCED,
                    ModItems.BATTERY_LITHIUM,
                    ModItems.BATTERY_TRIXITE
            );

            for (RegistryObject<Item> batteryRegObj : batteriesToAdd) {
                Item item = batteryRegObj.get();
                if (item instanceof ModBatteryItem batteryItem) {
                    ItemStack emptyStack = new ItemStack(batteryItem);
                    event.accept(emptyStack);
                    ItemStack chargedStack = new ItemStack(batteryItem);
                    ModBatteryItem.setEnergy(chargedStack, batteryItem.getCapacity());
                    event.accept(chargedStack);
                }
            }
        }


        if (event.getTab() == ModCreativeTabs.CIM_WEAPONS_TAB.get()) {

            event.accept(ModBlocks.DET_MINER);
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

        if (event.getTab() == ModCreativeTabs.CIM_TOOLS_TAB.get()) {

            event.accept(ModItems.SCREWDRIVER.get());
            event.accept(ModItems.CROWBAR.get());

            event.accept(ModItems.DETONATOR);
            event.accept(ModItems.MULTI_DETONATOR);
            event.accept(ModItems.RANGE_DETONATOR);

        }

        if (event.getTab() == ModCreativeTabs.CIM_NATURE_TAB.get()) {

            event.accept(ModBlocks.SEQUOIA_PLANKS.get());
            event.accept(ModBlocks.SEQUOIA_BARK.get());
            event.accept(ModBlocks.SEQUOIA_HEARTWOOD.get());
            event.accept(ModBlocks.SEQUOIA_LEAVES.get());
            event.accept(ModBlocks.WASTE_LOG.get());
            event.accept(ModBlocks.NECROSIS_PORTAL.get());
            event.accept(ModBlocks.NECROSIS_TEST.get());
            event.accept(ModBlocks.NECROSIS_TEST2.get());
            event.accept(ModBlocks.NECROSIS_TEST3.get());
            event.accept(ModBlocks.NECROSIS_TEST4.get());
            event.accept(ModItems.DEPTH_WORM_SPAWN_EGG);
            event.accept(ModBlocks.DEPTH_WORM_NEST);
            event.accept(ModBlocks.HIVE_SOIL);
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
    public void onAttachCapabilitiesLevel(AttachCapabilitiesEvent<Level> event) {
        // Проверка, чтобы не прикрепить дважды
        if (!event.getObject().isClientSide) {
            event.addCapability(new ResourceLocation("cim", "hive_network_manager"),
                    new HiveNetworkManagerProvider());
        }
    }

}