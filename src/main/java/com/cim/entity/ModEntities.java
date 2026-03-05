package com.cim.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.cim.entity.mobs.DepthWormEntity;
import com.cim.entity.weapons.bullets.TurretBulletEntity;
import com.cim.entity.weapons.turrets.TurretLightEntity;
import com.cim.entity.weapons.turrets.TurretLightLinkedEntity;
import com.cim.main.CrustalIncursionMod;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CrustalIncursionMod.MOD_ID);

    public static final RegistryObject<EntityType<TurretLightEntity>> TURRET_LIGHT = ENTITY_TYPES.register("turret_light",
            () -> EntityType.Builder.of(TurretLightEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.8f) // Размер хитбокса
                    .build(new ResourceLocation(CrustalIncursionMod.MOD_ID, "turret_light").toString()));

    public static final RegistryObject<EntityType<TurretLightLinkedEntity>> TURRET_LIGHT_LINKED = ENTITY_TYPES.register("turret_light_linked",
            () -> EntityType.Builder.<TurretLightLinkedEntity>of(TurretLightLinkedEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.8f) // Размер такой же как у обычной турели (или поправь если надо 1.5)
                    .build(new ResourceLocation(CrustalIncursionMod.MOD_ID, "turret_light_linked").toString()));

    public static final RegistryObject<EntityType<TurretBulletEntity>> TURRET_BULLET =
            ENTITY_TYPES.register("turret_bullet",
                    () -> EntityType.Builder.<TurretBulletEntity>of(TurretBulletEntity::new, MobCategory.MISC)
                            .sized(0.05f, 0.05f)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .setShouldReceiveVelocityUpdates(true)
                            .build("turret_bullet"));

    public static final RegistryObject<EntityType<DepthWormEntity>> DEPTH_WORM =
            ENTITY_TYPES.register("depth_worm",
                    () -> EntityType.Builder.of(DepthWormEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 0.6f) // Размер хитбокса (сделаем его поменьше для лучшего пути)
                            .build(new ResourceLocation(CrustalIncursionMod.MOD_ID, "depth_worm").toString()));

}
