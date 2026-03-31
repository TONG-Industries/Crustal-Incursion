package com.cim.compat.irisflw.backend;

import dev.engine_room.flywheel.backend.compile.ContextShader;
import dev.engine_room.flywheel.backend.compile.core.LinkResult;
import dev.engine_room.flywheel.backend.compile.core.ProgramLinker;
import dev.engine_room.flywheel.backend.compile.core.LinkResult.Failure;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import dev.engine_room.flywheel.backend.gl.shader.GlShader;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;
import com.cim.compat.irisflw.IrisFlw;
import com.cim.compat.irisflw.accessors.IrisRenderingPipelineAccessor;
import com.cim.compat.irisflw.accessors.ProgramDirectivesAccessor;
import com.cim.compat.irisflw.accessors.ProgramSourceAccessor;
import com.cim.compat.irisflw.flywheel.IrisFlwCompatGlProgram;
import com.cim.compat.irisflw.flywheel.IrisFlwCompatGlProgramBase;
import com.cim.compat.irisflw.flywheel.RenderLayerEventStateManager;
import com.cim.compat.irisflw.transformer.GlslTransformerFragPatcher;
import com.cim.compat.irisflw.transformer.GlslTransformerVertPatcher;

public class IrisProgramLinker extends ProgramLinker {
   private final Map<ProgramSet, ProgramFallbackResolver> resolvers = new HashMap();
   private final Iterable<StringPair> environmentDefines;
   private final GlslTransformerVertPatcher vertPatcher;
   public static final boolean PATCH_FRAG = false;
   private GlslTransformerFragPatcher fragPatcher;
   public ContextShader contextShader;
   static int programCounter;

   public IrisProgramLinker() {
      this.contextShader = ContextShader.DEFAULT;
      this.environmentDefines = StandardMacros.createStandardEnvironmentDefines();
      this.vertPatcher = new GlslTransformerVertPatcher();
   }

   public GlProgram link(List<GlShader> shaders, Consumer<GlProgram> preLink) {
      LinkResult linkResult = this.linkInternal(shaders, preLink);
      return (GlProgram)(linkResult != null && !(linkResult instanceof Failure) ? linkResult.unwrap() : IrisFlwCompatGlProgramBase.Invalid.INSTANCE);
   }

   private LinkResult linkInternal(List<GlShader> shaders, Consumer<GlProgram> preLink) {
      int handle = GL20.glCreateProgram();
      WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
      String vertexSource = null;
      String vertexShaderName = null;
      String fragSource = null;
      String fragShaderName = null;
      Iterator var9 = shaders.iterator();

      while(var9.hasNext()) {
         GlShader shader = (GlShader)var9.next();
         if (shader instanceof IntermediateGlShader) {
            IntermediateGlShader intermediateGlShader = (IntermediateGlShader)shader;
            if (intermediateGlShader.type == ShaderType.VERTEX) {
               vertexSource = intermediateGlShader.getSource();
               vertexShaderName = intermediateGlShader.getName();
            } else if (intermediateGlShader.type == ShaderType.FRAGMENT) {
               fragSource = intermediateGlShader.getSource();
               fragShaderName = intermediateGlShader.getName();
            }
         }
      }

      if (pipeline instanceof IrisRenderingPipeline) {
         IrisRenderingPipeline newPipeline = (IrisRenderingPipeline)pipeline;
         if (vertexSource != null && vertexShaderName != null && fragSource != null && fragShaderName != null) {
            return this.getIrisShaderLinkResult((IrisRenderingPipelineAccessor)newPipeline, vertexShaderName, vertexSource, fragShaderName, fragSource);
         }
      }

      GlProgram out = new GlProgram(handle);
      Iterator var12 = shaders.iterator();

      while(var12.hasNext()) {
         GlShader shader = (GlShader)var12.next();
         GL20.glAttachShader(handle, shader.handle());
      }

      preLink.accept(out);
      GL20.glLinkProgram(handle);
      String log = GL20.glGetProgramInfoLog(handle);
      if (linkSuccessful(handle)) {
         return LinkResult.success(out, log);
      } else {
         out.delete();
         return LinkResult.failure(log);
      }
   }

