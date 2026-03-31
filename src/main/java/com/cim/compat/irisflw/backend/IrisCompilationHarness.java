package com.cim.compat.irisflw.backend;

import dev.engine_room.flywheel.backend.compile.ContextShader;
import dev.engine_room.flywheel.backend.compile.core.CompilationHarness;
import dev.engine_room.flywheel.backend.compile.core.ShaderCache;
import dev.engine_room.flywheel.backend.compile.core.CompilationHarness.KeyCompiler;
import dev.engine_room.flywheel.backend.gl.GlObject;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import java.util.HashMap;
import java.util.Map;

public class IrisCompilationHarness<K> extends CompilationHarness<K> {
   private final ShaderSources sources;
   private final KeyCompiler<K> compiler;
   private final ShaderCache shaderCache;
   private final IrisProgramLinker programLinker;
   private final Map<K, GlProgram> programs = new HashMap();

   public IrisCompilationHarness(String marker, ShaderSources sources, KeyCompiler<K> compiler) {
      super(marker, sources, compiler);
      this.sources = sources;
      this.compiler = compiler;
      this.shaderCache = new IrisShaderCache();
      this.programLinker = new IrisProgramLinker();
   }

   public GlProgram get(K key) {
      return (GlProgram)this.programs.computeIfAbsent(key, this::compile);
   }

   private GlProgram compile(K key) {
      if (key instanceof IrisPipelineCompiler.PipelineProgramKey) {
         IrisPipelineCompiler.PipelineProgramKey pipelineKey = (IrisPipelineCompiler.PipelineProgramKey)key;
         this.programLinker.contextShader = pipelineKey.contextShader();
      } else {
         this.programLinker.contextShader = ContextShader.DEFAULT;
      }

      return this.compiler.compile(key, this.sources, this.shaderCache, this.programLinker);
   }

   public void delete() {
      super.delete();
      this.shaderCache.delete();
      this.programs.values().forEach(GlObject::delete);
      this.programs.clear();
   }
}
