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
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ShaftVisual extends AbstractBlockEntityVisual<ShaftBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance shaftInstance;
    @Nullable
    private TransformedInstance gearInstance;
    private final Direction facing;

    private float phaseOffset = 0f;
    private net.minecraft.world.item.Item currentGearItem;

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

        // 2. Инициализация шестерни
        this.currentGearItem = blockEntity.getAttachedGear().getItem();
        rebuildGear();

        setupStatic(shaftInstance, 0);
        updateLight(partialTick);

        // INFO: Логируем создание визуала для отслеживания бага с прозрачностью
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

                // 1. Берем координаты
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();

                // 2. ИСКУССТВЕННОЕ СЖАТИЕ СЕТКИ ДЛЯ БОЛЬШИХ ШЕСТЕРНЕЙ
                // Так как они стоят через 2 блока, мы делим координаты на 2.
                // Теперь шестерни на x=0 и x=2 будут иметь разную четность!
                // Используем floorDiv для защиты от багов в отрицательных координатах мира.
                if (gearSize == 2) {
                    x = Math.floorDiv(x, 2);
                    y = Math.floorDiv(y, 2);
                    z = Math.floorDiv(z, 2);
                }

                // 3. Высчитываем шахматный порядок
                int parity = Math.abs(x + y + z) % 2;

                // 4. Выбираем угол смещения: 22.5 для малой, 11.25 для большой
                float halfToothAngle = gearSize == 2 ? 11.25f : 22.5f;
                this.phaseOffset = (float) Math.toRadians(parity == 0 ? halfToothAngle : 0);

                setupStatic(this.gearInstance, this.phaseOffset);
            }
        }
    }

    private void setupStatic(TransformedInstance instance, float initialRotationZ) {
        instance.setIdentityTransform()
                // Используем локальные координаты!
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
        if (blockEntity.getAttachedGear().getItem() != this.currentGearItem) {
            this.currentGearItem = blockEntity.getAttachedGear().getItem();
            rebuildGear();
            updateLight(pt);
        }
    }

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private long lastFrameTime = -1;
    private float lastLoggedSpeed = Float.NaN;

    @Override
    public void beginFrame(Context ctx) {
        // Мгновенно замечаем установку или снятие шестерни!
        if (blockEntity.getAttachedGear().getItem() != this.currentGearItem) {
            this.currentGearItem = blockEntity.getAttachedGear().getItem();
            rebuildGear();
            if (this.gearInstance != null) {
                relight(pos, this.gearInstance);
            }
        }

        long now = System.currentTimeMillis();
        if (lastFrameTime == -1) lastFrameTime = now;
        float deltaSeconds = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;

        float targetSpeed = blockEntity.getVisualSpeed();

        // DIAGNOSTIC: логируем только при изменении скорости
        if (targetSpeed != lastLoggedSpeed) {
            com.cim.main.CrustalIncursionMod.LOGGER.info("[VISUAL-DIAG] beginFrame at {} | speed changed: {} -> {}",
                    pos, lastLoggedSpeed, targetSpeed);
            lastLoggedSpeed = targetSpeed;
        }

        // 1. Плавная визуальная инерция (догоняем targetSpeed)
        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.1f) {
            smoothedSpeed += speedDiff * 3.0f * deltaSeconds; // Коэффициент 3.0 определяет резкость торможения
        } else {
            smoothedSpeed = targetSpeed;
        }

        // 2. Увеличиваем внутренний угол плавно
        // Оригинальная формула: time = millis / 50, angle = time * speed * 0.1f
        // deltaSeconds = deltaMillis / 1000. В тиках это deltaSeconds * 20.
        // change = (deltaSeconds * 20) * smoothedSpeed * 0.1f = smoothedSpeed * deltaSeconds * 2.0f
        currentAngle += smoothedSpeed * 2.0f * deltaSeconds;
        
        float twoPi = (float) (2 * Math.PI);
        currentAngle = currentAngle % twoPi;
        if (currentAngle < 0) currentAngle += twoPi;

        // 3. Синхронизация фазы шестерней при постоянной скорости
        if (smoothedSpeed == targetSpeed && targetSpeed != 0) {
            float time = (float) (now % 100000) / 50f;
            float globalAngle = (time * targetSpeed * 0.1f) % twoPi;
            if (globalAngle < 0) globalAngle += twoPi;
            
            float angleDiff = (globalAngle - currentAngle) % twoPi;
            if (angleDiff > Math.PI) angleDiff -= twoPi;
            if (angleDiff < -Math.PI) angleDiff += twoPi;
            
            // Плавно подгоняем фазу (2.0f радиан в секунду)
            currentAngle += angleDiff * 5.0f * deltaSeconds;
        }

        setupStatic(shaftInstance, currentAngle);

        if (gearInstance != null) {
            setupStatic(gearInstance, currentAngle + phaseOffset);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        // Свет всегда считается от абсолютных координат (pos)
        relight(pos, shaftInstance);
        if (gearInstance != null) relight(pos, gearInstance);
    }

    @Override
    protected void _delete() {
        shaftInstance.delete();
        if (gearInstance != null) gearInstance.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(shaftInstance);
        if (gearInstance != null) consumer.accept(gearInstance);
    }
}