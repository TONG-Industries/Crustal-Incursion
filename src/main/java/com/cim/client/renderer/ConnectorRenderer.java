package com.cim.client.renderer;

import com.cim.block.entity.industrial.energy.ConnectorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class ConnectorRenderer implements BlockEntityRenderer<ConnectorBlockEntity> {

    private static final int SEGMENTS = 24; // увеличено для плавности
    private static final double SLACK = 1.03;

    // NEW: цвет провода #1e1c18
    private static final float R = 0.1176f; // 30/255
    private static final float G = 0.1098f; // 28/255
    private static final float B = 0.0941f; // 24/255
    private static final float A = 1.0f;

    public ConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ConnectorBlockEntity animatable, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        if (animatable.getConnections().isEmpty()) return;

        Level level = animatable.getLevel();
        if (level == null) return;

        Vec3 startWorld = animatable.getWireAttachmentPoint();
        Vec3 renderOrigin = Vec3.atLowerCornerOf(animatable.getBlockPos());
        Vec3 start = startWorld.subtract(renderOrigin);

        poseStack.pushPose();

        for (BlockPos otherPos : animatable.getConnections()) {
            if (animatable.getBlockPos().compareTo(otherPos) > 0) continue;

            BlockEntity otherBe = level.getBlockEntity(otherPos);
            if (!(otherBe instanceof ConnectorBlockEntity otherConnector)) continue;

            Vec3 endWorld = otherConnector.getWireAttachmentPoint();
            Vec3 end = endWorld.subtract(renderOrigin);

            // FIX: используем радиус из tier
            renderWire(poseStack, bufferSource, start, end, packedLight, packedOverlay,
                    animatable.getTier().wireRadius());
        }

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ConnectorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    // ========== CATENARY (Формула провисания) ==========

    private record CatenaryData(boolean isVertical, double offsetX, double offsetY,
                                double scale, Vec3 delta, double horLength, Vec3 vecA) {
        Vec3 getPoint(double t) {
            if (isVertical) {
                return new Vec3(vecA.x + delta.x * t, vecA.y + delta.y * t, vecA.z + delta.z * t);
            }
            double x = vecA.x + delta.x * t;
            double y = vecA.y + scale * Math.cosh((t * horLength - offsetX) / scale) + offsetY;
            double z = vecA.z + delta.z * t;
            return new Vec3(x, y, z);
        }
    }

    private CatenaryData computeCatenary(Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        double horLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        if (horLength < 0.05) {
            return new CatenaryData(true, 0, 0, 1, delta, 0, start);
        }

        double wireLength = delta.length() * SLACK;
        double l;
        {
            double goal = Math.sqrt(wireLength * wireLength - delta.y * delta.y) / horLength;
            double lower = 0, upper = 1;
            while (Math.sinh(upper) / upper < goal) {
                lower = upper;
                upper *= 2;
            }
            for (int i = 0; i < 20; i++) {
                double mid = (lower + upper) / 2.0;
                double val = Math.sinh(mid) / mid;
                if (val < goal) lower = mid;
                else if (val > goal) upper = mid;
                else break;
            }
            l = (lower + upper) / 2.0;
        }

        double scale = horLength / (2 * l);
        double offsetX = (horLength - scale * Math.log(
                (wireLength + delta.y) / (wireLength - delta.y))) * 0.5;
        double offsetY = -scale * Math.cosh((-offsetX) / scale);

        return new CatenaryData(false, offsetX, offsetY, scale, delta, horLength, start);
    }

    // ========== НОВЫЙ РЕНДЕР ПРОВОДА (гладкая труба) ==========

    private void renderWire(PoseStack poseStack, MultiBufferSource bufferSource,
                            Vec3 start, Vec3 end, int light, int overlay, float wireRadius) {

        CatenaryData catenary = computeCatenary(start, end);

        // 1. Получаем все точки кривой
        List<Vec3> points = new ArrayList<>(SEGMENTS + 1);
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = (double) i / SEGMENTS;
            points.add(catenary.getPoint(t));
        }

        // 2. Вычисляем направления (касательные векторы) в каждой точке
        List<Vec3> tangents = new ArrayList<>(SEGMENTS + 1);
        for (int i = 0; i <= SEGMENTS; i++) {
            if (i == 0) {
                // первая точка: направление к следующей
                tangents.add(points.get(1).subtract(points.get(0)).normalize());
            } else if (i == SEGMENTS) {
                // последняя точка: направление от предыдущей
                tangents.add(points.get(SEGMENTS).subtract(points.get(SEGMENTS - 1)).normalize());
            } else {
                // внутренняя точка: центральная разность
                Vec3 prev = points.get(i - 1);
                Vec3 next = points.get(i + 1);
                tangents.add(next.subtract(prev).normalize());
            }
        }

        // 3. Строим базисы (side, upDir) методом параллельного переноса
        List<Vec3> sideVectors = new ArrayList<>(SEGMENTS + 1);
        List<Vec3> upVectors = new ArrayList<>(SEGMENTS + 1);

        // Начальный базис: выбираем up = глобальный вектор (0,1,0),
        // а side получаем как tangent × up, затем нормализуем
        Vec3 tangent0 = tangents.get(0);
        Vec3 up0 = new Vec3(0, 1, 0);
        // Если касательная почти вертикальна, используем другой up
        if (Math.abs(tangent0.y) > 0.99) {
            up0 = new Vec3(1, 0, 0);
        }
        Vec3 side0 = tangent0.cross(up0).normalize();
        Vec3 upDir0 = tangent0.cross(side0).normalize(); // перпендикуляр к tangent и side
        sideVectors.add(side0);
        upVectors.add(upDir0);

        // Переносим базис вдоль кривой
        for (int i = 1; i <= SEGMENTS; i++) {
            Vec3 tPrev = tangents.get(i - 1);
            Vec3 tCurr = tangents.get(i);

            // Находим ось вращения и угол между tPrev и tCurr
            Vec3 rotAxis = tPrev.cross(tCurr);
            double angle = Math.acos(tPrev.dot(tCurr) / (tPrev.length() * tCurr.length()));

            if (rotAxis.lengthSqr() < 1e-6) {
                // Направление не изменилось — оставляем базис без изменений
                sideVectors.add(sideVectors.get(i - 1));
                upVectors.add(upVectors.get(i - 1));
            } else {
                rotAxis = rotAxis.normalize();
                // Поворачиваем предыдущие side и upDir вокруг rotAxis на угол angle
                Vec3 sidePrev = sideVectors.get(i - 1);
                Vec3 upPrev = upVectors.get(i - 1);

                Vec3 sideCurr = rotate(sidePrev, rotAxis, angle);
                Vec3 upCurr = rotate(upPrev, rotAxis, angle);

                // Ортонормируем на всякий случай (из-за погрешностей)
                sideCurr = sideCurr.normalize();
                upCurr = tCurr.cross(sideCurr).normalize();
                sideCurr = tCurr.cross(upCurr).normalize();

                sideVectors.add(sideCurr);
                upVectors.add(upCurr);
            }
        }

        // 4. Рисуем сегменты
        VertexConsumer consumer = bufferSource.getBuffer(
                RenderType.entityCutoutNoCull(
                        new ResourceLocation("minecraft", "textures/block/black_concrete.png")
                )
        );

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);

            Vec3 s1 = sideVectors.get(i).scale(wireRadius);
            Vec3 u1 = upVectors.get(i).scale(wireRadius);
            Vec3 s2 = sideVectors.get(i + 1).scale(wireRadius);
            Vec3 u2 = upVectors.get(i + 1).scale(wireRadius);

            // Четыре грани: +side, -side, +up, -up
            // Для каждой грани используем соответствующие векторы в p1 и p2
            emitQuad(matrix, normalMatrix, consumer, p1, p2, s1, s2, light, overlay); // +side
            emitQuad(matrix, normalMatrix, consumer, p1, p2, s1.scale(-1), s2.scale(-1), light, overlay); // -side
            emitQuad(matrix, normalMatrix, consumer, p1, p2, u1, u2, light, overlay); // +up
            emitQuad(matrix, normalMatrix, consumer, p1, p2, u1.scale(-1), u2.scale(-1), light, overlay); // -up
        }
    }

    // Вспомогательный поворот вектора вокруг оси
    private Vec3 rotate(Vec3 vec, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = vec.dot(axis);
        Vec3 cross = axis.cross(vec);
        return vec.scale(cos)
                .add(axis.scale(dot * (1 - cos)))
                .add(cross.scale(sin));
    }

    // Новая версия emitQuad, принимающая разные смещения для начала и конца
    private void emitQuad(Matrix4f mat, Matrix3f norm, VertexConsumer consumer,
                          Vec3 p1, Vec3 p2, Vec3 offset1, Vec3 offset2,
                          int light, int overlay) {
        Vec3 a = p1.add(offset1);
        Vec3 b = p1.subtract(offset1);
        Vec3 c = p2.subtract(offset2);
        Vec3 d = p2.add(offset2);

        // Нормаль берём как среднее направление offset (для простоты используем offset1)
        Vec3 n = offset1.normalize();
        float nx = (float) n.x, ny = (float) n.y, nz = (float) n.z;

        vert(mat, norm, consumer, a, nx, ny, nz, 0, 0, light, overlay);
        vert(mat, norm, consumer, b, nx, ny, nz, 0, 1, light, overlay);
        vert(mat, norm, consumer, c, nx, ny, nz, 1, 1, light, overlay);
        vert(mat, norm, consumer, d, nx, ny, nz, 1, 0, light, overlay);
    }

    private void vert(Matrix4f mat, Matrix3f norm, VertexConsumer consumer,
                      Vec3 pos, float nx, float ny, float nz,
                      float u, float v, int light, int overlay) {
        consumer.vertex(mat, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(R, G, B, A)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(norm, nx, ny, nz)
                .endVertex();
    }
}