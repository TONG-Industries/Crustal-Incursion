package razchexlitiel.cim.block.basic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import razchexlitiel.cim.block.basic.direction.FullOBlock;
import razchexlitiel.cim.block.basic.energy.ConverterBlock;
import razchexlitiel.cim.block.basic.energy.MachineBatteryBlock;
import razchexlitiel.cim.block.basic.energy.SwitchBlock;
import razchexlitiel.cim.block.basic.energy.WireBlock;
import razchexlitiel.cim.block.basic.explosives.DetMinerBlock;
import razchexlitiel.cim.block.basic.hive.DepthWormNestBlock;
import razchexlitiel.cim.block.basic.hive.HiveSoilBlock;
import razchexlitiel.cim.block.basic.rotation.*;
import razchexlitiel.cim.block.basic.weapons.TurretLightPlacerBlock;
import razchexlitiel.cim.item.fekal_electric.MachineBatteryBlockItem;
import razchexlitiel.cim.main.CrustalIncursionMod;
import razchexlitiel.cim.item.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CrustalIncursionMod.MOD_ID);

    //ЭНЕРГОСЕТЬ
    public static final List<RegistryObject<Block>> BATTERY_BLOCKS = new ArrayList<>();
    public static final RegistryObject<Block> MACHINE_BATTERY = registerBattery
            ("machine_battery", 1_000_000L);
    public static final RegistryObject<Block> MACHINE_BATTERY_LITHIUM = registerBattery
            ("machine_battery_lithium", 50_000_000L);
    public static final RegistryObject<Block> CONVERTER_BLOCK = registerBlock("converter_block",
            () -> new ConverterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Block> WIRE_COATED = registerBlock("wire_coated",
            () -> new WireBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final RegistryObject<Block> SWITCH = registerBlock("switch",
            () -> new SwitchBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));

    //ВЗРЫВЧАТКА
    public static final RegistryObject<Block> DET_MINER = registerBlock("det_miner",
            () -> new DetMinerBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    //ТУРЕЛИ
    public static final RegistryObject<Block> TURRET_LIGHT_PLACER = BLOCKS.register("turret_light_placer",
            () -> new TurretLightPlacerBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(5.0f, 4.0f).noOcclusion().requiresCorrectToolForDrops()));

    //БЛОКИ УЛЬЯ
    public static final RegistryObject<Block> DEPTH_WORM_NEST = registerBlock("depth_worm_nest",
            () -> new DepthWormNestBlock(BlockBehaviour.Properties.copy(Blocks.MUD).sound(SoundType.MUD)));
    public static final RegistryObject<Block> HIVE_SOIL = registerBlock("hive_soil",
            () -> new HiveSoilBlock(BlockBehaviour.Properties.copy(Blocks.MUD).sound(SoundType.MUD)));


    //ОБЫЧНЫЕ БЛОКИ
    public static final RegistryObject<Block> CRATE = registerBlock("crate",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BARREL).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> CRATE_AMMO = registerBlock("crate_ammo",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BARREL).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> CONCRETE = registerBlock("concrete",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_RED = registerBlock("concrete_red",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_BLUE = registerBlock("concrete_blue",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_GREEN = registerBlock("concrete_green",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_HAZARD_NEW = registerBlock("concrete_hazard_new",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_HAZARD_OLD = registerBlock("concrete_hazard_old",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> NECROSIS_TEST = registerBlock("necrosis_test",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> NECROSIS_TEST2 = registerBlock("necrosis_test2",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> NECROSIS_TEST3 = registerBlock("necrosis_test3",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> NECROSIS_TEST4 = registerBlock("necrosis_test4",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> WASTE_LOG = registerBlock("waste_log",
            () -> new FullOBlock(BlockBehaviour.Properties.copy(Blocks.OAK_WOOD).sound(SoundType.WOOD)));


    //СТУПЕНИ И ПОЛУБЛОКИ
    public static final RegistryObject<StairBlock> CONCRETE_STAIRS = registerBlock("concrete_stairs",
            () -> new StairBlock(CONCRETE.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_SLAB = registerBlock("concrete_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE.get())));
    public static final RegistryObject<StairBlock> CONCRETE_RED_STAIRS = registerBlock("concrete_red_stairs",
            () -> new StairBlock(CONCRETE_RED.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_RED.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_RED_SLAB = registerBlock("concrete_red_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_RED.get())));
    public static final RegistryObject<StairBlock> CONCRETE_BLUE_STAIRS = registerBlock("concrete_blue_stairs",
            () -> new StairBlock(CONCRETE_BLUE.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_BLUE.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_BLUE_SLAB = registerBlock("concrete_blue_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_BLUE.get())));
    public static final RegistryObject<StairBlock> CONCRETE_GREEN_STAIRS = registerBlock("concrete_green_stairs",
            () -> new StairBlock(CONCRETE_GREEN.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_GREEN.get())));


    public static final RegistryObject<SlabBlock> CONCRETE_GREEN_SLAB = registerBlock("concrete_green_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_GREEN.get())));
    public static final RegistryObject<StairBlock> CONCRETE_HAZARD_NEW_STAIRS = registerBlock("concrete_hazard_new_stairs",
            () -> new StairBlock(CONCRETE_HAZARD_NEW.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_HAZARD_NEW.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_HAZARD_NEW_SLAB = registerBlock("concrete_hazard_new_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_HAZARD_NEW.get())));
    public static final RegistryObject<StairBlock> CONCRETE_HAZARD_OLD_STAIRS = registerBlock("concrete_hazard_old_stairs",
            () -> new StairBlock(CONCRETE_HAZARD_OLD.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_HAZARD_OLD.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_HAZARD_OLD_SLAB = registerBlock("concrete_hazard_old_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_HAZARD_OLD.get())));


    // ДВЕРИ
    public static final RegistryObject<Block> SEQUOIA_DOOR = registerBlock("sequoia_door",
            () -> new net.minecraft.world.level.block.DoorBlock(
                    BlockBehaviour.Properties.copy(Blocks.DARK_OAK_DOOR).sound(SoundType.WOOD).noOcclusion(),
                    BlockSetType.DARK_OAK));

    // ЛЮКИ
    public static final RegistryObject<Block> SEQUOIA_TRAPDOOR = registerBlock("sequoia_trapdoor",
            () -> new net.minecraft.world.level.block.TrapDoorBlock( // <--- ВОТ ТУТ ИСПРАВЬ
                    BlockBehaviour.Properties.copy(Blocks.DARK_OAK_DOOR).sound(SoundType.WOOD).noOcclusion(),
                    BlockSetType.DARK_OAK));


    //БЛОКИ-ВРАЩЕНИЯ
    public static final RegistryObject<Block> MOTOR_ELECTRO = BLOCKS.register("motor_electro",
            () -> new MotorElectroBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 4.0f).noOcclusion().requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> WIND_GEN_FLUGER = BLOCKS.register("wind_gen_fluger",
            () -> new WindGenFlugerBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 4.0f).noOcclusion().requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SHAFT_IRON = BLOCKS.register("shaft_iron",
            () -> new ShaftBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 4.0f).noOcclusion().requiresCorrectToolForDrops(),
                    new ShaftType(300, 150, "shaft_iron",
                            "geo/shaft_iron.geo.json",
                            "animations/shaft_iron.animation.json")));
    public static final RegistryObject<Block> SHAFT_WOODEN = BLOCKS.register("shaft_wooden",
            () -> new ShaftBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .strength(2.0f, 3.0f).noOcclusion(),
                    new ShaftType(150, 75, "shaft_wooden",
                            "geo/shaft_wooden.geo.json",
                            "animations/shaft_wooden.animation.json")));
    public static final RegistryObject<Block> GEAR_PORT = registerBlock("gear_port",
            () -> new GearPortBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(5.0f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> STOPPER = registerBlock("stopper",
            () -> new StopperBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(5.0f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ADDER = registerBlock("adder",
            () -> new AdderBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(5.0f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> TACHOMETER = registerBlock("tachometer",
            () -> new TachometerBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(5.0f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ROTATION_METER = registerBlock("rotation_meter",
            () -> new RotationMeterBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(5.0f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> RCONVERTER = registerBlock("rconverter",
            () -> new RConverterBlock(BlockBehaviour.Properties.copy(Blocks.STONE)
                    .strength(5.0f, 4.0f).requiresCorrectToolForDrops()));

    //СИКВОЯ
    public static final RegistryObject<Block> SEQUOIA_BARK = registerBlock("sequoia_bark",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> SEQUOIA_HEARTWOOD = registerBlock("sequoia_heartwood",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> SEQUOIA_PLANKS  = registerBlock("sequoia_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(0.5f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SEQUOIA_ROOTS  = registerBlock("sequoia_roots",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(0.5f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SEQUOIA_ROOTS_MOSSY  = registerBlock("sequoia_roots_mossy",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(0.5f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SEQUOIA_BARK_DARK = registerBlock("sequoia_bark_dark",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> SEQUOIA_BARK_LIGHT = registerBlock("sequoia_bark_light",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> SEQUOIA_BARK_MOSSY = registerBlock("sequoia_bark_mossy",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> SEQUOIA_BIOME_MOSS = registerBlock("sequoia_biome_moss",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.MOSS_BLOCK)));
    public static final RegistryObject<Block> SEQUOIA_LEAVES = registerBlock("sequoia_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.copy(Blocks.SPRUCE_LEAVES).noOcclusion()
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)) {
                @Override
                public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return true;
                }
                @Override
                public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 60; // Как быстро сгорает
                }
                @Override
                public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 30; // Шанс, что огонь перекинется на этот блок
                }});





    // Вспомогательный метод: регистрирует блок И предмет для него
    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    // Регистрация предмета блока (чтобы он был в инвентаре)
    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static RegistryObject<Block> registerBattery(String name, long capacity) {
        // 1. Регистрируем БЛОК
        RegistryObject<Block> batteryBlock = BLOCKS.register(name,
                () -> new MachineBatteryBlock(Block.Properties.of().strength(5.0f).requiresCorrectToolForDrops(), capacity));

        // 2. Регистрируем ПРЕДМЕТ (MachineBatteryBlockItem)
        ModItems.ITEMS.register(name,
                () -> new MachineBatteryBlockItem(batteryBlock.get(), new Item.Properties(), capacity));

        // 3. Добавляем в список для TileEntity
        BATTERY_BLOCKS.add(batteryBlock);

        return batteryBlock;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}