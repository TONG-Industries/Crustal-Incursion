package com.cim.compat.irisflw.mixin.iris;

import java.util.Optional;
import java.util.function.Function;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.cim.compat.irisflw.accessors.ProgramSetAccessor;

@Mixin({ProgramSet.class})
public abstract class ProgramSetMixin implements ProgramSetAccessor {
   private ProgramSource gbuffersFlw;
   private ProgramSource shadowFlw;

   @Invoker(
      remap = false
   )
   public abstract ProgramSource callReadProgramSource(AbsolutePackPath var1, Function<AbsolutePackPath, String> var2, String var3, ProgramSet var4, ShaderProperties var5, boolean var6);

   @Invoker(
      remap = false
   )
   public abstract ProgramSource callReadProgramSource(AbsolutePackPath var1, Function<AbsolutePackPath, String> var2, String var3, ProgramSet var4, ShaderProperties var5, BlendModeOverride var6, boolean var7);

   @Inject(
      method = {"<init>"},
      remap = false,
      at = {@At("RETURN")}
   )
   private void initGBufferFlw(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider, ShaderProperties shaderProperties, ShaderPack pack, CallbackInfo ci) {
      FeatureFlags tessellationFlag = FeatureFlags.getValue("TESSELLATION_SHADERS");
      if (tessellationFlag == FeatureFlags.UNKNOWN) {
         tessellationFlag = FeatureFlags.getValue("TESSELATION_SHADERS");
      }

      boolean readTessellation = pack.hasFeature(tessellationFlag);
       this.gbuffersFlw = this.callReadProgramSource(directory, sourceProvider, "gbuffers_flw", (ProgramSet) (Object) this, shaderProperties, readTessellation);
       this.shadowFlw = this.callReadProgramSource(directory, sourceProvider, "shadow_flw", (ProgramSet) (Object) this, shaderProperties, BlendModeOverride.OFF, readTessellation);
   }

   public Optional<ProgramSource> getGbuffersFlw() {
      return this.gbuffersFlw.requireValid();
   }

   public Optional<ProgramSource> getShadowFlw() {
      return this.shadowFlw.requireValid();
   }
}
