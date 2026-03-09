package com.cim.block.entity.rotation;

import com.cim.block.basic.rotation.MiningPortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.entity.ModBlockEntities;

public class MiningPortBlockEntity extends BlockEntity implements RotationalNode {

    private long speed = 0;
    private long torque = 0;

    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    private final ItemStackHandler inventory = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final LazyOptional<IItemHandler> inventoryOptional = LazyOptional.of(() -> inventory);

    public MiningPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MINING_PORT_BE.get(), pos, state);
    }
    @Nullable
    private BlockPos placerPos;

    // Геттер и сеттер для placerPos
    public void setPlacerPos(@Nullable BlockPos pos) {
        this.placerPos = pos;
        setChanged();
        sync();
    }

    @Nullable
    public BlockPos getPlacerPos() {
        return placerPos;
    }

    // Добавить метод для получения данных
    public ContainerData getDataAccess() {
        return data;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MiningPortBlockEntity be) {
        if (level.isClientSide) return;

        // Обновляем флаг наличия головки
        boolean hasDrill = false;
        if (be.placerPos != null) {
            BlockEntity placerBe = level.getBlockEntity(be.placerPos);
            if (placerBe instanceof ShaftPlacerBlockEntity placer) {
                hasDrill = placer.hasDrillHead();
            } else {
                // Разместитель исчез – сбрасываем связь
                be.placerPos = null;
                be.setChanged();
            }
        }
        be.data.set(0, hasDrill ? 1 : 0);
    }

    protected final ContainerData data = new SimpleContainerData(1);
    // Rotational
    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }
    @Override public void setSpeed(long speed) { this.speed = speed; setChanged(); sync(); }
    @Override public void setTorque(long torque) { this.torque = torque; setChanged(); sync(); }
    @Override public long getMaxSpeed() { return 0; }
    @Override public long getMaxTorque() { return 0; }

    // RotationalNode
    @Override @Nullable public RotationSource getCachedSource() { return cachedSource; }
    @Override public void setCachedSource(@Nullable RotationSource source, long gameTime) {
        this.cachedSource = source;
        this.cacheTimestamp = gameTime;
    }
    @Override public boolean isCacheValid(long currentTime) {
        return cachedSource != null && (currentTime - cacheTimestamp) <= CACHE_LIFETIME;
    }
    @Override public void invalidateCache() {
        if (this.cachedSource != null) {
            this.cachedSource = null;
            if (level != null && !level.isClientSide) {
                invalidateNeighborCaches();
            }
        }
    }
    private void invalidateNeighborCaches() {
        if (level == null || level.isClientSide) return;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getBlockEntity(neighborPos) instanceof RotationalNode node) {
                node.invalidateCache();
            }
        }
    }

    public ItemStack addItem(ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int i = 0; i < inventory.getSlots(); i++) {
            remainder = inventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) break;
        }
        if (remainder.getCount() != stack.getCount()) {
            setChanged();
            sync();
        }
        return remainder;
    }

    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        Direction facing = getBlockState().getValue(MiningPortBlock.FACING);
        if (fromDir == facing.getOpposite()) {
            return new Direction[]{facing};      // со стороны входа – на выход
        } else if (fromDir == facing) {
            return new Direction[]{facing.getOpposite()}; // со стороны выхода – на вход
        } else {
            return new Direction[0];              // с других сторон не передаём
        }
    }

    public IItemHandler getInventory() { return inventory; }

    // Capability
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryOptional.invalidate();
    }

    // NBT
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
    }
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    public void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}