package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class GearPortBlockEntity extends BlockEntity implements RotationalNode {

    private long speed = 0;
    private long torque = 0;

    private Direction firstPort = null;
    private Direction secondPort = null;

    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    public GearPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GEAR_PORT_BE.get(), pos, state);
    }
    // GearPortBlockEntity.java (добавить в конец класса)
    public void setupPorts(Direction incoming, Direction outgoing) {
        this.firstPort = incoming;
        this.secondPort = outgoing;
        setChanged();
        sync();
        invalidateCache();
    }

    public void attachChain(Level level) {
        if (level.isClientSide) return;
        BlockPos start = worldPosition.above();
        for (int i = 0; i < 20; i++) {
            BlockPos current = start.above(i);
            BlockState state = level.getBlockState(current);
            if (!state.isAir()) {
                break; // встретили блок — прекращаем
            }
            level.setBlock(current, Blocks.CHAIN.defaultBlockState(), 3);
        }
    }

    /**
     * Логика отвертки:
     * ПКМ -> Установить/снять ПЕРВЫЙ порт
     * Shift + ПКМ -> Установить/снять ВТОРОЙ порт
     */
    public String handleScrewdriverClick(Direction face, boolean shift) {
        String msg;
        if (!shift) {
            // Обычный клик - работаем с первым портом
            if (firstPort == face) {
                firstPort = null;
                msg = "Первый порт убран";
            } else {
                if (secondPort == face) secondPort = null; // Нельзя два порта на одну сторону
                firstPort = face;
                msg = "Первый порт установлен на " + face.getName();
            }
        } else {
            // Shift клик - работаем со вторым портом
            if (secondPort == face) {
                secondPort = null;
                msg = "Второй порт убран";
            } else {
                if (firstPort == face) firstPort = null; // Нельзя два порта на одну сторону
                secondPort = face;
                msg = "Второй порт установлен на " + face.getName();
            }
        }

        invalidateCache();
        setChanged();
        sync();
        return msg;
    }

    // ========== Rotational ==========
    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }
    @Override public void setSpeed(long speed) { this.speed = speed; setChanged(); sync(); }
    @Override public void setTorque(long torque) { this.torque = torque; setChanged(); sync(); }
    @Override public long getMaxSpeed() { return 0; }
    @Override public long getMaxTorque() { return 0; }

    // ========== RotationalNode ==========
    @Override @Nullable public RotationSource getCachedSource() { return cachedSource; }
    @Override public void setCachedSource(@Nullable RotationSource source, long gameTime) {
        this.cachedSource = source;
        this.cacheTimestamp = gameTime;
    }
    @Override public boolean isCacheValid(long currentTime) {
        return cachedSource != null && (currentTime - cacheTimestamp) <= CACHE_LIFETIME;
    }

    @Override
    public void invalidateCache() {
        if (this.cachedSource != null || speed != 0) {
            this.cachedSource = null;
            this.speed = 0;
            this.torque = 0;
            if (level != null && !level.isClientSide) {
                invalidateNeighborCaches();
                setChanged();
                sync();
            }
        }
    }

    private void invalidateNeighborCaches() {
        if (level == null) return;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getBlockEntity(neighborPos) instanceof RotationalNode node) {
                node.invalidateCache();
            }
        }
    }

    @Override
    public boolean canProvideSource(@Nullable Direction fromDir) {
        // Мы отдаем энергию только в порты
        return fromDir != null && (fromDir == firstPort || fromDir == secondPort);
    }

    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        List<Direction> dirs = new ArrayList<>();
        if (fromDir == null) {
            if (firstPort != null) dirs.add(firstPort);
            if (secondPort != null) dirs.add(secondPort);
        } else {
            // Если пришли в один порт, выходим из другого
            if (fromDir == firstPort && secondPort != null) dirs.add(secondPort);
            else if (fromDir == secondPort && firstPort != null) dirs.add(firstPort);
        }
        return dirs.toArray(new Direction[0]);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GearPortBlockEntity be) {
        if (level.isClientSide) return;

        // Если портов нет - ничего не делаем
        if (be.firstPort == null && be.secondPort == null) return;

        long currentTime = level.getGameTime();
        if (!be.isCacheValid(currentTime)) {
            RotationSource s1 = null;
            RotationSource s2 = null;

            // БЕЗОПАСНЫЙ ПОИСК ДЛЯ ПЕРВОГО ПОРТА
            if (be.firstPort != null) {
                BlockEntity neighbor1 = level.getBlockEntity(pos.relative(be.firstPort));
                if (neighbor1 != null) { // <--- ВОТ ЭТА ПРОВЕРКА СПАСЕТ ОТ КРАША
                    s1 = RotationNetworkHelper.findSource(neighbor1, be.firstPort.getOpposite());
                }
            }

            // БЕЗОПАСНЫЙ ПОИСК ДЛЯ ВТОРОГО ПОРТА
            if (be.secondPort != null) {
                BlockEntity neighbor2 = level.getBlockEntity(pos.relative(be.secondPort));
                if (neighbor2 != null) { // <--- И ЗДЕСЬ ТОЖЕ
                    s2 = RotationNetworkHelper.findSource(neighbor2, be.secondPort.getOpposite());
                }
            }

            // Логика выбора источника (та же, что была)
            RotationSource finalSource = (s1 != null) ? s1 : s2;
            if (s1 != null && s2 != null) finalSource = new RotationSource(0, 0);

            be.setCachedSource(finalSource, currentTime);

            long newSpeed = (finalSource != null) ? finalSource.speed() : 0;
            long newTorque = (finalSource != null) ? finalSource.torque() : 0;

            if (be.speed != newSpeed || be.torque != newTorque) {
                be.speed = newSpeed;
                be.torque = newTorque;
                be.setChanged();
                be.sync();
                be.invalidateNeighborCaches();
            }
        }
    }


    // ========== NBT & Sync ==========
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        if (firstPort != null) tag.putInt("FP", firstPort.ordinal());
        if (secondPort != null) tag.putInt("SP", secondPort.ordinal());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.speed = tag.getLong("Speed");
        this.torque = tag.getLong("Torque");
        this.firstPort = tag.contains("FP") ? Direction.values()[tag.getInt("FP")] : null;
        this.secondPort = tag.contains("SP") ? Direction.values()[tag.getInt("SP")] : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasPortOnSide(Direction side) {
        // side — это сторона, по которой кликнул игрок
        return side == firstPort || side == secondPort;
    }


}