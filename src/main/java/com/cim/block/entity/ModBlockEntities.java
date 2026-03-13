package com.cim.block.entity;

import com.cim.block.entity.deco.BeamCollisionBlockEntity;
import com.cim.block.entity.energy.*;
import com.cim.block.entity.rotation.*;
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

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CrustalIncursionMod.MOD_ID);

    // ПРИМЕР: Некротический Алтарь (если такой был в cim)
    // .build(null) в 1.20.1 — это стандарт для создания типа

    public static final RegistryObject<BlockEntityType<DepthWormNestBlockEntity>> DEPTH_WORM_NEST =
            BLOCK_ENTITIES.register("depth_worm_nest",
                    () -> BlockEntityType.Builder.of(DepthWormNestBlockEntity::new,
                            ModBlocks.DEPTH_WORM_NEST.get()).build(null));

    public static final RegistryObject<BlockEntityType<HiveSoilBlockEntity>> HIVE_SOIL =
            BLOCK_ENTITIES.register("hive_soil", () ->
                    BlockEntityType.Builder.of(HiveSoilBlockEntity::new, ModBlocks.HIVE_SOIL.get())
                            .build(null)
            );

    public static final RegistryObject<BlockEntityType<RConverterBlockEntity>> R_CONVERTER_BE =
            BLOCK_ENTITIES.register("rconverter",
                    () -> BlockEntityType.Builder.of(RConverterBlockEntity::new, ModBlocks.RCONVERTER.get()).build(null));


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



    public static final RegistryObject<BlockEntityType<SwitchBlockEntity>> SWITCH_BE =
            BLOCK_ENTITIES.register("switch_be", () ->
                    BlockEntityType.Builder.of(SwitchBlockEntity::new, ModBlocks.SWITCH.get())
                            .build(null));


    public static final RegistryObject<BlockEntityType<WindGenFlugerBlockEntity>> WIND_GEN_FLUGER_BE =
            BLOCK_ENTITIES.register("wind_gen_fluger",
                    () -> BlockEntityType.Builder.of(WindGenFlugerBlockEntity::new, ModBlocks.WIND_GEN_FLUGER.get()).build(null));

    public static final RegistryObject<BlockEntityType<TachometerBlockEntity>> TACHOMETER_BE =
            BLOCK_ENTITIES.register("tachometer",
                    () -> BlockEntityType.Builder.of(TachometerBlockEntity::new, ModBlocks.TACHOMETER.get()).build(null));

    public static final RegistryObject<BlockEntityType<RotationMeterBlockEntity>> ROTATION_METER_BE =
            BLOCK_ENTITIES.register("rotation_meter_be",
                    () -> BlockEntityType.Builder.of(RotationMeterBlockEntity::new, ModBlocks.ROTATION_METER.get()).build(null));

    public static final RegistryObject<BlockEntityType<AdderBlockEntity>> ADDER_BE =
            BLOCK_ENTITIES.register("adder",
                    () -> BlockEntityType.Builder.of(AdderBlockEntity::new, ModBlocks.ADDER.get()).build(null));

    public static final RegistryObject<BlockEntityType<StopperBlockEntity>> STOPPER_BE =
            BLOCK_ENTITIES.register("stopper",
                    () -> BlockEntityType.Builder.of(StopperBlockEntity::new, ModBlocks.STOPPER.get()).build(null));

    public static final RegistryObject<BlockEntityType<GearPortBlockEntity>> GEAR_PORT_BE =
            BLOCK_ENTITIES.register("gear_port_be",
                    () -> BlockEntityType.Builder.of(GearPortBlockEntity::new, ModBlocks.GEAR_PORT.get()).build(null));

    public static final RegistryObject<BlockEntityType<ShaftPlacerBlockEntity>> SHAFT_PLACER_BE =
            BLOCK_ENTITIES.register("shaft_placer_be",
                    () -> BlockEntityType.Builder.of(ShaftPlacerBlockEntity::new, ModBlocks.SHAFT_PLACER.get()).build(null));

    public static final RegistryObject<BlockEntityType<DrillHeadBlockEntity>> DRILL_HEAD_BE =
            BLOCK_ENTITIES.register("drill_head_be",
                    () -> BlockEntityType.Builder.of(DrillHeadBlockEntity::new, ModBlocks.DRILL_HEAD.get()).build(null));

    public static final RegistryObject<BlockEntityType<MiningPortBlockEntity>> MINING_PORT_BE =
            BLOCK_ENTITIES.register("mining_port_be",
                    () -> BlockEntityType.Builder.of(MiningPortBlockEntity::new, ModBlocks.MINING_PORT.get()).build(null));

    public static final RegistryObject<BlockEntityType<MotorElectroBlockEntity>> MOTOR_ELECTRO_BE =
            BLOCK_ENTITIES.register("motor_electro_be",
                    () -> BlockEntityType.Builder.of(MotorElectroBlockEntity::new, ModBlocks.MOTOR_ELECTRO.get()).build(null));


    public static final RegistryObject<BlockEntityType<BeamCollisionBlockEntity>> BEAM_COLLISION_BE =
            BLOCK_ENTITIES.register("beam_collision_be", () ->
                    BlockEntityType.Builder.of(BeamCollisionBlockEntity::new,
                            ModBlocks.BEAM_COLLISION.get()).build(null));

    public static final RegistryObject<BlockEntityType<ShaftBlockEntity>> SHAFT_BLOCK_BE =
            BLOCK_ENTITIES.register("shaft",
                    () -> BlockEntityType.Builder.of(ShaftBlockEntity::new,
                            ModBlocks.SHAFT_IRON.get(),
                            ModBlocks.SHAFT_WOODEN.get() // и все другие валы
                    ).build(null));

    public static final RegistryObject<BlockEntityType<ConnectorBlockEntity>> CONNECTOR_BE =
            BLOCK_ENTITIES.register("connector", () ->
                    BlockEntityType.Builder.of(ConnectorBlockEntity::new,
                            ModBlocks.CONNECTOR.get(),         // Твой маленький коннектор
                            ModBlocks.MEDIUM_CONNECTOR.get(),  // ДОБАВИТЬ ЭТО!
                            ModBlocks.LARGE_CONNECTOR.get()    // ДОБАВИТЬ ЭТО!
                    ).build(null));
    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}