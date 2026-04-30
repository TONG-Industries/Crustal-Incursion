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

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private long lastFrameTime = -1;
    private boolean phaseSynced = false;

    @Override
    public void beginFrame(Context ctx) {
        long now = System.currentTimeMillis();
        if (lastFrameTime == -1) lastFrameTime = now;
        float deltaSeconds = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;

        float targetSpeed = blockEntity.getVisualSpeed();

        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.001f) {
            smoothedSpeed += speedDiff * 4.0f * deltaSeconds;
        } else {
            smoothedSpeed = targetSpeed;
        }

        currentAngle += smoothedSpeed * 2.0f * deltaSeconds;
        
        float twoPi = (float) (2 * Math.PI);
        currentAngle = currentAngle % twoPi;
        if (currentAngle < 0) currentAngle += twoPi;

        if (smoothedSpeed == targetSpeed && targetSpeed != 0) {
            float time = (float) (now % 100000) / 50f;
            float globalAngle = (time * targetSpeed * 0.1f) % twoPi;
            if (globalAngle < 0) globalAngle += twoPi;
            
            if (!this.phaseSynced) {
                currentAngle = globalAngle;
                this.phaseSynced = true;
            } else {
                float angleDiff = (globalAngle - currentAngle) % twoPi;
                if (angleDiff > Math.PI) angleDiff -= twoPi;
                if (angleDiff < -Math.PI) angleDiff += twoPi;
                
                float maxCorrection = 0.5f * deltaSeconds;
                float correction = Math.signum(angleDiff) * Math.min(Math.abs(angleDiff), maxCorrection);
                currentAngle += correction;
            }
        } else {
            this.phaseSynced = false;
        }

        if (targetSpeed == 0 && Math.abs(smoothedSpeed) < 5.0f) {
            float PI_OVER_4 = (float) (Math.PI / 4.0);
            float targetSnap = Math.round(currentAngle / PI_OVER_4) * PI_OVER_4;
            float snapDiff = targetSnap - currentAngle;
            
            if (Math.abs(snapDiff) > 0.001f) {
                float pull = 8.0f * (1.0f - (Math.abs(smoothedSpeed) / 5.0f));
                currentAngle += snapDiff * pull * deltaSeconds;
            } else {
                currentAngle = targetSnap;
            }
        }

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

        // Крутим плавно!
        shaft.rotateZ(currentAngle);

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