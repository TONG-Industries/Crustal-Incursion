package com.cim.block.entity.industrial.casting;

import com.cim.api.metal.Metal;
import com.cim.api.metal.MetalUnits;
import com.cim.api.metal.MetallurgyRegistry;
import com.cim.block.entity.ModBlockEntities;
import com.cim.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CastingPotBlockEntity extends BlockEntity {
    public static final int CAPACITY_MOLD_INGOT = MetalUnits.MB_PER_INGOT; // 111 мб

    private ItemStack mold = ItemStack.EMPTY;
    private ItemStack outputItem = ItemStack.EMPTY;

    private Metal currentMetal = null;
    private int storedMb = 0;
    private int capacity = 0;

    private int solidifyTimer = 0;
    private static final int SOLIDIFY_TIME = 100; // 5 секунд

    private int transferCooldown = 0;

    public CastingPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_POT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingPotBlockEntity be) {
        if (be.transferCooldown > 0) be.transferCooldown--;

        if (!be.outputItem.isEmpty()) return;

        if (be.mold.isEmpty()) {
            if (be.storedMb > 0) {
                be.clearMetal();
            }
            return;
        }

        be.updateCapacity();

        if (be.storedMb >= be.capacity && be.capacity > 0 && be.solidifyTimer < SOLIDIFY_TIME) {
            be.solidifyTimer++;
            be.setChanged();

            if (be.solidifyTimer >= SOLIDIFY_TIME) {
                be.createOutputItem();
            }
        }

        if (be.transferCooldown == 0 && be.storedMb > 0 && be.solidifyTimer == 0) {
            be.tryTransferToNeighbor(level, pos);
        }
    }



    public boolean isCompatibleWith(Metal metal) {
        if (storedMb == 0) return true;
        return currentMetal != null && currentMetal.equals(metal);
    }



    public int extractMetal(int maxAmount) {
        if (storedMb <= 0 || solidifyTimer > 0) return 0;
        int toExtract = Math.min(maxAmount, storedMb);
        storedMb -= toExtract;
        if (storedMb <= 0) {
            currentMetal = null;
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return toExtract;
    }

    private void tryTransferToNeighbor(Level level, BlockPos pos) {
        if (storedMb <= 0 || currentMetal == null || solidifyTimer > 0) return;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor instanceof CastingPotBlockEntity neighborPot) {
                // Используем canAcceptMetal с проверкой металла!
                if (neighborPot.canAcceptMetal(currentMetal)) {
                    int space = neighborPot.getRemainingCapacity();
                    int toTransfer = Math.min(10, storedMb);
                    int accepted = Math.min(toTransfer, space);

                    if (accepted > 0) {
                        this.storedMb -= accepted;
                        neighborPot.addMetal(currentMetal, accepted);
                        this.transferCooldown = 5;

                        if (this.storedMb <= 0) {
                            this.currentMetal = null;
                        }
                        setChanged();
                        return; // Передали в одного соседа, выходим
                    }
                }
            }
        }
    }

    private void updateCapacity() {
        if (mold.is(ModItems.MOLD_INGOT.get())) {
            this.capacity = CAPACITY_MOLD_INGOT;
        } else {
            this.capacity = 0;
        }
    }

    private void createOutputItem() {
        if (currentMetal == null || storedMb < capacity) return;

        if (currentMetal.hasIngot()) {
            this.outputItem = new ItemStack(currentMetal.getIngot());
        }

        this.storedMb = 0;
        this.currentMetal = null;
        this.solidifyTimer = 0;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void clearMetal() {
        this.storedMb = 0;
        this.currentMetal = null;
        this.solidifyTimer = 0;
        setChanged();
    }

    public ItemStack getMold() { return mold; }
    public ItemStack getOutputItem() { return outputItem; }
    public Metal getCurrentMetal() { return currentMetal; }
    public int getStoredMb() { return storedMb; }
    public int getCapacity() { updateCapacity(); return capacity; }
    public int getSolidifyProgress() { return solidifyTimer; }
    public int getSolidifyTime() { return SOLIDIFY_TIME; }

    public float getFillLevel() {
        if (capacity <= 0) return 0;
        return (float) storedMb / capacity;
    }

    public boolean canRemoveMold() {
        return storedMb <= 0 && outputItem.isEmpty();
    }

    public void setMold(ItemStack stack) {
        this.mold = stack.copy();
        updateCapacity();
        if (mold.isEmpty()) {
            clearMetal();
        }
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ItemStack takeOutput() {
        ItemStack result = outputItem.copy();
        this.outputItem = ItemStack.EMPTY;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return result;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Mold", mold.save(new CompoundTag()));
        tag.put("Output", outputItem.save(new CompoundTag()));
        tag.putInt("StoredMb", storedMb);
        tag.putInt("SolidifyTimer", solidifyTimer);
        if (currentMetal != null) {
            tag.putString("MetalId", currentMetal.getId().toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.mold = ItemStack.of(tag.getCompound("Mold"));
        this.outputItem = ItemStack.of(tag.getCompound("Output"));
        this.storedMb = tag.getInt("StoredMb");
        this.solidifyTimer = tag.getInt("SolidifyTimer");
        if (tag.contains("MetalId")) {
            ResourceLocation id = new ResourceLocation(tag.getString("MetalId"));
            MetallurgyRegistry.get(id).ifPresent(m -> this.currentMetal = m);
        }
        updateCapacity();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Mold", mold.save(new CompoundTag()));
        tag.put("Output", outputItem.save(new CompoundTag()));
        tag.putInt("StoredMb", storedMb);
        tag.putInt("SolidifyTimer", solidifyTimer);
        if (currentMetal != null) {
            tag.putString("MetalId", currentMetal.getId().toString());
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            CompoundTag tag = pkt.getTag();
            this.mold = ItemStack.of(tag.getCompound("Mold"));
            this.outputItem = ItemStack.of(tag.getCompound("Output"));
            this.storedMb = tag.getInt("StoredMb");
            this.solidifyTimer = tag.getInt("SolidifyTimer");
            if (tag.contains("MetalId")) {
                ResourceLocation id = new ResourceLocation(tag.getString("MetalId"));
                MetallurgyRegistry.get(id).ifPresent(m -> this.currentMetal = m);
            }
            updateCapacity();
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        this.load(tag);
    }


    public boolean canAcceptMetal(Metal metal) {
        if (mold.isEmpty()) return false;
        if (!outputItem.isEmpty()) return false;
        updateCapacity();
        if (storedMb >= capacity) return false;

        // Критически важно: проверяем совместимость если уже есть металл
        if (storedMb > 0 && currentMetal != null && !currentMetal.equals(metal)) {
            return false;
        }
        return true;
    }

    // Новый метод для проверки сколько ещё можно налить
    public int getRemainingCapacity() {
        updateCapacity();
        return capacity - storedMb;
    }

    // Измени метод addMetal чтобы возвращал сколько реально добавлено
    public int addMetal(Metal metal, int amount) {
        if (storedMb == 0) {
            this.currentMetal = metal;
        } else if (!this.currentMetal.equals(metal)) {
            return 0; // Не совместимо - не добавляем ничего
        }

        int toAdd = Math.min(amount, capacity - storedMb);
        if (toAdd > 0) {
            this.storedMb += toAdd;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return toAdd; // Возвращаем сколько реально добавили
    }
}