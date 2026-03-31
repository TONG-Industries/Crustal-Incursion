package com.cim.compat.irisflw.mixin.iris;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.cim.compat.irisflw.accessors.IrisRenderingPipelineAccessor;

@Mixin({IrisRenderingPipeline.class})
public abstract class MixinIrisRenderingPipeline implements IrisRenderingPipelineAccessor {
   @Unique
   private ProgramSet programSet;

   public ProgramSet getProgramSet() {
      return this.programSet;
   }

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")},
      remap = false
   )
   public void initSet(ProgramSet set, CallbackInfo callbackInfo) {
      this.programSet = set;
   }

   @Invoker(
      remap = false
   )
   public abstract ShaderInstance callCreateShader(String var1, ProgramSource var2, ProgramId var3, AlphaTest var4, VertexFormat var5, FogMode var6, boolean var7, boolean var8, boolean var9, boolean var10) throws IOException;

   @Invoker(
      remap = false
   )
   public abstract ShaderInstance callCreateShadowShader(String var1, ProgramSource var2, ProgramId var3, AlphaTest var4, VertexFormat var5, boolean var6, boolean var7, boolean var8) throws IOException;
}
