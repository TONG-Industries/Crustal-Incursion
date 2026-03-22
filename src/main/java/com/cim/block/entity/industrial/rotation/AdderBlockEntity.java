package com.cim.block.entity.industrial.rotation;

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
import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.basic.industrial.rotation.AdderBlock;
import com.cim.block.entity.ModBlockEntities;

public class AdderBlockEntity extends BlockEntity implements RotationalNode {

    private long speed = 0;
    private long torque = 0;

    // Кеширование для собственного источника (выход)
    @Nullable
    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    public AdderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADDER_BE.get(), pos, state);
    }

    // ========== Rotational ==========
    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }
    @Override public void setSpeed(long speed) { this.speed = speed; setChanged(); sync(); invalidateNeighborCaches(); }
    @Override public void setTorque(long torque) { this.torque = torque; setChanged(); sync(); invalidateNeighborCaches(); }
    @Override public long getMaxSpeed() { return 0; }
    @Override public long getMaxTorque() { return 0; }

    // ========== RotationalNode ==========
    @Override @Nullable
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
        if (cachedSource != null) {
            cachedSource = null;
            if (level != null && !level.isClientSide) {
                invalidateNeighborCaches();
            }
        }
    }

    private void invalidateNeighborCaches() {
        if (level == null || level.isClientSide) return;
        Direction facing = getBlockState().getValue(AdderBlock.FACING);
        Direction outputSide = facing.getOpposite();
        BlockPos neighborPos = worldPosition.relative(outputSide);
        if (level.getBlockEntity(neighborPos) instanceof RotationalNode node) {
            node.invalidateCache();
        }
    }

    @Override
    public boolean canProvideSource(@Nullable Direction fromDir) {
        if (fromDir == null) return false;
        BlockState state = getBlockState();
        Direction facing = state.getValue(AdderBlock.FACING);
        Direction outputSide = facing.getOpposite();
        // Сумматор отдаёт вращение только через выходную сторону
        return fromDir == outputSide;
    }

    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        // Сумматор не пропускает вращение транзитом
        return new Direction[0];
    }

    // ========== Тик ==========
    public static void tick(Level level, BlockPos pos, BlockState state, AdderBlockEntity be) {
        if (level.isClientSide) return;

        Direction facing = state.getValue(AdderBlock.FACING);
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();
        Direction outputSide = facing.getOpposite();

        long totalSpeed = 0;
        long totalTorque = 0;
        boolean anyInput = false;

        for (Direction dir : new Direction[]{left, right}) {
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) continue;

            RotationSource src = RotationNetworkHelper.findSource(neighbor, dir.getOpposite());
            if (src != null && src.speed() > 0) {
                totalSpeed += src.speed();
                totalTorque += src.torque(); // теперь суммируется крутящий момент
                anyInput = true;
            }
        }

        if (!anyInput) {
            totalSpeed = 0;
            totalTorque = 0;
        }

        // Обновляем состояние, если изменилось
        boolean changed = false;
        if (be.speed != totalSpeed) {
            be.speed = totalSpeed;
            changed = true;
        }
        if (be.torque != totalTorque) {
            be.torque = totalTorque;
            changed = true;
        }

        if (changed) {
            // Обновляем кеш для выходной стороны
            be.setCachedSource(new RotationSource(be.speed, be.torque), level.getGameTime());
            be.setChanged();
            be.sync();
            // Инвалидируем кеш у соседа на выходе, чтобы он пересчитал источник
            be.invalidateNeighborCaches();
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
        cachedSource = null; // сброс кеша при загрузке
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
}