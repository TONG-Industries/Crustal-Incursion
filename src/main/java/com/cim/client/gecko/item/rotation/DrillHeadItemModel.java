package com.cim.client.gecko.item.rotation;


import net.minecraft.resources.ResourceLocation;
import com.cim.item.rotation.DrillHeadItem;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class DrillHeadItemModel extends GeoModel<DrillHeadItem> {
    @Override
    public ResourceLocation getModelResource(DrillHeadItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/drill_head.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DrillHeadItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/block/drill_head.png");
    }
    @Override
    public ResourceLocation getAnimationResource(DrillHeadItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/drill_head.animation.json");
    }
}