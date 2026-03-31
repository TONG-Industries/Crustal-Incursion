package com.cim.client.renderer;

import com.cim.block.basic.industrial.casting.CastingPotBlock;
import com.cim.block.entity.industrial.casting.CastingPotBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
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
        int coolingTimer = blockEntity.getCoolingTimer();
        float coolingProgress = blockEntity.getCoolingProgress();

        // Получаем направление котла
        Direction facing = blockEntity.getBlockState().getValue(CastingPotBlock.FACING);

        // 1. Форма (повёрнута в направлении котла)
        if (!mold.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.25f, 0.5f);

            // Поворачиваем в направлении котла (с поправкой на боковые стороны)
            float rotationY = getRotationFromFacing(facing);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotationY));

            // Наклон формы (лежит плоско)
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));

            float scale = 0.75f;
            poseStack.scale(scale, scale, scale);

            itemRenderer.renderStatic(mold, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, blockEntity.getLevel(), 0);
            poseStack.popPose();
        }

        // 2. Жидкий металл (рендерим только если нет готового предмета)
        if (blockEntity.getStoredUnits() > 0 && output.isEmpty()) {
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

        // 3. Слиток + Эффект остывания (повёрнут в направлении котла)
        if (!output.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 4.01f / 16.0f, 0.5f);

            // Поворачиваем в направлении котла (с поправкой на боковые стороны)
            float rotationY = getRotationFromFacing(facing);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotationY));

            // Наклон слитка (лежит плоско)
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));

            float scale = 0.75f;
            poseStack.scale(scale, scale, scale);

            boolean isHot = coolingTimer > 0;
            int renderLight = isHot ? 15728880 : packedLight;

            itemRenderer.renderStatic(output, ItemDisplayContext.FIXED, renderLight, packedOverlay, poseStack, buffer, blockEntity.getLevel(), 0);

            if (isHot && coolingProgress > 0.01f) {
                renderHotGlow(poseStack, buffer, coolingProgress);
            }

            poseStack.popPose();
        }

        // 4. ШЛАК (если сформировался)
        if (blockEntity.hasSlag()) {
            ItemStack slagStack = blockEntity.getSlagStack();

            if (!slagStack.isEmpty()) {
                poseStack.pushPose();
                poseStack.translate(0.5f, 4.01f / 16.0f, 0.5f);

                // Поворачиваем в направлении котла (как слиток)
                float rotationY = getRotationFromFacing(facing);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotationY));

                // Лежит плоско (как слиток)
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));

                float scale = 0.75f;
                poseStack.scale(scale, scale, scale);

                // Подсветка если горячий
                boolean isHot = slagStack.hasTag() && slagStack.getTag().getInt("HotTime") > 0;
                int renderLight = isHot ? 15728880 : packedLight;

                itemRenderer.renderStatic(slagStack, ItemDisplayContext.FIXED,
                        renderLight, packedOverlay, poseStack, buffer,
                        blockEntity.getLevel(), 0);

                // Эффект свечения если горячий (опционально)
                if (isHot) {
                    int hotTime = slagStack.getTag().getInt("HotTime");
                    int maxTime = slagStack.getTag().getInt("HotTimeMax");
                    float progress = hotTime / (float) maxTime;
                    if (progress > 0.01f) {
                        renderHotGlow(poseStack, buffer, progress);
                    }
                }

                poseStack.popPose();
            }
        }
    }

    private void renderSlagBlock(PoseStack poseStack, MultiBufferSource buffer, int packedLight, int color) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entitySolid(
                new ResourceLocation("cim", "textures/block/slag_block.png"))); // или любая другая текстура

        float r = ((color >> 16) & 0xFF) / 255f * 0.5f; // Темнее
        float g = ((color >> 8) & 0xFF) / 255f * 0.5f;
        float b = (color & 0xFF) / 255f * 0.5f;

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float half = 0.5f;

        // Верхняя грань шлака
        builder.vertex(matrix, -half, half, -half).color(r, g, b, 1.0f).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, -half, half, half).color(r, g, b, 1.0f).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, half, half, half).color(r, g, b, 1.0f).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 1, 0).endVertex();
        builder.vertex(matrix, half, half, -half).color(r, g, b, 1.0f).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(normal, 0, 1, 0).endVertex();
    }

    /**
     * Конвертирует Direction в угол поворота (в градусах)
     * С поправкой: для EAST и WEST инвертируем на 180°
     */
    private float getRotationFromFacing(Direction facing) {
        float baseRotation = switch (facing) {
            case NORTH -> 0f;
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };

        // Для боковых сторон (EAST, WEST) инвертируем на 180°
        if (facing == Direction.EAST || facing == Direction.WEST) {
            baseRotation += 180f;
        }

        return baseRotation;
    }

    private void renderHotGlow(PoseStack poseStack, MultiBufferSource buffer, float coolingProgress) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(
                new ResourceLocation("minecraft", "textures/misc/white.png")));

        float alpha = 0.5f * coolingProgress;

        float r = 1.0f;
        float g = 0.3f + (0.5f * (1.0f - coolingProgress));
        float b = 0.0f + (0.4f * (1.0f - coolingProgress));

        if (alpha <= 0.01f) return;

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float s = 0.35f;
        float zOffset = 0.02f;

        addVertex(builder, matrix, normal, -s, -s, zOffset, r, g, b, alpha, 0, 0);
        addVertex(builder, matrix, normal, -s,  s, zOffset, r, g, b, alpha, 0, 1);
        addVertex(builder, matrix, normal,  s,  s, zOffset, r, g, b, alpha, 1, 1);
        addVertex(builder, matrix, normal,  s, -s, zOffset, r, g, b, alpha, 1, 0);
    }

    private void addVertex(VertexConsumer builder, Matrix4f matrix, Matrix3f normal,
                           float x, float y, float z, float r, float g, float b, float a, float u, float v) {
        builder.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880)
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