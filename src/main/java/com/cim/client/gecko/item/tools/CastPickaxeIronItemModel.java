package com.cim.client.gecko.item.tools;


import com.cim.item.rotation.DrillHeadItem;
import com.cim.item.tools.CastPickaxeIronItem;
import com.cim.main.CrustalIncursionMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CastPickaxeIronItemModel extends GeoModel<CastPickaxeIronItem> {
    @Override
    public ResourceLocation getModelResource(CastPickaxeIronItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/cast_pickaxe_iron.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CastPickaxeIronItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/cast_pickaxe_iron.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CastPickaxeIronItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/cast_pickaxe_iron.animation.json");
    }
}
