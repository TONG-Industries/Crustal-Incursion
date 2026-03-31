package com.cim.compat.irisflw.mixin.iris;

import dev.engine_room.flywheel.backend.Samplers;
import java.util.HashSet;
import java.util.Set;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(
   value = {ProgramSamplers.class},
   remap = false
)
public class MixinProgramSamplers {
   @ModifyVariable(
      method = {"builder"},
      at = @At("LOAD"),
      argsOnly = true
   )
   private static Set<Integer> modifyReservedTextureUnits(Set<Integer> var1) {
      Set<Integer> reservedTextureUnits = new HashSet();
      reservedTextureUnits.addAll(var1);
      reservedTextureUnits.add(Samplers.LIGHT_LUT.number);
      reservedTextureUnits.add(Samplers.LIGHT_SECTIONS.number);
      return reservedTextureUnits;
   }
}
