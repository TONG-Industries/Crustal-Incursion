package com.cim.client.gecko.item.rotation;

import net.minecraft.resources.ResourceLocation;
import com.cim.item.rotation.ShaftBlockItem;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class ShaftItemModel extends GeoModel<ShaftBlockItem> {
    @Override
    public ResourceLocation getModelResource(ShaftBlockItem animatable) {
        return animatable.getShaftType().getModelLocation();
    }

    @Override
    public ResourceLocation getTextureResource(ShaftBlockItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID,
                "textures/block/" + animatable.getShaftType().getTextureName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShaftBlockItem animatable) {
        return animatable.getShaftType().getAnimationLocation();
    }
}