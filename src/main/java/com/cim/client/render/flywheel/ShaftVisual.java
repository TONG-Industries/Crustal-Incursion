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
    }

    private void rebuildGear() {
        if (this.gearInstance != null) {
            this.gearInstance.delete();
            this.gearInstance = null;
        }

        net.minecraft.world.item.ItemStack gearStack = blockEntity.getAttachedGear();
        if (blockState.getValue(ShaftBlock.GEAR_SIZE) > 0 && !gearStack.isEmpty() && gearStack.getItem() instanceof com.cim.item.rotation.GearItem) {
            net.minecraft.resources.ResourceLocation gearId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gearStack.getItem());
            String gearName = gearId != null ? gearId.getPath() : "";
            PartialModel gearModel = ModModels.GEAR_MODELS.get(gearName);

            if (gearModel != null) {
                this.gearInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(gearModel)).createInstance();
                int parity = (pos.getX() + pos.getY() + pos.getZ()) % 2;
                this.phaseOffset = (float) Math.toRadians(parity == 0 ? 22.5 : 0);

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

        // БЫЛО: float speed = blockEntity.getSpeed();
// СТАЛО: берем адаптированную скорость
        float speed = blockEntity.getVisualSpeed();
        float time = (float) (System.currentTimeMillis() % 100000) / 50f;
        float angle = speed == 0 ? 0 : time * speed * 0.1f;

        setupStatic(shaftInstance, angle);

        if (gearInstance != null) {
            setupStatic(gearInstance, angle + phaseOffset);
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