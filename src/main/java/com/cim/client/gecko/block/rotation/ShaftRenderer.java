package com.cim.client.gecko.block.rotation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class ShaftRenderer extends GeoBlockRenderer<ShaftBlockEntity> {

    public ShaftRenderer(BlockEntityRendererProvider.Context context) {
        super(new ShaftModel());
    }

    @Override
    public void preRender(PoseStack poseStack, ShaftBlockEntity animatable,
                          BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick,
                          int packedLight, int packedOverlay, float red, float green,
                          float blue, float alpha) {

        BlockState state = animatable.getBlockState();
        Direction facing = state.getValue(ShaftBlock.FACING);

        // Смещаем к центру блока
        poseStack.translate(0.5f, 0.5f, 0.5f);

        // Поворот в зависимости от направления
        switch (facing) {
            case NORTH:
                break;
            case SOUTH:
                break;
            case EAST:
                break;
            case WEST:
                break;
            case UP:
                poseStack.translate(0f, 0.5f, -0.5f);
                break;
            case DOWN:
                poseStack.translate(0f, 0.5f, 0.5f);
                break;
        }

        // Возвращаем обратно
        poseStack.translate(-0.5f, -0.5f, -0.5f);

        super.preRender(poseStack, animatable, model, bufferSource, buffer,
                isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }
}