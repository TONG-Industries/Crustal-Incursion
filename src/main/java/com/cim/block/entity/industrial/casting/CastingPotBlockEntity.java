package com.cim.block.entity.industrial.casting;

import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.block.entity.ModBlockEntities;
import com.cim.item.ModItems;
import com.cim.event.SlagItem;
import com.cim.event.HotItemHandler; // НОВЫЙ ИМПОРТ
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CastingPotBlockEntity extends BlockEntity {
    public static final int CAPACITY_MOLD_INGOT = MetalUnits2.UNITS_PER_INGOT; // 9 единиц

    private ItemStack mold = ItemStack.EMPTY;
    private ItemStack outputItem = ItemStack.EMPTY;

    private Metal currentMetal = null;
    private int storedUnits = 0;
    private int capacity = 0;
    private float coolingTimer = 0; // Теперь float для плавности!
    private int solidifyTimer = 0;
    private static final int SOLIDIFY_TIME = 100;
    public static final float BASE_COOLING_TIME = 200f; // Теперь float!

    // Поля для шлака
    private static final int SLAG_FORMATION_TIME = 40;
    private int metalIdleTime = 0;
    private boolean isSlagged = false;
    private CompoundTag slagData = null;
    private float slagCoolingTimer = 0; // Теперь float!
    private int transferCooldown = 0;

    public CastingPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_POT.get(), pos, state);
    }

    private float getCoolingTimeForMold() {
        return BASE_COOLING_TIME;
    }

    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        public ItemStack getStackInSlot(int slot) {
            return outputItem;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            outputItem = stack;
            setChanged();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (coolingTimer > 0) return ItemStack.EMPTY;
            if (storedUnits > 0 || solidifyTimer > 0) return ItemStack.EMPTY;

            ItemStack res = outputItem.copy().split(amount);
            if (!simulate) {
                outputItem.shrink(amount);
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return res;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    private void createOutputItem() {
        if (currentMetal == null || storedUnits < capacity) return;

        ItemStack result = ItemStack.EMPTY;
        if (currentMetal.hasIngot()) {
            result = new ItemStack(currentMetal.getIngot());
        }

        if (!result.isEmpty()) {
            float coolTime = getCoolingTimeForMold();
            // Устанавливаем горячесть через HotItemHandler
            HotItemHandler.setHot(result, (int) (coolTime / 20f)); // в секундах

            this.outputItem = result;
        }
        this.storedUnits -= capacity;
        if (this.storedUnits == 0) this.currentMetal = null;
        this.solidifyTimer = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean tryInsertHotItem(ItemStack stack) {
        if (!outputItem.isEmpty() || storedUnits > 0 || mold.isEmpty() || coolingTimer > 0) return false;

        // Используем HotItemHandler для проверки горячести
        if (HotItemHandler.isHot(stack)) {
            float hotTime = stack.getTag().getFloat("HotTime");
            if (hotTime <= 0) return false;

            this.outputItem = stack.copy();
            this.outputItem.setCount(1);
            this.coolingTimer = hotTime;

            // Убираем тег горячести (он теперь в котле)
            this.outputItem.removeTagKey("HotTime");
            this.outputItem.removeTagKey("HotTimeMax");

            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return true;
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingPotBlockEntity be) {
        if (be.transferCooldown > 0) be.transferCooldown--;

        // === Остывание готового предмета (плавное!) ===
        if (be.coolingTimer > 0 && !be.outputItem.isEmpty()) {
            // Плавное уменьшение: -0.1 за тик
            be.coolingTimer = Math.max(0, be.coolingTimer - 0.1f);

            // Обновляем NBT только раз в секунду для синхронизации
            if ((int) be.coolingTimer % 20 == 0 || be.coolingTimer <= 0) {
                int displayTime = (int) be.coolingTimer;
                be.outputItem.getOrCreateTag().putInt("HotTime", displayTime);
                be.outputItem.getOrCreateTag().putInt("HotTimeMax", (int) BASE_COOLING_TIME);
                be.setChanged();
            }

            if (be.coolingTimer <= 0.5f) { // Почти остыл
                be.coolingTimer = 0;
                be.outputItem.removeTagKey("HotTime");
                be.outputItem.removeTagKey("HotTimeMax");
                be.setChanged();
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 2.0f);
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }

        if (!be.outputItem.isEmpty()) return;

        // === Остывание шлака (плавное!) ===
        if (be.isSlagged && be.slagData != null) {
            if (be.slagCoolingTimer > 0) {
                be.slagCoolingTimer = Math.max(0, be.slagCoolingTimer - 0.1f);

                // Синхронизация раз в секунду
                if ((int) be.slagCoolingTimer % 20 == 0) {
                    be.slagData.putFloat("HotTime", be.slagCoolingTimer);
                }

                if (be.slagCoolingTimer <= 0.5f) {
                    be.slagCoolingTimer = 0;
                    be.slagData.remove("HotTime");
                    be.slagData.remove("HotTimeMax");
                    if (!level.isClientSide) {
                        ((ServerLevel) level).sendParticles(ParticleTypes.POOF,
                                pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                                8, 0.25, 0.1, 0.25, 0.03);
                    }
                    be.setChanged();
                }
            }
            return;
        }

        // === Логика образования шлака ===
        if (be.storedUnits > 0 && be.storedUnits < be.capacity && !be.isSlagged) {
            boolean transferred = false;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(dir);
                if (level.getBlockEntity(neighborPos) instanceof CastingPotBlockEntity neighborPot) {
                    if (neighborPot.canAcceptMetal(be.currentMetal) && neighborPot.getRemainingCapacity() > 0) {
                        int toTransfer = Math.min(10, be.storedUnits);
                        int accepted = neighborPot.addMetal(be.currentMetal, toTransfer);
                        if (accepted > 0) {
                            be.storedUnits -= accepted;
                            be.transferCooldown = 5;
                            if (be.storedUnits <= 0) be.currentMetal = null;
                            be.setChanged();
                            level.sendBlockUpdated(pos, state, state, 3);
                            transferred = true;
                            break;
                        }
                    }
                }
            }
            if (!transferred) {
                be.metalIdleTime++;
                if (be.metalIdleTime >= SLAG_FORMATION_TIME) {
                    be.formSlag();
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                    return;
                }
            } else {
                be.metalIdleTime = 0;
            }
        } else {
            be.metalIdleTime = 0;
        }

        if (be.isSlagged) return;

        // Далее существующая логика...
        if (be.mold.isEmpty()) {
            if (be.storedUnits > 0) be.clearMetal();
            return;
        }

        be.updateCapacity();

        if (be.storedUnits > 0 && be.transferCooldown <= 0) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(dir);
                if (level.getBlockEntity(neighborPos) instanceof CastingPotBlockEntity neighborPot) {
                    if (neighborPot.canAcceptMetal(be.currentMetal) && neighborPot.getRemainingCapacity() > 0) {
                        int toTransfer = Math.min(10, be.storedUnits);
                        int accepted = neighborPot.addMetal(be.currentMetal, toTransfer);
                        if (accepted > 0) {
                            be.storedUnits -= accepted;
                            be.transferCooldown = 5;
                            if (be.storedUnits <= 0) be.currentMetal = null;
                            be.setChanged();
                            level.sendBlockUpdated(pos, state, state, 3);
                            break;
                        }
                    }
                }
            }
        }

        if (be.storedUnits >= be.capacity && be.capacity > 0 && be.transferCooldown <= 0) {
            if (be.solidifyTimer < SOLIDIFY_TIME) {
                be.solidifyTimer++;
                be.setChanged();
                if (be.solidifyTimer % 10 == 0) {
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            } else {
                be.createOutputItem();
                be.coolingTimer = be.getCoolingTimeForMold();
                be.solidifyTimer = 0;
                if (!level.isClientSide) {
                    ((ServerLevel) level).sendParticles(ParticleTypes.POOF,
                            pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                            8, 0.25, 0.1, 0.25, 0.03);
                }
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.6f);
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        } else {
            if (be.solidifyTimer > 0) {
                be.solidifyTimer = 0;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    private void formSlag() {
        if (currentMetal == null || storedUnits <= 0) return;

        isSlagged = true;
        slagData = new CompoundTag();
        slagData.putString(SlagItem.TAG_METAL_ID, currentMetal.getId().toString());
        slagData.putInt(SlagItem.TAG_AMOUNT, storedUnits);
        slagData.putInt(SlagItem.TAG_MELTING_POINT, currentMetal.getMeltingPoint());
        slagData.putInt(SlagItem.TAG_COLOR, currentMetal.getColor());
        slagData.putFloat(SlagItem.TAG_HEAT_CONSUMPTION, currentMetal.getHeatConsumptionPerTick()); // НОВОЕ!
        slagData.putFloat("HotTime", SlagItem.BASE_COOLING_TIME);
        slagData.putInt("HotTimeMax", SlagItem.BASE_COOLING_TIME);
        slagCoolingTimer = SlagItem.BASE_COOLING_TIME;

        storedUnits = 0;
        currentMetal = null;
        metalIdleTime = 0;

        if (level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.5f);
            ((ServerLevel) level).sendParticles(ParticleTypes.ASH,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    10, 0.3, 0.1, 0.3, 0.02);
        }
    }

    public ItemStack extractSlag() {
        if (!isSlagged || slagData == null) return ItemStack.EMPTY;

        ItemStack slag = SlagItem.createSlagFromNBT(slagData);
        clearSlag();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return slag;
    }

    public boolean hasSlag() {
        return isSlagged;
    }

    private void clearSlag() {
        isSlagged = false;
        slagData = null;
        metalIdleTime = 0;
        slagCoolingTimer = 0;
    }

    private void tryTransferToNeighbor() {
        if (this.storedUnits <= 0 || this.currentMetal == null) return;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbor instanceof CastingPotBlockEntity neighborPot) {
                if (neighborPot.canAcceptMetal(this.currentMetal) && neighborPot.getRemainingCapacity() > 0) {
                    int toTransfer = Math.min(10, this.storedUnits);
                    int accepted = neighborPot.addMetal(this.currentMetal, toTransfer);
                    if (accepted > 0) {
                        this.storedUnits -= accepted;
                        this.transferCooldown = 2;
                        if (this.storedUnits <= 0) this.currentMetal = null;
                        this.setChanged();
                        return;
                    }
                }
            }
        }
    }

    public boolean isCompatibleWith(Metal metal) {
        if (storedUnits == 0) return true;
        return currentMetal != null && currentMetal.equals(metal);
    }

    public int extractMetal(int maxAmount) {
        if (storedUnits <= 0 || solidifyTimer > 0) return 0;
        int toExtract = Math.min(maxAmount, storedUnits);
        storedUnits -= toExtract;
        if (storedUnits <= 0) {
            currentMetal = null;
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return toExtract;
    }

    private final LazyOptional<IItemHandler> inventoryCap = LazyOptional.of(() -> itemHandler);

    public List<CastingPotBlockEntity> findNetwork() {
        List<CastingPotBlockEntity> network = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(worldPosition); visited.add(worldPosition);

        while (!queue.isEmpty() && network.size() < 7) {
            BlockPos curr = queue.poll();
            if (level.getBlockEntity(curr) instanceof CastingPotBlockEntity pot) {
                network.add(pot);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos next = curr.relative(d);
                    if (!visited.contains(next) && level.getBlockState(next).is(getBlockState().getBlock())) {
                        visited.add(next); queue.add(next);
                    }
                }
            }
        }
        return network;
    }

    public int fillNetwork(Metal metal, int amount) {
        List<CastingPotBlockEntity> network = findNetwork();
        List<CastingPotBlockEntity> availablePools = network.stream()
                .filter(p -> p.canAcceptMetal(metal))
                .toList();
        if (availablePools.isEmpty()) return 0;

        int totalSpace = availablePools.stream().mapToInt(CastingPotBlockEntity::getRemainingCapacity).sum();
        if (totalSpace <= 0) return 0;

        int toFillTotal = Math.min(amount, totalSpace);
        int actuallyFilled = 0;
        int count = availablePools.size();
        int perPool = toFillTotal / count;
        int remainder = toFillTotal % count;

        for (CastingPotBlockEntity pool : availablePools) {
            int fillAmount = perPool + (remainder-- > 0 ? 1 : 0);
            if (fillAmount > 0) {
                int accepted = pool.addMetal(metal, fillAmount);
                actuallyFilled += accepted;
            }
        }
        return actuallyFilled;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return inventoryCap.cast();
        return super.getCapability(cap, side);
    }

    private void updateCapacity() {
        if (mold.is(ModItems.MOLD_INGOT.get())) {
            this.capacity = CAPACITY_MOLD_INGOT;
        } else {
            this.capacity = 0;
        }
    }

    public void clearMetal() {
        this.storedUnits = 0;
        this.currentMetal = null;
        this.solidifyTimer = 0;
        this.metalIdleTime = 0;
        setChanged();
    }

    // === Геттеры ===
    public ItemStack getMold() { return mold; }
    public ItemStack getOutputItem() { return outputItem; }
    public Metal getCurrentMetal() { return currentMetal; }
    public int getStoredUnits() { return storedUnits; }
    public int getCapacity() { updateCapacity(); return capacity; }
    public int getSolidifyProgress() { return solidifyTimer; }
    public int getSolidifyTime() { return SOLIDIFY_TIME; }
    public float getCoolingTimer() { return coolingTimer; } // Теперь float!

    public float getFillLevel() {
        if (capacity <= 0) return 0;
        return (float) storedUnits / capacity;
    }

    public boolean canRemoveMold() {
        return storedUnits <= 0 && solidifyTimer <= 0 && outputItem.isEmpty() && coolingTimer <= 0;
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
        // Сохраняем горячесть если нужно
        if (coolingTimer > 0) {
            HotItemHandler.setHot(result, (int) (coolingTimer / 20f));
        }
        this.outputItem = ItemStack.EMPTY;
        this.coolingTimer = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return result;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Mold", mold.save(new CompoundTag()));
        tag.put("Output", outputItem.save(new CompoundTag()));
        tag.putInt("StoredUnits", storedUnits);
        tag.putInt("SolidifyTimer", solidifyTimer);
        tag.putFloat("CoolingTimer", coolingTimer); // Теперь float!
        tag.putInt("MetalIdleTime", metalIdleTime);
        tag.putBoolean("IsSlagged", isSlagged);
        tag.putFloat("SlagCoolingTimer", slagCoolingTimer); // Теперь float!

        if (isSlagged && slagData != null) {
            tag.put("SlagData", slagData);
        }

        if (currentMetal != null) {
            tag.putString("MetalId", currentMetal.getId().toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.mold = ItemStack.of(tag.getCompound("Mold"));
        this.outputItem = ItemStack.of(tag.getCompound("Output"));
        this.storedUnits = tag.getInt("StoredUnits");
        this.solidifyTimer = tag.getInt("SolidifyTimer");
        this.coolingTimer = tag.getFloat("CoolingTimer"); // Теперь float!
        this.metalIdleTime = tag.getInt("MetalIdleTime");
        this.isSlagged = tag.getBoolean("IsSlagged");
        this.slagCoolingTimer = tag.getFloat("SlagCoolingTimer"); // Теперь float!

        if (tag.contains("SlagData")) {
            this.slagData = tag.getCompound("SlagData");
        }

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
        tag.putInt("StoredUnits", storedUnits);
        tag.putInt("SolidifyTimer", solidifyTimer);
        tag.putFloat("CoolingTimer", coolingTimer);

        tag.putBoolean("IsSlagged", isSlagged);
        if (isSlagged && slagData != null) {
            tag.put("SlagData", slagData);
        }
        tag.putInt("MetalIdleTime", metalIdleTime);

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
            this.storedUnits = tag.getInt("StoredUnits");
            this.solidifyTimer = tag.getInt("SolidifyTimer");
            this.coolingTimer = tag.getFloat("CoolingTimer");

            this.isSlagged = tag.getBoolean("IsSlagged");
            if (tag.contains("SlagData")) {
                this.slagData = tag.getCompound("SlagData");
            } else {
                this.slagData = null;
            }
            this.metalIdleTime = tag.getInt("MetalIdleTime");

            if (tag.contains("MetalId")) {
                ResourceLocation id = new ResourceLocation(tag.getString("MetalId"));
                MetallurgyRegistry.get(id).ifPresent(m -> this.currentMetal = m);
            } else {
                this.currentMetal = null;
            }
            updateCapacity();
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        this.load(tag);
    }

    // === Методы для работы с металлом ===
    public boolean canAcceptMetal(Metal metal) {
        if (isSlagged) return false;
        if (mold.isEmpty()) return false;
        if (!outputItem.isEmpty()) return false;
        if (coolingTimer > 0) return false;
        updateCapacity();
        if (storedUnits >= capacity) return false;
        if (storedUnits > 0 && currentMetal != null && !currentMetal.equals(metal)) {
            return false;
        }
        return true;
    }

    public int getRemainingCapacity() {
        updateCapacity();
        if (coolingTimer > 0 || !outputItem.isEmpty()) return 0;
        return capacity - storedUnits;
    }

    public int addMetal(Metal metal, int amount) {
        if (isSlagged) return 0;
        if (this.coolingTimer > 0 || !this.outputItem.isEmpty()) {
            return 0;
        }
        if (this.storedUnits == 0) {
            this.currentMetal = metal;
        } else if (!this.currentMetal.equals(metal)) {
            return 0;
        }
        int toAdd = Math.min(amount, this.capacity - this.storedUnits);
        if (toAdd > 0) {
            this.storedUnits += toAdd;
            this.metalIdleTime = 0;
            if (this.isSlagged) {
                clearSlag();
            }
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return toAdd;
    }

    public float getCoolingProgress() {
        if (coolingTimer <= 0) return 0;
        return coolingTimer / BASE_COOLING_TIME;
    }

    public boolean isCooling() {
        return coolingTimer > 0;
    }

    public ItemStack getSlagStack() {
        if (isSlagged && slagData != null) {
            return SlagItem.createSlagFromNBT(slagData);
        }
        return ItemStack.EMPTY;
    }

    public int getSlagColor() {
        if (slagData != null && slagData.contains("Color")) {
            return slagData.getInt("Color");
        }
        return 0x888888;
    }

    public float getSlagHotTime() {
        if (!isSlagged || slagData == null) return 0;
        return slagData.getFloat("HotTime");
    }

    public float getSlagHotProgress() {
        if (!isSlagged || slagData == null) return 0;
        float hotTime = slagData.getFloat("HotTime");
        int maxTime = slagData.getInt("HotTimeMax");
        if (maxTime == 0) maxTime = SlagItem.BASE_COOLING_TIME;
        return hotTime / (float) maxTime;
    }

    public ItemStack getSlagStackForRender() {
        if (!isSlagged || slagData == null) return ItemStack.EMPTY;
        return SlagItem.createSlagFromNBT(slagData);
    }
}