package com.cim.block.entity.energy;


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
import com.cim.block.basic.energy.MachineBatteryBlock;
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
 * Режимы: 0 = BOTH, 1 = INPUT, 2 = OUTPUT, 3 = DISABLED
 */
public class MachineBatteryBlockEntity extends BlockEntity implements MenuProvider, IEnergyProvider, IEnergyReceiver, GeoBlockEntity
{

    // ====================== КАРКАС: базовые параметры = 0 ======================
    private long capacity = 0;          // Суммарная ёмкость из ячеек
    private long chargingSpeed = 0;     // Среднее арифметическое скоростей зарядки
    private long unchargingSpeed = 0;   // Среднее арифметическое скоростей разрядки
    private long energy = 0;
    private long lastEnergy = 0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Режимы работы (0 = BOTH, 1 = INPUT, 2 = OUTPUT, 3 = DISABLED)
    public int modeOnNoSignal = 0;
    public int modeOnSignal = 0;
    private Priority priority = Priority.LOW;
    private long energyDelta = 0;

    // ====================== СЛОТЫ ДЛЯ ЭНЕРГОЯЧЕЕК (4 штуки, 2x2) ======================
    public static final int CELL_SLOT_COUNT = 4;
    private final ItemStack[] cellSlots = new ItemStack[CELL_SLOT_COUNT];
    private final boolean[] cellEmpty = new boolean[CELL_SLOT_COUNT]; // true = пустой

