package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.basic.industrial.rotation.Mode;
import com.cim.block.basic.industrial.rotation.TachometerBlock;
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

public class TachometerBlockEntity extends BlockEntity implements RotationalNode {

    private long speed = 0;
    private long torque = 0;
    private int multiplier = 1; // 1,2,3

    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    public TachometerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TACHOMETER_BE.get(), pos, state);
    }

    // ========== Rotational ==========
    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }

    @Override
    public void setSpeed(long speed) {
        this.speed = speed;
        setChanged();
        sync();
        invalidateNeighborCaches(); // ВАЖНО
    }

    @Override
    public void setTorque(long torque) {
        this.torque = torque;
        setChanged();
        sync();
        invalidateNeighborCaches(); // ВАЖНО
    }

    @Override public long getMaxSpeed() { return 0; }
    @Override public long getMaxTorque() { return 0; }

    // ========== RotationalNode ==========
    @Override @Nullable public RotationSource getCachedSource() { return cachedSource; }
    @Override public void setCachedSource(@Nullable RotationSource source, long gameTime) { this.cachedSource = source; this.cacheTimestamp = gameTime; }
    @Override public boolean isCacheValid(long currentTime) { return cachedSource != null && (currentTime - cacheTimestamp) <= CACHE_LIFETIME; }

    @Override
    public void invalidateCache() {
        // Проверка обязательна, чтобы избежать бесконечной рекурсии!
        if (this.cachedSource != null) {
            this.cachedSource = null;
            if (level != null && !level.isClientSide) {
                invalidateNeighborCaches();
            }
        }
    }
    private void invalidateNeighborCaches() {
        if (level == null || level.isClientSide) return;
        Direction facing = getBlockState().getValue(TachometerBlock.FACING);
        Direction left = TachometerBlock.getLeft(facing);
        Direction right = TachometerBlock.getRight(facing);

        for (Direction dir : new Direction[]{left, right}) {
            if (dir != null) {
                BlockPos neighborPos = worldPosition.relative(dir);
                if (level.getBlockEntity(neighborPos) instanceof RotationalNode node) {
                    node.invalidateCache();
                }
            }
        }
    }

    @Override
    public boolean canProvideSource(@Nullable Direction fromDir) {
        return false;
    }

    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        Direction facing = getBlockState().getValue(TachometerBlock.FACING);
        Direction left = TachometerBlock.getLeft(facing);
        Direction right = TachometerBlock.getRight(facing);

        if (fromDir != null) {
            if (fromDir == left || fromDir == right) {
                return new Direction[]{fromDir.getOpposite()};
            } else {
                return new Direction[0];
            }
        } else {
            return new Direction[]{left, right};
        }
    }

    // ========== Специфичные методы ==========
    public int getMultiplier() { return multiplier; }

    public void setMultiplier(int multiplier) {
        if (multiplier < 1 || multiplier > 3) multiplier = 1;
        this.multiplier = multiplier;
        setChanged();
        sync();
        updateRedstone();
    }

    public Mode getMode() {
        return getBlockState().getValue(TachometerBlock.MODE);
    }

    public int getRedstoneSignal() {
        // Используем текущие поля speed/torque, так как cachedSource может быть null в промежутках
        long value = (getMode() == Mode.SPEED) ? this.speed : this.torque;
        if (value <= 0) return 0;

        // Масштабируем: допустим, при multiplier=1 и скорости 100 сигнал будет 5
        // Чтобы сигнал не "дергался", добавим небольшое округление вверх
        double raw = (value * (double)multiplier) / 20.0;
        return (int) Math.min(15, Math.max(0, raw));
    }

    // ========== Тик ==========
    public static void tick(Level level, BlockPos pos, BlockState state, TachometerBlockEntity be) {
        if (level.isClientSide) return;

        long currentTime = level.getGameTime();

        if (!be.isCacheValid(currentTime)) {
            RotationSource source = RotationNetworkHelper.findSource(be, null);
            be.setCachedSource(source, currentTime);
            // Здесь be.speed еще старый, но updateRedstone вызовет пересчет.
            // Однако основной "удар" должен быть ниже.
        }

        RotationSource src = be.getCachedSource();
        long newSpeed = (src != null) ? src.speed() : 0;
        long newTorque = (src != null) ? src.torque() : 0;

        boolean changed = false;
        if (be.speed != newSpeed) { be.speed = newSpeed; changed = true; } // ПРИСВАИВАЕМ СРАЗУ
        if (be.torque != newTorque) { be.torque = newTorque; changed = true; }

        if (changed) {
            be.setChanged();
            be.sync();
            be.invalidateNeighborCaches();
            // Теперь, когда be.speed и be.torque УЖЕ обновлены,
            // уведомляем соседей. Они вызовут getSignal и получат новые данные.
            be.updateRedstone();
        }
    }

    // TachometerBlockEntity.java

    private void updateRedstone() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            // 1. Уведомляем сам блок
            level.updateNeighborsAt(worldPosition, state.getBlock());

            // 2. Уведомляем ВСЕХ соседей, что сигнал вокруг изменился
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = worldPosition.relative(dir);
                level.neighborChanged(neighborPos, state.getBlock(), worldPosition);
                level.updateNeighborsAt(neighborPos, state.getBlock());
            }
        }
    }

    // ========== NBT и синхронизация ==========
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        tag.putInt("Multiplier", multiplier);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
        multiplier = tag.getInt("Multiplier");
        if (multiplier < 1 || multiplier > 3) multiplier = 1;
        cachedSource = null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        tag.putInt("Multiplier", multiplier);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
        multiplier = tag.getInt("Multiplier");
        if (multiplier < 1 || multiplier > 3) multiplier = 1;
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

    public boolean hasSource() {
        return cachedSource != null;
    }
}