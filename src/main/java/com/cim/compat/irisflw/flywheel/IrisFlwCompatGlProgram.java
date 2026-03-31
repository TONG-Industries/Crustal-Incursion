package com.cim.compat.irisflw.flywheel;

import com.mojang.blaze3d.shaders.Uniform;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import com.cim.compat.irisflw.iris.GlUniformMcMatrix3f;
import com.cim.compat.irisflw.iris.GlUniformMcMatrix4f;

public class IrisFlwCompatGlProgram extends IrisFlwCompatGlProgramBase {
   public ShaderInstance shader;
   protected GlUniformMcMatrix4f uniformIrisProjMat;
   protected GlUniformMcMatrix4f iris_uniformModelViewMat;
   protected GlUniformMcMatrix3f uniformNormalMatrix;
   protected GlUniformMcMatrix4f uniformModelViewProjMat;

   public IrisFlwCompatGlProgram(ShaderInstance shader, ShaderType type, String name) {
       super(shader.getId());
       this.shader = shader;
       int progId = shader.getId();
//       if (shader.MODEL_VIEW_MATRIX == null) {
//           shader.MODEL_VIEW_MATRIX = new Uniform("ModelViewMat", 10, 16, shader);
//           shader.MODEL_VIEW_MATRIX.set(new Matrix4f());
//       }

      this.uniformIrisProjMat = new GlUniformMcMatrix4f(GL20.glGetUniformLocation(progId, "iris_ProjMat"));
      this.iris_uniformModelViewMat = new GlUniformMcMatrix4f(GL20.glGetUniformLocation(progId, "iris_ModelViewMat"));
      this.uniformNormalMatrix = new GlUniformMcMatrix3f(GL20.glGetUniformLocation(progId, "iris_NormalMat"));
      this.uniformModelViewProjMat = new GlUniformMcMatrix4f(GL20.glGetUniformLocation(progId, "flw_ModelViewProjMat"));
   }

   public void bind() {
      this.shader.apply();
      if (RenderLayerEventStateManager.isRenderingShadow()) {
         this.setProjectionMatrix(ShadowRenderer.PROJECTION);
         this.setModelViewMatrix(ShadowRenderer.MODELVIEW);
      } else {
         this.setProjectionMatrix(CapturedRenderingState.INSTANCE.getGbufferProjection());
         this.setModelViewMatrix(CapturedRenderingState.INSTANCE.getGbufferModelView());
      }

   }

   public void clear() {
      this.shader.clear();
   }

   public int getProgramHandle() {
      return this.shader.getId();
   }

   public void setProjectionMatrix(Matrix4f projectionMatrix) {
      this.uniformIrisProjMat.set(projectionMatrix);
   }

   public void setModelViewMatrix(Matrix4f modelView) {
      this.iris_uniformModelViewMat.set(modelView);
      if (this.uniformNormalMatrix != null) {
         Matrix4f normalMatrix = new Matrix4f(modelView);
         normalMatrix.invert();
         normalMatrix.transpose();
         this.uniformNormalMatrix.set(new Matrix3f(normalMatrix));
      }

   }
}
