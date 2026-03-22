package com.cim.block.entity.industrial.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.cim.api.energy.IEnergyConnector;
import com.cim.api.energy.IEnergyReceiver;
import com.cim.api.energy.LongEnergyWrapper;
import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.Rotational;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.basic.industrial.rotation.MotorElectroBlock;
import com.cim.block.entity.ModBlockEntities;
import com.cim.capability.ModCapabilities;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class MotorElectroBlockEntity extends BlockEntity implements
        GeoBlockEntity, Rotational, RotationalNode, IEnergyReceiver, IEnergyConnector {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ========== Стандартные методы IEnergyReceiver ==========
    @Override
    public IEnergyReceiver.Priority getPriority() {
        return IEnergyReceiver.Priority.NORMAL;
    }

    private long speed = 0;
    private long torque = 0;
    private static final long MAX_SPEED = 100;
    private static final long MAX_TORQUE = 50;

    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    private float currentAnimationSpeed = 0f;
    private static final float ACCELERATION = 0.1f;
    private static final float DECELERATION = 0.05f;
    private static final int STOP_DELAY_TICKS = 5;
    private static final float MIN_ANIM_SPEED = 0.01f;
    private int ticksWithoutPower = 0;

    private long energyStored = 0;
    private final long MAX_ENERGY = 50000;
    private final long MAX_RECEIVE = 1000;
    private final long ENERGY_PER_TICK = 25; // 500 / 20

    private boolean isGeneratorMode = false;
    private long lastReceivedRotation = 0;
    private static final double GEN_EFFICIENCY = 0.75;

    private boolean isSwitchedOn = false;
    private int bootTimer = 0;

    private final LazyOptional<IEnergyReceiver> hbmReceiverOptional = LazyOptional.of(() -> this);
    private final LazyOptional<IEnergyConnector> hbmConnectorOptional = LazyOptional.of(() -> this); // <-- добавлено
    private final LazyOptional<IEnergyStorage> forgeEnergyOptional = LazyOptional.of(() -> new LongEnergyWrapper(this, LongEnergyWrapper.BitMode.LOW));

    public MotorElectroBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOTOR_ELECTRO_BE.get(), pos, state);
    }

    // ========== Управление ==========

    public void togglePower() {
        this.isSwitchedOn = !this.isSwitchedOn;
        if (this.isSwitchedOn) {
            this.bootTimer = 60;
        } else {
            stopMotor();
        }
        updateState();
    }

    public void toggleGeneratorMode() {
        this.isGeneratorMode = !this.isGeneratorMode;
        stopMotor();
        this.lastReceivedRotation = 0;
        updateState();
    }

    private void updateState() {
        setChanged();
        sync();
        invalidateNeighborCaches();
    }

    private void stopMotor() {
        this.speed = 0;
        this.torque = 0;
        this.bootTimer = 0;
        // Принудительно уведомить соседей об остановке
        if (level != null && !level.isClientSide) {
            invalidateNeighborCaches();
        }
    }

    // ========== Тик (Логика) ==========

    public static void tick(Level level, BlockPos pos, BlockState state, MotorElectroBlockEntity be) {
        if (level.isClientSide) {
            be.handleClientAnimation(); // Добавили обработку анимации на клиенте
            return;
        }

        // === СЕРДЦЕБИЕНИЕ СЕТИ (Дефибриллятор) ===
        // Гарантирует, что мотор НИКОГДА не выпадет из энергосети,
        // даже если чанки загрузились криво или сеть перестроилась.
        if (level.getGameTime() % 20 == 0) {
            com.cim.api.energy.EnergyNetworkManager manager = com.cim.api.energy.EnergyNetworkManager.get((net.minecraft.server.level.ServerLevel) level);
            if (!manager.hasNode(pos)) {
                manager.addNode(pos);
            }
        }
        // =========================================

        if (!be.isSwitchedOn) {
            if (be.speed != 0) {
                be.stopMotor();
                be.updateState();
            }
            be.lastReceivedRotation = 0;
            return;
        }

        if (be.isGeneratorMode) {
            // Оптимизация: используем кеш, как в валах
            long currentTime = level.getGameTime();
            if (!be.isCacheValid(currentTime)) {
                RotationSource source = RotationNetworkHelper.findSource(be, null);
                be.setCachedSource(source, currentTime);
            }

            RotationSource src = be.getCachedSource();
            long newSpeed = 0;
            long newTorque = 0;

            if (src != null && src.speed() > 0) {
                newSpeed = src.speed();
                newTorque = src.torque();
                be.lastReceivedRotation = Math.abs(newSpeed * newTorque);
                long generated = (long) ((be.lastReceivedRotation / 20.0) * GEN_EFFICIENCY);
                be.energyStored = Math.min(be.MAX_ENERGY, be.energyStored + generated);
            } else {
                be.lastReceivedRotation = 0;
            }

            // Записываем скорость в be.speed, чтобы она улетела на клиент для анимации
            if (be.speed != newSpeed || be.torque != newTorque) {
                be.speed = newSpeed;
                be.torque = newTorque;
                be.updateState();
            }

            if (be.energyStored > 0) be.pushEnergyToNeighbors();
        } else {
            // Режим МОТОРА
            if (be.bootTimer > 0) {
                be.bootTimer--;
                return;
            }

            if (be.energyStored >= be.ENERGY_PER_TICK) {
                be.energyStored -= be.ENERGY_PER_TICK;
                if (be.speed != MAX_SPEED) {
                    be.speed = MAX_SPEED;
                    be.torque = MAX_TORQUE;
                    be.updateState();
                }
            } else {
                be.stopMotor();
                be.updateState();
            }
        }
    }

    private void pushEnergyToNeighbors() {
        for (Direction dir : Direction.values()) {
            if (this.energyStored <= 0) break;
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbor != null) {
                neighbor.getCapability(ModCapabilities.ENERGY_RECEIVER, dir.getOpposite()).ifPresent(receiver -> {
                    long toPush = Math.min(this.energyStored, 1000);
                    long accepted = receiver.receiveEnergy(toPush, false);
                    this.energyStored -= accepted;
                    if (accepted > 0) setChanged();
                });
            }
        }
    }

    // ========== Rotational Node (Связь с Create) ==========

    @Override
    public boolean canProvideSource(@Nullable Direction fromDir) {
        boolean canWork = isSwitchedOn && !isGeneratorMode && energyStored > 0 && bootTimer <= 0;
        return canWork && fromDir == getBlockState().getValue(MotorElectroBlock.FACING);
    }

    @Override
    public void invalidateCache() {
        if (this.cachedSource != null) {
            this.cachedSource = null;
        }
    }

    private void invalidateNeighborCaches() {
        if (level == null || level.isClientSide) return;
        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbor instanceof RotationalNode node) {
                node.invalidateCache();
            }
        }
    }

    // ========== Стандартные методы ==========

    @Override public long getEnergyStored() { return energyStored; }
    @Override public long getMaxEnergyStored() { return MAX_ENERGY; }
    @Override public void setEnergyStored(long energy) { this.energyStored = Math.max(0, Math.min(energy, MAX_ENERGY)); setChanged(); }
    @Override public long getReceiveSpeed() { return MAX_RECEIVE; }
    @Override public boolean canReceive() { return !isGeneratorMode && energyStored < MAX_ENERGY; }

    // ИСПРАВЛЕНО: Разрешаем подключение со всех сторон, кроме лицевой
    @Override
    public boolean canConnectEnergy(Direction side) {
        return side != getBlockState().getValue(MotorElectroBlock.FACING);
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        if (!canReceive()) return 0;
        long energyReceived = Math.min(MAX_ENERGY - energyStored, Math.min(MAX_RECEIVE, maxReceive));
        if (!simulate && energyReceived > 0) {
            energyStored += energyReceived;
            setChanged();
        }
        return energyReceived;
    }

    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }
    @Override public void setSpeed(long speed) { this.speed = Math.min(speed, MAX_SPEED); updateState(); }
    @Override public void setTorque(long torque) { this.torque = Math.min(torque, MAX_TORQUE); updateState(); }
    @Override public long getMaxSpeed() { return MAX_SPEED; }
    @Override public long getMaxTorque() { return MAX_TORQUE; }

    @Nullable
    private static RotationSource getDirectSource(BlockEntity neighbor, Direction dir) {
        if (neighbor instanceof MotorElectroBlockEntity motor) {
            Direction motorFacing = motor.getBlockState().getValue(MotorElectroBlock.FACING);
            // ВАЖНО: Если мотор в режиме генератора, он НЕ является источником!
            if (motorFacing == dir.getOpposite() && !motor.isGeneratorMode()) {
                return new RotationSource(motor.getSpeed(), motor.getTorque());
            }
        } else if (neighbor instanceof WindGenFlugerBlockEntity windGen) {
            return new RotationSource(windGen.getSpeed(), windGen.getTorque());
        }
        return null;
    }

    @Override
    public @Nullable RotationSource getCachedSource() {
        return null;
    }

    @Override public void setCachedSource(@Nullable RotationSource source, long gameTime) { this.cachedSource = source; this.cacheTimestamp = gameTime; }
    @Override public boolean isCacheValid(long currentTime) { return cachedSource != null && (currentTime - cacheTimestamp) <= CACHE_LIFETIME; }
    public boolean isGeneratorMode() {
        return this.isGeneratorMode;
    }
    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        return new Direction[]{getBlockState().getValue(MotorElectroBlock.FACING)};
    }

    // ========== Capabilities ==========

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        // Проверка на энергетические capabilities
        if (cap == ModCapabilities.ENERGY_RECEIVER || cap == ModCapabilities.ENERGY_CONNECTOR) {
            // Возвращаем capability только если сторона разрешена (или side == null для внутренних вызовов)
            if (side == null || canConnectEnergy(side)) {
                if (cap == ModCapabilities.ENERGY_RECEIVER) {
                    return hbmReceiverOptional.cast();
                } else {
                    return hbmConnectorOptional.cast();
                }
            } else {
                return LazyOptional.empty();
            }
        }
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY) {
            return forgeEnergyOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        hbmReceiverOptional.invalidate();
        hbmConnectorOptional.invalidate();
        forgeEnergyOptional.invalidate();
    }

    // ========== NBT и синхронизация ==========

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        isGeneratorMode = tag.getBoolean("IsGeneratorMode");
        energyStored = tag.getLong("Energy");
        isSwitchedOn = tag.getBoolean("IsSwitchedOn");
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("IsGeneratorMode", isGeneratorMode);
        tag.putLong("Energy", energyStored);
        tag.putBoolean("IsSwitchedOn", isSwitchedOn);
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    // ========== GUI Data ==========

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.min(energyStored, Integer.MAX_VALUE);
                case 1 -> (int) Math.min(MAX_ENERGY, Integer.MAX_VALUE);
                case 2 -> isSwitchedOn ? 1 : 0;
                case 3 -> bootTimer;
                case 4 -> (int) Math.min(isGeneratorMode ? lastReceivedRotation : speed * torque, 100000);
                case 5 -> isGeneratorMode ? 1 : 0;
                default -> 0;
            };
        }
        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = value;
                case 2 -> isSwitchedOn = value == 1;
                case 3 -> bootTimer = value;
                case 5 -> isGeneratorMode = value == 1;
            }
        }
        @Override public int getCount() { return 6; }
    };

    public ContainerData getDataAccess() { return data; }

    @Override
    public void onLoad() {
        super.onLoad();
        // При загрузке блока или установке заставляем его крикнуть: "Я ТУТ!"
        if (level != null && !level.isClientSide) {
            com.cim.api.energy.EnergyNetworkManager.get(
                    (net.minecraft.server.level.ServerLevel) level).addNode(worldPosition);
        }
    }

    // ========== GeckoLib ==========
    private void handleClientAnimation() {
        float targetSpeed = (speed > 0) ? Math.max(0.1f, speed / 100f) : 0f;
        if (targetSpeed > 0) {
            ticksWithoutPower = 0;
            if (currentAnimationSpeed < targetSpeed) currentAnimationSpeed = Math.min(currentAnimationSpeed + ACCELERATION, targetSpeed);
            else if (currentAnimationSpeed > targetSpeed) currentAnimationSpeed = Math.max(currentAnimationSpeed - ACCELERATION, targetSpeed);
        } else {
            if (currentAnimationSpeed > 0) {
                ticksWithoutPower++;
                if (ticksWithoutPower > STOP_DELAY_TICKS) currentAnimationSpeed = Math.max(currentAnimationSpeed - DECELERATION, 0f);
            } else ticksWithoutPower = 0;
        }
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rotation", 0, this::animationPredicate));
    }

    private <E extends GeoBlockEntity> PlayState animationPredicate(AnimationState<E> event) {
        if (currentAnimationSpeed < MIN_ANIM_SPEED) return PlayState.STOP;
        event.getController().setAnimation(RawAnimation.begin().thenLoop("rotation"));
        event.getController().setAnimationSpeed(currentAnimationSpeed);
        return PlayState.CONTINUE;
    }
}