package com.cim.client.gecko.block.rotation;

import net.minecraft.resources.ResourceLocation;
import com.cim.block.entity.industrial.rotation.DrillHeadBlockEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class DrillHeadModel extends GeoModel<DrillHeadBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/drill_head.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/block/drill_head.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/drill_head.animation.json");

    @Override
    public ResourceLocation getModelResource(DrillHeadBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DrillHeadBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DrillHeadBlockEntity animatable) {
        return ANIMATION;
    }
}