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
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class MotorVisual extends AbstractBlockEntityVisual<MotorElectroBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance base;
    private final TransformedInstance shaft;
    private final Direction facing;

    public MotorVisual(VisualizationContext ctx, MotorElectroBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        this.facing = blockState.getValue(MotorElectroBlock.FACING);

        this.base = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.MOTOR_BASE)).createInstance();
        this.shaft = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.HALF_SHAFT)).createInstance();

        setupStaticBase();
    }

    private void setupStaticBase() {
        base.setIdentityTransform()
                .translate(pos)
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
                .translate(pos)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            shaft.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            shaft.rotateX((float) Math.toRadians(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            shaft.rotateY((float) Math.toRadians(180));
        }

        // Если мотор смотрит вверх, крутим вал в обратную сторону (-angle),
        // чтобы визуально он совпадал с основной линией валов!
        if (facing == Direction.UP) {
            shaft.rotateZ(-angle);
        } else {
            shaft.rotateZ(angle);
        }

        shaft.translate(-0.5f, -0.5f, -0.5f);
        shaft.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
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