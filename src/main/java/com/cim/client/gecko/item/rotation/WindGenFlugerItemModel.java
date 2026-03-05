package com.cim.client.gecko.item.rotation;


import net.minecraft.resources.ResourceLocation;
import com.cim.item.rotation.WindGenFlugerBlockItem;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class WindGenFlugerItemModel extends GeoModel<WindGenFlugerBlockItem> {
    @Override
    public ResourceLocation getModelResource(WindGenFlugerBlockItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/wind_gen_fluger.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(WindGenFlugerBlockItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/block/wind_gen_fluger.png");
    }

    @Override
    public ResourceLocation getAnimationResource(WindGenFlugerBlockItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/wind_gen_fluger.animation.json");
    }
}