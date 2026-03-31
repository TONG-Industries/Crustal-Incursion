package com.cim.compat.irisflw.mixin.flw;

import dev.engine_room.flywheel.backend.compile.core.Compile.ShaderCompiler;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   value = {ShaderCompiler.class},
   remap = false
)
public interface ShaderCompilerAccessor {
   @Accessor("shaderType")
   ShaderType getShaderType();
}
