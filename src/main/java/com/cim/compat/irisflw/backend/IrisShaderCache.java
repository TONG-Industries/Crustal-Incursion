package com.cim.compat.irisflw.backend;

import dev.engine_room.flywheel.backend.compile.core.Compilation;
import dev.engine_room.flywheel.backend.compile.core.ShaderCache;
import dev.engine_room.flywheel.backend.compile.core.ShaderResult;
import dev.engine_room.flywheel.backend.compile.core.ShaderResult.Success;
import dev.engine_room.flywheel.backend.gl.GlObject;
import dev.engine_room.flywheel.backend.gl.shader.GlShader;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import dev.engine_room.flywheel.backend.glsl.GlslVersion;
import dev.engine_room.flywheel.backend.glsl.SourceComponent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class IrisShaderCache extends ShaderCache {
   private final Map<IrisShaderCache.ShaderKey, ShaderResult> inner = new HashMap();

   public GlShader compile(GlslVersion glslVersion, ShaderType shaderType, String name, Consumer<Compilation> callback, List<SourceComponent> sourceComponents) {
      IrisShaderCache.ShaderKey key = new IrisShaderCache.ShaderKey(glslVersion, shaderType, name);
      ShaderResult cached = (ShaderResult)this.inner.get(key);
      if (cached != null) {
         return cached.unwrap();
      } else {
         Compilation ctx = new IrisCompilation();
         ctx.version(glslVersion);
         ctx.define(shaderType.define);
         callback.accept(ctx);
         Objects.requireNonNull(ctx);
         expand(sourceComponents, ctx::appendComponent);
         ShaderResult out = ctx.compile(shaderType, name);
         this.inner.put(key, out);
         return out.unwrap();
      }
   }

   public void delete() {
      this.inner.values().stream().filter((r) -> {
         return r instanceof Success;
      }).map(ShaderResult::unwrap).forEach(GlObject::delete);
      this.inner.clear();
   }

   private static void expand(List<SourceComponent> rootSources, Consumer<SourceComponent> out) {
      LinkedHashSet<SourceComponent> included = new LinkedHashSet();
      Iterator var3 = rootSources.iterator();

      while(var3.hasNext()) {
         SourceComponent component = (SourceComponent)var3.next();
         recursiveDepthFirstInclude(included, component);
         included.add(component);
      }

      included.forEach(out);
   }

   private static void recursiveDepthFirstInclude(Set<SourceComponent> included, SourceComponent component) {
      Iterator var2 = component.included().iterator();

      while(var2.hasNext()) {
         SourceComponent include = (SourceComponent)var2.next();
         recursiveDepthFirstInclude(included, include);
      }

      included.addAll(component.included());
   }

   private static record ShaderKey(GlslVersion glslVersion, ShaderType shaderType, String name) {
      private ShaderKey(GlslVersion glslVersion, ShaderType shaderType, String name) {
         this.glslVersion = glslVersion;
         this.shaderType = shaderType;
         this.name = name;
      }

      public GlslVersion glslVersion() {
         return this.glslVersion;
      }

      public ShaderType shaderType() {
         return this.shaderType;
      }

      public String name() {
         return this.name;
      }
   }
}
