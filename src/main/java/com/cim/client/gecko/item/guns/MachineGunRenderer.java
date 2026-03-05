package com.cim.client.gecko.item.guns;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.cim.item.guns.MachineGunItem;
import com.cim.main.CrustalIncursionMod;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MachineGunRenderer extends GeoItemRenderer<MachineGunItem> {

    public MachineGunRenderer() {
        super(new MachineGunModel());
    }

    @Override
    public void renderRecursively(
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            MachineGunItem animatable,
            software.bernie.geckolib.cache.object.GeoBone bone,
            net.minecraft.client.renderer.RenderType renderType,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            com.mojang.blaze3d.vertex.VertexConsumer buffer,
            boolean isReRender,
            float partialTick,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        ItemStack stack = this.getCurrentItemStack();
        if (stack == null) {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
            return;
        }

        int ammo = animatable.getAmmo(stack);
        String boneName = bone.getName();

        // Скрытие патронов в ленте
        int visibleAmmoInBelt = Math.max(0, ammo - 1);
        if (boneName.equals("ammo3")) {
            if (visibleAmmoInBelt < 3) return;
        }
        if (boneName.equals("ammo2")) {
            if (visibleAmmoInBelt < 2) return;
        }
        if (boneName.equals("ammo")) {
            if (visibleAmmoInBelt < 1) return;
        }

        // ✅ НОВОЕ: Скрытие гильзы, если патронов нет вообще (включая казённик)
        if (boneName.equals("gilse")) {
            if (ammo <= 0) return; // Нет патронов = нет гильзы
        }

        // Рендерим остальные кости
        super.renderRecursively(
                poseStack, animatable, bone, renderType, bufferSource,
                buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha
        );
    }

    @Override
    public ResourceLocation getTextureLocation(MachineGunItem animatable) {
        ItemStack stack = this.getCurrentItemStack();

        // 1. Дефолтная текстура, если что-то пошло не так или стак пуст
        if (stack == null || stack.isEmpty()) {
            return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/gun/machinegun.png");
        }

        // 2. Определяем ID заряженного патрона
        String loadedId = animatable.getLoadedAmmoID(stack);

        // Если патронов нет или ID не записан — возвращаем стандартную
        if (loadedId == null || loadedId.isEmpty()) {
            return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/gun/machinegun.png");
        }

        // 3. Убираем префикс мода (cim:), если он есть
        if (loadedId.contains(":")) {
            loadedId = loadedId.split(":")[1];
        }

        // 4. Подбираем имя файла на основе ключевых слов в ID патрона
        String textureName = "machinegun"; // Базовая

        if (loadedId.contains("piercing")) {
            textureName = "machinegun_piercing";
        }
        else if (loadedId.contains("hollow")) {
            textureName = "machinegun_hollow";
        }
        else if (loadedId.contains("radio")) {
            textureName = "machinegun_radio";
        }
        else if (loadedId.contains("fire")) {
            textureName = "machinegun_fire";
        }

        // Можно добавить другие типы (gold, uranium и т.д.) по аналогии

        return new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/item/gun/" + textureName + ".png");
    }


}
