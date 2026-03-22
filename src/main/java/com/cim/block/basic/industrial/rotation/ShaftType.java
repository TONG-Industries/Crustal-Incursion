package com.cim.block.basic.industrial.rotation;

import net.minecraft.resources.ResourceLocation;
import com.cim.main.CrustalIncursionMod;

public class ShaftType {
    private final long maxSpeed;
    private final long maxTorque;
    private final String textureName; // имя текстуры (без расширения)
    private final ResourceLocation modelLocation;
    private final ResourceLocation animationLocation;

    public ShaftType(long maxSpeed, long maxTorque, String textureName,
                     String modelPath, String animationPath) {
        this.maxSpeed = maxSpeed;
        this.maxTorque = maxTorque;
        this.textureName = textureName;
        this.modelLocation = new ResourceLocation(CrustalIncursionMod.MOD_ID, modelPath);
        this.animationLocation = new ResourceLocation(CrustalIncursionMod.MOD_ID, animationPath);
    }

    public long getMaxSpeed() { return maxSpeed; }
    public long getMaxTorque() { return maxTorque; }
    public String getTextureName() { return textureName; }
    public ResourceLocation getModelLocation() { return modelLocation; }
    public ResourceLocation getAnimationLocation() { return animationLocation; }
}