package com.cim.client.gecko.block.turrets;


import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.cim.block.entity.weapons.TurretLightPlacerBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class TurretLightPlacerRenderer extends GeoBlockRenderer<TurretLightPlacerBlockEntity> {
    public TurretLightPlacerRenderer(BlockEntityRendererProvider.Context context) {
        super(new TurretLightPlacerModel());
    }
}
