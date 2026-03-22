package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.Rotational;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Random;

public class WindGenFlugerBlockEntity extends BlockEntity implements GeoBlockEntity, Rotational, RotationalNode {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private long speed = 0;
    private long torque = 0;
    private static final long MAX_SPEED = 100;
    private static final long MAX_TORQUE = 25;

    // Выходная сторона (куда передаётся вращение) – всегда вниз
    private static final Direction OUTPUT_SIDE = Direction.DOWN;

    // Интервал изменения ветра (30 секунд = 600 тиков) + случайная вариация
    private int changeCooldown = 0;
    private static final int BASE_CHANGE_INTERVAL = 600; // 30 сек
    private static final int INTERVAL_VARIATION = 200;   // ±10 сек

    private final Random random = new Random();

    // Анимация вращения
    private float currentAnimationSpeed = 0f;
    private static final float ACCELERATION = 0.05f;
    private static final float DECELERATION = 0.02f;
    private static final int STOP_DELAY_TICKS = 10;
    private int ticksWithoutPower = 0;

    // Направление ветра (поворот корпуса)
    private float currentWindYaw = 0f;
    private float targetWindYaw = 0f;
    private boolean shouldPlayTurnAnimation = false;

    private static final float MAX_TURN_PER_TICK = 0.5f; // макс. поворот за тик (град)
    private static final float MAX_YAW_CHANGE = 10f;     // макс. изменение направления за раз (град)

    // Анимации
    private static final RawAnimation ROTATION = RawAnimation.begin().thenLoop("rotation");
    private static final RawAnimation FLUGER = RawAnimation.begin().thenLoop("fluger");
    private static final RawAnimation FLUGER_FAST = RawAnimation.begin().thenPlay("fluger_fast");

    // Поля для RotationalNode (кеш источника – не используется, но требуется интерфейсом)
    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    public float getCurrentWindYaw() {
        return currentWindYaw;
    }

