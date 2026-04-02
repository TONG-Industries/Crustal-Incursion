package com.cim.client.render.flywheel;

import com.cim.block.basic.industrial.rotation.MotorElectroBlock;
import com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity;
// Если ModModels лежит в другой папке, поправь этот импорт:
import com.cim.client.render.flywheel.ModModels;

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

// Обязательно добавляем implements SimpleDynamicVisual, как в твоем ShaftVisual!
public class MotorVisual extends AbstractBlockEntityVisual<MotorElectroBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance base;
    private final TransformedInstance shaft;
    private final Direction facing;

    public MotorVisual(VisualizationContext ctx, MotorElectroBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        this.facing = blockState.getValue(MotorElectroBlock.FACING);

        // 1. Создаем инстанс корпуса (статичный)
        // Если у тебя PartialModel, оборачиваем в Models.partial() как того требует новый Flywheel
        // Добавили () после instancerProvider
        this.base = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.MOTOR_BASE)).createInstance();

        this.shaft = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.HALF_SHAFT)).createInstance();

        setupStaticBase();
    }

    private void setupStaticBase() {
        base.setIdentityTransform()
                .translate(pos) // В AbstractBlockEntityVisual переменная называется pos
                .center();

        Direction.Axis axis = facing.getAxis();

        // Поворачиваем корпус в зависимости от направления
        if (axis == Direction.Axis.X) {
            base.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            base.rotateX((float) Math.toRadians(facing == Direction.UP ? -90 : 90));
        } else if (facing == Direction.SOUTH) {
            base.rotateY((float) Math.toRadians(180));
        }

        base.uncenter().setChanged();
    }

    @Override
    public void beginFrame(Context ctx) {
        float speed = blockEntity.getSpeed();
        // Временная заглушка для постоянного вращения (пока нет общей синхронизации сети)
        float time = (float) (System.currentTimeMillis() % 100000) / 50f;
        float angle = time * speed * 0.1f;

        shaft.setIdentityTransform()
                .translate(pos)
                .center();

        // 1. Сначала поворачиваем вал в ту же сторону, куда смотрит мотор
        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            shaft.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            shaft.rotateX((float) Math.toRadians(facing == Direction.UP ? -90 : 90));
        } else if (facing == Direction.SOUTH) {
            shaft.rotateY((float) Math.toRadians(180));
        }

        // 2. Затем вращаем вал вокруг его локальной оси Z
        // (Предполагается, что в Blockbench половинка вала смотрит на Север/Юг)
        shaft.rotateZ(angle);

        shaft.uncenter().setChanged();
    }

    // Обязательный метод, которого не хватало для компиляции!


    @Override
    protected void _delete() {
        base.delete();
        shaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        // Передаем оба инстанса консьюмеру.
        // Благодаря этому, когда ты будешь ломать мотор, на нем будут рисоваться трещины!
        consumer.accept(base);
        consumer.accept(shaft);
    }

    @Override
    public void updateLight(float partialTick) {
        // Обновляем освещение сразу для корпуса и для вала,
        // чтобы они не были черными в темной пещере.
        relight(pos, base, shaft);
    }
}