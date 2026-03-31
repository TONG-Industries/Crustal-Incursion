package com.cim.compat.irisflw.backend;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.material.CutoutShader;
import dev.engine_room.flywheel.api.material.FogShader;
import dev.engine_room.flywheel.api.material.LightShader;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import dev.engine_room.flywheel.backend.BackendConfig;
import dev.engine_room.flywheel.backend.MaterialShaderIndices;
import dev.engine_room.flywheel.backend.Samplers;
import dev.engine_room.flywheel.backend.compile.ContextShader;
import dev.engine_room.flywheel.backend.compile.Pipeline;
import dev.engine_room.flywheel.backend.compile.PipelineCompiler.OitMode;
import dev.engine_room.flywheel.backend.compile.component.InstanceStructComponent;
import dev.engine_room.flywheel.backend.compile.core.CompilationHarness;
import dev.engine_room.flywheel.backend.compile.core.Compile;
import dev.engine_room.flywheel.backend.compile.core.Compile.ProgramStitcher;
import dev.engine_room.flywheel.backend.engine.uniform.FrameUniforms;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.backend.gl.GlCompat;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import dev.engine_room.flywheel.backend.glsl.GlslVersion;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import dev.engine_room.flywheel.backend.glsl.SourceComponent;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.util.ResourceUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import com.cim.compat.irisflw.IrisFlw;
import com.cim.compat.irisflw.mixin.flw.PipelineCompilerAccessor;

public class IrisPipelineCompiler {
   private static final Set<IrisPipelineCompiler> ALL = Collections.newSetFromMap(new WeakHashMap());
   private static final Compile<IrisPipelineCompiler.PipelineProgramKey> PIPELINE = new Compile();
   private static final ResourceLocation API_IMPL_VERT = ResourceUtil.rl("internal/api_impl.vert");
   private static final ResourceLocation API_IMPL_FRAG = ResourceUtil.rl("internal/api_impl.frag");
   private final CompilationHarness<IrisPipelineCompiler.PipelineProgramKey> harness;

   public IrisPipelineCompiler(CompilationHarness<IrisPipelineCompiler.PipelineProgramKey> harness) {
      this.harness = harness;
      ALL.add(this);
   }

   public GlProgram get(InstanceType<?> instanceType, ContextShader contextShader, Material material, OitMode oit, boolean isShadow) {
      LightShader light = material.light();
      CutoutShader cutout = material.cutout();
      MaterialShaders shaders = material.shaders();
      FogShader fog = material.fog();
      MaterialShaderIndices.fogSources().index(fog.source());
      MaterialShaderIndices.cutoutSources().index(cutout.source());
      return this.harness.get(new IrisPipelineCompiler.PipelineProgramKey(instanceType, contextShader, light, shaders, cutout != CutoutShaders.OFF, FrameUniforms.debugOn(), oit, isShadow));
   }

   public void delete() {
      this.harness.delete();
   }

   public static void deleteAll() {
      ALL.forEach(IrisPipelineCompiler::delete);
   }

