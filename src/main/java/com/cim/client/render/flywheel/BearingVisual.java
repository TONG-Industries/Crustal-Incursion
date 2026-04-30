package com.cim.client.render.flywheel;

import com.cim.api.rotation.ShaftDiameter;
import com.cim.api.rotation.ShaftMaterial;
import com.cim.block.basic.industrial.rotation.BearingBlock;
import com.cim.block.entity.industrial.rotation.BearingBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.UP;

public class BearingVisual extends AbstractBlockEntityVisual<BearingBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance innerRing;
    @Nullable
    private TransformedInstance shaft;

    private final Direction facing;
    private ShaftMaterial currentMaterial;
    private ShaftDiameter currentDiameter;

    // Локальные координаты
    private final float localX;
    private final float localY;
    private final float localZ;

    public BearingVisual(VisualizationContext ctx, BearingBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.facing = blockState.getValue(BearingBlock.FACING);

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        this.innerRing = instancerProvider().instancer(
                InstanceTypes.TRANSFORMED,
                Models.partial(ModModels.BEARING_INNER_RING)
        ).createInstance();

        this.currentMaterial = blockEntity.getShaftMaterial();
        this.currentDiameter = blockEntity.getShaftDiameter();

        createShaftInstance();
        setupStaticPositions();
        updateLight(partialTick);

        // DEBUG: Логируем создание визуала для отслеживания бага с прозрачностью
        if (com.cim.main.CrustalIncursionMod.LOGGER.isInfoEnabled()) {
            com.cim.main.CrustalIncursionMod.LOGGER.info("[CIM-Visual] BearingVisual CREATED at {} | hasShaft={}", pos, blockEntity.hasShaft());
        }
    }

    private void createShaftInstance() {
        if (blockEntity.hasShaft() && currentMaterial != null && currentDiameter != null) {
            // ИСПОЛЬЗУЕМ .name(), чтобы избежать багов с переопределенным toString()
            String matName = currentMaterial.name().toLowerCase();
            String diaName = currentDiameter.name().toLowerCase();
            String shaftName = "shaft_" + diaName + "_" + matName;

            PartialModel shaftModel = ModModels.SHAFT_MODELS.get(shaftName);
            if (shaftModel == null) {
                // Если модель не зарегистрирована, логируем это, а не просто крашимся
                System.out.println("[CIM-Debug] ВНИМАНИЕ: Модель " + shaftName + " не найдена в ModModels! Используем HALF_SHAFT.");
                shaftModel = ModModels.HALF_SHAFT;
            }

            this.shaft = instancerProvider().instancer(
                    InstanceTypes.TRANSFORMED,
                    Models.partial(shaftModel)
            ).createInstance();
        }
    }

    private void setupStaticPositions() {
        applyStaticTransform(this.innerRing);
        if (this.shaft != null) {
            applyStaticTransform(this.shaft);
        }
    }

    private void applyStaticTransform(TransformedInstance instance) {
        instance.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            instance.rotateY((float) Math.toRadians(facing == EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            instance.rotateX((float) Math.toRadians(facing == UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            instance.rotateY((float) Math.toRadians(180));
        }

        instance.translate(-0.5f, -0.5f, -0.5f);
        instance.setChanged();
    }

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private long lastFrameTime = -1;
    private boolean phaseSynced = false;

    @Override
    public void beginFrame(Context ctx) {
        // 1. АБСОЛЮТНАЯ ЗАЩИТА ОТ ДЕСИНКА
        // Проверяем актуальное состояние NBT каждый кадр
        boolean shaftStateChanged = blockEntity.hasShaft() != (this.shaft != null);
        boolean materialChanged = blockEntity.getShaftMaterial() != currentMaterial;
        boolean diameterChanged = blockEntity.getShaftDiameter() != currentDiameter;

        // Если что-то изменилось (например, мы вставили вал) — мгновенно пересобираем
        if (shaftStateChanged || materialChanged || diameterChanged) {
            this.currentMaterial = blockEntity.getShaftMaterial();
            this.currentDiameter = blockEntity.getShaftDiameter();

            if (this.shaft != null) {
                this.shaft.delete();
                this.shaft = null;
            }

            createShaftInstance();

            if (this.shaft != null) {
                applyStaticTransform(this.shaft);
                relight(pos, this.shaft); // Считаем свет для нового вала
            }
        }

        // 2. ВРАЩЕНИЕ с плавной инерцией
        long now = System.currentTimeMillis();
        if (lastFrameTime == -1) lastFrameTime = now;
        float deltaSeconds = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;

        float targetSpeed = blockEntity.getVisualSpeed();

        // 2.1 Плавное изменение скорости
        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.001f) {
            smoothedSpeed += speedDiff * 4.0f * deltaSeconds; // Увеличил до 4
        } else {
            smoothedSpeed = targetSpeed;
        }

        // 2.2 Увеличиваем внутренний угол плавно
        currentAngle += smoothedSpeed * 2.0f * deltaSeconds;
        
        float twoPi = (float) (2 * Math.PI);
        currentAngle = currentAngle % twoPi;
        if (currentAngle < 0) currentAngle += twoPi;

        // 2.3 Синхронизация фазы при постоянной скорости
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

        // 2.4 ДОКОВКА ПРИ ОСТАНОВКЕ (МАГНИТНЫЙ ЭФФЕКТ)
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
        
        applyRotation(this.innerRing, currentAngle);
        if (this.shaft != null) {
            applyRotation(this.shaft, currentAngle);
        }
    }

    private void applyRotation(TransformedInstance instance, float angle) {
        instance.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            instance.rotateY((float) Math.toRadians(facing == EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            instance.rotateX((float) Math.toRadians(facing == UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            instance.rotateY((float) Math.toRadians(180));
        }

        instance.rotateZ(angle);
        instance.translate(-0.5f, -0.5f, -0.5f);
        instance.setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        if (this.shaft != null) {
            relight(pos, this.innerRing, this.shaft);
        } else {
            relight(pos, this.innerRing);
        }
    }

    @Override
    protected void _delete() {
        this.innerRing.delete();
        if (this.shaft != null) {
            this.shaft.delete();
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(this.innerRing);
        if (this.shaft != null) {
            consumer.accept(this.shaft);
        }
    }
}