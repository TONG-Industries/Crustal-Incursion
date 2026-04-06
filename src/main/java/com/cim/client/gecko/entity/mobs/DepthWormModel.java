package com.cim.client.gecko.entity.mobs;


import net.minecraft.resources.ResourceLocation;
import com.cim.entity.mobs.depth_worm.DepthWormEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class DepthWormModel extends GeoModel<DepthWormEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/depth_worm.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/entity/depth_worm.png");
    private static final ResourceLocation TEXTURE_ATTACK = new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/entity/depth_worm_attack.png");
    private static final ResourceLocation ANIM = new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/depth_worm.animation.json");

    @Override
    public ResourceLocation getModelResource(DepthWormEntity entity) { return MODEL; }
    @Override
    public ResourceLocation getAnimationResource(DepthWormEntity entity) { return ANIM; }
    @Override
    public ResourceLocation getTextureResource(DepthWormEntity entity) {
        // Условие: открывает рот при атаке, в полете, при замахе ИЛИ когда получил урон (isAngry)
        if (entity.isAttacking() || entity.isFlying() || entity.swingTime > 0 || entity.isAngry()) {
            return TEXTURE_ATTACK;
        }
        return TEXTURE;
    }


}