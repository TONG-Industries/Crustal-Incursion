package com.cim.client.gecko.block.turrets;


import net.minecraft.resources.ResourceLocation;
import com.cim.block.entity.weapons.TurretLightPlacerBlockEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class TurretLightPlacerModel extends GeoModel<TurretLightPlacerBlockEntity> {
    @Override
    public ResourceLocation getModelResource(TurretLightPlacerBlockEntity animatable) {
        // Имя файла: buffer_small.geo.json
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/buffer_small.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TurretLightPlacerBlockEntity animatable) {
        // Имя файла: buffer_light.jpg (лучше переименовать в .png, Minecraft любит png)
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/block/buffer_light.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TurretLightPlacerBlockEntity animatable) {
        // Если анимаций нет, можно вернуть null или заглушку
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/turret_light_placer.animation.json");
    }
}
