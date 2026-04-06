package com.cim.client.gecko.entity.mobs;


import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.LivingEntity;
import com.cim.entity.mobs.depth_worm.DepthWormEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DepthWormRenderer extends GeoEntityRenderer<DepthWormEntity> {
    public DepthWormRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DepthWormModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    protected void applyRotations(DepthWormEntity animatable, com.mojang.blaze3d.vertex.PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);

        if (animatable.isAttacking() && animatable.getTarget() != null) {
            LivingEntity target = animatable.getTarget();
            // Вычисляем угол наклона к цели
            double dy = target.getEyeY() - animatable.getEyeY();
            double dx = target.getX() - animatable.getX();
            double dz = target.getZ() - animatable.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            // Переводим в градусы и ограничиваем наклон
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
            pitch = net.minecraft.util.Mth.clamp(pitch, -45.0F, 45.0F);

            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(pitch));
        }
    }

}
