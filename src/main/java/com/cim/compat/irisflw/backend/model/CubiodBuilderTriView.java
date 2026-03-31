package com.cim.compat.irisflw.backend.model;

import net.irisshaders.iris.vertices.views.TriView;
import org.joml.Vector2f;
import org.joml.Vector3f;

public class CubiodBuilderTriView implements TriView {
   Vector3f[] pos;
   Vector2f[] uvs = new Vector2f[4];

   public void setup(Vector3f[] pos, float minU, float maxU, float minV, float maxV) {
      this.pos = pos;
      this.uvs[0] = new Vector2f(maxU, minV);
      this.uvs[1] = new Vector2f(minU, minV);
      this.uvs[2] = new Vector2f(minU, maxV);
      this.uvs[3] = new Vector2f(maxU, maxV);
   }

   public float x(int i) {
      return this.pos[i].x();
   }

   public float y(int i) {
      return this.pos[i].y();
   }

   public float z(int i) {
      return this.pos[i].z();
   }

   public float u(int i) {
      return this.uvs[i].x;
   }

   public float v(int i) {
      return this.uvs[i].y;
   }
}
