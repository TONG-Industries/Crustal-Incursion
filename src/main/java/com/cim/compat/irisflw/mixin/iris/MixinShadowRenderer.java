package com.cim.compat.irisflw.mixin.iris;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.event.RenderContextImpl;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.cim.compat.irisflw.flywheel.RenderLayerEventStateManager;

@Mixin({ShadowRenderer.class})
public abstract class MixinShadowRenderer {
   @Final
   @Shadow
   private boolean shouldRenderBlockEntities;
   @Shadow(
      remap = false
   )
   @Final
   private float sunPathRotation;
   @Shadow(
      remap = false
   )
   @Final
   private float intervalSize;
   @Final
   @Shadow
   private RenderBuffers buffers;

   @Shadow(
      remap = false
   )
   public static PoseStack createShadowModelView(float sunPathRotation, float intervalSize) {
      return null;
   }

    @Inject(
            method = {"renderShadows"},
            remap = false,
            at = {@At("HEAD")}
    )
    private void injectRenderShadow(LevelRendererAccessor levelRendererAccessor, Camera camera, CallbackInfo ci) {
        if (this.shouldRenderBlockEntities) {
            RenderLayerEventStateManager.setRenderingShadow(true);
        }
    }

    @Inject(
            method = {"renderShadows"},
            remap = false,
            at = {@At("TAIL")}
    )
    private void injectRenderShadowTail(LevelRendererAccessor levelRendererAccessor, Camera camera, CallbackInfo ci) {
        RenderLayerEventStateManager.setRenderingShadow(false);
    }

    @Inject(
            method = {"renderShadows"},
            remap = false,
            at = {@At(
                    value = "INVOKE_STRING",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;m_6182_(Ljava/lang/String;)V",
                    args = {"ldc=draw entities"}
            )}
    )
    private void injectRenderShadowBeforeDrawEntities(LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci) {
        if (this.shouldRenderBlockEntities) {
            PoseStack poseStack = createShadowModelView(this.sunPathRotation, this.intervalSize);
            RenderContextImpl flywheel$renderContext = RenderContextImpl.create((LevelRenderer)levelRenderer, levelRenderer.getLevel(), this.buffers, poseStack, ShadowRenderer.PROJECTION, playerCamera, CapturedRenderingState.INSTANCE.getTickDelta());
            VisualizationManager manager = VisualizationManager.get(levelRenderer.getLevel());
            if (manager != null) {
                manager.renderDispatcher().afterEntities(flywheel$renderContext);
            }
        }
    }
}
