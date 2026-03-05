package com.cim.client.gecko.entity.turrets;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.cim.entity.weapons.turrets.TurretLightEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TurretLightRenderer extends GeoEntityRenderer<TurretLightEntity> {

    public TurretLightRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TurretLightModel());
        this.shadowRadius = 0.7f;
    }

    @Override
    public void render(TurretLightEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}