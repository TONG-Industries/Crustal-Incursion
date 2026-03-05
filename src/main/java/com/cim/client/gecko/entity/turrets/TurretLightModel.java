package com.cim.client.gecko.entity.turrets;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.cim.entity.weapons.turrets.TurretLightEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class TurretLightModel extends GeoModel<TurretLightEntity> {

    @Override
    public ResourceLocation getModelResource(TurretLightEntity object) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/turret_light.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TurretLightEntity object) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/entity/turret_light.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TurretLightEntity object) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/turret_light.animation.json");
    }

    @Override
    public void setCustomAnimations(TurretLightEntity animatable, long instanceId, AnimationState<TurretLightEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // ✅ ВАЖНО: Если турель еще не разложена, не вращаем голову!
        // Это дает анимации "deploy" полностью контролировать кости.
        if (!animatable.isDeployed()) {
            return;
        }

        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

        // Получаем кость основания башни (поворот влево-вправо)
        CoreGeoBone topBase = getAnimationProcessor().getBone("top_base");
        if (topBase != null) {
            topBase.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }

        // Получаем кость пушки (поворот вверх-вниз)
        CoreGeoBone cannon = getAnimationProcessor().getBone("cannon");
        if (cannon != null) {
            cannon.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
        }
    }
}
