package razchexlitiel.cim.client.gecko.block.rotation;
import razchexlitiel.cim.block.basic.rotation.ShaftType;
import net.minecraft.resources.ResourceLocation;
import razchexlitiel.cim.block.basic.rotation.ShaftBlock;
import razchexlitiel.cim.block.entity.rotation.ShaftBlockEntity;
import razchexlitiel.cim.main.CrustalIncursionMod;
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