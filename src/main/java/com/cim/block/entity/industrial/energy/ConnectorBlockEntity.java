package com.cim.block.entity.industrial.energy;

import com.cim.api.energy.ConnectorTier;
import com.cim.api.energy.IEnergyConnector;
import com.cim.block.basic.industrial.energy.ConnectorBlock;
import com.cim.block.entity.ModBlockEntities;
import com.cim.capability.ModCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ConnectorBlockEntity extends BlockEntity implements IEnergyConnector {

    private final LazyOptional<IEnergyConnector> connectorCap = LazyOptional.of(() -> this);
    private final Set<BlockPos> connections = new HashSet<>();

    public ConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONNECTOR_BE.get(), pos, state);
    }

    public ConnectorTier getTier() {
        if (getBlockState().getBlock() instanceof ConnectorBlock cb) return cb.tier;
        // Резервный вариант, если что-то пошло не так
        return new ConnectorTier(16, 1, 0.03125f, 4, 6);
    }

    // ========== МНОЖЕСТВЕННЫЕ СОЕДИНЕНИЯ ==========

    public Set<BlockPos> getConnections() {
        return Collections.unmodifiableSet(connections);
    }

    public boolean canConnect(BlockPos other) {
        if (connections.contains(other)) return false; // Уже подключен
        return connections.size() < getTier().maxConnections();
    }

    public void connectTo(BlockPos other) {
        if (!canConnect(other)) return;
        this.connections.add(other);
        setChanged();
        syncToClient();

        if (level != null && !level.isClientSide) {
            com.cim.api.energy.EnergyNetworkManager manager = com.cim.api.energy.EnergyNetworkManager.get((net.minecraft.server.level.ServerLevel) level);
            manager.removeNode(worldPosition);
            manager.addNode(worldPosition);
        }
    }

    public void disconnect(BlockPos other) {
        if (this.connections.remove(other)) {
            setChanged();
            syncToClient();

            if (level != null && !level.isClientSide) {
                com.cim.api.energy.EnergyNetworkManager manager = com.cim.api.energy.EnergyNetworkManager.get((net.minecraft.server.level.ServerLevel) level);
                manager.removeNode(worldPosition);
                manager.addNode(worldPosition);

                manager.removeNode(other);
                manager.addNode(other);
            }
        }
    }

    public void onRemoved() {
        if (level != null && !level.isClientSide) {
            // Копируем сет, чтобы избежать ConcurrentModificationException
            Set<BlockPos> copies = new HashSet<>(connections);
            for (BlockPos otherPos : copies) {
                BlockEntity otherBe = level.getBlockEntity(otherPos);
                if (otherBe instanceof ConnectorBlockEntity otherConn) {
                    otherConn.disconnect(this.worldPosition); // Отключаем тот конец от нас
                }
            }
            connections.clear();
        }
    }

    // ========== ТОЧКА КРЕПЛЕНИЯ ПРОВОДА ==========

    public Vec3 getWireAttachmentPoint() {
        Direction facing = getBlockState().getValue(ConnectorBlock.FACING);
        double heightOffset = getTier().height() / 16.0;
        double offsetFromCenter = 0.5 - heightOffset;

        // Добавляем смещение 0.01 по направлению фейса (наружу от блока)
        double outwardOffset = 0.01;

        double lx = 0.5 - offsetFromCenter * facing.getStepX() + outwardOffset * facing.getStepX();
        double ly = 0.5 - offsetFromCenter * facing.getStepY() + outwardOffset * facing.getStepY();
        double lz = 0.5 - offsetFromCenter * facing.getStepZ() + outwardOffset * facing.getStepZ();

        return new Vec3(
                worldPosition.getX() + lx,
                worldPosition.getY() + ly,
                worldPosition.getZ() + lz
        );
    }

    // ========== IEnergyConnector ==========

    @Override
    public boolean canConnectEnergy(Direction side) {
        Direction facing = getBlockState().getValue(ConnectorBlock.FACING);
        return side == facing.getOpposite();
    }

    // ========== Capabilities ==========

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ModCapabilities.ENERGY_CONNECTOR) {
            return connectorCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        connectorCap.invalidate();
    }

    // ========== NBT & RenderBox ==========

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag list = new ListTag();
        for (BlockPos pos : connections) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        tag.put("Connections", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        connections.clear();
        if (tag.contains("Connections", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Connections", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                connections.add(NbtUtils.readBlockPos(list.getCompound(i)));
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (!connections.isEmpty()) {
            AABB box = new AABB(worldPosition);
            for (BlockPos pos : connections) {
                box = box.minmax(new AABB(pos));
            }
            return box.inflate(1.0);
        }
        return super.getRenderBoundingBox();
    }

    // ========== Синхронизация с клиентом ==========

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ========== Интеграция с сетью ==========

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            com.cim.api.energy.EnergyNetworkManager.get(
                    (net.minecraft.server.level.ServerLevel) level).addNode(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            com.cim.api.energy.EnergyNetworkManager.get(
                    (net.minecraft.server.level.ServerLevel) level).removeNode(worldPosition);
        }
    }
}