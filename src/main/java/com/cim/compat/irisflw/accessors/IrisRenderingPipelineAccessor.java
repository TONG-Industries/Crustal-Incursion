package com.cim.compat.irisflw.accessors;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.minecraft.client.renderer.ShaderInstance;

public interface IrisRenderingPipelineAccessor {
   ProgramSet getProgramSet();

   ShaderInstance callCreateShader(String var1, ProgramSource var2, ProgramId var3, AlphaTest var4, VertexFormat var5, FogMode var6, boolean var7, boolean var8, boolean var9, boolean var10) throws IOException;

   ShaderInstance callCreateShadowShader(String var1, ProgramSource var2, ProgramId var3, AlphaTest var4, VertexFormat var5, boolean var6, boolean var7, boolean var8) throws IOException;
}
