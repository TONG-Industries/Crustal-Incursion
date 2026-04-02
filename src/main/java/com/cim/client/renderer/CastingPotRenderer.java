package com.cim.client.renderer;

import com.cim.api.metallurgy.system.Metal;
import com.cim.block.basic.industrial.casting.CastingPotBlock;
import com.cim.block.entity.industrial.casting.CastingPotBlockEntity;
import com.cim.event.HotItemHandler;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CastingPotRenderer implements BlockEntityRenderer<CastingPotBlockEntity> {
    private static final ResourceLocation LIQUID_METAL_TEXTURE = new ResourceLocation("cim", "textures/machine/liquid_metal.png");
    private static final ResourceLocation HOT_GLOW_TEXTURE = new ResourceLocation("minecraft", "textures/misc/white.png");

    private final ItemRenderer itemRenderer;

    public CastingPotRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(CastingPotBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        ItemStack mold = blockEntity.getMold();
        ItemStack output = blockEntity.getOutputItem();
        float coolingTimer = blockEntity.getCoolingTimer();
        float coolingProgress = blockEntity.getCoolingProgress();

        Direction facing = blockEntity.getBlockState().getValue(CastingPotBlock.FACING);

        // === ПРОВЕРКА НА EAST-WEST ДЛЯ ИНВЕРСИИ ===
        boolean needsInverse = (facing == Direction.EAST || facing == Direction.WEST);
        float inverseRotation = needsInverse ? 180f : 0f;

        // 1. ФОРМА
        if (!mold.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.25f, 0.5f);

            float rotationY = getRotationFromFacing(facing);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotationY));

            // ИНВЕРСИЯ НА EAST-WEST
            if (needsInverse) {
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
            }

            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
            float scale = 0.75f;
            poseStack.scale(scale, scale, scale);

            itemRenderer.renderStatic(mold, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, blockEntity.getLevel(), 0);
            poseStack.popPose();
        }

        // 2. ЖИДКИЙ МЕТАЛЛ (рендерим только если нет готового предмета)
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

        // 3. ГОТОВЫЙ ПРЕДМЕТ (или БЛОК)
        if (!output.isEmpty()) {
            if (blockEntity.isBlockMold()) {
                renderHotBlock(blockEntity, poseStack, buffer, packedLight, packedOverlay, facing, needsInverse);
            } else {
                renderOutputItem(blockEntity, poseStack, buffer, packedLight, packedOverlay, facing, needsInverse, output);
            }
        }

        // 4. ШЛАК
        if (blockEntity.hasSlag()) {
            renderSlag(blockEntity, poseStack, buffer, packedLight, packedOverlay, facing, needsInverse);
        }
    }

    private void renderHotBlock(CastingPotBlockEntity blockEntity, PoseStack poseStack,
                                MultiBufferSource buffer, int packedLight, int packedOverlay,
                                Direction facing, boolean needsInverse) {

        Metal metal = blockEntity.getBlockRenderMetal();
        if (metal == null || !metal.hasBlock()) return;

        Block block = metal.getBlock();
        BlockState blockState = block.defaultBlockState();

        // Проверяем горячесть (чтобы блок хотя бы сам по себе рендерился ярким без теней)
        ItemStack output = blockEntity.getOutputItem();
        boolean isHot = HotItemHandler.isHot(output);
        float heatRatio = isHot ? HotItemHandler.getHeatRatio(output) : 0f;

        // Если блок горячий, задаем ему максимальное внутреннее освещение
        int renderLight = (isHot && heatRatio > 0.1f) ? 15728880 : packedLight;

        poseStack.pushPose();

        // 1. Сначала ставим "якорь" ровно в центр котла по X и Z
        poseStack.translate(0.5f, 0.0f, 0.5f);

        // 2. Вращаем
        float rotationY = getRotationFromFacing(facing);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotationY));
        if (needsInverse) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        }

        // 3. Поднимаем дно блока на 4 пикселя (4 / 16 = 0.25)
        poseStack.translate(0.0f, 0.25f, 0.0f);

        // 4. Задаём размеры: 9 пикселей ширина/длина (9/16 = 0.5625), 4 пикселя высота (4/16 = 0.25)
        float scaleXZ = 9.0f / 16.0f;
        float scaleY = 4.0f / 16.0f;
        poseStack.scale(scaleXZ, scaleY, scaleXZ);

        // 5. Центрируем сам куб рендера. Сдвигаем на -0.5 только по X и Z.
        // По Y не трогаем, чтобы дно осталось ровно на высоте 0.25
        poseStack.translate(-0.5f, 0.0f, -0.5f);

        // Рендерим сам блок
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                blockState, poseStack, buffer, renderLight, packedOverlay
        );

        poseStack.popPose();
    }

    /**
     * Существующий рендер предмета (выносим в отдельный метод)
     */
    private void renderOutputItem(CastingPotBlockEntity blockEntity, PoseStack poseStack,
                                  MultiBufferSource buffer, int packedLight, int packedOverlay,
                                  Direction facing, boolean needsInverse, ItemStack output) {

        poseStack.pushPose();
        poseStack.translate(0.5f, 4.01f / 16.0f, 0.5f);

        float rotationY = getRotationFromFacing(facing);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotationY));

        if (needsInverse) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        }

        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        float scale = 0.75f;
        poseStack.scale(scale, scale, scale);

        boolean isHot = HotItemHandler.isHot(output);
        float heatRatio = isHot ? HotItemHandler.getHeatRatio(output) : 0f;
        int renderLight = (isHot && heatRatio > 0.1f) ? 15728880 : packedLight;

        itemRenderer.renderStatic(output, ItemDisplayContext.FIXED, renderLight, packedOverlay, poseStack, buffer, blockEntity.getLevel(), 0);

        if (isHot && heatRatio > 0.05f) {
            renderHotGlowEffect(poseStack, buffer, heatRatio);
            renderTemperatureTint(poseStack, buffer, heatRatio);
        }

        poseStack.popPose();
    }

    
    /**
     * Рендер шлака с эффектом горячести
     */
    private void renderSlag(CastingPotBlockEntity blockEntity, PoseStack poseStack,
                            MultiBufferSource buffer, int packedLight, int packedOverlay,
                            Direction facing, boolean needsInverse) {

        ItemStack slagStack = blockEntity.getSlagStackForRender();
        if (slagStack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(0.5f, 4.01f / 16.0f, 0.5f);

        float rotationY = getRotationFromFacing(facing);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotationY));

        // ИНВЕРСИЯ НА EAST-WEST
        if (needsInverse) {
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        }

        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));

        float scale = 0.75f;
        poseStack.scale(scale, scale, scale);

        boolean isHot = HotItemHandler.isHot(slagStack);
        float heatRatio = isHot ? HotItemHandler.getHeatRatio(slagStack) : 0f;

        int renderLight = (isHot && heatRatio > 0.1f) ? 15728880 : packedLight;

        itemRenderer.renderStatic(slagStack, ItemDisplayContext.FIXED,
                renderLight, packedOverlay, poseStack, buffer,
                blockEntity.getLevel(), 0);

        if (isHot && heatRatio > 0.05f) {
            renderHotGlowEffect(poseStack, buffer, heatRatio);
        }

        poseStack.popPose();
    }

    /**
     * Рендерит оранжевое свечение для горячих предметов
     */
    private void renderHotGlowEffect(PoseStack poseStack, MultiBufferSource buffer, float heatRatio) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(HOT_GLOW_TEXTURE));

        // Альфа зависит от нагрева: чем горячее, тем ярче
        float alpha = 0.6f * heatRatio;
        if (alpha < 0.05f) return;

        // Цвет: от ярко-оранжевого (горячий) к тёмно-красному (остывает)
        float r = 1.0f;
        float g = 0.4f + (0.4f * heatRatio); // Больше зелёного когда горячо
        float b = 0.1f * heatRatio;

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float s = 0.4f; // Размер глоу
        float zOffset = 0.001f; // Чуть выше предмета

        // Простой квадрат поверх предмета
        addVertex(builder, matrix, normal, -s, -s, zOffset, r, g, b, alpha, 0, 0);
        addVertex(builder, matrix, normal, -s,  s, zOffset, r, g, b, alpha, 0, 1);
        addVertex(builder, matrix, normal,  s,  s, zOffset, r, g, b, alpha, 1, 1);
        addVertex(builder, matrix, normal,  s, -s, zOffset, r, g, b, alpha, 1, 0);
    }

    /**
     * Дополнительная тонировка цветом температуры
     */
    private void renderTemperatureTint(PoseStack poseStack, MultiBufferSource buffer, float heatRatio) {
        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(HOT_GLOW_TEXTURE));

        float alpha = 0.25f * heatRatio;
        if (alpha < 0.05f) return;

        // Тонировка: оранжевый оттенок
        float r = 1.0f;
        float g = 0.6f;
        float b = 0.2f;

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        float s = 0.38f;
        float zOffset = 0.002f;

        addVertex(builder, matrix, normal, -s, -s, zOffset, r, g, b, alpha, 0, 0);
        addVertex(builder, matrix, normal, -s,  s, zOffset, r, g, b, alpha, 0, 1);
        addVertex(builder, matrix, normal,  s,  s, zOffset, r, g, b, alpha, 1, 1);
        addVertex(builder, matrix, normal,  s, -s, zOffset, r, g, b, alpha, 1, 0);
    }

    /**
     * Конвертирует Direction в угол поворота
     */
    private float getRotationFromFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> 0f;
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
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

    /**
     * Рендер жидкого металла
     */
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