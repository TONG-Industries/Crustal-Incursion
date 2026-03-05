package com.cim.client.gecko.item.energy;

import net.minecraft.resources.ResourceLocation;
import com.cim.item.energy.EnergyCellItem;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class EnergyCellItemModel extends GeoModel<EnergyCellItem> {

    @Override
    public ResourceLocation getModelResource(EnergyCellItem animatable) {
        // assets/cim/geo/energy_cell_basic.geo.json
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/energy_cell_basic.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(EnergyCellItem animatable) {
        // assets/cim/textures/item/energy_cell_basic.png
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/energy_cell_basic.png");
    }

    @Override
    public ResourceLocation getAnimationResource(EnergyCellItem animatable) {
        // Нет анимаций — используем пустой файл
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/empty.animation.json");
    }
}