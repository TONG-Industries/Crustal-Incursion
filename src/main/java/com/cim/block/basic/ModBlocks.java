package com.cim.block.basic;

import com.cim.api.energy.ConnectorTier;
import com.cim.api.fluids.PipeTier;
import com.cim.api.rotation.ShaftDiameter;
import com.cim.api.rotation.ShaftMaterial;
import com.cim.block.basic.deco.BeamBlock;
import com.cim.block.basic.deco.BeamCollisionBlock;
import com.cim.block.basic.deco.SteelPropsBlock;
import com.cim.block.basic.fluids.FluidBarrelBlock;

import com.cim.block.basic.fluids.FluidPipeBlock;

import com.cim.block.basic.industrial.casting.CastingDescentBlock;
import com.cim.block.basic.industrial.casting.CastingPotBlock;
import com.cim.block.basic.industrial.energy.*;

import com.cim.block.basic.industrial.rotation.MotorElectroBlock;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.basic.necrosis.hive.HiveRootsBlock;

import com.cim.multiblock.industrial.HeaterBlock;

import com.cim.multiblock.industrial.SmelterBlock;

import com.cim.multiblock.system.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.cim.block.basic.direction.FullOBlock;
import com.cim.block.basic.weapons.explosives.DetMinerBlock;
import com.cim.block.basic.necrosis.NecrosisPortalBlock;
import com.cim.block.basic.necrosis.hive.DepthWormNestBlock;
import com.cim.block.basic.necrosis.hive.HiveSoilBlock;
import com.cim.block.basic.weapons.TurretLightPlacerBlock;
import com.cim.item.energy.MachineBatteryBlockItem;
import com.cim.main.CrustalIncursionMod;
import com.cim.item.ModItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CrustalIncursionMod.MOD_ID);

    //ЭНЕРГОСЕТЬ
    public static final List<RegistryObject<Block>> BATTERY_BLOCKS = new ArrayList<>();

    public static final RegistryObject<Block> MACHINE_BATTERY = registerBattery("machine_battery");

    public static final RegistryObject<Block> CONVERTER_BLOCK = registerBlock("converter_block",
            () -> new ConverterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));
    public static final RegistryObject<Block> WIRE_COATED = registerBlock("wire_coated",
            () -> new WireBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final RegistryObject<Block> SWITCH = registerBlock("switch",
            () -> new SwitchBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)));

    // Маленький (Ваш старый)
    public static final RegistryObject<Block> CONNECTOR = registerBlock("connector",
            () -> new ConnectorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK),
                    new ConnectorTier(16, 3, 0.03125f, 4, 6)));

    // Средний
    public static final RegistryObject<Block> MEDIUM_CONNECTOR = registerBlock("medium_connector",
            () -> new ConnectorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK),
                    new ConnectorTier(32, 7, 0.05f, 6, 8)));

    // Большой
    public static final RegistryObject<Block> LARGE_CONNECTOR = registerBlock("large_connector",
            () -> new ConnectorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK),
                    new ConnectorTier(100, 11, 0.08f, 8, 13)));

    //трубы

    public static final RegistryObject<Block> BRONZE_FLUID_PIPE = registerBlock("bronze_fluid_pipe",
            () -> new FluidPipeBlock(PipeTier.BRONZE, BlockBehaviour.Properties.copy(Blocks.COPPER_BLOCK).noOcclusion()));

    // Стальная труба
    public static final RegistryObject<Block> STEEL_FLUID_PIPE = registerBlock("steel_fluid_pipe",
            () -> new FluidPipeBlock(PipeTier.STEEL, BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));

    // Свинцовая труба
    public static final RegistryObject<Block> LEAD_FLUID_PIPE = registerBlock("lead_fluid_pipe",
            () -> new FluidPipeBlock(PipeTier.LEAD, BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final RegistryObject<Block> TUNGSTEN_FLUID_PIPE = registerBlock("tungsten_fluid_pipe",
            () -> new FluidPipeBlock(PipeTier.TUNGSTEN, BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));




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
    public static final RegistryObject<Block> DEPTH_WORM_NEST_DEAD = registerBlock("depth_worm_nest_dead",
            () -> new DepthWormNestBlock(BlockBehaviour.Properties.copy(Blocks.MUD).sound(SoundType.MUD)));
    public static final RegistryObject<Block> HIVE_SOIL_DEAD = registerBlock("hive_soil_dead",
            () -> new HiveSoilBlock(BlockBehaviour.Properties.copy(Blocks.MUD).sound(SoundType.MUD)));
    public static final RegistryObject<Block> HIVE_ROOTS = registerBlock("hive_roots",
            () -> new HiveRootsBlock(BlockBehaviour.Properties.copy(Blocks.SPORE_BLOSSOM).noCollission().instabreak()));


    //ОБЫЧНЫЕ БЛОКИ
    public static final RegistryObject<Block> CRATE = registerBlock("crate",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BARREL).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> CRATE_AMMO = registerBlock("crate_ammo",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.BARREL).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> CONCRETE = registerBlock("concrete",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> PIPE_TEST = registerBlock("pipe_test",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SMELTER = registerBlock("smelter",
            () -> new SmelterBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noCollission()));
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
    public static final RegistryObject<Block> NECROSIS_PORTAL = registerBlock("necrosis_portal",
            () -> new NecrosisPortalBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> DIRT_ROUGH = registerBlock("dirt_rough",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.ROOTED_DIRT).requiresCorrectToolForDrops()));
   public static final RegistryObject<Block> DECO_STEEL_DARK = registerBlock("deco_steel_dark",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> DECO_STEEL = registerBlock("deco_steel",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));
     public static final RegistryObject<Block> DECO_STEEL_SMOG = registerBlock("deco_steel_smog",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> BASALT_ROUGH = registerBlock("basalt_rough",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> WASTE_LOG = registerBlock("waste_log",
            () -> new FullOBlock(BlockBehaviour.Properties.copy(Blocks.OAK_WOOD).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> FIREBRICK_BLOCK = registerBlock("firebrick_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> REINFORCEDBRICK_BLOCK = registerBlock("reinforcedbrick_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> DECO_LEAD = registerBlock("deco_lead",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> DECO_BEAM = registerBlock("deco_beam",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CASTING_DESCENT = registerBlock("casting_descent",
            () -> new CastingDescentBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> CASTING_POT = registerBlock("casting_pot",
            () -> new CastingPotBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.NETHER_BRICKS).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> STEEL_PROPS = registerBlock("steel_props",
            () -> new SteelPropsBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> CONCRETE_TILE = registerBlock("concrete_tile",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_ARMED_GLASS = registerBlock("concrete_armed_glass",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion()));
    public static final RegistryObject<Block> CONCRETE_NET = registerBlock("concrete_net",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_REINFORCED = registerBlock("concrete_reinforced",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_REINFORCED_HEAVY = registerBlock("concrete_reinforced_heavy",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_STRIPPED = registerBlock("concrete_stripped",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_TILE_ALT = registerBlock("concrete_tile_alt",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> CONCRETE_TILE_ALT_BLUE = registerBlock("concrete_tile_alt_blue",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));



    //СТУПЕНИ И ПОЛУБЛОКИ
    public static final RegistryObject<StairBlock> CONCRETE_TILE_ALT_STAIRS = registerBlock("concrete_tile_alt_stairs",
            () -> new StairBlock(CONCRETE_TILE_ALT.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_TILE_ALT.get())));
    public static final RegistryObject<StairBlock> CONCRETE_TILE_ALT_BLUE_STAIRS = registerBlock("concrete_tile_alt_blue_stairs",
            () -> new StairBlock(CONCRETE_TILE_ALT_BLUE.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_TILE_ALT_BLUE.get())));
    public static final RegistryObject<StairBlock> CONCRETE_STAIRS = registerBlock("concrete_stairs",
            () -> new StairBlock(CONCRETE.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE.get())));
    public static final RegistryObject<StairBlock> CONCRETE_TILE_STAIRS = registerBlock("concrete_tile_stairs",
            () -> new StairBlock(CONCRETE_TILE.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_TILE.get())));
    public static final RegistryObject<StairBlock> FIREBRICK_STAIRS = registerBlock("firebrick_stairs",
            () -> new StairBlock(FIREBRICK_BLOCK.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(FIREBRICK_BLOCK.get())));
    public static final RegistryObject<StairBlock> REINFORCEDBRICK_STAIRS = registerBlock("reinforcedbrick_stairs",
            () -> new StairBlock(REINFORCEDBRICK_BLOCK.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(REINFORCEDBRICK_BLOCK.get())));
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
    public static final RegistryObject<StairBlock> CONCRETE_STRIPPED_STAIRS = registerBlock("concrete_stripped_stairs",
            () -> new StairBlock(CONCRETE_STRIPPED.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_STRIPPED.get())));
    public static final RegistryObject<StairBlock> CONCRETE_REINFORCED_STAIRS = registerBlock("concrete_reinforced_stairs",
            () -> new StairBlock(CONCRETE_REINFORCED.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_REINFORCED.get())));
    public static final RegistryObject<StairBlock> CONCRETE_REINFORCED_HEAVY_STAIRS = registerBlock("concrete_reinforced_heavy_stairs",
            () -> new StairBlock(CONCRETE_REINFORCED_HEAVY.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CONCRETE_REINFORCED_HEAVY.get())));


    public static final RegistryObject<SlabBlock> CONCRETE_STRIPPED_SLAB = registerBlock("concrete_stripped_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_STRIPPED.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_REINFORCED_SLAB = registerBlock("concrete_reinforced_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_REINFORCED.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_REINFORCED_HEAVY_SLAB = registerBlock("concrete_reinforced_heavy_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_REINFORCED_HEAVY.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_TILE_SLAB = registerBlock("concrete_tile_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_TILE.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_TILE_ALT_SLAB = registerBlock("concrete_tile_alt_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_TILE_ALT.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_TILE_ALT_BLUE_SLAB = registerBlock("concrete_tile_alt_blue_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_TILE_ALT.get())));
    public static final RegistryObject<SlabBlock> CONCRETE_GREEN_SLAB = registerBlock("concrete_green_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CONCRETE_GREEN.get())));
    public static final RegistryObject<SlabBlock> FIREBRICK_SLAB = registerBlock("firebrick_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(FIREBRICK_BLOCK.get())));
    public static final RegistryObject<SlabBlock> REINFORCEDBRICK_SLAB = registerBlock("reinforcedbrick_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(REINFORCEDBRICK_BLOCK.get())));
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


    //ЖИДКОСТИ

    public static final RegistryObject<Block> FLUID_BARREL = registerBlock("fluid_barrel",
            () -> new FluidBarrelBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 4.0f).noOcclusion().requiresCorrectToolForDrops()));


    // Техническая болванка, из которой мы будем воровать полигоны в PipeBakedModel
    public static final RegistryObject<Block> PIPE_SPOTS = BLOCKS.register("pipe_spots",
            () -> new com.cim.block.basic.fluids.FluidPipeBlock(
                    com.cim.api.fluids.PipeTier.BRONZE, // <--- Просто даем ему любой тир-заглушку
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.GLASS)
                            .noCollission().noOcclusion().noLootTable()
            ));


//    //БЛОКИ-ВРАЩЕНИЯ


    // Максимально чистая регистрация базового вала
    // Меняем BLOCKS.register на твой метод registerBlock


    public static final RegistryObject<Block> MOTOR_ELECTRO = registerBlock("motor_electro",
            () -> new MotorElectroBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));

    public static final RegistryObject<Block> SHAFT_LIGHT_IRON = registerShaft(ShaftMaterial.IRON, ShaftDiameter.LIGHT);
    public static final RegistryObject<Block> SHAFT_MEDIUM_IRON = registerShaft(ShaftMaterial.IRON, ShaftDiameter.MEDIUM);
    public static final RegistryObject<Block> SHAFT_HEAVY_IRON = registerShaft(ShaftMaterial.IRON, ShaftDiameter.HEAVY);

    // ВАЛЫ: ДЮРАЛЮМИНИЙ
    public static final RegistryObject<Block> SHAFT_LIGHT_DURALUMIN = registerShaft(ShaftMaterial.DURALUMIN, ShaftDiameter.LIGHT);
    public static final RegistryObject<Block> SHAFT_MEDIUM_DURALUMIN = registerShaft(ShaftMaterial.DURALUMIN, ShaftDiameter.MEDIUM);
    public static final RegistryObject<Block> SHAFT_HEAVY_DURALUMIN = registerShaft(ShaftMaterial.DURALUMIN, ShaftDiameter.HEAVY);

    // ВАЛЫ: СТАЛЬ
    public static final RegistryObject<Block> SHAFT_LIGHT_STEEL = registerShaft(ShaftMaterial.STEEL, ShaftDiameter.LIGHT);
    public static final RegistryObject<Block> SHAFT_MEDIUM_STEEL = registerShaft(ShaftMaterial.STEEL, ShaftDiameter.MEDIUM);
    public static final RegistryObject<Block> SHAFT_HEAVY_STEEL = registerShaft(ShaftMaterial.STEEL, ShaftDiameter.HEAVY);

    // ВАЛЫ: ТИТАН
    public static final RegistryObject<Block> SHAFT_LIGHT_TITANIUM = registerShaft(ShaftMaterial.TITANIUM, ShaftDiameter.LIGHT);
    public static final RegistryObject<Block> SHAFT_MEDIUM_TITANIUM = registerShaft(ShaftMaterial.TITANIUM, ShaftDiameter.MEDIUM);
    public static final RegistryObject<Block> SHAFT_HEAVY_TITANIUM = registerShaft(ShaftMaterial.TITANIUM, ShaftDiameter.HEAVY);

    // ВАЛЫ: КАРБИД ВОЛЬФРАМА
    public static final RegistryObject<Block> SHAFT_LIGHT_TUNGSTEN_CARBIDE = registerShaft(ShaftMaterial.TUNGSTEN_CARBIDE, ShaftDiameter.LIGHT);
    public static final RegistryObject<Block> SHAFT_MEDIUM_TUNGSTEN_CARBIDE = registerShaft(ShaftMaterial.TUNGSTEN_CARBIDE, ShaftDiameter.MEDIUM);
    public static final RegistryObject<Block> SHAFT_HEAVY_TUNGSTEN_CARBIDE = registerShaft(ShaftMaterial.TUNGSTEN_CARBIDE, ShaftDiameter.HEAVY);

    private static RegistryObject<Block> registerShaft(ShaftMaterial mat, ShaftDiameter dia) {
        String name = "shaft_" + dia.name + "_" + mat.name();
        // ТЕПЕРЬ ИСПОЛЬЗУЕМ registerBlock, чтобы создались и блок, и предмет!
        return registerBlock(name, () -> new ShaftBlock(BlockBehaviour.Properties.of().strength(2.0f), mat, dia));
    }

    //декоративные блоки
    public static final RegistryObject<Block> BEAM_BLOCK = registerBlock("beam_block",
            () -> new BeamBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(5.0f, 6.0f).noOcclusion().requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> BEAM_COLLISION = BLOCKS.register("beam_collision",
            () -> new BeamCollisionBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 6.0f) // Изменили с -1.0f на 2.0f
                    .noOcclusion()
                    .noLootTable()));


    //СЕКВОЯ
    public static final RegistryObject<Block> SEQUOIA_BARK = registerBlock("sequoia_bark",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> SEQUOIA_HEARTWOOD = registerBlock("sequoia_heartwood",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)));
    public static final RegistryObject<Block> SEQUOIA_PLANKS  = registerBlock("sequoia_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(0.5f, 4.0f).requiresCorrectToolForDrops()));
    public static final RegistryObject<SlabBlock> SEQUOIA_SLAB = registerBlock("sequoia_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(SEQUOIA_PLANKS.get())));
    public static final RegistryObject<StairBlock> SEQUOIA_STAIRS = registerBlock("sequoia_stairs",
            () -> new StairBlock(SEQUOIA_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(SEQUOIA_PLANKS.get())));
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



    public static final RegistryObject<Block> MORY_BLOCK = registerBlock("mory_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> ANTON_CHIGUR = registerBlock("anton_chigur",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(0.5F, 6.0F).sound(SoundType.WOOD).requiresCorrectToolForDrops()));


    //МУЛЬТИБЛОКИ
    public static final RegistryObject<Block> MULTIBLOCK_PART = BLOCKS.register("multiblock_part",
            () -> new MultiblockPartBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));

    // 2. Регистрируем блок самого Нагревателя
    public static final RegistryObject<Block> HEATER = BLOCKS.register("heater",
            () -> new HeaterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion()));



    // Вспомогательный метод регистрации без предмета
    private static <T extends Block> RegistryObject<T> registerBlockOnly(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }



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

    private static <T extends Block> RegistryObject<T> registerBlockWithoutItem(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    private static RegistryObject<Block> registerBattery(String name) {
        RegistryObject<Block> batteryBlock = BLOCKS.register(name,
                () -> new MachineBatteryBlock(Block.Properties.of().strength(5.0f).requiresCorrectToolForDrops().noOcclusion()));

        ModItems.ITEMS.register(name,
                () -> new MachineBatteryBlockItem(batteryBlock.get(), new Item.Properties()));

        BATTERY_BLOCKS.add(batteryBlock);
        return batteryBlock;
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
