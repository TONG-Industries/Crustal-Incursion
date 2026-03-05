package com.cim.client.gecko.entity.bullets;


import net.minecraft.resources.ResourceLocation;
import com.cim.entity.weapons.bullets.TurretBulletEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.model.GeoModel;

public class TurretBulletModel extends GeoModel<TurretBulletEntity> {

    @Override
    public ResourceLocation getModelResource(TurretBulletEntity object) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/turret_bullet.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TurretBulletEntity object) {
        String ammoId = object.getAmmoId(); // Например: "cim:ammo_turret_fire"

        // 1. Убираем префикс мода (cim:)
        String path = ammoId;
        if (path.contains(":")) {
            path = path.split(":")[1];
        }

        // 2. Логика маппинга имени предмета на имя текстуры пули
        // Если предмет называется "ammo_turret_fire", мы хотим текстуру "turret_bullet_fire.png"

        // Стандартный префикс предметов патронов
        String itemPrefix = "ammo_turret";
        // Стандартный префикс текстур пуль
        String bulletPrefix = "turret_bullet";

        if (path.startsWith(itemPrefix)) {
            // "ammo_turret_fire" -> "_fire"
            String suffix = path.replace(itemPrefix, "");

            // Если суффикс пустой, это обычная пуля
            if (suffix.isEmpty()) {
                return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/entity/" + bulletPrefix + ".png");
            }

            // Иначе это какой-то тип (fire, piercing, explosive)
            // Возвращаем "textures/entity/turret_bullet_fire.png"
            return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/entity/" + bulletPrefix + suffix + ".png");
        }

        // Фолбэк (если ID предмета совсем другой)
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/entity/turret_bullet.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TurretBulletEntity animatable) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/turret_bullet.animation.json");
    }
}
