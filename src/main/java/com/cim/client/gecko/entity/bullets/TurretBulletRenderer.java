package com.cim.client.gecko.entity.bullets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.cim.entity.weapons.bullets.TurretBulletEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

public class TurretBulletRenderer extends GeoEntityRenderer<TurretBulletEntity> {

    public TurretBulletRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TurretBulletModel());
        addRenderLayer(new TurretBulletGlowLayer(this));
    }

    // --- ФИНАЛЬНОЕ ИСПРАВЛЕНИЕ ВРАЩЕНИЯ ---
    @Override
    protected void applyRotations(TurretBulletEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        // 1. Интерполируем углы, чтобы анимация была плавной (без рывков при 60+ FPS)
        float yaw = Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot());
        float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());

        // 2. Вращение по оси Y (Yaw / Горизонталь)
        // Вычитаем 180, потому что стандартная модель в MC смотрит на Юг, а математика дает Север
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 180.0F));

        // 3. Вращение по оси X (Pitch / Вертикаль)
        // ВАЖНО: Применяем ПОСЛЕ поворота по Y, чтобы ось X уже была повернута правильно
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // 4. Вращение по оси Z (Roll / Вращение пули вокруг себя)
        // Если хотите эффект сверла
        poseStack.mulPose(Axis.ZP.rotationDegrees(animatable.spin));
    }
    // --------------------------------------

    public static class TurretBulletGlowLayer extends GeoRenderLayer<TurretBulletEntity> {
        public TurretBulletGlowLayer(GeoEntityRenderer<TurretBulletEntity> entityRenderer) {
            super(entityRenderer);
        }

        @Override
        public void render(PoseStack poseStack, TurretBulletEntity entity, BakedGeoModel bakedModel,
                           RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                           float partialTick, int packedLight, int packedOverlay) {

            String ammoId = entity.getAmmoId();

            String path = ammoId;
            if (path.contains(":")) path = path.split(":")[1];
            String textureName = "turret_bullet_glow";

            if (path.startsWith("ammo_turret")) {
                String suffix = path.replace("ammo_turret", "");
                if (!suffix.isEmpty()) textureName = "turret_bullet" + suffix + "_glow";
            } else if (path.contains("piercing")) {
                textureName = "turret_bullet_piercing_glow";
            }

            ResourceLocation glowTexture = new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/entity/" + textureName + ".png");
            RenderType glowRenderType = RenderType.eyes(glowTexture);

            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, entity,
                    glowRenderType, bufferSource.getBuffer(glowRenderType),
                    partialTick, 15728880, packedOverlay, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}
