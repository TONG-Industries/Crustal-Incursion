package com.cim.client.renderer;


import com.cim.block.entity.industrial.casting.CastingPotBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CastingPotRenderer implements BlockEntityRenderer<CastingPotBlockEntity> {
    private final ItemRenderer itemRenderer;

    public CastingPotRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(CastingPotBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        ItemStack mold = blockEntity.getMold();
        if (mold.isEmpty()) return;

        poseStack.pushPose();

        // Позиция: центр блока (0.5), высота 4 пикселя (4/16 = 0.25)
        poseStack.translate(0.5f, 0.25f, 0.5f);

        // Масштаб: 12 пикселей / 16 пикселей = 0.75
        float scale = 12.0f / 16.0f;
        poseStack.scale(scale, scale, scale);

        // Поворачиваем предмет чтобы он лежал горизонтально (как на земле)
        poseStack.mulPose(Axis.XP.rotationDegrees(90));

        // Без вращения! Предмет статичный.

        // Рендерим предмет как 2D спрайт
        itemRenderer.renderStatic(
                mold,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                buffer,
                blockEntity.getLevel(),
                0
        );

        poseStack.popPose();
    }
}