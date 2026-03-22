package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.basic.industrial.rotation.StopperBlock;
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

import java.util.ArrayList;
import java.util.List;

public class StopperBlockEntity extends BlockEntity implements RotationalNode {

    private long speed = 0;
    private long torque = 0;

    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    public StopperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STOPPER_BE.get(), pos, state);
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
        Direction facing = getBlockState().getValue(StopperBlock.FACING);
        Direction left = getLeft(facing);
        Direction right = getRight(facing);

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
        // Если стоппер выключен (разомкнут), он не передает вращение
        if (!getBlockState().getValue(StopperBlock.ENABLED)) {
            return new Direction[0];
        }

        Direction facing = getBlockState().getValue(StopperBlock.FACING);
        Direction left = getLeft(facing);
        Direction right = getRight(facing);

        if (fromDir != null) {
            if (fromDir == left || fromDir == right) {
                return new Direction[]{fromDir.getOpposite()};
            } else {
                return new Direction[0];
            }
        } else {
            List<Direction> list = new ArrayList<>();
            if (left != null) list.add(left);
            if (right != null) list.add(right);
            return list.toArray(new Direction[0]);
        }
    }

    // Вспомогательные методы (можно вынести в утилиты)
    private Direction getLeft(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            case WEST -> Direction.SOUTH;
            case UP -> Direction.NORTH; // Для вертикальных стопперов логика может отличаться
            case DOWN -> Direction.SOUTH;
            default -> null;
        };
    }

    private Direction getRight(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> null;
        };
    }

    // ========== Тик ==========
    public static void tick(Level level, BlockPos pos, BlockState state, StopperBlockEntity be) {
        if (level.isClientSide) return;

        long currentTime = level.getGameTime();

        if (!be.isCacheValid(currentTime)) {
            // Используем новую сигнатуру
            RotationSource source = RotationNetworkHelper.findSource(be, null);
            be.setCachedSource(source, currentTime);
        }

        RotationSource src = be.getCachedSource();
        long newSpeed = (src != null) ? src.speed() : 0;
        long newTorque = (src != null) ? src.torque() : 0;

        boolean changed = false;
        if (be.speed != newSpeed) { be.speed = newSpeed; changed = true; }
        if (be.torque != newTorque) { be.torque = newTorque; changed = true; }

        if (changed) {
            be.setChanged();
            be.sync();
            be.invalidateNeighborCaches(); // ВАЖНО!
        }
    }

    // ========== NBT и синхронизация ==========
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
        cachedSource = null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
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