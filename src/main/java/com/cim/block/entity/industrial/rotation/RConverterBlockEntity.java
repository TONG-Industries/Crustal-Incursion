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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.cim.api.energy.IEnergyConnector;
import com.cim.api.energy.IEnergyProvider;
import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.basic.industrial.rotation.RConverterBlock;
import com.cim.block.entity.ModBlockEntities;
import com.cim.capability.ModCapabilities;

public class RConverterBlockEntity extends BlockEntity implements RotationalNode, IEnergyProvider, IEnergyConnector {

    // ========== Константы ==========
    private static final long MAX_ENERGY = 100000;        // максимальный запас энергии
    private static final long MAX_EXTRACT = 500;         // макс. выдача за тик
    private static final double EFFICIENCY = 0.4;          // КПД
    private static final long CACHE_LIFETIME = 10;         // время жизни кеша источника

    // ========== Поля состояния ==========
    private long speed = 0;
    private long torque = 0;
    private long energyStored = 0;
    private long currentEnergyPerTick = 0;                 // для отображения в гуи/чате

    // ========== Кеширование источника вращения ==========
    @Nullable
    private RotationSource cachedSource;
    private long cacheTimestamp;

    // ========== Capabilities ==========
    private final LazyOptional<IEnergyProvider> hbmProviderOpt = LazyOptional.of(() -> this);
    private final LazyOptional<IEnergyConnector> hbmConnectorOpt = LazyOptional.of(() -> this);

    // ========== Конструктор ==========
    public RConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.R_CONVERTER_BE.get(), pos, state);
    }

    // ========== Rotational ==========
    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }
    @Override public void setSpeed(long speed) { this.speed = speed; setChanged(); }
    @Override public void setTorque(long torque) { this.torque = torque; setChanged(); }
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
        if (cachedSource != null || speed != 0 || torque != 0) {
            this.cachedSource = null;
            if (level != null && !level.isClientSide) {
                invalidateNeighborCaches();
            }
        }
    }
    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        Direction facing = getBlockState().getValue(RConverterBlock.FACING);
        Direction inputSide = facing.getOpposite();
        if (fromDir == null) {
            return new Direction[]{inputSide};
        } else if (fromDir == inputSide) {
            return new Direction[0];
        } else {
            return new Direction[0];
        }
    }
    @Override
    public boolean canProvideSource(@Nullable Direction fromDir) { return false; }

    private void invalidateNeighborCaches() {
        if (level == null || level.isClientSide) return;
        Direction facing = getBlockState().getValue(RConverterBlock.FACING);
        BlockPos back = worldPosition.relative(facing.getOpposite());
        if (level.getBlockEntity(back) instanceof RotationalNode node) {
            node.invalidateCache();
        }
    }

    // ========== IEnergyProvider ==========
    @Override public long getEnergyStored() { return energyStored; }
    @Override public long getMaxEnergyStored() { return MAX_ENERGY; }
    @Override public void setEnergyStored(long energy) {
        this.energyStored = Math.max(0, Math.min(energy, MAX_ENERGY));
        setChanged();
    }
    @Override public long getProvideSpeed() { return MAX_EXTRACT; }
    @Override public boolean canExtract() {
        Direction facing = getBlockState().getValue(RConverterBlock.FACING);
        // Можем отдавать только через лицевую сторону
        return energyStored > 0 && facing != null;
    }
    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        if (!canExtract()) return 0;
        long toExtract = Math.min(energyStored, Math.min(MAX_EXTRACT, maxExtract));
        if (!simulate && toExtract > 0) {
            energyStored -= toExtract;
            setChanged();
        }
        return toExtract;
    }

    // ========== IEnergyConnector ==========
    @Override
    public boolean canConnectEnergy(Direction side) {
        // Разрешаем подключение проводов только к лицевой стороне (выход энергии)
        return side == getBlockState().getValue(RConverterBlock.FACING);
    }

    // ========== Capabilities ==========
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ModCapabilities.ENERGY_PROVIDER || cap == ModCapabilities.ENERGY_CONNECTOR) {
            if (side == null || canConnectEnergy(side)) {
                if (cap == ModCapabilities.ENERGY_PROVIDER) {
                    return hbmProviderOpt.cast();
                } else {
                    return hbmConnectorOpt.cast();
                }
            } else {
                return LazyOptional.empty();
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        hbmProviderOpt.invalidate();
        hbmConnectorOpt.invalidate();
    }

    // ========== Логика тика (сервер) ==========
    public static void tick(Level level, BlockPos pos, BlockState state, RConverterBlockEntity be) {
        if (level.isClientSide) return;

        long currentTime = level.getGameTime();

        // Обновляем источник вращения, если кеш устарел
        if (!be.isCacheValid(currentTime)) {
            RotationSource source = RotationNetworkHelper.findSource(be, null);
            be.setCachedSource(source, currentTime);
        }

        RotationSource src = be.getCachedSource();
        long newSpeed = (src != null) ? src.speed() : 0;
        long newTorque = (src != null) ? src.torque() : 0;

        boolean changed = false;
        if (be.speed != newSpeed) {
            be.speed = newSpeed;
            changed = true;
        }
        if (be.torque != newTorque) {
            be.torque = newTorque;
            changed = true;
        }

        // Генерация энергии
        long rot = Math.abs(newSpeed * newTorque);
        long energyToGenerate = (long) ((rot / 20.0) * EFFICIENCY);
        be.currentEnergyPerTick = energyToGenerate;

        if (energyToGenerate > 0) {
            be.energyStored = Math.min(MAX_ENERGY, be.energyStored + energyToGenerate);
            changed = true;
        }

        if (changed) {
            be.setChanged();
            be.sync();
            be.invalidateNeighborCaches(); // уведомляем соседа сзади об изменении вращения
        }

        // ВНИМАНИЕ: Не толкаем энергию вручную! Этим занимается EnergyNetwork.
    }

    // ========== Синхронизация с клиентом ==========
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        tag.putLong("Energy", energyStored);
        tag.putLong("CurrentEnergyPerTick", currentEnergyPerTick);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
        energyStored = tag.getLong("Energy");
        currentEnergyPerTick = tag.getLong("CurrentEnergyPerTick");
        cachedSource = null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        tag.putLong("CurrentEnergyPerTick", currentEnergyPerTick);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
        currentEnergyPerTick = tag.getLong("CurrentEnergyPerTick");
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

    // ========== Геттер для использования в блоке ==========
    public long getCurrentEnergyPerTick() {
        return currentEnergyPerTick;
    }
}