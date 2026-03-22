package com.cim.client.gecko.block.rotation;


import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.cim.block.entity.industrial.rotation.WindGenFlugerBlockEntity;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class WindGenFlugerRenderer extends GeoBlockRenderer<WindGenFlugerBlockEntity> {
    public WindGenFlugerRenderer(BlockEntityRendererProvider.Context context) {
        super(new WindGenFlugerModel());
    }
}