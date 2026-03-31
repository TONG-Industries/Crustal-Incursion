package com.cim.compat.irisflw.iris;

import java.nio.FloatBuffer;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniform;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryStack;

public class GlUniformMcMatrix4f extends GlUniform<Matrix4f> {
   public GlUniformMcMatrix4f(int index) {
      super(index);
   }

   public void set(Matrix4f value) {
      MemoryStack stack = MemoryStack.stackPush();

      try {
         FloatBuffer buf = stack.callocFloat(16);
         value.get(buf);
         GL30C.glUniformMatrix4fv(this.index, false, buf);
      } catch (Throwable var6) {
         if (stack != null) {
            try {
               stack.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (stack != null) {
         stack.close();
      }

   }
}
