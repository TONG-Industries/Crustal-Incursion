package com.cim.client.render.flywheel;

import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ShaftVisual extends AbstractBlockEntityVisual<ShaftBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance instance;
    private final Direction facing;

    public ShaftVisual(VisualizationContext ctx, ShaftBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        this.facing = blockState.getValue(ShaftBlock.FACING);
        PartialModel dynamicModel = ModModels.SHAFT_MODELS.get(blockState.getBlock());

        Instancer<TransformedInstance> instancer = instancerProvider().instancer(
                InstanceTypes.TRANSFORMED,
                Models.partial(dynamicModel)
        );

        this.instance = instancer.createInstance();
        setupStatic();
    }

    private void setupStatic() {
        instance.setIdentityTransform()
                .translate(pos)
                .translate(0.5f, 0.5f, 0.5f); // 1. Кидаем точку вращения в центр блока

        // 2. Ставим вал по направлению блока
        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            instance.rotateY((float) Math.toRadians(90));
        } else if (axis == Direction.Axis.Y) {
            instance.rotateX((float) Math.toRadians(90));
        }

        instance.translate(-0.5f, -0.5f, -0.5f); // 3. Возвращаем сетку координат на место
        instance.setChanged();
    }

    @Override
    public void beginFrame(Context ctx) {
        float speed = blockEntity.getSpeed();
        if (speed == 0) return;

        float time = (float) (System.currentTimeMillis() % 100000) / 50f;
        float angle = time * speed * 0.1f;

        instance.setIdentityTransform()
                .translate(pos)
                .translate(0.5f, 0.5f, 0.5f); // 1. Снова кидаем точку вращения в центр

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            instance.rotateY((float) Math.toRadians(90));
        } else if (axis == Direction.Axis.Y) {
            instance.rotateX((float) Math.toRadians(90));
        }

        instance.rotateZ(angle); // 2. Вращаем саму деталь вокруг своей оси

        instance.translate(-0.5f, -0.5f, -0.5f); // 3. Возвращаем геометрию обратно
        instance.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, instance);
    }

    @Override
    protected void _delete() {
        instance.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(instance);
    }
}