    // ====================== СЛОТЫ ДЛЯ БАТАРЕЕК (зарядка/разрядка) — как было ======================
    private final ItemStackHandler itemHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.getCapability(ForgeCapabilities.ENERGY).isPresent();
        }
    };

    // [ИСПРАВЛЕНИЕ] Убрали final и инициализацию в конструкторе (кроме empty)
    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();
    private LazyOptional<IEnergyProvider> hbmProvider = LazyOptional.empty();
    private LazyOptional<IEnergyReceiver> hbmReceiver = LazyOptional.empty();
    private LazyOptional<IEnergyConnector> hbmConnector = LazyOptional.empty();

    private PackedEnergyCapabilityProvider feCapabilityProvider;

    protected final ContainerData data;

    public MachineBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_BATTERY_BE.get(), pos, state);

        // Каркас: базовые параметры = 0
        this.capacity = 0;
        this.chargingSpeed = 0;
        this.unchargingSpeed = 0;

        // Инициализируем слоты ячеек как пустые
        for (int i = 0; i < CELL_SLOT_COUNT; i++) {
            cellSlots[i] = ItemStack.EMPTY;
            cellEmpty[i] = true;
        }

        this.feCapabilityProvider = new PackedEnergyCapabilityProvider(this);

        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> modeOnNoSignal;
                    case 1 -> modeOnSignal;
                    case 2 -> priority.ordinal();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> modeOnNoSignal = value;
                    case 1 -> modeOnSignal = value;
                    case 2 -> priority = Priority.values()[Math.max(0, Math.min(value, Priority.values().length - 1))];
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    // ====================== МЕТОДЫ ЭНЕРГОЯЧЕЕК ======================

    /**
     * Вставить энергоячейку в указанный слот (0-3).
     * Возвращает true если вставка успешна.
     */
    public boolean insertCell(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CELL_SLOT_COUNT) return false;
        if (!cellEmpty[slot]) return false; // Слот уже занят
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof EnergyCellItem cell)) return false;
        if (!cell.isValidCell(stack)) return false;

        // Вставляем ровно 1 ячейку
        cellSlots[slot] = stack.split(1);
        cellEmpty[slot] = false;

        recalculateCellStats();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    /**
     * Извлечь энергоячейку из указанного слота (0-3).
     * Возвращает ItemStack ячейки или EMPTY.
     */
    public ItemStack extractCell(int slot) {
        if (slot < 0 || slot >= CELL_SLOT_COUNT) return ItemStack.EMPTY;
        if (cellEmpty[slot]) return ItemStack.EMPTY;

        ItemStack extracted = cellSlots[slot].copy();
        cellSlots[slot] = ItemStack.EMPTY;
        cellEmpty[slot] = true;

        recalculateCellStats();

        // Защита: если энергия больше нового буфера, обрезаем
        if (energy > capacity) {
            energy = capacity;
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return extracted;
    }

    /**
     * Пересчитать все параметры каркаса на основе вставленных ячеек.
     *
     * capacity = СУММА capacity всех ячеек
     * chargingSpeed = СРЕДНЕЕ АРИФМЕТИЧЕСКОЕ chargingSpeed заполненных слотов
     * unchargingSpeed = СРЕДНЕЕ АРИФМЕТИЧЕСКОЕ unchargingSpeed заполненных слотов
     */
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

        if (filledCount > 0) {
            this.chargingSpeed = totalChargingSpeed / filledCount;
            this.unchargingSpeed = totalUnchargingSpeed / filledCount;
        } else {
            this.chargingSpeed = 0;
            this.unchargingSpeed = 0;
        }
    }

    /**
     * Проверить, пустой ли слот ячейки.
     */
    public boolean isCellEmpty(int slot) {
        if (slot < 0 || slot >= CELL_SLOT_COUNT) return true;
        return cellEmpty[slot];
    }

    /**
     * Получить ItemStack ячейки в слоте (для рендера или проверки).
     */
    public ItemStack getCellStack(int slot) {
        if (slot < 0 || slot >= CELL_SLOT_COUNT) return ItemStack.EMPTY;
        return cellSlots[slot];
    }

    /**
     * Количество заполненных слотов.
     */
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

        // Пересчитываем параметры при загрузке (ячейки уже десериализованы из NBT)
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

        // Проверяем консистентность флагов ячеек (на случай десинка)
        be.validateCellFlags();

        be.chargeFromItem();
        be.dischargeToItem();
    }

    /**
     * Проверяем, что флаги isEmpty соответствуют реальному содержимому слотов.
     * Вызывается в tick() для надёжности.
     */
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

    // ====================== ЗАРЯДКА / РАЗРЯДКА БАТАРЕЕК (без изменений) ======================

    private void chargeFromItem() {
        var stack = itemHandler.getStackInSlot(0);
        if (stack.isEmpty()) return;

        long spaceAvailable = capacity - energy;
        if (spaceAvailable <= 0) return;

        var hbmCap = stack.getCapability(ModCapabilities.ENERGY_PROVIDER);
        if (hbmCap.isPresent()) {
            hbmCap.ifPresent(source -> {
                if (!source.canExtract()) return;
                long toExtract = Math.min(chargingSpeed, spaceAvailable);
                if (toExtract <= 0) return;
                long extracted = source.extractEnergy(toExtract, false);
                if (extracted > 0) {
                    this.energy += extracted;
                    setChanged();
                }
            });
            return;
        }

        stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(source -> {
            if (!source.canExtract()) return;
            long wanted = Math.min(chargingSpeed, spaceAvailable);
            if (wanted <= 0) return;
            int maxTransfer = (int) Math.min(wanted, Integer.MAX_VALUE);
            int extracted = source.extractEnergy(maxTransfer, false);
            if (extracted > 0) {
                this.energy += extracted;
                setChanged();
            }
        });
    }

    private void dischargeToItem() {
        var stack = itemHandler.getStackInSlot(1);
        if (stack.isEmpty()) return;

        long availableEnergy = energy;
        if (availableEnergy <= 0) return;

        var hbmCap = stack.getCapability(ModCapabilities.ENERGY_RECEIVER);
        if (hbmCap.isPresent()) {
            hbmCap.ifPresent(target -> {
                if (!target.canReceive()) return;
                long toTransfer = Math.min(unchargingSpeed, availableEnergy);
                if (toTransfer <= 0) return;
                long accepted = target.receiveEnergy(toTransfer, false);
                if (accepted > 0) {
                    this.energy -= accepted;
                    setChanged();
                }
            });
            return;
        }

        stack.getCapability(ForgeCapabilities.ENERGY).ifPresent(target -> {
            if (!target.canReceive()) return;
            long wanted = Math.min(unchargingSpeed, availableEnergy);
            if (wanted <= 0) return;
            int maxTransfer = (int) Math.min(wanted, Integer.MAX_VALUE);
            int accepted = target.receiveEnergy(maxTransfer, false);
            if (accepted > 0) {
                this.energy -= accepted;
                setChanged();
            }
        });
    }

    // ====================== ГЕТТЕРЫ ======================

    public int getCurrentMode() {
        if (level == null) return modeOnNoSignal;
        return level.hasNeighborSignal(this.worldPosition) ? modeOnSignal : modeOnNoSignal;
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
        return this.unchargingSpeed; // Скорость отдачи = скорость разрядки
    }

    @Override
    public long getReceiveSpeed() {
        return this.chargingSpeed; // Скорость приёма = скорость зарядки
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
        int mode = getCurrentMode();
        return (mode == 0 || mode == 2) && this.energy > 0 && this.unchargingSpeed > 0;
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
        int mode = getCurrentMode();
        return (mode == 0 || mode == 1) && this.energy < this.capacity && this.chargingSpeed > 0;
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

        int mode = getCurrentMode();

        if (cap == ModCapabilities.ENERGY_PROVIDER && (mode == 0 || mode == 2)) {
            return hbmProvider.cast();
        }

        if (cap == ModCapabilities.ENERGY_RECEIVER && (mode == 0 || mode == 1)) {
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
        this.modeOnNoSignal = tag.getInt("modeOnNoSignal");
        this.modeOnSignal = tag.getInt("modeOnSignal");
        if (tag.contains("priority")) {
            int priorityIndex = tag.getInt("priority");
            this.priority = Priority.values()[Math.max(0, Math.min(priorityIndex, Priority.values().length - 1))];
        }
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        }

        // Загрузка энергоячеек
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

        // Пересчитываем параметры после загрузки ячеек
        recalculateCellStats();
        // Защита: обрезаем энергию если буфер уменьшился
        if (energy > capacity) energy = capacity;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", this.energy);
        tag.putInt("modeOnNoSignal", this.modeOnNoSignal);
        tag.putLong("lastEnergy", this.lastEnergy);
        tag.putLong("energyDelta", this.energyDelta);
        tag.putInt("modeOnSignal", this.modeOnSignal);
        tag.putInt("priority", this.priority.ordinal());
        tag.put("Inventory", itemHandler.serializeNBT());

        // Сохранение энергоячеек
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

    public void handleButtonPress(int buttonId) {
        switch (buttonId) {
            case 0 -> this.data.set(0, (this.modeOnNoSignal + 1) % 4);
            case 1 -> this.data.set(1, (this.modeOnSignal + 1) % 4);
            case 2 -> {
                Priority[] priorities = Priority.values();
                int currentIndex = this.priority.ordinal();
                int nextIndex = (currentIndex + 1) % priorities.length;
                this.data.set(2, nextIndex);
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
        saveAdditional(tag); // Сохраняем ВСЕ данные, включая ячейки
        return tag;
    }

    /**
     * Пакет, который отправляется клиенту при вызове
     * level.sendBlockUpdated() (после вставки/извлечения ячейки).
     */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Анимаций нет — только показ/скрытие костей в рендерере.
        // Метод обязателен, но оставляем пустым.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}