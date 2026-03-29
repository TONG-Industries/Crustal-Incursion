package com.cim.client.renderer;

import com.cim.block.entity.industrial.casting.CastingPotBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CastingPotRenderer implements BlockEntityRenderer<CastingPotBlockEntity> {
    private static final ResourceLocation LIQUID_METAL_TEXTURE = new ResourceLocation("cim", "textures/machine/liquid_metal.png");
    private final ItemRenderer itemRenderer;

    public CastingPotRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(CastingPotBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        ItemStack mold = blockEntity.getMold();
        ItemStack output = blockEntity.getOutputItem();
        float cooling = blockEntity.getCoolingProgress();

        // 1. Форма
        if (!mold.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.25f, 0.5f);
            float scale = 0.75f;
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            itemRenderer.renderStatic(mold, net.minecraft.world.item.ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, blockEntity.getLevel(), 0);
            poseStack.popPose();
        }

       // 2. Жидкий металл
        if (blockEntity.getStoredMb() > 0 && output.isEmpty()) {
            float fillLevel = blockEntity.getFillLevel();
            float heightPixels = 0.1f + 1.9f * fillLevel;
            float yCenter = (4.35f + heightPixels / 2.0f) / 16.0f;
            int color = blockEntity.getCurrentMetal() != null ? blockEntity.getCurrentMetal().getColor() : 0xFFFFFF;

            poseStack.pushPose();
            poseStack.translate(0.5f, yCenter, 0.5f);
            poseStack.scale(0.75f, heightPixels / 16.0f, 0.75f);
            renderLiquidCube(poseStack, buffer, 15728880, color);
            poseStack.popPose();
        }

        // 3. Слиток + Эффект остывания
        if (!output.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 4.01f / 16.0f, 0.5f);
            float scale = 0.75f;
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));

            // Свечение предмета в темноте пока он горячий
            int renderLight = cooling > 0.05f ? 15728880 : packedLight;

            itemRenderer.renderStatic(output, net.minecraft.world.item.ItemDisplayContext.FIXED, renderLight, packedOverlay, poseStack, buffer, blockEntity.getLevel(), 0);

            if (cooling > 0) {
                renderHotGlow(poseStack, buffer, cooling);
            }

            poseStack.popPose();
        }
    }


    private void renderHotGlow(PoseStack poseStack, MultiBufferSource buffer, float cooling) {
        // Используем стандартную белую текстуру инвентаря/частиц для наложения цвета
        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(new ResourceLocation("minecraft", "textures/misc/white.png")));

        float alpha = 0.5f * cooling; // Прозрачность оранжевого слоя
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // Размер чуть меньше слитка (12 пикселей = 0.75f), чтобы не было артефактов на краях
        float s = 0.35f;

        // Рисуем плоскость оранжевого цвета (1.0f, 0.4f, 0.0f)
        // Координата Z (0.02f) приподнимает слой над слитком, чтобы текстуры не мерцали
        addVertex(builder, matrix, normal, -s, -s, 0.02f, 1.0f, 0.4f, 0.0f, alpha, 0, 0);
        addVertex(builder, matrix, normal, -s,  s, 0.02f, 1.0f, 0.4f, 0.0f, alpha, 0, 1);
        addVertex(builder, matrix, normal,  s,  s, 0.02f, 1.0f, 0.4f, 0.0f, alpha, 1, 1);
        addVertex(builder, matrix, normal,  s, -s, 0.02f, 1.0f, 0.4f, 0.0f, alpha, 1, 0);
    }

    private void addVertex(VertexConsumer builder, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float r, float g, float b, float a, float u, float v) {
        builder.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(15728880) // Максимальная яркость (предмет "светится")
                .normal(normal, 0, 0, 1)
                .endVertex();
    }



    private void renderLiquidCube(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int color) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entitySolid(LIQUID_METAL_TEXTURE));

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1.0f;

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float half = 0.5f;

        // Верх
        builder.vertex(matrix, -half, half, -half).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, -half, half, half).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, half, half, half).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, half, half, -half).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 1, 0).endVertex();

        // Низ
        builder.vertex(matrix, half, -half, -half).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, half, -half, half).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, -half, -half, half).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, -1, 0).endVertex();
        builder.vertex(matrix, -half, -half, -half).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, -1, 0).endVertex();

        // Стороны
        builder.vertex(matrix, -half, -half, half).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, half, -half, half).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, half, half, half).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 0, 1).endVertex();
        builder.vertex(matrix, -half, half, half).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 0, 1).endVertex();

        builder.vertex(matrix, half, -half, -half).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, -half, -half, -half).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, -half, half, -half).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 0, -1).endVertex();
        builder.vertex(matrix, half, half, -half).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 0, -1).endVertex();

        builder.vertex(matrix, -half, -half, -half).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -half, -half, half).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -half, half, half).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, -1, 0, 0).endVertex();
        builder.vertex(matrix, -half, half, -half).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, -1, 0, 0).endVertex();

        builder.vertex(matrix, half, -half, half).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, half, -half, -half).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, half, half, -half).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 1, 0, 0).endVertex();
        builder.vertex(matrix, half, half, half).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 1, 0, 0).endVertex();
    }
}