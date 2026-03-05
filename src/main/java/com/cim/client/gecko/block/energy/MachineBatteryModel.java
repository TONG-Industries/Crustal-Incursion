package com.cim.client.gecko.block.energy;

import net.minecraft.resources.ResourceLocation;
import com.cim.block.entity.energy.MachineBatteryBlockEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class MachineBatteryModel extends GeoModel<MachineBatteryBlockEntity> {

    // Путь к .geo.json — кладёшь в:
    // src/main/resources/assets/cim/geo/module_energy_storage.geo.json
    private static final ResourceLocation MODEL =
            new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/module_energy_storage.geo.json");

    // Путь к текстуре — кладёшь в:
    // src/main/resources/assets/cim/textures/block/module_energy_storage.png
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/block/module_energy_storage.png");

    @Override
    public ResourceLocation getModelResource(MachineBatteryBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(MachineBatteryBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(MachineBatteryBlockEntity animatable) {
        // Нет анимаций — возвращаем пустой ресурс
        // GeckoLib это нормально принимает для статичных моделей
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/empty.animation.json");
    }
}