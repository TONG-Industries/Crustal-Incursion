package com.cim.block.entity;

import com.cim.block.entity.conglomerate.ConglomerateBlockEntity;
import com.cim.block.entity.deco.BeamCollisionBlockEntity;
import com.cim.block.entity.fluids.FluidBarrelBlockEntity;

import com.cim.block.entity.fluids.FluidPipeBlockEntity;

import com.cim.block.entity.industrial.MillstoneBlockEntity;
import com.cim.block.entity.industrial.casting.SmallSmelterBlockEntity;
import com.cim.block.entity.industrial.rotation.BearingBlockEntity;
import com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import com.cim.multiblock.industrial.HeaterBlockEntity;

import com.cim.block.entity.industrial.casting.CastingDescentBlockEntity;
import com.cim.block.entity.industrial.casting.CastingPotBlockEntity;
import com.cim.block.entity.industrial.energy.*;

import com.cim.multiblock.industrial.HeaterBlockEntity;
import com.cim.multiblock.industrial.SmelterBlockEntity;

import com.cim.multiblock.system.MultiblockPartEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.cim.block.entity.hive.DepthWormNestBlockEntity;
import com.cim.block.entity.hive.HiveSoilBlockEntity;
import com.cim.block.entity.weapons.TurretLightPlacerBlockEntity;
import com.cim.main.CrustalIncursionMod;
import com.cim.block.basic.ModBlocks;

