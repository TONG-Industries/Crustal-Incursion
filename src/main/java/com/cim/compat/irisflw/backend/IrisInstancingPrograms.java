package com.cim.compat.irisflw.backend;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.backend.compile.ContextShader;
import dev.engine_room.flywheel.backend.compile.OitPrograms;
import dev.engine_room.flywheel.backend.compile.PipelineCompiler.OitMode;
import dev.engine_room.flywheel.backend.gl.GlCompat;
import dev.engine_room.flywheel.backend.glsl.GlslVersion;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import dev.engine_room.flywheel.backend.glsl.SourceComponent;
import dev.engine_room.flywheel.backend.util.AtomicReferenceCounted;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import com.cim.compat.irisflw.flywheel.IrisFlwCompatGlProgramBase;

public class IrisInstancingPrograms extends AtomicReferenceCounted {
   private static final List<String> EXTENSIONS;
   @Nullable
   private static IrisInstancingPrograms instance;
   private final IrisPipelineCompiler pipeline;
   private final OitPrograms oitPrograms;

   private IrisInstancingPrograms(IrisPipelineCompiler pipeline, OitPrograms oitPrograms) {
      this.pipeline = pipeline;
      this.oitPrograms = oitPrograms;
   }

   private static List<String> getExtensions(GlslVersion glslVersion) {
      Builder<String> extensions = ImmutableList.builder();
      if (glslVersion.compareTo(GlslVersion.V330) < 0) {
         extensions.add("GL_ARB_shader_bit_encoding");
      }

      return extensions.build();
   }

   public static void reload(ShaderSources sources, List<SourceComponent> vertexComponents, List<SourceComponent> fragmentComponents) {
      if (GlCompat.SUPPORTS_INSTANCING) {
         IrisPipelineCompiler pipelineCompiler = IrisPipelineCompiler.create(sources, IrisFlwPipelines.IRIS_INSTANCING, vertexComponents, fragmentComponents, EXTENSIONS);
         OitPrograms fullscreen = OitPrograms.createFullscreenCompiler(sources);
         IrisInstancingPrograms newInstance = new IrisInstancingPrograms(pipelineCompiler, fullscreen);
         setInstance(newInstance);
      }
   }

   public static void setInstance(@Nullable IrisInstancingPrograms newInstance) {
      if (instance != null) {
         instance.release();
      }

      if (newInstance != null) {
         newInstance.acquire();
      }

      instance = newInstance;
   }

   @Nullable
   public static IrisInstancingPrograms get() {
      return instance;
   }

   public static boolean allLoaded() {
      return instance != null;
   }

   public static void kill() {
      setInstance((IrisInstancingPrograms)null);
   }

   public IrisFlwCompatGlProgramBase get(InstanceType<?> instanceType, ContextShader contextShader, Material material, OitMode mode, boolean isShadow) {
      return (IrisFlwCompatGlProgramBase)this.pipeline.get(instanceType, contextShader, material, mode, isShadow);
   }

   public OitPrograms oitPrograms() {
      return this.oitPrograms;
   }

   protected void _delete() {
      this.pipeline.delete();
      this.oitPrograms.delete();
   }

   static {
      EXTENSIONS = getExtensions(GlCompat.MAX_GLSL_VERSION);
   }
}
