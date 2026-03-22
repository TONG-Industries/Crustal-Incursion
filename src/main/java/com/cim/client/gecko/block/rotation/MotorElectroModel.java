package com.cim.client.gecko.block.rotation;


import net.minecraft.resources.ResourceLocation;
import com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class MotorElectroModel extends GeoModel<MotorElectroBlockEntity> {
    @Override
    public ResourceLocation getModelResource(MotorElectroBlockEntity animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/motor_electro.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MotorElectroBlockEntity animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/block/motor_electro.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MotorElectroBlockEntity animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/motor_electro.animation.json"); // если есть
    }
}