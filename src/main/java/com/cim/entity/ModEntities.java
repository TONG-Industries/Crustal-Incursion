package com.cim.entity;

import com.cim.entity.mobs.depth_worm.DepthWormBrutalEntity;
import com.cim.entity.mobs.grenadier.GrenadierZombieEntity;
import com.cim.entity.weapons.grenades.GrenadeIfProjectileEntity;
import com.cim.entity.weapons.grenades.GrenadeNucProjectileEntity;
import com.cim.entity.weapons.grenades.GrenadeProjectileEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.cim.entity.mobs.depth_worm.DepthWormEntity;
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

    public static final RegistryObject<EntityType<GrenadierZombieEntity>> GRENADIER_ZOMBIE =
            ENTITY_TYPES.register("grenadier_zombie",
                    () -> EntityType.Builder.of(GrenadierZombieEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.95f) // Размеры как у обычного зомби
                            .build("grenadier_zombie"));

    public static final RegistryObject<EntityType<DepthWormEntity>> DEPTH_WORM =
            ENTITY_TYPES.register("depth_worm",
                    () -> EntityType.Builder.of(DepthWormEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 0.6f) // Размер хитбокса (сделаем его поменьше для лучшего пути)
                            .build(new ResourceLocation(CrustalIncursionMod.MOD_ID, "depth_worm").toString()));

    public static final RegistryObject<EntityType<DepthWormBrutalEntity>> DEPTH_WORM_BRUTAL =
            ENTITY_TYPES.register("depth_worm_brutal",
                    () -> EntityType.Builder.of(DepthWormBrutalEntity::new, MobCategory.MONSTER)
                            .sized(0.7F, 0.6F)
                            .build("depth_worm_brutal"));

    public static final RegistryObject<EntityType<GrenadeProjectileEntity>> GRENADE_PROJECTILE =
            ENTITY_TYPES.register("grenade_projectile",
                    () -> EntityType.Builder.<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .build("grenade_projectile"));

    public static final RegistryObject<EntityType<GrenadeProjectileEntity>> GRENADEHE_PROJECTILE =
            ENTITY_TYPES.register("grenadehe_projectile",
                    () -> EntityType.Builder.<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .build("grenadehe_projectile"));

    public static final RegistryObject<EntityType<GrenadeProjectileEntity>> GRENADEFIRE_PROJECTILE =
            ENTITY_TYPES.register("grenadefire_projectile",
                    () -> EntityType.Builder.<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .build("grenadefire_projectile"));

    public static final RegistryObject<EntityType<GrenadeProjectileEntity>> GRENADESMART_PROJECTILE =
            ENTITY_TYPES.register("grenadesmart_projectile",
                    () -> EntityType.Builder.<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .build("grenadesmart_projectile"));

    public static final RegistryObject<EntityType<GrenadeProjectileEntity>> GRENADESLIME_PROJECTILE =
            ENTITY_TYPES.register("grenadeslime_projectile",
                    () -> EntityType.Builder.<GrenadeProjectileEntity>of(GrenadeProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .build("grenadeslime_projectile"));

    public static final RegistryObject<EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_projectile",
                    () -> EntityType.Builder.<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("grenade_if_projectile"));

    public static final RegistryObject<EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_FIRE_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_fire_projectile",
                    () -> EntityType.Builder.<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .build("grenade_if_fire_projectile"));

    public static final RegistryObject<EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_SLIME_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_slime_projectile",
                    () -> EntityType.Builder.<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("grenade_if_slime_projectile"));

    public static final RegistryObject<EntityType<GrenadeIfProjectileEntity>> GRENADE_IF_HE_PROJECTILE =
            ENTITY_TYPES.register("grenade_if_he_projectile",
                    () -> EntityType.Builder.<GrenadeIfProjectileEntity>of(GrenadeIfProjectileEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .build("grenade_if_he_projectile"));


    public static final RegistryObject<EntityType<GrenadeNucProjectileEntity>> GRENADE_NUC_PROJECTILE =
            ENTITY_TYPES.register("grenade_nuc_projectile",
                    () -> EntityType.Builder.<GrenadeNucProjectileEntity>of(GrenadeNucProjectileEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("grenade_nuc_projectile"));
}