import static com.cim.block.basic.ModBlocks.BLOCKS;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CrustalIncursionMod.MOD_ID);


    public static final RegistryObject<BlockEntityType<DepthWormNestBlockEntity>> DEPTH_WORM_NEST =
            BLOCK_ENTITIES.register("depth_worm_nest",
                    () -> BlockEntityType.Builder.of(DepthWormNestBlockEntity::new,
                            ModBlocks.DEPTH_WORM_NEST.get()).build(null));

    public static final RegistryObject<BlockEntityType<HiveSoilBlockEntity>> HIVE_SOIL =
            BLOCK_ENTITIES.register("hive_soil", () ->
                    BlockEntityType.Builder.of(HiveSoilBlockEntity::new, ModBlocks.HIVE_SOIL.get())
                            .build(null)
            );

    public static final RegistryObject<BlockEntityType<MillstoneBlockEntity>> MILLSTONE =
            BLOCK_ENTITIES.register("millstone",
                    () -> BlockEntityType.Builder.of(MillstoneBlockEntity::new, ModBlocks.JERNOVA.get()).build(null));

    public static final RegistryObject<BlockEntityType<SmallSmelterBlockEntity>> SMALL_SMELTER_BE =
            BLOCK_ENTITIES.register("small_smelter",
                    () -> BlockEntityType.Builder.of(SmallSmelterBlockEntity::new, ModBlocks.SMALL_SMELTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<ConglomerateBlockEntity>> CONGLOMERATE =
            BLOCK_ENTITIES.register("conglomerate",
                    () -> BlockEntityType.Builder.of(ConglomerateBlockEntity::new,
                            ModBlocks.CONGLOMERATE.get()).build(null));

    public static final RegistryObject<BlockEntityType<TurretLightPlacerBlockEntity>> TURRET_LIGHT_PLACER_BE =
            BLOCK_ENTITIES.register("turret_light_placer",
                    () -> BlockEntityType.Builder.of(TurretLightPlacerBlockEntity::new, ModBlocks.TURRET_LIGHT_PLACER.get()).build(null));


    public static final RegistryObject<BlockEntityType<MachineBatteryBlockEntity>> MACHINE_BATTERY_BE =
            BLOCK_ENTITIES.register("machine_battery_be", () -> {
                // Превращаем список RegistryObject в массив Block[]
                Block[] validBlocks = ModBlocks.BATTERY_BLOCKS.stream()
                        .map(RegistryObject::get)
                        .toArray(Block[]::new);

                return BlockEntityType.Builder.<MachineBatteryBlockEntity>of(MachineBatteryBlockEntity::new, validBlocks)
                        .build(null);
            });

    public static final RegistryObject<BlockEntityType<WireBlockEntity>> WIRE_BE =
            BLOCK_ENTITIES.register("wire_be", () ->
                    BlockEntityType.Builder.<WireBlockEntity>of(WireBlockEntity::new, ModBlocks.WIRE_COATED.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<ConverterBlockEntity>> CONVERTER_BE =
            BLOCK_ENTITIES.register("converter_be",
                    () -> BlockEntityType.Builder.of(ConverterBlockEntity::new, ModBlocks.CONVERTER_BLOCK.get()).build(null));



    // 1. BlockEntity для части мультиблока
    public static final RegistryObject<BlockEntityType<MultiblockPartEntity>> MULTIBLOCK_PART = BLOCK_ENTITIES.register("multiblock_part",
            () -> BlockEntityType.Builder.of(MultiblockPartEntity::new, ModBlocks.MULTIBLOCK_PART.get()).build(null));

    // 2. BlockEntity для Нагревателя
    public static final RegistryObject<BlockEntityType<HeaterBlockEntity>> HEATER_BE = BLOCK_ENTITIES.register("heater_be",
            () -> BlockEntityType.Builder.of(HeaterBlockEntity::new, ModBlocks.HEATER.get()).build(null));

    public static final RegistryObject<BlockEntityType<SmelterBlockEntity>> SMELTER_BE = BLOCK_ENTITIES.register("smelter_be",
            () -> BlockEntityType.Builder.of(SmelterBlockEntity::new, ModBlocks.SMELTER.get()).build(null));


    public static final RegistryObject<BlockEntityType<SwitchBlockEntity>> SWITCH_BE =
            BLOCK_ENTITIES.register("switch_be", () ->
                    BlockEntityType.Builder.of(SwitchBlockEntity::new, ModBlocks.SWITCH.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<CastingDescentBlockEntity>> CASTING_DESCENT = BLOCK_ENTITIES.register(
            "casting_descent",
            () -> BlockEntityType.Builder.of(CastingDescentBlockEntity::new, ModBlocks.CASTING_DESCENT.get()).build(null)
    );

    public static final RegistryObject<BlockEntityType<CastingPotBlockEntity>> CASTING_POT =
            BLOCK_ENTITIES.register("casting_pot",
                    () -> BlockEntityType.Builder.of(CastingPotBlockEntity::new,
                            com.cim.block.basic.ModBlocks.CASTING_POT.get()).build(null));

    public static final RegistryObject<BlockEntityType<ShaftBlockEntity>> SHAFT_BE = BLOCK_ENTITIES.register("shaft_be",
            () -> BlockEntityType.Builder.of(ShaftBlockEntity::new,
                    ModBlocks.SHAFT_LIGHT_IRON.get(),
                    ModBlocks.SHAFT_MEDIUM_IRON.get(),
                    ModBlocks.SHAFT_HEAVY_IRON.get(),
                    ModBlocks.SHAFT_LIGHT_DURALUMIN.get(),
                    ModBlocks.SHAFT_MEDIUM_DURALUMIN.get(),
                    ModBlocks.SHAFT_HEAVY_DURALUMIN.get(),
                    ModBlocks.SHAFT_LIGHT_STEEL.get(),
                    ModBlocks.SHAFT_MEDIUM_STEEL.get(),
                    ModBlocks.SHAFT_HEAVY_STEEL.get(),
                    ModBlocks.SHAFT_LIGHT_TITANIUM.get(),
                    ModBlocks.SHAFT_MEDIUM_TITANIUM.get(),
                    ModBlocks.SHAFT_HEAVY_TITANIUM.get(),
                    ModBlocks.SHAFT_LIGHT_TUNGSTEN_CARBIDE.get(),
                    ModBlocks.SHAFT_MEDIUM_TUNGSTEN_CARBIDE.get(),
                    ModBlocks.SHAFT_HEAVY_TUNGSTEN_CARBIDE.get()
            ).build(null));

    public static final RegistryObject<BlockEntityType<BearingBlockEntity>> BEARING_BE = BLOCK_ENTITIES.register("bearing_be",
            () -> BlockEntityType.Builder.of(BearingBlockEntity::new, ModBlocks.BEARING_BLOCK.get())
                    .build(null));

    public static final RegistryObject<BlockEntityType<MotorElectroBlockEntity>> MOTOR_ELECTRO_BE =
            BLOCK_ENTITIES.register("motor_electro",
                    () -> BlockEntityType.Builder.of(MotorElectroBlockEntity::new, ModBlocks.MOTOR_ELECTRO.get()).build(null));

    public static final RegistryObject<BlockEntityType<FluidPipeBlockEntity>> FLUID_PIPE_BE =
            BLOCK_ENTITIES.register("fluid_pipe_be",
                    () -> BlockEntityType.Builder.of(FluidPipeBlockEntity::new,
                            // Просто перечисляем все наши трубы через запятую:
                            ModBlocks.BRONZE_FLUID_PIPE.get(),
                            ModBlocks.STEEL_FLUID_PIPE.get(),
                            ModBlocks.LEAD_FLUID_PIPE.get(),
                            ModBlocks.TUNGSTEN_FLUID_PIPE.get()
                            // ... и любые другие трубы, которые ты добавишь в будущем
                    ).build(null));


    public static final RegistryObject<BlockEntityType<BeamCollisionBlockEntity>> BEAM_COLLISION_BE =
            BLOCK_ENTITIES.register("beam_collision_be", () ->
                    BlockEntityType.Builder.of(BeamCollisionBlockEntity::new,
                            ModBlocks.BEAM_COLLISION.get()).build(null));

//    public static final RegistryObject<BlockEntityType<ShaftBlockEntity>> SHAFT_BLOCK_BE =
//            BLOCK_ENTITIES.register("shaft",
//                    () -> BlockEntityType.Builder.of(ShaftBlockEntity::new,
//                            ModBlocks.SHAFT_IRON.get(),
//                            ModBlocks.SHAFT_WOODEN.get() // и все другие валы
//                    ).build(null));

    public static final RegistryObject<BlockEntityType<ConnectorBlockEntity>> CONNECTOR_BE =
            BLOCK_ENTITIES.register("connector", () ->
                    BlockEntityType.Builder.of(ConnectorBlockEntity::new,
                            ModBlocks.CONNECTOR.get(),
                            ModBlocks.MEDIUM_CONNECTOR.get(),
                            ModBlocks.LARGE_CONNECTOR.get()
                    ).build(null));


    public static final RegistryObject<BlockEntityType<FluidBarrelBlockEntity>> FLUID_BARREL_BE =
            BLOCK_ENTITIES.register("fluid_barrel_be",
                    () -> BlockEntityType.Builder.of(FluidBarrelBlockEntity::new, ModBlocks.FLUID_BARREL.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}