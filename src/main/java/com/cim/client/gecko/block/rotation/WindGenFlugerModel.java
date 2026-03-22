package com.cim.client.gecko.block.rotation;


import net.minecraft.resources.ResourceLocation;
import com.cim.block.entity.industrial.rotation.WindGenFlugerBlockEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class WindGenFlugerModel extends GeoModel<WindGenFlugerBlockEntity> {
    @Override
    public ResourceLocation getModelResource(WindGenFlugerBlockEntity animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/wind_gen_fluger.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(WindGenFlugerBlockEntity animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/block/wind_gen_fluger.png");
    }

    @Override
    public ResourceLocation getAnimationResource(WindGenFlugerBlockEntity animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/wind_gen_fluger.animation.json");
    }

    @Override
    public void setCustomAnimations(WindGenFlugerBlockEntity animatable, long instanceId, AnimationState<WindGenFlugerBlockEntity> animationState) {
        // Получаем кость "wind_gen" — убедитесь, что имя совпадает с моделью
        CoreGeoBone windGen = this.getAnimationProcessor().getBone("wind_gen");
        if (windGen != null) {
            // Поворачиваем кость вокруг Y на текущий угол (в радианах)
            windGen.setRotY((float) Math.toRadians(animatable.getCurrentWindYaw()));
        }
    }
}