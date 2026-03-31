package com.cim.compat.irisflw.flywheel;

import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import org.joml.Matrix4f;

public abstract class IrisFlwCompatGlProgramBase extends GlProgram {
   public IrisFlwCompatGlProgramBase(int handle) {
      super(handle);
   }

   public abstract void bind();

   public abstract void clear();

   public abstract void setProjectionMatrix(Matrix4f var1);

   public abstract void setModelViewMatrix(Matrix4f var1);

   public static class Invalid extends IrisFlwCompatGlProgramBase {
      public static final IrisFlwCompatGlProgramBase.Invalid INSTANCE = new IrisFlwCompatGlProgramBase.Invalid();

      public Invalid() {
         super(0);
      }

      public void bind() {
      }

      public void clear() {
      }

      public void setProjectionMatrix(Matrix4f projectionMatrix) {
      }

      public void setModelViewMatrix(Matrix4f modelView) {
      }

      public void delete() {
      }
   }
}
