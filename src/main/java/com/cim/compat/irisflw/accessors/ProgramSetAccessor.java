package com.cim.compat.irisflw.accessors;

import java.util.Optional;
import java.util.function.Function;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;

public interface ProgramSetAccessor {
   ProgramSource callReadProgramSource(AbsolutePackPath var1, Function<AbsolutePackPath, String> var2, String var3, ProgramSet var4, ShaderProperties var5, boolean var6);

   ProgramSource callReadProgramSource(AbsolutePackPath var1, Function<AbsolutePackPath, String> var2, String var3, ProgramSet var4, ShaderProperties var5, BlendModeOverride var6, boolean var7);

   Optional<ProgramSource> getGbuffersFlw();

   Optional<ProgramSource> getShadowFlw();
}
