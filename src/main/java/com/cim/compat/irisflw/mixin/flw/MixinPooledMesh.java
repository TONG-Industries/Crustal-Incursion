package com.cim.compat.irisflw.mixin.flw;

import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.backend.engine.MeshPool.PooledMesh;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.cim.compat.irisflw.IrisFlw;
import com.cim.compat.irisflw.backend.model.IrisExtVertexView;

@Mixin(
   value = {PooledMesh.class},
   remap = false
)
public abstract class MixinPooledMesh {
   @Shadow
   @Final
   private Mesh mesh;

   @Shadow
   public abstract int vertexCount();

   @Inject(
      method = {"byteSize"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void irisflw$byteSize(CallbackInfoReturnable<Integer> cir) {
      if (IrisFlw.isUsingExtendedVertexFormat()) {
         cir.setReturnValue((int)(IrisExtVertexView.STRIDE * (long)this.vertexCount()));
      }

   }
}
