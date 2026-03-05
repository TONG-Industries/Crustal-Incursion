package com.cim.client.gecko.item.ammo;


import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import com.cim.item.weapons.ammo.AmmoTurretItem;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

// Используем базовый класс AmmoTurretItem как дженерик
public class AmmoTurretModel extends GeoModel<AmmoTurretItem> {

    @Override
    public ResourceLocation getModelResource(AmmoTurretItem object) {
        // Одна модель для всех коробок
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/ammo_turret.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AmmoTurretItem object) {
        // 1. Получаем ID предмета (например "cim:ammo_turret_fire")
        ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(object);

        if (registryName != null) {
            String path = registryName.getPath(); // "ammo_turret_fire"

            // 2. Ищем текстуру с таким же именем в папке textures/item/
            // Пример: assets/cim/textures/item/ammo_turret_fire.png
            return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/ammo/" + path + ".png");
        }

        // Фолбэк на дефолт
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/ammo/ammo_turret.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AmmoTurretItem animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/ammo_turret.animation.json");
    }
}
