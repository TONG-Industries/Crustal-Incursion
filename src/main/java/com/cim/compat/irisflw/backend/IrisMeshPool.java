package com.cim.compat.irisflw.backend;

import dev.engine_room.flywheel.backend.InternalVertex;
import dev.engine_room.flywheel.backend.engine.IndexPool;
import dev.engine_room.flywheel.backend.engine.MeshPool;
import dev.engine_room.flywheel.backend.gl.array.GlVertexArray;
import dev.engine_room.flywheel.backend.gl.buffer.GlBuffer;
import java.lang.reflect.Field;
import com.cim.compat.irisflw.IrisFlw;

public class IrisMeshPool extends MeshPool {
   private final GlBuffer vbo;
   private final IndexPool indexPool;

   public IrisMeshPool() {
      try {
         Field indexPoolField = MeshPool.class.getDeclaredField("indexPool");
         indexPoolField.setAccessible(true);
         this.indexPool = (IndexPool)indexPoolField.get(this);
         Field vboField = MeshPool.class.getDeclaredField("vbo");
         vboField.setAccessible(true);
         this.vbo = (GlBuffer)vboField.get(this);
         if (IrisFlw.isUsingExtendedVertexFormat()) {
            Field vertexViewField = MeshPool.class.getDeclaredField("vertexView");
            vertexViewField.setAccessible(true);
            vertexViewField.set(this, IrisInternalVertex.createVertexView());
         }

      } catch (IllegalAccessException | NoSuchFieldException var4) {
         throw new RuntimeException(var4);
      }
   }

   public void bind(GlVertexArray vertexArray) {
      this.indexPool.bind(vertexArray);
      if (IrisFlw.isUsingExtendedVertexFormat()) {
         vertexArray.bindVertexBuffer(0, this.vbo.handle(), 0L, IrisInternalVertex.EXT_STRIDE);
         vertexArray.bindAttributes(0, 0, IrisInternalVertex.EXT_ATTRIBUTES);
      } else {
         vertexArray.bindVertexBuffer(0, this.vbo.handle(), 0L, InternalVertex.STRIDE);
         vertexArray.bindAttributes(0, 0, InternalVertex.ATTRIBUTES);
      }

   }
}
