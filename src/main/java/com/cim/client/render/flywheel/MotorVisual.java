package com.cim.client.render.flywheel;

import com.cim.block.basic.industrial.rotation.MotorElectroBlock;
import com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class MotorVisual extends AbstractBlockEntityVisual<MotorElectroBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance base;
    private final TransformedInstance shaft;
    private final Direction facing;

    // Локальные координаты относительно матрицы Engine Room
    private final float localX;
    private final float localY;
    private final float localZ;

    public MotorVisual(VisualizationContext ctx, MotorElectroBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        if (blockState.hasProperty(MotorElectroBlock.FACING)) {
            this.facing = blockState.getValue(MotorElectroBlock.FACING);
        } else {
            this.facing = Direction.NORTH;
        }

        // === САМОЕ ВАЖНОЕ: ВЫЧИСЛЯЕМ ЛОКАЛЬНУЮ ПОЗИЦИЮ ===
        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        this.base = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.MOTOR_BASE)).createInstance();
        this.shaft = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.HALF_SHAFT)).createInstance();

        setupStaticBase();
        updateLight(partialTick);
    }

    private void setupStaticBase() {
        base.setIdentityTransform()
                // Используем localX, Y, Z вместо pos!
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            base.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            base.rotateX((float) Math.toRadians(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            base.rotateY((float) Math.toRadians(180));
        }

        base.translate(-0.5f, -0.5f, -0.5f);
        base.setChanged();
    }

    @Override
    public void beginFrame(Context ctx) {
        long speed = blockEntity.getVisualSpeed();
        float time = (float) (System.currentTimeMillis() % 100000) / 50f;
        float angle = time * speed * 0.1f;

        shaft.setIdentityTransform()
                // Используем localX, Y, Z вместо pos!
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            shaft.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            shaft.rotateX((float) Math.toRadians(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            shaft.rotateY((float) Math.toRadians(180));
        }

        // Никаких проверок, просто крутим!
        shaft.rotateZ(angle);

        shaft.translate(-0.5f, -0.5f, -0.5f);
        shaft.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        // relight всегда требует АБСОЛЮТНЫЙ pos для проверки света в мире, здесь всё правильно
        relight(pos, base, shaft);
    }

    @Override
    protected void _delete() {
        base.delete();
        shaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(base);
        consumer.accept(shaft);
    }
}