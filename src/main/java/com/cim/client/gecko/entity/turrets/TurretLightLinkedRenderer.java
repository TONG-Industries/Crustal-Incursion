package com.cim.client.gecko.entity.turrets;


import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.cim.entity.weapons.turrets.TurretLightLinkedEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TurretLightLinkedRenderer extends GeoEntityRenderer<TurretLightLinkedEntity> {
    public TurretLightLinkedRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TurretLightLinkedModel());
        this.shadowRadius = 0.7f;
    }
}
