package com.cim.client.render.flywheel;

import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ShaftVisual extends AbstractBlockEntityVisual<ShaftBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance shaftInstance;
    @Nullable private TransformedInstance gearInstance;
    @Nullable private TransformedInstance pulleyInstance; // НОВОЕ

    private final Direction facing;

    // Список для ремней теперь использует наш кастомный тип BeltInstance
    private final java.util.List<BeltInstance> beltSegments = new java.util.ArrayList<>();
    private BlockPos lastConnectedPos = null;

    private float phaseOffset = 0f;
    private net.minecraft.world.item.Item currentGearItem;
    private net.minecraft.world.item.Item currentPulleyItem; // НОВОЕ

    // Локальные координаты
    private final float localX;
    private final float localY;
    private final float localZ;

    public ShaftVisual(VisualizationContext ctx, ShaftBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.facing = blockState.getValue(ShaftBlock.FACING);

        // Вычисляем локальную позицию
        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        // 1. ИНИЦИАЛИЗАЦИЯ ВАЛА
        net.minecraft.resources.ResourceLocation shaftId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
        String shaftName = shaftId != null ? shaftId.getPath() : "";
        PartialModel shaftModel = ModModels.SHAFT_MODELS.getOrDefault(shaftName, ModModels.HALF_SHAFT);
        this.shaftInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel)).createInstance();

        // 2. Инициализация шестерни и шкива
        this.currentGearItem = blockEntity.getAttachedGear().getItem();
        this.currentPulleyItem = blockEntity.getAttachedPulley().getItem();

        rebuildGear();
        rebuildPulley();

        setupStatic(shaftInstance, 0);
        updateLight(partialTick);

        // INFO: Логируем создание визуала (Твой оригинальный лог)
        if (com.cim.main.CrustalIncursionMod.LOGGER.isInfoEnabled()) {
            com.cim.main.CrustalIncursionMod.LOGGER.info("[CIM-Visual] ShaftVisual CREATED at {} | model={} | origin=({},{},{})",
                    pos, shaftModel != null ? "OK" : "NULL",
                    ctx.renderOrigin().getX(), ctx.renderOrigin().getY(), ctx.renderOrigin().getZ());
        }
    }

    private void rebuildGear() {
        if (this.gearInstance != null) {
            this.gearInstance.delete();
            this.gearInstance = null;
        }

        net.minecraft.world.item.ItemStack gearStack = blockEntity.getAttachedGear();
        int gearSize = blockState.getValue(ShaftBlock.GEAR_SIZE);

        if (gearSize > 0 && !gearStack.isEmpty() && gearStack.getItem() instanceof com.cim.item.rotation.GearItem) {
            net.minecraft.resources.ResourceLocation gearId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gearStack.getItem());
            String gearName = gearId != null ? gearId.getPath() : "";
            PartialModel gearModel = ModModels.GEAR_MODELS.get(gearName);

            if (gearModel != null) {
                this.gearInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(gearModel)).createInstance();

                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();

                int axisCoord = 0;
                if (facing.getAxis() == Direction.Axis.X) axisCoord = x;
                else if (facing.getAxis() == Direction.Axis.Y) axisCoord = y;
                else if (facing.getAxis() == Direction.Axis.Z) axisCoord = z;

                // Твой алгоритм четности для стыковки
                int parity = Math.abs(x + y + z + axisCoord + (gearSize == 2 ? 1 : 0)) % 2;
                float halfToothAngle = gearSize == 2 ? 11.25f : 22.5f;
                this.phaseOffset = (float) Math.toRadians(parity == 0 ? halfToothAngle : 0);

                setupStatic(this.gearInstance, this.phaseOffset);
            }
        }
    }

    private void rebuildPulley() {
        if (this.pulleyInstance != null) {
            this.pulleyInstance.delete();
            this.pulleyInstance = null;
        }

        int pulleySize = blockState.getValue(ShaftBlock.PULLEY_SIZE);
        if (pulleySize > 0 && blockEntity.hasPulley()) {
            PartialModel pulleyModel = ModModels.PULLEY_MODELS.get("pulley");
            if (pulleyModel != null) {
                this.pulleyInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(pulleyModel)).createInstance();
                setupStatic(this.pulleyInstance, 0);
            }
        }
    }

    private void setupStatic(TransformedInstance instance, float initialRotationZ) {
        instance.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            instance.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            instance.rotateX((float) Math.toRadians(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            instance.rotateY((float) Math.toRadians(180));
        }

        if (initialRotationZ != 0) {
            instance.rotateZ(initialRotationZ);
        }

        instance.translate(-0.5f, -0.5f, -0.5f);
        instance.setChanged();
    }

    @Override
    public void update(float pt) {
        super.update(pt);
        // Проверка изменений предметов для динамического обновления
        if (blockEntity.getAttachedGear().getItem() != this.currentGearItem) {
            this.currentGearItem = blockEntity.getAttachedGear().getItem();
            rebuildGear();
            updateLight(pt);
        }
        if (blockEntity.getAttachedPulley().getItem() != this.currentPulleyItem) {
            this.currentPulleyItem = blockEntity.getAttachedPulley().getItem();
            rebuildPulley();
            updateLight(pt);
        }
    }

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private long lastFrameTime = -1;
    private float lastLoggedSpeed = Float.NaN;
    private boolean phaseSynced = false;

    @Override
    public void beginFrame(Context ctx) {
        // Мгновенное обновление при смене деталей
        if (blockEntity.getAttachedGear().getItem() != this.currentGearItem) {
            this.currentGearItem = blockEntity.getAttachedGear().getItem();
            rebuildGear();
            if (this.gearInstance != null) relight(pos, this.gearInstance);
        }
        if (blockEntity.getAttachedPulley().getItem() != this.currentPulleyItem) {
            this.currentPulleyItem = blockEntity.getAttachedPulley().getItem();
            rebuildPulley();
            if (this.pulleyInstance != null) relight(pos, this.pulleyInstance);
        }

        // Логика обновления ремня
        BlockPos connectedPos = blockEntity.getConnectedPulley();
        if (connectedPos != lastConnectedPos) {
            lastConnectedPos = connectedPos;
            rebuildBelt();
        }

        long now = System.currentTimeMillis();
        if (lastFrameTime == -1) lastFrameTime = now;
        float deltaSeconds = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;

        float targetSpeed = blockEntity.getVisualSpeed();

        // Твой диагностический лог
        if (targetSpeed != lastLoggedSpeed) {
            com.cim.main.CrustalIncursionMod.LOGGER.info("[VISUAL-DIAG] beginFrame at {} | speed changed: {} -> {}",
                    pos, lastLoggedSpeed, targetSpeed);
            lastLoggedSpeed = targetSpeed;
        }

        // 1. Плавная визуальная инерция (Твоя логика)
        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.001f) {
            smoothedSpeed += speedDiff * 4.0f * deltaSeconds;
        } else {
            smoothedSpeed = targetSpeed;
        }

        // 2. Расчет угла
        currentAngle += smoothedSpeed * 2.0f * deltaSeconds;
        float twoPi = (float) (2 * Math.PI);
        currentAngle = currentAngle % twoPi;
        if (currentAngle < 0) currentAngle += twoPi;

        // 3. Твоя синхронизация фазы
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

        // 4. Твой МАГНИТНЫЙ ЭФФЕКТ доковки зубьев
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

        // 5. НОВОЕ: АНИМАЦИЯ РЕМНЯ (Сдвиг текстуры через шейдер)
        float radius = getPulleyRadius(blockEntity);
        float speedScroll = currentAngle * radius;

        for (BeltInstance segment : beltSegments) {
            // Двигаем текстуру сегмента синхронно с валом
            segment.setUv(segment.uvScale, segment.uvScroll + speedScroll);
            segment.setChanged();
        }

        // Применяем финальные трансформации
        setupStatic(shaftInstance, currentAngle);
        if (gearInstance != null) setupStatic(gearInstance, currentAngle + phaseOffset);
        if (pulleyInstance != null) setupStatic(pulleyInstance, currentAngle);
    }

    private float getPulleyRadius(ShaftBlockEntity be) {
        if (be.hasPulley() && be.getAttachedPulley().getItem() instanceof com.cim.item.rotation.PulleyItem pulley) {
            return (pulley.getDiameterPixels() / 2.0f) / 16.0f;
        }
        return 0f;
    }

    private void rebuildBelt() {
        beltSegments.forEach(Instance::delete);
        beltSegments.clear();

        BlockPos connectedPos = blockEntity.getConnectedPulley();
        if (connectedPos == null) return;
        if (pos.compareTo(connectedPos) > 0) return;

        if (!(level.getBlockEntity(connectedPos) instanceof ShaftBlockEntity otherBE)) return;
        if (!blockEntity.hasPulley() || !otherBE.hasPulley()) return;

        float r1 = getPulleyRadius(blockEntity);
        float r2 = getPulleyRadius(otherBE);
        if (r1 == 0 || r2 == 0) return;

        Direction.Axis axis = facing.getAxis();
        float dx = connectedPos.getX() - pos.getX();
        float dy = connectedPos.getY() - pos.getY();
        float dz = connectedPos.getZ() - pos.getZ();

        float du = 0, dv = 0;
        if (axis == Direction.Axis.X) { du = dz; dv = dy; }
        else if (axis == Direction.Axis.Y) { du = dx; dv = dz; }
        else if (axis == Direction.Axis.Z) { du = dx; dv = dy; }

        float distance = (float) Math.sqrt(du * du + dv * dv);
        float baseAngle = (float) Math.atan2(dv, du);
        float alpha = (float) Math.asin((r1 - r2) / distance);
        float straightLength = (float) Math.sqrt(distance * distance - (r1 - r2) * (r1 - r2));

        // Прямые участки (Используем возможности шейдера рисовать любую длину без растягивания)
        float dirAngle1 = baseAngle - alpha;
        float touchAngle1 = dirAngle1 + (float)Math.PI / 2f;
        addBeltSegment(axis, r1 * (float)Math.cos(touchAngle1), r1 * (float)Math.sin(touchAngle1), dirAngle1, straightLength, 0);

        float dirAngle2 = baseAngle + alpha;
        float touchAngle2 = dirAngle2 - (float)Math.PI / 2f;
        addBeltSegment(axis, r1 * (float)Math.cos(touchAngle2), r1 * (float)Math.sin(touchAngle2), dirAngle2, straightLength, 0);

        // Дуги обхвата
        renderArc(axis, 0, 0, r1, touchAngle1, touchAngle2);
        renderArc(axis, du, dv, r2, touchAngle2, touchAngle1);
    }

    private void renderArc(Direction.Axis axis, float uCenter, float vCenter, float radius, float startAngle, float endAngle) {
        float step = (float) Math.toRadians(10);
        float currentDist = 0;

        for (float angle = startAngle; angle < endAngle; angle += step) {
            float nextAngle = Math.min(angle + step, endAngle);
            float u1 = uCenter + radius * (float)Math.cos(angle);
            float v1 = vCenter + radius * (float)Math.sin(angle);
            float u2 = uCenter + radius * (float)Math.cos(nextAngle);
            float v2 = vCenter + radius * (float)Math.sin(nextAngle);

            float len = (float)Math.sqrt(Math.pow(u2 - u1, 2) + Math.pow(v2 - v1, 2));
            float dirAngle = (float)Math.atan2(v2 - v1, u2 - u1);

            addBeltSegment(axis, u1, v1, dirAngle, len, currentDist);
            currentDist += len;
        }
    }

    private void addBeltSegment(Direction.Axis axis, float u, float v, float angle, float length, float baseOffset) {
        BeltInstance segment = instancerProvider()
                .instancer(BeltInstance.TYPE, Models.partial(ModModels.BELT_SEGMENT))
                .createInstance();

        segment.setIdentityTransform()
                .translate(localX + 0.5f, localY + 0.5f, localZ + 0.5f);

        if (axis == Direction.Axis.X) {
            segment.translate(0, v, u).rotateX(-angle);
        } else if (axis == Direction.Axis.Y) {
            segment.translate(u, 0, v).rotateY(-angle + (float)Math.PI / 2f).rotateZ((float)Math.PI / 2f);
        } else if (axis == Direction.Axis.Z) {
            segment.translate(u, v, 0).rotateZ(angle).rotateY((float)Math.PI / 2f);
        }

        segment.scale(1, 1, length);
        segment.translate(-0.5f, -0.5f, 0.0f);

        // Магия шейдера: передаем длину и офсет для идеальной сшивки текстуры
        segment.setUv(length, baseOffset);

        segment.setChanged();
        relight(pos, segment);
        beltSegments.add(segment);
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, shaftInstance);
        if (gearInstance != null) relight(pos, gearInstance);
        if (pulleyInstance != null) relight(pos, pulleyInstance);
        for (BeltInstance segment : beltSegments) {
            relight(pos, segment);
        }
    }

    @Override
    protected void _delete() {
        shaftInstance.delete();
        if (gearInstance != null) gearInstance.delete();
        if (pulleyInstance != null) pulleyInstance.delete();
        beltSegments.forEach(Instance::delete);
        beltSegments.clear();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(shaftInstance);
        if (gearInstance != null) consumer.accept(gearInstance);
        if (pulleyInstance != null) consumer.accept(pulleyInstance);
        for (BeltInstance segment : beltSegments) {
            consumer.accept(segment);
        }
    }
}