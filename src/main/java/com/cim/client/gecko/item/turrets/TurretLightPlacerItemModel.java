package com.cim.client.gecko.item.turrets;

import net.minecraft.resources.ResourceLocation;
import com.cim.item.weapons.turrets.TurretLightPlacerBlockItem;
import software.bernie.geckolib.model.GeoModel;

// Обрати внимание: дженерик теперь TurretLightPlacerBlockItem
public class TurretLightPlacerItemModel extends GeoModel<TurretLightPlacerBlockItem> {

    @Override
    public ResourceLocation getModelResource(TurretLightPlacerBlockItem animatable) {
        return new ResourceLocation("cim", "geo/turret_light.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TurretLightPlacerBlockItem animatable) {
        return new ResourceLocation("cim", "textures/entity/turret_light.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TurretLightPlacerBlockItem animatable) {
        return new ResourceLocation("cim", "animations/turret_light_placer.animation.json");
    }
}
