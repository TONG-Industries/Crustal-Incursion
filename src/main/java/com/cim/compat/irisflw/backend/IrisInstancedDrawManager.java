package com.cim.compat.irisflw.backend;

import com.mojang.datafixers.util.Pair;
import dev.engine_room.flywheel.api.backend.Engine.CrumblingBlock;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model.ConfiguredMesh;
import dev.engine_room.flywheel.backend.Samplers;
import dev.engine_room.flywheel.backend.compile.ContextShader;
import dev.engine_room.flywheel.backend.compile.InstancingPrograms;
import dev.engine_room.flywheel.backend.compile.PipelineCompiler.OitMode;
import dev.engine_room.flywheel.backend.engine.CommonCrumbling;
import dev.engine_room.flywheel.backend.engine.DrawManager;
import dev.engine_room.flywheel.backend.engine.GroupKey;
import dev.engine_room.flywheel.backend.engine.InstanceHandleImpl;
import dev.engine_room.flywheel.backend.engine.InstancerKey;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.MaterialEncoder;
import dev.engine_room.flywheel.backend.engine.MaterialRenderState;
import dev.engine_room.flywheel.backend.engine.TextureBinder;
import dev.engine_room.flywheel.backend.engine.AbstractInstancer.Recreate;
import dev.engine_room.flywheel.backend.engine.MeshPool.PooledMesh;
import dev.engine_room.flywheel.backend.engine.embed.Environment;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
import dev.engine_room.flywheel.backend.engine.indirect.OitFramebuffer;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDraw;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedInstancer;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedLight;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.backend.gl.TextureBuffer;
import dev.engine_room.flywheel.backend.gl.array.GlVertexArray;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleMaterial.Builder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import com.cim.compat.irisflw.flywheel.IrisFlwCompatGlProgramBase;
import com.cim.compat.irisflw.flywheel.RenderLayerEventStateManager;

public class IrisInstancedDrawManager extends DrawManager<InstancedInstancer<?>> {
   private static final Comparator<InstancedDraw> DRAW_COMPARATOR;
   private final List<InstancedDraw> allDraws = new ArrayList();
   private boolean needSort = false;
   private final List<InstancedDraw> draws = new ArrayList();
   private final List<InstancedDraw> oitDraws = new ArrayList();
   private final IrisInstancingPrograms programs;
   private final IrisMeshPool meshPool;
   private final GlVertexArray vao;
   private final TextureBuffer instanceTexture;
   private final InstancedLight light;
   private final OitFramebuffer oitFramebuffer;

   public IrisInstancedDrawManager(IrisInstancingPrograms programs) {
      programs.acquire();
      this.programs = programs;
      this.meshPool = new IrisMeshPool();
      this.vao = GlVertexArray.create();
      this.instanceTexture = new TextureBuffer();
      this.light = new InstancedLight();
      this.meshPool.bind(this.vao);
      this.oitFramebuffer = new OitFramebuffer(programs.oitPrograms());
   }

   public void render(LightStorage lightStorage, EnvironmentStorage environmentStorage) {
      super.render(lightStorage, environmentStorage);
      this.instancers.values().removeIf((instancer) -> {
         if (instancer.instanceCount() == 0) {
            instancer.delete();
            return true;
         } else {
            instancer.updateBuffer();
            return false;
         }
      });
      this.needSort |= this.allDraws.removeIf(InstancedDraw::deleted);
      if (this.needSort) {
         this.allDraws.sort(DRAW_COMPARATOR);
         this.draws.clear();
         this.oitDraws.clear();
         Iterator var3 = this.allDraws.iterator();

         while(var3.hasNext()) {
            InstancedDraw draw = (InstancedDraw)var3.next();
            if (draw.material().transparency() == Transparency.ORDER_INDEPENDENT) {
               this.oitDraws.add(draw);
            } else {
               this.draws.add(draw);
            }
         }

         this.needSort = false;
      }

      this.meshPool.flush();
      this.light.flush(lightStorage);
      if (!this.allDraws.isEmpty()) {
         Uniforms.bindAll();
         this.vao.bindForDraw();
         TextureBinder.bindLightAndOverlay();
         this.light.bind();
         this.submitDraws();
         if (!this.oitDraws.isEmpty()) {
            this.oitFramebuffer.prepare();
            this.oitFramebuffer.depthRange();
            this.submitOitDraws(OitMode.DEPTH_RANGE);
            this.oitFramebuffer.renderTransmittance();
            this.submitOitDraws(OitMode.GENERATE_COEFFICIENTS);
            this.oitFramebuffer.renderDepthFromTransmittance();
            this.vao.bindForDraw();
            this.oitFramebuffer.accumulate();
            this.submitOitDraws(OitMode.EVALUATE);
            this.oitFramebuffer.composite();
         }

         MaterialRenderState.reset();
         TextureBinder.resetLightAndOverlay();
      }
   }