   @Nullable
   private LinkResult getIrisShaderLinkResult(IrisRenderingPipelineAccessor newPipeline, String vertexName, String vertexSource, String fragName, String newFragSource) {
      ProgramSet programSet = newPipeline.getProgramSet();
      boolean isShadow = RenderLayerEventStateManager.isRenderingShadow();
      boolean isEmbedded = this.contextShader == ContextShader.EMBEDDED;
      Optional<ProgramSource> sourceReferenceOpt = this.getProgramSourceReference(programSet, vertexName, isShadow, isEmbedded);
      if (sourceReferenceOpt.isEmpty()) {
         return null;
      } else {
         ProgramSource sourceRef = (ProgramSource)sourceReferenceOpt.get();
         String vertexRef = (String)sourceRef.getVertexSource().orElseThrow();
         String fragRef = (String)sourceRef.getFragmentSource().orElseThrow();
         String newVertexSource = this.vertPatcher.patch(vertexRef, vertexSource, isShadow, isEmbedded, IrisFlw.isUsingExtendedVertexFormat());
         newVertexSource = JcppProcessor.glslPreprocessSource(newVertexSource, this.environmentDefines);
         String shaderName = vertexName + "_" + fragName;
         ProgramSource newProgramSource = this.programSourceOverrideVertexSource(shaderName, programSet, sourceRef, newVertexSource, fragRef);
         ((ProgramDirectivesAccessor)newProgramSource.getDirectives()).setFlwAlphaTestOverride(new AlphaTest(AlphaTestFunction.GREATER, 0.5F));
         return this.createWorldProgramBySource(shaderName, isShadow, newPipeline, newProgramSource);
      }
   }

   private static boolean linkSuccessful(int handle) {
      return GL20.glGetProgrami(handle, 35714) == 1;
   }

   protected LinkResult createWorldProgramBySource(String name, boolean isShadow, IrisRenderingPipelineAccessor pipeline, ProgramSource processedSource) {
      ShaderInstance override = null;

      try {
         if (isShadow) {
            override = pipeline.callCreateShadowShader(this.getFlwShaderName(name, true), processedSource, ProgramId.Block, AlphaTest.ALWAYS, IrisVertexFormats.TERRAIN, false, false, false);
         } else {
            override = pipeline.callCreateShader(this.getFlwShaderName(name, false), processedSource, ProgramId.Block, AlphaTest.ALWAYS, IrisVertexFormats.TERRAIN, FogMode.OFF, false, false, false, false);
         }
      } catch (Exception var7) {
         IrisFlw.LOGGER.error("Fail to compile shader", var7);
         return LinkResult.failure(var7.toString());
      }

      return override != null ? LinkResult.success(new IrisFlwCompatGlProgram(override, ShaderType.VERTEX, name), "") : null;
   }

   private String getFlwShaderName(String shaderName, boolean isShadow) {
      String randomId = String.valueOf(programCounter);
      ++programCounter;
      return isShadow ? String.format("shadow_flw_%s_%s", shaderName, randomId) : String.format("gbuffers_flw_%s_%s", shaderName, randomId);
   }

   @NotNull
   protected ProgramSource programSourceOverrideVertexSource(String shaderName, ProgramSet programSet, ProgramSource source, String vertexSource, String fragSource) {
      ShaderProperties properties = ((ProgramSourceAccessor)source).getShaderProperties();
      BlendModeOverride blendModeOverride = ((ProgramSourceAccessor)source).getBlendModeOverride();
      return new ProgramSource(source.getName() + "_" + shaderName, vertexSource, (String)source.getGeometrySource().orElse(null), (String)source.getTessControlSource().orElse(null), (String)source.getTessEvalSource().orElse(null), fragSource, programSet, properties, blendModeOverride);
   }

   protected Optional<ProgramSource> getProgramSourceReference(ProgramSet programSet, String flwShaderName, boolean isShadow, boolean isEmbedded) {
      ProgramFallbackResolver resolver = (ProgramFallbackResolver)this.resolvers.computeIfAbsent(programSet, ProgramFallbackResolver::new);
      if (isShadow) {
         ProgramSource shadow = (ProgramSource)resolver.resolve(ProgramId.Shadow).orElse(null);
         if (shadow == null) {
            return Optional.empty();
         } else {
            ShaderProperties properties = ((ProgramSourceAccessor)shadow).getShaderProperties();
            BlendModeOverride blendModeOverride = ((ProgramSourceAccessor)shadow).getBlendModeOverride();
            return Optional.of(new ProgramSource("shadow_flw", (String)shadow.getVertexSource().orElseThrow(), (String)shadow.getGeometrySource().orElse(null), (String)null, (String)null, (String)shadow.getFragmentSource().orElseThrow(), programSet, properties, blendModeOverride));
         }
      } else {
         ProgramId refProgramId = ProgramId.Block;
         if (isEmbedded) {
            refProgramId = ProgramId.Terrain;
         }

         ProgramSource refProgram = (ProgramSource)resolver.resolve(refProgramId).orElse(null);
         if (refProgram == null) {
            return Optional.empty();
         } else {
            ShaderProperties properties = ((ProgramSourceAccessor)refProgram).getShaderProperties();
            BlendModeOverride blendModeOverride = ((ProgramSourceAccessor)refProgram).getBlendModeOverride();
            return Optional.of(new ProgramSource("gbuffer_flw", (String)refProgram.getVertexSource().orElseThrow(), (String)refProgram.getGeometrySource().orElse(null), (String)null, (String)null, (String)refProgram.getFragmentSource().orElseThrow(), programSet, properties, blendModeOverride));
         }
      }
   }
}
