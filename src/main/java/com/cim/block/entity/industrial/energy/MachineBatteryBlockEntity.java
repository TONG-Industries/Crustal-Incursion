package com.cim.block.entity.industrial.energy;


import com.cim.api.energy.*;
import com.cim.item.energy.EnergyCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
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
import com.cim.block.entity.ModBlockEntities;
import com.cim.capability.ModCapabilities;
import com.cim.menu.MachineBatteryMenu;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * Энергохранилище-каркас с настраиваемыми режимами работы и слотами для энергоячеек.
 * Базовые параметры каркаса = 0. Все характеристики зависят от вставленных ячеек.
 *
 * 16 слотов для батареек:
 *   Слоты 0-3: CHARGE INPUT (незаряженные предметы кладут сюда)
 *   Слоты 4-7: CHARGE OUTPUT (заряженные перемещаются сюда)
 *   Слоты 8-11: DISCHARGE INPUT (заряженные предметы для разрядки)
 *   Слоты 12-15: DISCHARGE OUTPUT (разряженные перемещаются сюда)
 *
 * Режимы: 0 = BOTH, 1 = INPUT, 2 = OUTPUT, 3 = DISABLED
 */
public class MachineBatteryBlockEntity extends BlockEntity implements MenuProvider, IEnergyProvider, IEnergyReceiver, GeoBlockEntity
{

    // ====================== КАРКАС: базовые параметры = 0 ======================
    private long capacity = 0;
    private long chargingSpeed = 0;
    private long unchargingSpeed = 0;
    private long energy = 0;
    private long lastEnergy = 0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Один режим работы (без редстоун-сигнала)
    // 0 = BOTH, 1 = INPUT, 2 = OUTPUT, 3 = DISABLED
    public int mode = 0;
    private Priority priority = Priority.LOW;
    private long energyDelta = 0;

    // ====================== СЛОТЫ ДЛЯ ЭНЕРГОЯЧЕЕК (4 штуки, 2x2) ======================
    public static final int CELL_SLOT_COUNT = 4;
    private final ItemStack[] cellSlots = new ItemStack[CELL_SLOT_COUNT];
    private final boolean[] cellEmpty = new boolean[CELL_SLOT_COUNT];

    // ====================== СЛОТЫ ДЛЯ БАТАРЕЕК (16 слотов) ======================
    // 0-3: charge input, 4-7: charge output, 8-11: discharge input, 12-15: discharge output
    public static final int TOTAL_ITEM_SLOTS = 16;
    public static final int CHARGE_INPUT_START = 0;
    public static final int CHARGE_INPUT_END = 4;
    public static final int CHARGE_OUTPUT_START = 4;
    public static final int CHARGE_OUTPUT_END = 8;
    public static final int DISCHARGE_INPUT_START = 8;
    public static final int DISCHARGE_INPUT_END = 12;
    public static final int DISCHARGE_OUTPUT_START = 12;
    public static final int DISCHARGE_OUTPUT_END = 16;

