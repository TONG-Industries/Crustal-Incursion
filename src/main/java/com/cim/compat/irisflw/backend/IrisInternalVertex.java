package com.cim.compat.irisflw.backend;

import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.Layout;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.backend.LayoutAttributes;
import dev.engine_room.flywheel.backend.gl.array.VertexAttribute;
import dev.engine_room.flywheel.lib.util.ResourceUtil;
import dev.engine_room.flywheel.lib.vertex.VertexView;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import com.cim.compat.irisflw.backend.model.IrisExtVertexView;

public class IrisInternalVertex {
   public static final Layout EXT_LAYOUT;
   public static final List<VertexAttribute> EXT_ATTRIBUTES;
   public static final int EXT_STRIDE;
   public static final ResourceLocation EXT_LAYOUT_SHADER;
   public static final ResourceLocation LAYOUT_SHADER;

   private IrisInternalVertex() {
   }

   public static VertexView createVertexView() {
      return new IrisExtVertexView();
   }

   static {
      EXT_LAYOUT = LayoutBuilder.create().vector("position", FloatRepr.FLOAT, 3).vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4).vector("tex", FloatRepr.FLOAT, 2).vector("light", FloatRepr.UNSIGNED_SHORT, 2).vector("normal", FloatRepr.NORMALIZED_BYTE, 4).vector("extend", FloatRepr.FLOAT, 4).vector("mc_Entity", FloatRepr.SHORT, 2).build();
      EXT_ATTRIBUTES = LayoutAttributes.attributes(EXT_LAYOUT);
      EXT_STRIDE = EXT_LAYOUT.byteSize();
      EXT_LAYOUT_SHADER = ResourceUtil.rl("internal/iris_instancing/vertex_ext_input.vert");
      LAYOUT_SHADER = ResourceUtil.rl("internal/iris_instancing/vertex_input.vert");
   }
}
