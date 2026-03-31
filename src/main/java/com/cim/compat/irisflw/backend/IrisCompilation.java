package com.cim.compat.irisflw.backend;

import dev.engine_room.flywheel.backend.compile.core.Compilation;
import dev.engine_room.flywheel.backend.compile.core.ShaderResult;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import com.cim.compat.irisflw.mixin.flw.FlwCompilationAccessor;

public class IrisCompilation extends Compilation {
   private final FlwCompilationAccessor compilationAccessor = (FlwCompilationAccessor)this;

   public ShaderResult compile(ShaderType shaderType, String name) {
      String source = this.compilationAccessor.getFullSource().toString();
      String shaderName = name + "." + shaderType.extension;
      FlwCompilationAccessor.invokeDumpSource(source, shaderName);
      return ShaderResult.success(new IntermediateGlShader(source, shaderType, name), "");
   }
}