    private final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_ITEM_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            // Только предметы с энергией допускаются в input-слоты
            // Output-слоты не принимают предметы напрямую от игрока
            if (slot >= CHARGE_INPUT_START && slot < CHARGE_INPUT_END) {
                return stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
            }
            if (slot >= DISCHARGE_INPUT_START && slot < DISCHARGE_INPUT_END) {
                return stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
            }
            // Output-слоты: предметы попадают только программно
            return false;
        }
    };

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    private LazyOptional<IEnergyProvider> hbmProvider = LazyOptional.empty();
    private LazyOptional<IEnergyReceiver> hbmReceiver = LazyOptional.empty();
    private LazyOptional<IEnergyConnector> hbmConnector = LazyOptional.empty();

    private PackedEnergyCapabilityProvider feCapabilityProvider;

    protected final ContainerData data;

    public MachineBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_BATTERY_BE.get(), pos, state);

        this.capacity = 0;
        this.chargingSpeed = 0;
        this.unchargingSpeed = 0;

        for (int i = 0; i < CELL_SLOT_COUNT; i++) {
            cellSlots[i] = ItemStack.EMPTY;
            cellEmpty[i] = true;
        }

        this.feCapabilityProvider = new PackedEnergyCapabilityProvider(this);

        // ContainerData: 2 поля (mode, priority)
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> mode;
                    case 1 -> priority.ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> mode = value;
                    case 1 -> priority = Priority.values()[Math.max(0, Math.min(value, Priority.values().length - 1))];
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    // ====================== МЕТОДЫ ЭНЕРГОЯЧЕЕК ======================

    public boolean insertCell(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CELL_SLOT_COUNT) return false;
        if (!cellEmpty[slot]) return false;
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof EnergyCellItem cell)) return false;
        if (!cell.isValidCell(stack)) return false;

        ItemStack cellStack = stack.split(1);

        long cellEnergy = EnergyCellItem.getStoredEnergy(cellStack);
        if (cellEnergy > 0) {
            this.energy += cellEnergy;
            EnergyCellItem.setStoredEnergy(cellStack, 0);
        }

        cellSlots[slot] = cellStack;
        cellEmpty[slot] = false;

        recalculateCellStats();

        if (energy > capacity) {
            energy = capacity;
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public ItemStack extractCell(int slot) {
        if (slot < 0 || slot >= CELL_SLOT_COUNT) return ItemStack.EMPTY;
        if (cellEmpty[slot]) return ItemStack.EMPTY;

        ItemStack extracted = cellSlots[slot].copy();

        long cellMax = EnergyCellItem.getMaxEnergy(extracted);
        long cellCurrent = EnergyCellItem.getStoredEnergy(extracted);
        long cellSpace = cellMax - cellCurrent;
        long available = this.energy;
        long toAbsorb = Math.min(cellSpace, available);

        if (toAbsorb > 0) {
            EnergyCellItem.setStoredEnergy(extracted, cellCurrent + toAbsorb);
            this.energy -= toAbsorb;
        }

        cellSlots[slot] = ItemStack.EMPTY;
        cellEmpty[slot] = true;

        recalculateCellStats();

        if (energy > capacity) {
            energy = capacity;
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return extracted;
    }

    private void recalculateCellStats() {
        long totalCapacity = 0;
        long totalChargingSpeed = 0;
        long totalUnchargingSpeed = 0;
        int filledCount = 0;

        for (int i = 0; i < CELL_SLOT_COUNT; i++) {
            if (!cellEmpty[i] && cellSlots[i].getItem() instanceof EnergyCellItem cell) {
                totalCapacity += cell.getCellCapacity(cellSlots[i]);
                totalChargingSpeed += cell.getCellChargingSpeed(cellSlots[i]);
                totalUnchargingSpeed += cell.getCellUnchargingSpeed(cellSlots[i]);
                filledCount++;
            }
        }

        this.capacity = totalCapacity;
        this.chargingSpeed = totalChargingSpeed;
        this.unchargingSpeed = totalUnchargingSpeed;
    }

    public boolean isCellEmpty(int slot) {
        if (slot < 0 || slot >= CELL_SLOT_COUNT) return true;
        return cellEmpty[slot];
    }

    public ItemStack getCellStack(int slot) {
        if (slot < 0 || slot >= CELL_SLOT_COUNT) return ItemStack.EMPTY;
        return cellSlots[slot];
    }

    public int getFilledCellCount() {
        int count = 0;
        for (int i = 0; i < CELL_SLOT_COUNT; i++) {
            if (!cellEmpty[i]) count++;
        }
        return count;
    }

    // ====================== ЗАГРУЗКА / СОХРАНЕНИЕ ======================

    @Override
    public void onLoad() {
        super.onLoad();
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
        hbmProvider = LazyOptional.of(() -> this);
        hbmReceiver = LazyOptional.of(() -> this);
        hbmConnector = LazyOptional.of(() -> this);

        if (level != null && !level.isClientSide) {
            com.cim.api.energy.EnergyNetworkManager.get(
                    (net.minecraft.server.level.ServerLevel) level).addNode(worldPosition);
        }

        recalculateCellStats();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (hbmConnector.isPresent()) hbmConnector.invalidate();
        if (lazyItemHandler.isPresent()) lazyItemHandler.invalidate();
        if (hbmProvider.isPresent()) hbmProvider.invalidate();
        if (hbmReceiver.isPresent()) hbmReceiver.invalidate();
        if (feCapabilityProvider != null) feCapabilityProvider.invalidate();
    }

    // ====================== TICK ======================

    public static void tick(Level level, BlockPos pos, BlockState state, MachineBatteryBlockEntity be) {
        if (level.isClientSide) return;

        EnergyNetworkManager manager = EnergyNetworkManager.get((ServerLevel) level);
        if (!manager.hasNode(pos)) {
            manager.addNode(pos);
        }

        long gameTime = level.getGameTime();

        if (gameTime % 10 == 0) {
            be.energyDelta = (be.energy - be.lastEnergy) / 10;
            be.lastEnergy = be.energy;
        }

        be.validateCellFlags();

        be.chargeItems();
        be.dischargeItems();
    }

    private void validateCellFlags() {
        boolean needRecalc = false;
        for (int i = 0; i < CELL_SLOT_COUNT; i++) {
            boolean shouldBeEmpty = cellSlots[i].isEmpty() || !(cellSlots[i].getItem() instanceof EnergyCellItem);
            if (cellEmpty[i] != shouldBeEmpty) {
                cellEmpty[i] = shouldBeEmpty;
                if (shouldBeEmpty) {
                    cellSlots[i] = ItemStack.EMPTY;
                }
                needRecalc = true;
            }
        }
        if (needRecalc) {
            recalculateCellStats();
            if (energy > capacity) energy = capacity;
            setChanged();
        }
    }

    // ====================== ЗАРЯДКА / РАЗРЯДКА (4+4 слоты) ======================

    /**
     * Зарядка предметов из слотов 0-3 (charge input).
     * Когда предмет полностью заряжен, перемещаем в слоты 4-7 (charge output).
     */
    private void chargeItems() {
        for (int i = CHARGE_INPUT_START; i < CHARGE_INPUT_END; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            long spaceAvailable = capacity - energy;
            if (energy <= 0 && spaceAvailable <= 0) continue; // нет энергии для зарядки

            boolean charged = false;

            // Попро��уем HBM capability
            var hbmCap = stack.getCapability(ModCapabilities.ENERGY_RECEIVER);
            if (hbmCap.isPresent()) {
                hbmCap.ifPresent(target -> {
                    if (!target.canReceive()) return;
                    long toTransfer = Math.min(unchargingSpeed, energy);
                    if (toTransfer <= 0) return;
                    long accepted = target.receiveEnergy(toTransfer, false);
                    if (accepted > 0) {
                        this.energy -= accepted;
                        setChanged();
                    }
                });
            } else {
                // Forge Energy
                stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(target -> {
                    if (!target.canReceive()) return;
                    long wanted = Math.min(unchargingSpeed, energy);
                    if (wanted <= 0) return;
                    int maxTransfer = (int) Math.min(wanted, Integer.MAX_VALUE);
                    int accepted = target.receiveEnergy(maxTransfer, false);
                    if (accepted > 0) {
                        this.energy -= accepted;
                        setChanged();
                    }
                });
            }

            // Проверяем, полностью ли заряжен предмет
            if (isItemFullyCharged(stack)) {
                // Пробуем переместить в output
                int outputSlot = findFreeOutputSlot(CHARGE_OUTPUT_START, CHARGE_OUTPUT_END, stack);
                if (outputSlot >= 0) {
                    moveItem(i, outputSlot);
                }
            }
        }
    }

    /**
     * Разрядка предметов из слотов 8-11 (discharge input).
     * Когда предмет полностью разряжен, перемещаем в слоты 12-15 (discharge output).
     */
    private void dischargeItems() {
        for (int i = DISCHARGE_INPUT_START; i < DISCHARGE_INPUT_END; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            long spaceAvailable = capacity - energy;
            if (spaceAvailable <= 0) continue;

            // Попробуем HBM capability
            var hbmCap = stack.getCapability(ModCapabilities.ENERGY_PROVIDER);
            if (hbmCap.isPresent()) {
                hbmCap.ifPresent(source -> {
                    if (!source.canExtract()) return;
                    long toExtract = Math.min(chargingSpeed, capacity - energy);
                    if (toExtract <= 0) return;
                    long extracted = source.extractEnergy(toExtract, false);
                    if (extracted > 0) {
                        this.energy += extracted;
                        setChanged();
                    }
                });
            } else {
                // Forge Energy
                stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(source -> {
                    if (!source.canExtract()) return;
                    long wanted = Math.min(chargingSpeed, capacity - energy);
                    if (wanted <= 0) return;
                    int maxTransfer = (int) Math.min(wanted, Integer.MAX_VALUE);
                    int extracted = source.extractEnergy(maxTransfer, false);
                    if (extracted > 0) {
                        this.energy += extracted;
                        setChanged();
                    }
                });
            }

            // Проверяем, полностью ли разряжен предмет
            if (isItemFullyDischarged(stack)) {
                int outputSlot = findFreeOutputSlot(DISCHARGE_OUTPUT_START, DISCHARGE_OUTPUT_END, stack);
                if (outputSlot >= 0) {
                    moveItem(i, outputSlot);
                }
            }
        }
    }

    private boolean isItemFullyCharged(ItemStack stack) {
        // HBM check
        var hbmCap = stack.getCapability(ModCapabilities.ENERGY_RECEIVER);
        if (hbmCap.isPresent()) {
            return hbmCap.map(r -> r.getEnergyStored() >= r.getMaxEnergyStored()).orElse(false);
        }
        // Forge check
        return stack.getCapability(ForgeCapabilities.ENERGY)
                .map(s -> s.getEnergyStored() >= s.getMaxEnergyStored())
                .orElse(false);
    }

    private boolean isItemFullyDischarged(ItemStack stack) {
        // HBM check
        var hbmCap = stack.getCapability(ModCapabilities.ENERGY_PROVIDER);
        if (hbmCap.isPresent()) {
            return hbmCap.map(p -> p.getEnergyStored() <= 0).orElse(false);
        }
        // Forge check
        return stack.getCapability(ForgeCapabilities.ENERGY)
                .map(s -> s.getEnergyStored() <= 0)
                .orElse(false);
    }

    private int findFreeOutputSlot(int start, int end, ItemStack stack) {
        for (int i = start; i < end; i++) {
            ItemStack existing = itemHandler.getStackInSlot(i);
            if (existing.isEmpty()) {
                return i;
            }
            // Стакаемые предметы
            if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                return i;
            }
        }
        return -1;
    }

    private void moveItem(int fromSlot, int toSlot) {
        ItemStack source = itemHandler.getStackInSlot(fromSlot);
        if (source.isEmpty()) return;

        ItemStack existing = itemHandler.getStackInSlot(toSlot);
        if (existing.isEmpty()) {
            itemHandler.setStackInSlot(toSlot, source.copy());
            itemHandler.setStackInSlot(fromSlot, ItemStack.EMPTY);
        } else if (ItemStack.isSameItemSameTags(existing, source)) {
            int space = existing.getMaxStackSize() - existing.getCount();
            int toMove = Math.min(source.getCount(), space);
            if (toMove > 0) {
                existing.grow(toMove);
                source.shrink(toMove);
            }
        }
        setChanged();
    }

    // ====================== ГЕТТЕРЫ ======================

    public int getCurrentMode() {
        return this.mode;
    }

    public long getEnergyDelta() {
        return this.energyDelta;
    }

    public long getChargingSpeed() {
        return this.chargingSpeed;
    }

    public long getUnchargingSpeed() {
        return this.unchargingSpeed;
    }

    // --- IEnergyProvider & IEnergyReceiver ---
    @Override
    public long getEnergyStored() {
        return this.energy;
    }

    @Override
    public long getMaxEnergyStored() {
        return this.capacity;
    }

    @Override
    public void setEnergyStored(long energy) {
        this.energy = Math.max(0, Math.min(this.capacity, energy));
        setChanged();
    }

    @Override
    public long getProvideSpeed() {
        return this.unchargingSpeed;
    }

    @Override
    public long getReceiveSpeed() {
        return this.chargingSpeed;
    }

    @Override
    public Priority getPriority() {
        return this.priority;
    }

    public void setPriority(Priority p) {
        this.priority = p;
        setChanged();
    }

    @Override
    public boolean canConnectEnergy(Direction side) {
        return true;
    }

    @Override
    public long extractEnergy(long maxExtract, boolean simulate) {
        if (!canExtract()) return 0;
        long energyExtracted = Math.min(this.energy, Math.min(this.unchargingSpeed, maxExtract));
        if (!simulate && energyExtracted > 0) {
            setEnergyStored(this.energy - energyExtracted);
        }
        return energyExtracted;
    }

    @Override
    public boolean canExtract() {
        int m = getCurrentMode();
        return (m == 0 || m == 2) && this.energy > 0 && this.unchargingSpeed > 0;
    }

    @Override
    public long receiveEnergy(long maxReceive, boolean simulate) {
        if (!canReceive()) return 0;
        long energyReceived = Math.min(this.capacity - this.energy, Math.min(this.chargingSpeed, maxReceive));
        if (!simulate && energyReceived > 0) {
            setEnergyStored(this.energy + energyReceived);
        }
        return energyReceived;
    }

    @Override
    public boolean canReceive() {
        int m = getCurrentMode();
        return (m == 0 || m == 1) && this.energy < this.capacity && this.chargingSpeed > 0;
    }

    // --- Capabilities ---
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }

        if (cap == ModCapabilities.ENERGY_CONNECTOR) {
            return hbmConnector.cast();
        }

        int m = getCurrentMode();

        if (cap == ModCapabilities.ENERGY_PROVIDER && (m == 0 || m == 2)) {
            return hbmProvider.cast();
        }

        if (cap == ModCapabilities.ENERGY_RECEIVER && (m == 0 || m == 1)) {
            return hbmReceiver.cast();
        }

        LazyOptional<T> feCap = feCapabilityProvider.getCapability(cap, side);
        if (feCap.isPresent()) return feCap;

        return super.getCapability(cap, side);
    }

    // ====================== NBT ======================

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.energy = tag.getLong("Energy");
        this.lastEnergy = tag.getLong("lastEnergy");
        this.energyDelta = tag.getLong("energyDelta");
        this.mode = tag.getInt("mode");
        if (tag.contains("priority")) {
            int priorityIndex = tag.getInt("priority");
            this.priority = Priority.values()[Math.max(0, Math.min(priorityIndex, Priority.values().length - 1))];
        }
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        }

        for (int i = 0; i < CELL_SLOT_COUNT; i++) {
            String key = "Cell_" + i;
            if (tag.contains(key)) {
                cellSlots[i] = ItemStack.of(tag.getCompound(key));
                cellEmpty[i] = cellSlots[i].isEmpty();
            } else {
                cellSlots[i] = ItemStack.EMPTY;
                cellEmpty[i] = true;
            }
        }

        recalculateCellStats();
        if (energy > capacity) energy = capacity;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", this.energy);
        tag.putInt("mode", this.mode);
        tag.putLong("lastEnergy", this.lastEnergy);
        tag.putLong("energyDelta", this.energyDelta);
        tag.putInt("priority", this.priority.ordinal());
        tag.put("Inventory", itemHandler.serializeNBT());

        for (int i = 0; i < CELL_SLOT_COUNT; i++) {
            if (!cellSlots[i].isEmpty()) {
                tag.put("Cell_" + i, cellSlots[i].save(new CompoundTag()));
            }
        }
    }

    // ====================== МЕНЮ ======================

    @Override
    public Component getDisplayName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
        return new MachineBatteryMenu(windowId, playerInventory, this, this.data);
    }

    /**
     * Обработка нажатия кнопок в GUI.
     * buttonId 0 = переключение режима
     * buttonId 1 = переключение приоритета
     */
    public void handleButtonPress(int buttonId) {
        switch (buttonId) {
            case 0 -> this.data.set(0, (this.mode + 1) % 4);
            case 1 -> {
                Priority[] priorities = Priority.values();
                int currentIndex = this.priority.ordinal();
                int nextIndex = (currentIndex + 1) % priorities.length;
                this.data.set(1, nextIndex);
            }
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
    }

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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}