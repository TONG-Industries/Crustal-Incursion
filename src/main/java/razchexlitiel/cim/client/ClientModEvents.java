package razchexlitiel.cim.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import razchexlitiel.cim.block.entity.ModBlockEntities;
import razchexlitiel.cim.client.config.ModConfigKeybindHandler;
import razchexlitiel.cim.client.gecko.block.rotation.MotorElectroRenderer;
import razchexlitiel.cim.client.gecko.block.rotation.ShaftRenderer;
import razchexlitiel.cim.client.gecko.block.rotation.WindGenFlugerRenderer;
import razchexlitiel.cim.client.gecko.block.turrets.TurretLightPlacerRenderer;
import razchexlitiel.cim.client.gecko.entity.bullets.TurretBulletRenderer;
import razchexlitiel.cim.client.gecko.entity.mobs.DepthWormRenderer;
import razchexlitiel.cim.client.gecko.entity.turrets.TurretLightLinkedRenderer;
import razchexlitiel.cim.client.gecko.entity.turrets.TurretLightRenderer;
import razchexlitiel.cim.client.loader.ProceduralWireLoader;
import razchexlitiel.cim.client.overlay.gui.GUIMotorElectro;
import razchexlitiel.cim.client.overlay.gui.GUITurretAmmo;
import razchexlitiel.cim.client.overlay.hud.OverlayAmmoHud;
import razchexlitiel.cim.client.overlay.gui.GUIMachineBattery;
import razchexlitiel.cim.client.renderer.ClientRenderHandler;
import razchexlitiel.cim.entity.ModEntities;
import razchexlitiel.cim.item.ModItems;
import razchexlitiel.cim.main.CrustalIncursionMod;
import razchexlitiel.cim.menu.ModMenuTypes;

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
        MenuScreens.register(ModMenuTypes.MOTOR_ELECTRO_MENU.get(), GUIMotorElectro::new);
        MenuScreens.register(ModMenuTypes.TURRET_AMMO_MENU.get(), GUITurretAmmo::new);


        BlockEntityRenderers.register(ModBlockEntities.MOTOR_ELECTRO_BE.get(), MotorElectroRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.SHAFT_BLOCK_BE.get(), ShaftRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.WIND_GEN_FLUGER_BE.get(), WindGenFlugerRenderer::new);
        BlockEntityRenderers.register(ModBlockEntities.TURRET_LIGHT_PLACER_BE.get(), TurretLightPlacerRenderer::new);



    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityRenderers.register(ModEntities.TURRET_BULLET.get(), TurretBulletRenderer::new);
        EntityRenderers.register(ModEntities.DEPTH_WORM.get(), DepthWormRenderer::new);
        event.registerEntityRenderer(ModEntities.TURRET_LIGHT.get(), TurretLightRenderer::new);
        EntityRenderers.register(ModEntities.TURRET_LIGHT_LINKED.get(), TurretLightLinkedRenderer::new);
    }

    @SubscribeEvent
    public static void onModelRegister(ModelEvent.RegisterGeometryLoaders event) {
        event.register("procedural_wire", new ProceduralWireLoader());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MinecraftForge.EVENT_BUS.register(ModConfigKeybindHandler.class);

    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "ammo_hud", OverlayAmmoHud.HUD_AMMO);
    }
}