   private void submitDraws() {
      boolean isShadow = RenderLayerEventStateManager.isRenderingShadow();
      Iterator var2 = this.draws.iterator();

      while(var2.hasNext()) {
         InstancedDraw drawCall = (InstancedDraw)var2.next();
         Material material = drawCall.material();
         GroupKey<?> groupKey = drawCall.groupKey;
         Environment environment = groupKey.environment();
         IrisFlwCompatGlProgramBase program = this.programs.get(groupKey.instanceType(), environment.contextShader(), material, OitMode.OFF, isShadow);
         if (program != null) {
            program.bind();
            environment.setupDraw(program);
            uploadMaterialUniform(program, material);
            program.setUInt("_flw_vertexOffset", drawCall.mesh().baseVertex());
            MaterialRenderState.setup(material);
            Samplers.INSTANCE_BUFFER.makeActive();
            drawCall.render(this.instanceTexture);
            program.clear();
         }
      }

   }

   private void submitOitDraws(OitMode mode) {
      boolean isShadow = RenderLayerEventStateManager.isRenderingShadow();
      Iterator var3 = this.oitDraws.iterator();

      while(var3.hasNext()) {
         InstancedDraw drawCall = (InstancedDraw)var3.next();
         Material material = drawCall.material();
         GroupKey<?> groupKey = drawCall.groupKey;
         Environment environment = groupKey.environment();
         IrisFlwCompatGlProgramBase program = this.programs.get(groupKey.instanceType(), environment.contextShader(), material, mode, isShadow);
         program.bind();
         environment.setupDraw(program);
         uploadMaterialUniform(program, material);
         program.setUInt("_flw_vertexOffset", drawCall.mesh().baseVertex());
         MaterialRenderState.setupOit(material);
         Samplers.INSTANCE_BUFFER.makeActive();
         drawCall.render(this.instanceTexture);
         program.clear();
      }

   }

   public void delete() {
      this.instancers.values().forEach(InstancedInstancer::delete);
      this.allDraws.forEach(InstancedDraw::delete);
      this.allDraws.clear();
      this.draws.clear();
      this.oitDraws.clear();
      this.meshPool.delete();
      this.instanceTexture.delete();
      this.programs.release();
      this.vao.delete();
      this.light.delete();
      this.oitFramebuffer.delete();
      super.delete();
   }

   protected <I extends Instance> InstancedInstancer<I> create(InstancerKey<I> key) {
      return new InstancedInstancer(key, new Recreate(key, this));
   }

   protected <I extends Instance> void initialize(InstancerKey<I> key, InstancedInstancer<?> instancer) {
      instancer.init();
      List<ConfiguredMesh> meshes = key.model().meshes();

      for(int i = 0; i < meshes.size(); ++i) {
         ConfiguredMesh entry = (ConfiguredMesh)meshes.get(i);
         PooledMesh mesh = this.meshPool.alloc(entry.mesh());
         GroupKey<?> groupKey = new GroupKey(key.type(), key.environment());
         InstancedDraw instancedDraw = new InstancedDraw(instancer, mesh, groupKey, entry.material(), key.bias(), i);
         this.allDraws.add(instancedDraw);
         this.needSort = true;
         instancer.addDrawCall(instancedDraw);
      }

   }