   static IrisPipelineCompiler create(ShaderSources sources, Pipeline pipeline, List<SourceComponent> vertexComponents, List<SourceComponent> fragmentComponents, Collection<String> extensions) {
      ProgramStitcher<IrisPipelineCompiler.PipelineProgramKey> harnessStitcher = PIPELINE.program().link(PIPELINE.shader(GlCompat.MAX_GLSL_VERSION, ShaderType.VERTEX).nameMapper((key) -> {
         String instance = ResourceUtil.toDebugFileNameNoExtension(key.instanceType().vertexShader());
         String material = ResourceUtil.toDebugFileNameNoExtension(key.materialShaders().vertexSource());
         String context = key.contextShader().nameLowerCase();
         String debug = key.debugEnabled() ? "_debug" : "";
         return "pipeline/" + pipeline.compilerMarker() + "/" + instance + "/" + material + "_" + context + debug;
      }).requireExtensions(extensions).onCompile((rl, compilation) -> {
         if (GlCompat.MAX_GLSL_VERSION.compareTo(GlslVersion.V400) < 0 && !extensions.contains("GL_ARB_gpu_shader5")) {
            compilation.define("fma(a, b, c) ((a) * (b) + (c))");
         }

      }).onCompile((key, comp) -> {
         key.contextShader().onCompile(comp);
      }).onCompile((key, comp) -> {
         BackendConfig.INSTANCE.lightSmoothness().onCompile(comp);
      }).onCompile((key, comp) -> {
         if (key.debugEnabled()) {
            comp.define("_FLW_DEBUG");
         }

      }).withResource(API_IMPL_VERT).withComponent((key) -> {
         return new InstanceStructComponent(key.instanceType());
      }).withResource((key) -> {
         return key.instanceType().vertexShader();
      }).withResource((key) -> {
         return key.materialShaders().vertexSource();
      }).withComponents(vertexComponents).withResource(($) -> {
         return IrisFlw.isUsingExtendedVertexFormat() ? IrisInternalVertex.EXT_LAYOUT_SHADER : IrisInternalVertex.LAYOUT_SHADER;
      }).withComponent((key) -> {
         return pipeline.assembler().assemble(key.instanceType());
      }).withResource(pipeline.vertexMain())).link(PIPELINE.shader(GlCompat.MAX_GLSL_VERSION, ShaderType.FRAGMENT).nameMapper((key) -> {
         String context = key.contextShader().nameLowerCase();
         String material = ResourceUtil.toDebugFileNameNoExtension(key.materialShaders().fragmentSource());
         String light = ResourceUtil.toDebugFileNameNoExtension(key.light().source());
         String debug = key.debugEnabled() ? "_debug" : "";
         String cutout = key.useCutout() ? "_cutout" : "";
         String oit = key.oit().name;
         return "pipeline/" + pipeline.compilerMarker() + "/frag/" + material + "/" + light + "_" + context + cutout + debug + oit;
      }).requireExtensions(extensions).enableExtension("GL_ARB_conservative_depth").onCompile((rl, compilation) -> {
         if (GlCompat.MAX_GLSL_VERSION.compareTo(GlslVersion.V400) < 0 && !extensions.contains("GL_ARB_gpu_shader5")) {
            compilation.define("fma(a, b, c) ((a) * (b) + (c))");
         }

      }).onCompile((key, comp) -> {
         key.contextShader().onCompile(comp);
      }).onCompile((key, comp) -> {
         BackendConfig.INSTANCE.lightSmoothness().onCompile(comp);
      }).onCompile((key, comp) -> {
         if (key.debugEnabled()) {
            comp.define("_FLW_DEBUG");
         }

      }).onCompile((key, comp) -> {
         if (key.useCutout()) {
            comp.define("_FLW_USE_DISCARD");
         }

      }).onCompile((key, comp) -> {
         if (key.oit() != OitMode.OFF) {
            comp.define("_FLW_OIT");
            comp.define(key.oit().define);
         }

      }).withResource(API_IMPL_FRAG).withResource((key) -> {
         return key.materialShaders().fragmentSource();
      }).withComponents(fragmentComponents).withComponent((key) -> {
         return PipelineCompilerAccessor.GetFOG();
      }).withResource((key) -> {
         return key.light().source();
      }).with((key, fetcher) -> {
         return (SourceComponent)(key.useCutout() ? PipelineCompilerAccessor.GetCUTOUT() : fetcher.get(CutoutShaders.OFF.source()));
      }).withResource(pipeline.fragmentMain())).preLink((key, program) -> {
         program.bindAttribLocation("_flw_aPos", 0);
         program.bindAttribLocation("_flw_aColor", 1);
         program.bindAttribLocation("_flw_aTexCoord", 2);
         program.bindAttribLocation("_flw_aOverlay", 3);
         program.bindAttribLocation("_flw_aLight", 4);
         program.bindAttribLocation("_flw_aNormal", 5);
      }).postLink((key, program) -> {
         Uniforms.setUniformBlockBindings(program);
         program.bind();
         program.setSamplerBinding("flw_diffuseTex", Samplers.DIFFUSE);
         program.setSamplerBinding("flw_overlayTex", Samplers.OVERLAY);
         program.setSamplerBinding("flw_lightTex", Samplers.LIGHT);
         program.setSamplerBinding("_flw_depthRange", Samplers.DEPTH_RANGE);
         program.setSamplerBinding("_flw_coefficients", Samplers.COEFFICIENTS);
         program.setSamplerBinding("_flw_blueNoise", Samplers.NOISE);
         pipeline.onLink().accept(program);
         key.contextShader().onLink(program);
         GlProgram.unbind();
      });
      IrisCompilationHarness<IrisPipelineCompiler.PipelineProgramKey> harness = new IrisCompilationHarness(pipeline.compilerMarker(), sources, harnessStitcher);
      return new IrisPipelineCompiler(harness);
   }

   public static record PipelineProgramKey(InstanceType<?> instanceType, ContextShader contextShader, LightShader light, MaterialShaders materialShaders, boolean useCutout, boolean debugEnabled, OitMode oit, boolean isShadow) {
      public PipelineProgramKey(InstanceType<?> instanceType, ContextShader contextShader, LightShader light, MaterialShaders materialShaders, boolean useCutout, boolean debugEnabled, OitMode oit, boolean isShadow) {
         this.instanceType = instanceType;
         this.contextShader = contextShader;
         this.light = light;
         this.materialShaders = materialShaders;
         this.useCutout = useCutout;
         this.debugEnabled = debugEnabled;
         this.oit = oit;
         this.isShadow = isShadow;
      }

      public InstanceType<?> instanceType() {
         return this.instanceType;
      }

      public ContextShader contextShader() {
         return this.contextShader;
      }

      public LightShader light() {
         return this.light;
      }

      public MaterialShaders materialShaders() {
         return this.materialShaders;
      }

      public boolean useCutout() {
         return this.useCutout;
      }

      public boolean debugEnabled() {
         return this.debugEnabled;
      }

      public OitMode oit() {
         return this.oit;
      }

      public boolean isShadow() {
         return this.isShadow;
      }
   }
}
