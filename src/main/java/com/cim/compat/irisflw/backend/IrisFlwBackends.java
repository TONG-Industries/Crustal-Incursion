package com.cim.compat.irisflw.backend;

import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.backend.engine.EngineImpl;
import dev.engine_room.flywheel.backend.gl.GlCompat;
import dev.engine_room.flywheel.lib.backend.SimpleBackend;
import dev.engine_room.flywheel.lib.util.ShadersModHelper;
import net.minecraft.resources.ResourceLocation;
import com.cim.compat.irisflw.IrisFlw;

public class IrisFlwBackends {
   public static final Backend INSTANCING = SimpleBackend.builder().engineFactory((level) -> {
      return new EngineImpl(level, new IrisInstancedDrawManager(IrisInstancingPrograms.get()), 256);
   }).priority(2000).supported(() -> {
      return GlCompat.SUPPORTS_INSTANCING && IrisInstancingPrograms.allLoaded() && ShadersModHelper.isShaderPackInUse();
   }).register(new ResourceLocation("irisflw", "iris_instancing"));

   public static void init() {
      IrisFlw.LOGGER.info("IrisFLW backends initialized: {}", INSTANCING.toString());
   }
}
