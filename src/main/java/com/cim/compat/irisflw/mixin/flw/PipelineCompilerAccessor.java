package com.cim.compat.irisflw.mixin.flw;

import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.compile.component.UberShaderComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   value = {PipelineCompiler.class},
   remap = false
)
public interface PipelineCompilerAccessor {
   @Accessor("FOG")
   static UberShaderComponent GetFOG() {
      throw new AssertionError();
   }

   @Accessor("CUTOUT")
   static UberShaderComponent GetCUTOUT() {
      throw new AssertionError();
   }
}
