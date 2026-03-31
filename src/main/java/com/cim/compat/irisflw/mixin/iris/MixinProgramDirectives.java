package com.cim.compat.irisflw.mixin.iris;

import java.util.Optional;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.cim.compat.irisflw.accessors.ProgramDirectivesAccessor;

@Mixin(
   value = {ProgramDirectives.class},
   remap = false
)
public class MixinProgramDirectives implements ProgramDirectivesAccessor {
   @Unique
   private AlphaTest flwAlphaTestOverride;

   public void setFlwAlphaTestOverride(AlphaTest alphaTest) {
      this.flwAlphaTestOverride = alphaTest;
   }

   @Inject(
      method = {"getAlphaTestOverride"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void injectAlphaTestOverride(CallbackInfoReturnable<Optional<AlphaTest>> cir) {
      if (this.flwAlphaTestOverride != null) {
         cir.setReturnValue(Optional.of(this.flwAlphaTestOverride));
      }

   }
}
