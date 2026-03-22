package com.cim.client.gecko.block.rotation;
import com.cim.block.basic.industrial.rotation.ShaftType;
import net.minecraft.resources.ResourceLocation;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class ShaftModel extends GeoModel<ShaftBlockEntity> {

    @Override
    public ResourceLocation getModelResource(ShaftBlockEntity animatable) {
        ShaftType type = ((ShaftBlock) animatable.getBlockState().getBlock()).getShaftType();
        return type.getModelLocation();
    }

    @Override
    public ResourceLocation getTextureResource(ShaftBlockEntity animatable) {
        ShaftType type = ((ShaftBlock) animatable.getBlockState().getBlock()).getShaftType();
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/block/" + type.getTextureName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(ShaftBlockEntity animatable) {
        ShaftType type = ((ShaftBlock) animatable.getBlockState().getBlock()).getShaftType();
        return type.getAnimationLocation();
    }
}