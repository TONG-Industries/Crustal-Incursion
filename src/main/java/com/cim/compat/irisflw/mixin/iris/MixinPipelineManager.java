package com.cim.compat.irisflw.mixin.iris;

import net.irisshaders.iris.pipeline.PipelineManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.cim.compat.irisflw.backend.IrisPipelineCompiler;

@Mixin({PipelineManager.class})
public class MixinPipelineManager {
   @Inject(
      method = {"destroyPipeline"},
      at = {@At("TAIL")},
      remap = false
   )
   public void irisflw$destroyPipeline(CallbackInfo ci) {
      IrisPipelineCompiler.deleteAll();
   }
}
