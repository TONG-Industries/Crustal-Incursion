package com.cim.client;

import com.cim.api.metallurgy.system.ItemHeatColorRegistry;
import com.cim.main.ResourceRegistry;
import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.fluids.FluidPipeBlockEntity;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import com.cim.client.gecko.block.energy.MachineBatteryRenderer;
import com.cim.client.overlay.gui.*;
import com.cim.client.render.flywheel.ModModels;
import com.cim.client.render.flywheel.MotorVisual;
import com.cim.client.render.flywheel.ShaftVisual;
import com.cim.client.renderer.*;

import com.cim.item.tools.FluidIdentifierItem;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;


import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.cim.block.entity.ModBlockEntities;
import com.cim.client.config.ModConfigKeybindHandler;
//import com.cim.client.gecko.block.rotation.DrillHeadRenderer;
//import com.cim.client.gecko.block.rotation.MotorElectroRenderer;
//import com.cim.client.gecko.block.rotation.ShaftRenderer;
//import com.cim.client.gecko.block.rotation.WindGenFlugerRenderer;
import com.cim.client.gecko.block.turrets.TurretLightPlacerRenderer;
import com.cim.client.gecko.entity.bullets.TurretBulletRenderer;
import com.cim.client.gecko.entity.mobs.DepthWormRenderer;
import com.cim.client.gecko.entity.turrets.TurretLightLinkedRenderer;
import com.cim.client.gecko.entity.turrets.TurretLightRenderer;
import com.cim.client.overlay.hud.OverlayAmmoHud;
import com.cim.entity.ModEntities;
import com.cim.item.ModItems;
import com.cim.main.CrustalIncursionMod;
import com.cim.menu.ModMenuTypes;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;

@Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        MinecraftForge.EVENT_BUS.register(ClientRenderHandler.class);

        ModItems.MACHINEGUN.ifPresent(item -> {
            ItemProperties.register(item,
                    new ResourceLocation(CrustalIncursionMod.MOD_ID, "pull"),
                    (pStack, pLevel, pEntity, pSeed) -> {
                        if (pEntity != null && pEntity.isUsingItem() && pEntity.getUseItem() == pStack) {
                            return 1.0f;
                        }
                        return 0.0f;
                    });
        });

        MenuScreens.register(ModMenuTypes.MACHINE_BATTERY_MENU.get(), GUIMachineBattery::new);
//        MenuScreens.register(ModMenuTypes.MOTOR_ELECTRO_MENU.get(), GUIMotorElectro::new);
        MenuScreens.register(ModMenuTypes.TURRET_AMMO_MENU.get(), GUITurretAmmo::new);
//        MenuScreens.register(ModMenuTypes.SHAFT_PLACER_MENU.get(), GUIShaftPlacer::new);
//        MenuScreens.register(ModMenuTypes.MINING_PORT_MENU.get(), GUIMiningPort::new);
        MenuScreens.register(ModMenuTypes.FLUID_BARREL_MENU.get(), GUIFluidBarrel::new);
        MenuScreens.register(ModMenuTypes.HEATER_MENU.get(), GUIHeater::new);
        MenuScreens.register(ModMenuTypes.SMELTER_MENU.get(), GUISmelter::new);

//        BlockEntityRenderers.register(ModBlockEntities.MOTOR_ELECTRO_BE.get(), MotorElectroRenderer::new);
//        BlockEntityRenderers.register(ModBlockEntities.SHAFT_BLOCK_BE.get(), ShaftRenderer::new);
//        BlockEntityRenderers.register(ModBlockEntities.WIND_GEN_FLUGER_BE.get(), WindGenFlugerRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.TURRET_LIGHT_PLACER_BE.get(), TurretLightPlacerRenderer::new);
//        BlockEntityRenderers.register(ModBlockEntities.DRILL_HEAD_BE.get(), DrillHeadRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.MACHINE_BATTERY_BE.get(), MachineBatteryRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.CONNECTOR_BE.get(), ConnectorRenderer::new);

        event.registerBlockEntityRenderer(ModBlockEntities.CASTING_DESCENT.get(), CastingDescentRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BEAM_COLLISION_BE.get(), BeamCollisionRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CASTING_POT.get(), com.cim.client.renderer.CastingPotRenderer::new);

        ModEntities.GRENADE_NUC_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADE_IF_FIRE_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADE_IF_SLIME_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADE_IF_HE_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADE_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADEHE_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADEFIRE_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADESMART_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADESLIME_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
        ModEntities.GRENADE_IF_PROJECTILE.ifPresent(entityType ->
                EntityRenderers.register(entityType, ThrownItemRenderer::new));
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        // Заставляем игру принудительно загрузить модель половинки вала
        event.register(new net.minecraft.resources.ResourceLocation("cim", "block/half_shaft"));

        // Корпус мотора тоже можно добавить сюда для надежности
        event.register(new net.minecraft.resources.ResourceLocation("cim", "block/electro_motor"));
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // 1. Инициализируем загрузку кастомной 3D модели для Flywheel
        event.enqueueWork(() -> {
            ModModels.init();
        });

