package com.cim.api.fluids;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, "cim");
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.Keys.FLUIDS, "cim");
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.Keys.BLOCKS, "cim");

    // Текстуры ванильной воды, которые мы будем красить
    private static final ResourceLocation WATER_STILL = new ResourceLocation("block/water_still");
    private static final ResourceLocation WATER_FLOW = new ResourceLocation("block/water_flow");

    // ==========================================
    // 1. ПЕРОКСИД ВОДОРОДА (Жидкость)
    // ==========================================
    public static final RegistryObject<FluidType> HYDROGEN_PEROXIDE_TYPE = FLUID_TYPES.register("hydrogen_peroxide",
            () -> new BaseFluidType(FluidType.Properties.create().density(1450).viscosity(1100).temperature(300),
                    WATER_STILL, WATER_FLOW, 0xAAFFFFFF,
                    5,
                    0));

    public static final RegistryObject<FlowingFluid> HYDROGEN_PEROXIDE_SOURCE = FLUIDS.register("hydrogen_peroxide",
            () -> new ForgeFlowingFluid.Source(ModFluids.HYDROGEN_PEROXIDE_PROPS));
    public static final RegistryObject<FlowingFluid> HYDROGEN_PEROXIDE_FLOWING = FLUIDS.register("flowing_hydrogen_peroxide",
            () -> new ForgeFlowingFluid.Flowing(ModFluids.HYDROGEN_PEROXIDE_PROPS));
    public static final RegistryObject<LiquidBlock> HYDROGEN_PEROXIDE_BLOCK = BLOCKS.register("hydrogen_peroxide_block",
            () -> new LiquidBlock(HYDROGEN_PEROXIDE_SOURCE, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).replaceable().noCollission().strength(100.0F).noLootTable()));

    private static final ForgeFlowingFluid.Properties HYDROGEN_PEROXIDE_PROPS = new ForgeFlowingFluid.Properties(
            HYDROGEN_PEROXIDE_TYPE, HYDROGEN_PEROXIDE_SOURCE, HYDROGEN_PEROXIDE_FLOWING).block(HYDROGEN_PEROXIDE_BLOCK);

    // ==========================================
    // 2. СЕРНАЯ КИСЛОТА (Жидкость)
    // ==========================================
    public static final RegistryObject<FluidType> SULFURIC_ACID_TYPE = FLUID_TYPES.register("sulfuric_acid",
            () -> new BaseFluidType(FluidType.Properties.create().density(1830).viscosity(2000).temperature(350),
                    WATER_STILL, WATER_FLOW, 0xCCAAAA00,
                    25,
                    0));

    public static final RegistryObject<FlowingFluid> SULFURIC_ACID_SOURCE = FLUIDS.register("sulfuric_acid",
            () -> new ForgeFlowingFluid.Source(ModFluids.SULFURIC_ACID_PROPS));
    public static final RegistryObject<FlowingFluid> SULFURIC_ACID_FLOWING = FLUIDS.register("flowing_sulfuric_acid",
            () -> new ForgeFlowingFluid.Flowing(ModFluids.SULFURIC_ACID_PROPS));
    public static final RegistryObject<LiquidBlock> SULFURIC_ACID_BLOCK = BLOCKS.register("sulfuric_acid_block",
            () -> new LiquidBlock(SULFURIC_ACID_SOURCE, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).replaceable().noCollission().strength(100.0F).noLootTable()));

    private static final ForgeFlowingFluid.Properties SULFURIC_ACID_PROPS = new ForgeFlowingFluid.Properties(
            SULFURIC_ACID_TYPE, SULFURIC_ACID_SOURCE, SULFURIC_ACID_FLOWING).block(SULFURIC_ACID_BLOCK);

    // ==========================================
    // 3. ПРИРОДНЫЙ ГАЗ (Газ - без блока)
    // ==========================================
    public static final RegistryObject<FluidType> NATURAL_GAS_TYPE = FLUID_TYPES.register("natural_gas",
            () -> new BaseFluidType(FluidType.Properties.create().density(-800).viscosity(500).temperature(300),
                    WATER_STILL, WATER_FLOW, 0x44B0C4DE,
                    0,
                    0));

    public static final RegistryObject<FlowingFluid> NATURAL_GAS_SOURCE = FLUIDS.register("natural_gas",
            () -> new ForgeFlowingFluid.Source(ModFluids.NATURAL_GAS_PROPS));
    public static final RegistryObject<FlowingFluid> NATURAL_GAS_FLOWING = FLUIDS.register("flowing_natural_gas",
            () -> new ForgeFlowingFluid.Flowing(ModFluids.NATURAL_GAS_PROPS));

    private static final ForgeFlowingFluid.Properties NATURAL_GAS_PROPS = new ForgeFlowingFluid.Properties(
            NATURAL_GAS_TYPE, NATURAL_GAS_SOURCE, NATURAL_GAS_FLOWING);

    // ==========================================
    // 4. ПАР (Газ - без блока)
    // ==========================================
    public static final RegistryObject<FluidType> STEAM_TYPE = FLUID_TYPES.register("steam",
            () -> new BaseFluidType(FluidType.Properties.create().density(-1000).viscosity(200).temperature(373),
                    WATER_STILL, WATER_FLOW, 0x88FFFFFF,
                    0,
                    0));

    public static final RegistryObject<FlowingFluid> STEAM_SOURCE = FLUIDS.register("steam",
            () -> new ForgeFlowingFluid.Source(ModFluids.STEAM_PROPS));
    public static final RegistryObject<FlowingFluid> STEAM_FLOWING = FLUIDS.register("flowing_steam",
            () -> new ForgeFlowingFluid.Flowing(ModFluids.STEAM_PROPS));

    private static final ForgeFlowingFluid.Properties STEAM_PROPS = new ForgeFlowingFluid.Properties(
            STEAM_TYPE, STEAM_SOURCE, STEAM_FLOWING);

    // ==========================================
    // РЕГИСТРАЦИЯ В ШИНЕ (Не трогаем)
    // ==========================================
    public static void register(IEventBus eventBus) {
        FLUID_TYPES.register(eventBus);
        FLUIDS.register(eventBus);
        BLOCKS.register(eventBus);
    }
}