    public WindGenFlugerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_GEN_FLUGER_BE.get(), pos, state);
        // При первом создании генерируем случайные начальные значения
        speed = (long) (random.nextDouble() * MAX_SPEED);
        torque = calculateTorque(speed);
        targetWindYaw = (random.nextFloat() * 80) - 40; // от -40 до 40
        currentWindYaw = targetWindYaw; // без интерполяции вначале
        changeCooldown = random.nextInt(BASE_CHANGE_INTERVAL + INTERVAL_VARIATION) + 20; // небольшой разброс
    }

    private long calculateTorque(long spd) {
        return Math.round(spd * MAX_TORQUE / (double) MAX_SPEED);
    }

    // ========== Rotational ==========
    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }
    // ========== Rotational ==========
    @Override public void setSpeed(long speed) {
        long oldSpeed = this.speed;
        this.speed = Math.min(speed, MAX_SPEED);
        this.torque = calculateTorque(this.speed);

        if (this.speed != oldSpeed) {
            setChanged();
            sync();
            invalidateNeighborCaches(); // ВАЖНО: Уведомляем вал снизу
        }
    }
    @Override public void setTorque(long torque) {
        // torque устанавливается автоматически из speed, поэтому игнорируем
    }
    @Override public long getMaxSpeed() { return MAX_SPEED; }
    @Override public long getMaxTorque() { return MAX_TORQUE; }

    // ========== RotationalNode ==========
    @Override
    @Nullable
    public RotationSource getCachedSource() {
        return cachedSource;
    }

    @Override
    public void setCachedSource(@Nullable RotationSource source, long gameTime) {
        this.cachedSource = source;
        this.cacheTimestamp = gameTime;
    }

    @Override
    public boolean isCacheValid(long currentTime) {
        return cachedSource != null && (currentTime - cacheTimestamp) <= CACHE_LIFETIME;
    }


    @Override
    public void invalidateCache() {
        // Ветряк сам источник, ему не нужно сбрасывать свой кеш (он null),
        // но он может пнуть соседа снизу
        invalidateNeighborCaches();
    }

    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        return new Direction[0]; // Не передает сигнал сквозь себя
    }

    @Override
    public boolean canProvideSource(@Nullable Direction fromDir) {
        return fromDir == OUTPUT_SIDE; // Только снизу
    }

    private void invalidateNeighborCaches() {
        if (level == null || level.isClientSide) return;
        BlockPos neighborPos = worldPosition.relative(OUTPUT_SIDE);
        BlockEntity neighbor = level.getBlockEntity(neighborPos);
        if (neighbor instanceof RotationalNode node) {
            node.invalidateCache();
        }
    }

    // ========== Тик ==========
    public static void tick(Level level, BlockPos pos, BlockState state, WindGenFlugerBlockEntity be) {
        if (level.isClientSide) {
            be.handleClientAnimation();
            be.handleWindYawAnimation();
        } else {
            be.tickServer();
        }
    }

    private void tickServer() {
        if (changeCooldown <= 0) {
            updateWindParameters();
            changeCooldown = BASE_CHANGE_INTERVAL + random.nextInt(INTERVAL_VARIATION * 2 + 1) - INTERVAL_VARIATION;
            if (changeCooldown < 20) changeCooldown = 20;
        } else {
            changeCooldown--;
        }
    }

    private void updateWindParameters() {
        long currentSpeed = this.speed;
        double probIncrease = 1.0 - (currentSpeed / (double) MAX_SPEED);
        boolean increase = random.nextDouble() < probIncrease;

        long maxDelta = 10;
        long delta = (long) (random.nextDouble() * maxDelta) + 1;
        if (!increase) delta = -delta;

        long newSpeed = currentSpeed + delta;
        newSpeed = Math.max(0, Math.min(MAX_SPEED, newSpeed));
        setSpeed(newSpeed); // вызовет инвалидацию кеша у соседей

        float currentYaw = targetWindYaw;
        boolean increaseYaw = random.nextBoolean();
        float deltaYaw = (float) (random.nextDouble() * MAX_YAW_CHANGE);
        if (!increaseYaw) deltaYaw = -deltaYaw;

        float newYaw = currentYaw + deltaYaw;
        newYaw = Math.max(-40, Math.min(40, newYaw));
        setTargetWindYaw(newYaw);
    }

    // ========== Клиент: анимация ==========
    private void handleClientAnimation() {
        float targetSpeed = (speed > 0) ? Math.max(0.1f, speed / 100f) : 0f;
        if (targetSpeed > 0) {
            ticksWithoutPower = 0;
            if (currentAnimationSpeed < targetSpeed) {
                currentAnimationSpeed = Math.min(currentAnimationSpeed + ACCELERATION, targetSpeed);
            } else if (currentAnimationSpeed > targetSpeed) {
                currentAnimationSpeed = Math.max(currentAnimationSpeed - ACCELERATION, targetSpeed);
            }
        } else {
            if (currentAnimationSpeed > 0) {
                ticksWithoutPower++;
                if (ticksWithoutPower > STOP_DELAY_TICKS) {
                    currentAnimationSpeed = Math.max(currentAnimationSpeed - DECELERATION, 0f);
                }
            } else {
                ticksWithoutPower = 0;
            }
        }
    }

    private void handleWindYawAnimation() {
        if (Math.abs(targetWindYaw - currentWindYaw) > 0.01f) {
            float delta = targetWindYaw - currentWindYaw;
            float step = Math.copySign(Math.min(Math.abs(delta), MAX_TURN_PER_TICK), delta);
            currentWindYaw += step;
            if (!shouldPlayTurnAnimation && Math.abs(step) > 0.01f) {
                shouldPlayTurnAnimation = true;
                triggerAnim("fluger_controller", "fluger_fast");
            }
        } else {
            shouldPlayTurnAnimation = false;
        }
    }

    public void setTargetWindYaw(float yaw) {
        this.targetWindYaw = yaw;
        setChanged();
        sync();
    }

    // GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Контроллер вращения
        controllers.add(new AnimationController<>(this, "rotation_controller", 0, this::rotationPredicate));

        // Контроллер флюгера
        AnimationController<WindGenFlugerBlockEntity> flugerController = new AnimationController<>(this, "fluger_controller", 0, this::flugerPredicate);
        flugerController.setAnimation(FLUGER); // сразу задаём зацикленную анимацию
        flugerController.setAnimationSpeed(1.0f);
        controllers.add(flugerController);
    }

    private <E extends GeoBlockEntity> PlayState rotationPredicate(AnimationState<E> event) {
        AnimationController<?> controller = event.getController();
        // Устанавливаем анимацию только если контроллер остановлен (например, при первом запуске или после сбоя)
        if (controller.getAnimationState() == AnimationController.State.STOPPED) {
            controller.setAnimation(ROTATION);
        }
        controller.setAnimationSpeed(currentAnimationSpeed);
        return PlayState.CONTINUE;
    }

    private <E extends GeoBlockEntity> PlayState flugerPredicate(AnimationState<E> event) {
        AnimationController<?> controller = event.getController();
        // Если контроллер остановлен (после проигрывания fluger_fast), возвращаем зацикленную анимацию
        if (controller.getAnimationState() == AnimationController.State.STOPPED) {
            controller.setAnimation(FLUGER);
        }
        return PlayState.CONTINUE;
    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getBoneResetTime() { return 0; }

    // NBT
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        tag.putFloat("TargetWindYaw", targetWindYaw);
        tag.putInt("ChangeCooldown", changeCooldown);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
        targetWindYaw = tag.getFloat("TargetWindYaw");
        changeCooldown = tag.getInt("ChangeCooldown");
        // currentWindYaw не сохраняем, так как на клиенте она интерполируется
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        tag.putFloat("TargetWindYaw", targetWindYaw);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
        targetWindYaw = tag.getFloat("TargetWindYaw");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}