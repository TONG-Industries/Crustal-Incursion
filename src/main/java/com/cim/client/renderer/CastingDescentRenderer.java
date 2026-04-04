package com.cim.client.renderer;
import com.cim.api.metallurgy.system.Metal;
import com.cim.block.basic.industrial.casting.CastingDescentBlock;
import com.cim.block.entity.industrial.casting.CastingDescentBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class CastingDescentRenderer implements BlockEntityRenderer<CastingDescentBlockEntity> {
    private static final ResourceLocation LIQUID_METAL_TEXTURE = new ResourceLocation("cim", "textures/machine/liquid_metal.png");

    // Базовые размеры из твоих данных
    private static final float W = 3.7f / 16f; // Ширина
    private static final float T = 1.2f / 16f; // Толщина/Высота сегмента

    public CastingDescentRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CastingDescentBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!be.isPouring()) return;
        Metal metal = be.getPouringMetal();
        if (metal == null) return;

        Direction facing = be.getBlockState().getValue(CastingDescentBlock.FACING);
        float streamEndY = be.getStreamEndY();
        int color = metal.getColor();
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;

        float time = (be.getLevel().getGameTime() + partialTick) * 0.1f;
        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(LIQUID_METAL_TEXTURE));

        // СТРУЯ ВСЕГДА СВЕТИТСЯ В ТЕМНОТЕ
        int glowLight = 15728880;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        poseStack.translate(-0.5, -0.5, -0.5);

        // СЕГМЕНТ 3: z1 = 12.05 (ещё на 0.05 ближе к сегменту 2)
        renderBox(poseStack, builder, glowLight, r, g, b, 1.0f, 6.1f/16f, 1.2f/16f, 12.05f/16f, 9.8f/16f, 2.4f/16f, 16.0f/16f, time, 0.3f);
        // СЕГМЕНТ 2: Уменьшен на 0.001px и сдвинут на 0.1px назад
        poseStack.pushPose();
        poseStack.translate(8f/16f, 1.8f/16f, 12.15f/16f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-22.5f));
        poseStack.translate(-8f/16f, -1.8f/16f, -12.15f/16f);

        // Корректировка: x сдвинуты на 0.0005 к центру (общее уменьшение 0.001)
        // z сдвинуты на 0.1 пикселя назад (к сегменту 3)
        float x1 = (6.1f + 0.0005f) / 16f;
        float x2 = (9.8f - 0.0005f) / 16f;
        float z1 = (7.7f + 0.1f) / 16f;
        float z2 = (12.175f + 0.1f) / 16f;

        renderBox(poseStack, builder, glowLight, r, g, b, 1.0f, x1, 1.2f/16f, z1, x2, 2.4f/16f, z2, time, 0.4f);
        poseStack.popPose();

        // СЕГМЕНТ 1: Текстура вниз (-time)
        float s1TopY = 0.666f / 16f;
        if (streamEndY < s1TopY) {
            renderBox(poseStack, builder, glowLight, r, g, b, 1.0f, 6.1f/16f, streamEndY, 7.9f/16f, 9.8f/16f, s1TopY, 7.9f/16f + 1.2f/16f, -time, s1TopY - streamEndY);
        }
        poseStack.popPose();
    }


    // ИСПРАВЛЕННЫЙ renderBox (Добавлены North и South стороны)
    private void renderBox(PoseStack ps, VertexConsumer builder, int light, float r, float g, float b, float a, float x1, float y1, float z1, float x2, float y2, float z2, float offset, float len) {
        Matrix4f m = ps.last().pose(); Matrix3f n = ps.last().normal();
        float u1 = 0, u2 = 1;
        float vMax = offset % 1.0f, vMin = vMax + len;

        // ВЕРХ
        vertex(builder, m, n, x1, y2, z1, r, g, b, a, u1, vMax, light, 0, 1, 0);
        vertex(builder, m, n, x1, y2, z2, r, g, b, a, u1, vMin, light, 0, 1, 0);
        vertex(builder, m, n, x2, y2, z2, r, g, b, a, u2, vMin, light, 0, 1, 0);
        vertex(builder, m, n, x2, y2, z1, r, g, b, a, u2, vMax, light, 0, 1, 0);

        // ПЕРЕД (ЮГ +Z) - Течет вниз
        vertex(builder, m, n, x1, y1, z2, r, g, b, a, u1, vMin, light, 0, 0, 1);
        vertex(builder, m, n, x2, y1, z2, r, g, b, a, u2, vMin, light, 0, 0, 1);
        vertex(builder, m, n, x2, y2, z2, r, g, b, a, u2, vMax, light, 0, 0, 1);
        vertex(builder, m, n, x1, y2, z2, r, g, b, a, u1, vMax, light, 0, 0, 1);

        // ЗАД (СЕВЕР -Z) - Течет вниз (Добавлено!)
        vertex(builder, m, n, x2, y1, z1, r, g, b, a, u1, vMin, light, 0, 0, -1);
        vertex(builder, m, n, x1, y1, z1, r, g, b, a, u2, vMin, light, 0, 0, -1);
        vertex(builder, m, n, x1, y2, z1, r, g, b, a, u2, vMax, light, 0, 0, -1);
        vertex(builder, m, n, x2, y2, z1, r, g, b, a, u1, vMax, light, 0, 0, -1);

        // БОКА (West/East)
        vertex(builder, m, n, x1, y1, z1, r, g, b, a, 0, vMin, light, -1, 0, 0);
        vertex(builder, m, n, x1, y1, z2, r, g, b, a, 1, vMin, light, -1, 0, 0);
        vertex(builder, m, n, x1, y2, z2, r, g, b, a, 1, vMax, light, -1, 0, 0);
        vertex(builder, m, n, x1, y2, z1, r, g, b, a, 0, vMax, light, -1, 0, 0);

        vertex(builder, m, n, x2, y1, z2, r, g, b, a, 0, vMin, light, 1, 0, 0);
        vertex(builder, m, n, x2, y1, z1, r, g, b, a, 1, vMin, light, 1, 0, 0);
        vertex(builder, m, n, x2, y2, z1, r, g, b, a, 1, vMax, light, 1, 0, 0);
        vertex(builder, m, n, x2, y2, z2, r, g, b, a, 0, vMax, light, 1, 0, 0);
    }

    private void vertex(VertexConsumer b, Matrix4f m, Matrix3f n, float x, float y, float z,
                        float r, float g, float bl, float a, float u, float v, int light,
                        float nx, float ny, float nz) {
        b.vertex(m, x, y, z).color(r, g, bl, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(CastingDescentBlockEntity be) { return true; }
}