   public void renderCrumbling(List<CrumblingBlock> crumblingBlocks) {
      boolean isShadow = RenderLayerEventStateManager.isRenderingShadow();
      Map<GroupKey<?>, ? extends Int2ObjectMap<? extends List<? extends Pair<? extends InstancedInstancer<?>, InstanceHandleImpl<?>>>>> byType = doCrumblingSort(crumblingBlocks, (handle) -> {
         if (handle instanceof InstancedInstancer) {
            InstancedInstancer<?> instancer = (InstancedInstancer)handle;
            return instancer;
         } else {
            return null;
         }
      });
      if (!byType.isEmpty()) {
         Builder crumblingMaterial = SimpleMaterial.builder();
         Uniforms.bindAll();
         this.vao.bindForDraw();
         TextureBinder.bindLightAndOverlay();
         Iterator var5 = byType.entrySet().iterator();

         while(var5.hasNext()) {
            Entry<GroupKey<?>, ? extends Int2ObjectMap<? extends List<? extends Pair<? extends InstancedInstancer<?>, InstanceHandleImpl<?>>>>> groupEntry = (Entry)var5.next();
            Int2ObjectMap<? extends List<? extends Pair<? extends InstancedInstancer<?>, InstanceHandleImpl<?>>>> byProgress = (Int2ObjectMap)groupEntry.getValue();
            GroupKey<?> shader = (GroupKey)groupEntry.getKey();
            ObjectIterator var9 = byProgress.int2ObjectEntrySet().iterator();

            while(var9.hasNext()) {
               it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<? extends List<? extends Pair<? extends InstancedInstancer<?>, InstanceHandleImpl<?>>>> progressEntry = (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry)var9.next();
               Samplers.CRUMBLING.makeActive();
               TextureBinder.bind((ResourceLocation)ModelBakery.DESTROY_STAGES.get(progressEntry.getIntKey()));
               Iterator var11 = ((List)progressEntry.getValue()).iterator();

               while(var11.hasNext()) {
                  Pair<? extends InstancedInstancer<?>, InstanceHandleImpl<?>> instanceHandlePair = (Pair)var11.next();
                  InstancedInstancer<?> instancer = (InstancedInstancer)instanceHandlePair.getFirst();
                  int index = ((InstanceHandleImpl)instanceHandlePair.getSecond()).index;
                  Iterator var15 = instancer.draws().iterator();

                  while(var15.hasNext()) {
                     InstancedDraw draw = (InstancedDraw)var15.next();
                     CommonCrumbling.applyCrumblingProperties(crumblingMaterial, draw.material());
                     IrisFlwCompatGlProgramBase program = this.programs.get(shader.instanceType(), ContextShader.CRUMBLING, crumblingMaterial, OitMode.OFF, isShadow);
                     program.bind();
                     program.setInt("_flw_baseInstance", index);
                     uploadMaterialUniform(program, crumblingMaterial);
                     MaterialRenderState.setup(crumblingMaterial);
                     Samplers.INSTANCE_BUFFER.makeActive();
                     draw.renderOne(this.instanceTexture);
                     program.clear();
                  }
               }
            }
         }

         MaterialRenderState.reset();
         TextureBinder.resetLightAndOverlay();
      }
   }

   public void triggerFallback() {
      InstancingPrograms.kill();
       Minecraft.getInstance().gameRenderer.resetProjectionMatrix(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
   }

   public static void uploadMaterialUniform(GlProgram program, Material material) {
      int packedFogAndCutout = MaterialEncoder.packUberShader(material);
      int packedMaterialProperties = MaterialEncoder.packProperties(material);
      program.setUVec2("_flw_packedMaterial", packedFogAndCutout, packedMaterialProperties);
   }

   static {
      DRAW_COMPARATOR = Comparator.comparing(InstancedDraw::bias).thenComparing(InstancedDraw::indexOfMeshInModel).thenComparing(InstancedDraw::material, MaterialRenderState.COMPARATOR);
   }
}
