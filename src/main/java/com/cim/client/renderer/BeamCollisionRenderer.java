package com.cim.client.renderer;

import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.deco.BeamCollisionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BeamCollisionRenderer implements BlockEntityRenderer<BeamCollisionBlockEntity> {

    public BeamCollisionRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BeamCollisionBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // Мы рендерим балку только если этот блок является "Мастером"
        if (!blockEntity.isMaster()) return;

        Vec3 startPos = blockEntity.getStartPos();
        Vec3 endPos = blockEntity.getEndPos();

        if (startPos == null || endPos == null) return;

        BlockPos bePos = blockEntity.getBlockPos();

        // 1. Вычисляем смещение (Offset)
        // Рендер по умолчанию начинается в углу текущего блока (bePos).
        // Нам нужно сдвинуть его в точный центр Блока А (startPos).
        double offsetX = startPos.x - bePos.getX();
        double offsetY = startPos.y - bePos.getY();
        double offsetZ = startPos.z - bePos.getZ();

        // 2. Вектор направления от Блока А до Блока Б
        double dx = endPos.x - startPos.x;
        double dy = endPos.y - startPos.y;
        double dz = endPos.z - startPos.z;

        // Расстояние для вытягивания 3D-модели
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance == 0) return;

        // Вычисляем углы поворота по осям Y и X
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        poseStack.pushPose();

        // 3. Магия сдвига: Переносим точку отсчета рендера ровно в центр Блока А!
        poseStack.translate(offsetX, offsetY, offsetZ);

        // 4. Поворачиваем балку в сторону цели
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));

        // 5. Центрируем 3D-модель
        // Поскольку модель в Blockbench рисуется от угла (0,0,0), мы сдвигаем её на полблока
        // назад и влево, чтобы ось вращения проходила ровно по центру сечения балки.
        poseStack.translate(-0.5, -0.5, 0);

        // 6. Растягиваем модель вдоль оси Z на точную длину расстояния
        poseStack.scale(1.0f, 1.0f, (float) distance);

        // 7. Получаем рендерер и стейт балки
        BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        BlockState beamState = ModBlocks.BEAM_BLOCK.get().defaultBlockState();

        // 8. Рисуем балку
        blockRenderer.renderSingleBlock(beamState, poseStack, buffer, packedLight, packedOverlay,
                net.minecraftforge.client.model.data.ModelData.EMPTY, RenderType.solid());

        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BeamCollisionBlockEntity blockEntity) {
        // Обязательно true, иначе балка исчезнет, если отвернуться от Мастера
        return true;
    }
}