//===========================регистрации визуализаторов для Flywheel=====================================
// ======================================================================================================
        VisualizerRegistry.setVisualizer(ModBlockEntities.SHAFT_BE.get(), new dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer<com.cim.block.entity.industrial.rotation.ShaftBlockEntity>() {

            @Override
            public BlockEntityVisual<? super ShaftBlockEntity> createVisual(VisualizationContext ctx, ShaftBlockEntity be, float partialTick) {
                // Возвращаем наш визуал, передавая все нужные параметры
                return new ShaftVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(ShaftBlockEntity be) {
                // Обязательно true! Отключаем ванильный рендер для максимального FPS
                return true;
            }
        });

        VisualizerRegistry.setVisualizer(ModBlockEntities.MOTOR_ELECTRO_BE.get(), new dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer<com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity>() {

            @Override
            public dev.engine_room.flywheel.api.visual.BlockEntityVisual<? super com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity> createVisual(dev.engine_room.flywheel.api.visualization.VisualizationContext ctx, com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity be, float partialTick) {
                // Возвращаем визуал мотора
                return new com.cim.client.render.flywheel.MotorVisual(ctx, be, partialTick);
            }

            @Override
            public boolean skipVanillaRender(com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity be) {
                // Отключаем ванильный рендер, чтобы Flywheel взял всё на себя
                return true;
            }
        });
    }
    //================================================================================================
    //================================================================================================

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityRenderers.register(ModEntities.TURRET_BULLET.get(), TurretBulletRenderer::new);
        EntityRenderers.register(ModEntities.DEPTH_WORM.get(), DepthWormRenderer::new);
        event.registerEntityRenderer(ModEntities.TURRET_LIGHT.get(), TurretLightRenderer::new);
        EntityRenderers.register(ModEntities.TURRET_LIGHT_LINKED.get(), TurretLightLinkedRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MinecraftForge.EVENT_BUS.register(ModConfigKeybindHandler.class);

    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "ammo_hud", OverlayAmmoHud.HUD_AMMO);
    }


    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // Перечисляем все наши трубы
        Block[] pipes = {
                ModBlocks.BRONZE_FLUID_PIPE.get(),
                ModBlocks.STEEL_FLUID_PIPE.get(),
                ModBlocks.LEAD_FLUID_PIPE.get(),
                ModBlocks.TUNGSTEN_FLUID_PIPE.get()
                // добавь остальные
        };

        for (Block pipe : pipes) {
            for (BlockState state : pipe.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel original = event.getModels().get(location);
                if (original != null) {
                    // Оборачиваем оригинальную модель в наш хардкорный движок
                    event.getModels().put(location, new PipeBakedModel(original));
                }
            }
        }
    }

    // 2. АППАРАТНАЯ РАСКРАСКА
    @SubscribeEvent
    public static void registerBlockColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            // tintIndex == 1 мы прописали в PipeBakedModel
            if (tintIndex == 1 && level != null && pos != null) {
                if (level.getBlockEntity(pos) instanceof com.cim.block.entity.fluids.FluidPipeBlockEntity be) {
                    net.minecraft.world.level.material.Fluid fluid = be.getFilterFluid();

                    if (fluid != net.minecraft.world.level.material.Fluids.EMPTY) {

                        // --- ИСКЛЮЧЕНИЕ ДЛЯ ВАНИЛЬНОЙ ЛАВЫ ---
                        if (fluid == net.minecraft.world.level.material.Fluids.LAVA || fluid == net.minecraft.world.level.material.Fluids.FLOWING_LAVA) {
                            return 0xFF5500; // Красивый огненно-оранжевый цвет
                        }
                        // --- ИСКЛЮЧЕНИЕ ДЛЯ ВАНИЛЬНОЙ ВОДЫ (на всякий случай) ---
                        if (fluid == net.minecraft.world.level.material.Fluids.WATER || fluid == net.minecraft.world.level.material.Fluids.FLOWING_WATER) {
                            return 0x3F76E4; // Стандартный синий цвет воды
                        }

                        // Для всех твоих кастомных жидкостей берем их родной цвет
                        return net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getTintColor();
                    }
                }
            }
            return -1; // -1 значит "не перекрашивать"
        }, ModBlocks.BRONZE_FLUID_PIPE.get(), ModBlocks.STEEL_FLUID_PIPE.get(), ModBlocks.LEAD_FLUID_PIPE.get(), ModBlocks.TUNGSTEN_FLUID_PIPE.get() /* Добавь остальные трубы */);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {

        // === РЕГИСТРАЦИЯ ГРАДИЕНТОВ ===
        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.RED_TO_WHITE,
                Items.IRON_INGOT,
                Items.IRON_NUGGET,
                Items.IRON_BLOCK,  // Block автоматически конвертируется в Item
                ResourceRegistry.getMainUnit("steel"),
                ResourceRegistry.getSmallUnit("steel"),
                ResourceRegistry.getBlock("steel")  // Block автоматически конвертируется в Item
        );


        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.YELLOW_TO_WHITE,
                Items.GOLD_INGOT,
                Items.GOLD_NUGGET,
                Items.GOLD_BLOCK
        );


        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.YELLOW_TO_WHITE,
                Items.COPPER_INGOT,
                Items.COPPER_BLOCK
        );


        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.RED_TO_WHITE,
                Items.NETHERITE_INGOT,
                Items.NETHERITE_BLOCK
        );


        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.ORANGE_TO_WHITE,
                ResourceRegistry.getMainUnit("aluminum"),
                ResourceRegistry.getSmallUnit("aluminum"),
                ResourceRegistry.getBlock("aluminum"),
                ResourceRegistry.getMainUnit("bronze"),
                ResourceRegistry.getSmallUnit("bronze"),
                ResourceRegistry.getBlock("bronze"),
                ResourceRegistry.getMainUnit("tin"),
                ResourceRegistry.getSmallUnit("tin"),
                ResourceRegistry.getBlock("tin"),
                ResourceRegistry.getMainUnit("zinc"),
                ResourceRegistry.getSmallUnit("zinc"),
                ResourceRegistry.getBlock("zinc")
        );

        // === БЛОКИ МЕТАЛЛОВ ===
        // Регистрируем блоки как предметы (BlockItem)
        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.RED_TO_WHITE,
                net.minecraft.world.level.block.Blocks.IRON_BLOCK,
                ResourceRegistry.getBlock("steel")
        );

        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.YELLOW_TO_WHITE,
                net.minecraft.world.level.block.Blocks.GOLD_BLOCK,
                net.minecraft.world.level.block.Blocks.COPPER_BLOCK
        );

        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.RED_TO_WHITE,
                net.minecraft.world.level.block.Blocks.NETHERITE_BLOCK
        );

        ItemHeatColorRegistry.registerMixed(ItemHeatColorRegistry.HeatGradient.ORANGE_TO_WHITE,
                ResourceRegistry.getBlock("aluminum"),
                ResourceRegistry.getBlock("bronze"),
                ResourceRegistry.getBlock("tin"),
                ResourceRegistry.getBlock("zinc")
        );

        // === РЕГИСТРАЦИЯ ОБРАБОТЧИКОВ ЦВЕТА ===
        // Стандартный обработчик для всех горячих предметов
        event.register((stack, tintIndex) -> ItemHeatColorRegistry.getHeatColor(stack, tintIndex),
                Items.IRON_INGOT, Items.IRON_NUGGET, Items.IRON_BLOCK,
                Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLD_BLOCK,
                Items.COPPER_INGOT, Items.COPPER_BLOCK,
                Items.NETHERITE_INGOT, Items.NETHERITE_BLOCK,
                ResourceRegistry.getMainUnit("steel"),
                ResourceRegistry.getSmallUnit("steel"),
                ResourceRegistry.getBlock("steel"),
                ResourceRegistry.getMainUnit("aluminum"),
                ResourceRegistry.getSmallUnit("aluminum"),
                ResourceRegistry.getBlock("aluminum"),
                ResourceRegistry.getMainUnit("zinc"),
                ResourceRegistry.getSmallUnit("zinc"),
                ResourceRegistry.getBlock("zinc"),
                ResourceRegistry.getMainUnit("tin"),
                ResourceRegistry.getSmallUnit("tin"),
                ResourceRegistry.getBlock("tin"),
                ResourceRegistry.getMainUnit("bronze"),
                ResourceRegistry.getSmallUnit("bronze"),
                ResourceRegistry.getBlock("bronze")
        );

        // Специальный обработчик для шлака
        event.register((stack, tintIndex) -> ItemHeatColorRegistry.getSlagHeatColor(stack, tintIndex),
                ModItems.SLAG.get()
        );
    }
}
