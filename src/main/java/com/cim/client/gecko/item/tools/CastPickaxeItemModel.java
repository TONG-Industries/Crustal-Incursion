package com.cim.client.gecko.item.tools;


import com.cim.item.tools.cast_pickaxes.CastPickaxeItem;
import com.cim.item.tools.cast_pickaxes.materials.CastPickaxeSteelItem;
import com.cim.main.CrustalIncursionMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.GeoModel;

public class CastPickaxeItemModel extends GeoModel<CastPickaxeItem> {

    @Override
    public ResourceLocation getModelResource(CastPickaxeItem animatable) {
        if (animatable instanceof CastPickaxeSteelItem) {
            return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/cast_pickaxe_steel.geo.json");
        }
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/cast_pickaxe_iron.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CastPickaxeItem animatable) {
        if (animatable instanceof CastPickaxeSteelItem) {
            return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/cast_pickaxe_steel.png");
        }
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/cast_pickaxe_iron.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CastPickaxeItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/cast_pickaxe_iron.animation.json");
    }

    /**
     * Переопределяем для динамической скорости анимации
     */
    @Override
    public void setCustomAnimations(CastPickaxeItem animatable, long instanceId, software.bernie.geckolib.core.animation.AnimationState<CastPickaxeItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // Устанавливаем скорость анимации на основе времени зарядки
        float speed = animatable.getAnimationSpeed();
        if (speed != 1.0f) {
            animationState.getController().setAnimationSpeed(speed);
        }
    }
}