package com.cim.compat.irisflw.mixin.flw.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder.DrawState;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.cim.compat.irisflw.IrisFlw;
import com.cim.compat.irisflw.backend.model.IrisExtVertexView;
import com.cim.compat.irisflw.backend.model.IrisVertexReader;

@Mixin(
   targets = {"dev.engine_room.flywheel.lib.model.baked.MeshHelper"},
   remap = false
)
public class MixinMeshHelper {
   @Inject(
      method = {"blockVerticesToMesh"},
      remap = false,
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void irisflw$blockVerticesToMesh(RenderedBuffer buffer, @Nullable String meshDescriptor, CallbackInfoReturnable<SimpleQuadMesh> cir) {
      if (IrisFlw.isUsingExtendedVertexFormat()) {
          DrawState drawState = buffer.drawState();
          int vertexCount = drawState.vertexCount();
          long srcStride = (long)drawState.format().getVertexSize();
          IrisExtVertexView vertexView = new IrisExtVertexView();
          long dstStride = vertexView.stride();
          ByteBuffer src = buffer.vertexBuffer();
         MemoryBlock dst = MemoryBlock.mallocTracked((long)vertexCount * dstStride);
         long srcPtr = MemoryUtil.memAddress(src);
         long dstPtr = dst.ptr();
         vertexView.ptr(dstPtr);
         vertexView.vertexCount(vertexCount);
         vertexView.nativeMemoryOwner(dst);
         IrisVertexReader vertexReader = new IrisVertexReader(srcPtr, vertexCount);
         vertexReader.writeAll(vertexView);
         vertexView.readAllExtended(vertexReader);
         cir.setReturnValue(new SimpleQuadMesh(vertexView, meshDescriptor));
      }

   }
}
