package com.cim.client.gecko.entity.turrets;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.cim.entity.weapons.turrets.TurretLightLinkedEntity;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class TurretLightLinkedModel extends GeoModel<TurretLightLinkedEntity> {

    @Override
    public ResourceLocation getModelResource(TurretLightLinkedEntity object) {
        // Используем ТУ ЖЕ модель, что и у обычной турели
        //супер, турель работает отлично! давай переработаем блок-размещатель. вместо того чтобы спавнить турель на его позиции и исчезать, он будет спавнить турель НА себе и полностью привязывать её к блоку. то есть, если сломать порт, то и турель пропадёт. гаечный ключ при этом будет демонтировать и блок и турель при нажатии на них. просто у нас в моде есть энергосеть и я бы хотел привязать турель к ней в будущем. сделай полностью отдельные классы-дубликаты, которые будут полностью копировать функционал (и те вышеописанные функции) и модель (для турели используй уже существующий рендерер и модель), просто сделай приписку _linked к энтити турели, а блок назови turret_light_placer чтобы были версии полностью автономной и связанной с блоком турелей. регистрацию я сделаю сам

        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "geo/turret_light.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TurretLightLinkedEntity object) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/entity/turret_light.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TurretLightLinkedEntity object) {
        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "animations/turret_light.animation.json");
    }

    @Override
    public void setCustomAnimations(TurretLightLinkedEntity animatable, long instanceId, AnimationState<TurretLightLinkedEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        if (!animatable.isDeployed()) {
            return;
        }

        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        CoreGeoBone topBase = getAnimationProcessor().getBone("top_base");
        if (topBase != null) {
            topBase.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }
        CoreGeoBone cannon = getAnimationProcessor().getBone("cannon");
        if (cannon != null) {
            cannon.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
        }
    }
}
