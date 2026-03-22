package com.cim.client.gecko.block.energy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.cim.block.basic.industrial.energy.MachineBatteryBlock;
import com.cim.block.entity.industrial.energy.MachineBatteryBlockEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.Optional;

public class MachineBatteryRenderer extends GeoBlockRenderer<MachineBatteryBlockEntity> {

    // Имена костей из .geo.json
    private static final String[] BATTERY_BONE_NAMES = {
            "battery0", "battery1", "battery2", "battery3"
    };

    public MachineBatteryRenderer(BlockEntityRendererProvider.Context context) {
        super(new MachineBatteryModel());
    }

    @Override
    public void preRender(PoseStack poseStack, MachineBatteryBlockEntity animatable,
                          BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green,
                          float blue, float alpha) {

        // ========== 1. Скрываем/показываем кости батарей ==========
        for (int i = 0; i < BATTERY_BONE_NAMES.length; i++) {
            Optional<GeoBone> bone = model.getBone(BATTERY_BONE_NAMES[i]);
            if (bone.isPresent()) {
                // Показываем кость ТОЛЬКО если ячейка вставлена (не пустая)
                bone.get().setHidden(animatable.isCellEmpty(i));
            }
        }

        // ========== 2. Поворот модели по FACING блока ==========
        BlockState state = animatable.getBlockState();
        Direction facing = state.getValue(MachineBatteryBlock.FACING);

        // Центрируем модель
        poseStack.translate(0.5f, 0.0f, 0.5f);


        // Возвращаем обратно
        poseStack.translate(-0.5f, 0.0f, -0.5f);

        super.preRender(poseStack, animatable, model, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }
}
