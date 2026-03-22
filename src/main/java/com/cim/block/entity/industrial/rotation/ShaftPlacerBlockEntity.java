package com.cim.block.entity.industrial.rotation;

import com.cim.api.energy.EnergyNetworkManager;
import com.cim.block.basic.industrial.rotation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.cim.api.energy.IEnergyConnector;
import com.cim.api.energy.IEnergyReceiver;
import com.cim.api.energy.LongEnergyWrapper;
import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.ModBlockEntities;
import com.cim.capability.ModCapabilities;

public class ShaftPlacerBlockEntity extends BlockEntity implements RotationalNode, IEnergyReceiver, IEnergyConnector {
    private long energyStored = 0;
    private final long MAX_ENERGY = 50000;
    private final long ENERGY_PER_SHAFT = 1500;
    private final long ENERGY_PER_PORT = 5000;
    private final long RECEIVE_SPEED = 1000;
    private final long ENERGY_PER_CHAIN = 250;
    private final long ENERGY_PER_PILLAR_BLOCK = 50;
    private static final int MAX_CHAIN_LENGTH = 10;
    private static final int PILLAR_BLOCK_DELAY = 10; // 0.5 секунды

    private boolean retracting = false;
    private int retractTimer = 0;
    private static final int RETRACT_DELAY = 20; // 1 секунда
    private boolean isSwitchedOn = false;
    private int shaftsAfterLastPort = 0;
    private int totalChainLength = 0;
    private boolean hasDrillHead = false;
    private long speed = 0;
    private long torque = 0;

    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;
    public boolean isRetracting() { return retracting; }
    private final ItemStackHandler inventory = new ItemStackHandler(18) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final LazyOptional<IItemHandler> inventoryOptional = LazyOptional.of(() -> inventory);

    @Nullable
    private BlockPos miningPortPos;
    @Nullable
    private BlockPos headPos;

    // Поля для постройки опорной колонны
    private boolean isBuildingPillar = false;
    private BlockPos pillarPortPos;
    private int pillarBlocksPlaced = 0;
    private int pillarTotalBlocks;
    private BlockItem pillarBlockItem;
    private Direction pillarDirection;
    private long nextPillarTime = 0;

    private final LazyOptional<IEnergyReceiver> energyReceiverOptional = LazyOptional.of(() -> this);
    private final LazyOptional<IEnergyConnector> energyConnectorOptional = LazyOptional.of(() -> this);
    private final LazyOptional<net.minecraftforge.energy.IEnergyStorage> forgeEnergyOptional =
            LazyOptional.of(() -> new LongEnergyWrapper(this, LongEnergyWrapper.BitMode.LOW));

    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) Math.min(energyStored, Integer.MAX_VALUE);
                case 1 -> (int) MAX_ENERGY;
                case 2 -> isSwitchedOn ? 1 : 0;
                case 3 -> shaftsAfterLastPort;
                case 4 -> totalChainLength;
                case 5 -> canPlaceNext() ? 1 : 0;
                case 6 -> hasDrillHead ? 1 : 0;
                case 7 -> retracting ? 1 : 0;
                default -> 0;
            };
        }
        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energyStored = value;
                case 2 -> isSwitchedOn = value == 1;
                case 3 -> shaftsAfterLastPort = value;
                case 4 -> totalChainLength = value;
            }
        }
        @Override public int getCount() { return 8; }
    };

    public ShaftPlacerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAFT_PLACER_BE.get(), pos, state);
    }

    private ItemStack addItemToInventory(ItemStack stack) {
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

    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        Direction facing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        if (fromDir == facing.getOpposite()) {
            return new Direction[]{facing};
        } else if (fromDir == facing) {
            return new Direction[]{facing.getOpposite()};
        } else {
            return new Direction[0];
        }
    }

    // ========== Energy ==========
    @Override public long getEnergyStored() { return energyStored; }
    @Override public long getMaxEnergyStored() { return MAX_ENERGY; }
    @Override public void setEnergyStored(long energy) { this.energyStored = Math.max(0, Math.min(energy, MAX_ENERGY)); setChanged(); }
    @Override public long getReceiveSpeed() { return RECEIVE_SPEED; }
    @Override public IEnergyReceiver.Priority getPriority() { return IEnergyReceiver.Priority.NORMAL; }
    @Override public boolean canReceive() { return energyStored < MAX_ENERGY; }
    @Override public long receiveEnergy(long maxReceive, boolean simulate) {
        if (!canReceive()) return 0;
        long received = Math.min(MAX_ENERGY - energyStored, Math.min(RECEIVE_SPEED, maxReceive));
        if (!simulate && received > 0) {
            energyStored += received;
            setChanged();
        }
        return received;
    }
    @Override
    public boolean canConnectEnergy(Direction side) {
        return side != getBlockState().getValue(ShaftPlacerBlock.FACING);
    }

    // ========== Capability ==========
    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ModCapabilities.ENERGY_RECEIVER || cap == ModCapabilities.ENERGY_CONNECTOR) {
            if (side == null || canConnectEnergy(side)) {
                if (cap == ModCapabilities.ENERGY_RECEIVER) {
                    return energyReceiverOptional.cast();
                } else {
                    return energyConnectorOptional.cast();
                }
            } else {
                return LazyOptional.empty();
            }
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return forgeEnergyOptional.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyReceiverOptional.invalidate();
        energyConnectorOptional.invalidate();
        forgeEnergyOptional.invalidate();
        inventoryOptional.invalidate();
    }

    // ========== Inventory helpers ==========
    private int findShaftSlot() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && (stack.getItem() == ModBlocks.SHAFT_IRON.get().asItem() ||
                    stack.getItem() == ModBlocks.SHAFT_WOODEN.get().asItem())) {
                return i;
            }
        }
        return -1;
    }

    public void toggleRetractMode() {
        if (retracting) {
            retracting = false;
            retractTimer = 0;
        } else {
            retracting = true;
            retractTimer = RETRACT_DELAY;
            setSpeed(0);
            setTorque(0);
        }
        setChanged();
        sync();
    }

    private int findPortSlot() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModBlocks.GEAR_PORT.get().asItem()) {
                return i;
            }
        }
        return -1;
    }

    private int findChainSlot() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == Blocks.CHAIN.asItem()) {
                return i;
            }
        }
        return -1;
    }

    private int findSlotForItem(boolean needPort) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (needPort) {
                if (stack.getItem() == ModBlocks.GEAR_PORT.get().asItem()) {
                    return i;
                }
            } else {
                if (stack.getItem() == ModBlocks.SHAFT_IRON.get().asItem() ||
                        stack.getItem() == ModBlocks.SHAFT_WOODEN.get().asItem()) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void updateRetracting(Level level) {
        if (!retracting) return;

        if (retractTimer > 0) {
            retractTimer--;
            return;
        }

        if (!tryRemoveNextBlock(level)) {
            retracting = false;
            setChanged();
            sync();
        }
    }

    // ========== Placement logic ==========
    private BlockPos getNextPlacePos() {
        Direction facing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        return worldPosition.relative(facing, totalChainLength + 1);
    }

    public boolean canPlaceNext() {
        if (!isSwitchedOn || isBuildingPillar) return false;
        Direction facing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        BlockPos nextPos = getNextPlacePos();
        if (!level.isEmptyBlock(nextPos) && !level.getBlockState(nextPos).canBeReplaced()) {
            return false;
        }
        boolean needPort = (shaftsAfterLastPort >= 5);
        int slotIndex = needPort ? findPortSlot() : findShaftSlot();
        if (slotIndex == -1) return false;
        long energyCost = needPort ? ENERGY_PER_PORT : ENERGY_PER_SHAFT;
        return energyStored >= energyCost;
    }

    public boolean hasResourcesForNext() {
        if (!isSwitchedOn || isBuildingPillar) return false;
        boolean needPort = (shaftsAfterLastPort >= 5);
        int slotIndex = needPort ? findPortSlot() : findShaftSlot();
        if (slotIndex == -1) return false;
        long energyCost = needPort ? ENERGY_PER_PORT : ENERGY_PER_SHAFT;
        return energyStored >= energyCost;
    }

    public boolean isBusy() {
        return isBuildingPillar;
    }

    private void startPillarConstruction(Level level, BlockPos portPos, Direction dir, BlockItem blockItem, int totalBlocks) {
        this.isBuildingPillar = true;
        this.pillarPortPos = portPos;
        this.pillarBlocksPlaced = 0;
        this.pillarTotalBlocks = totalBlocks;
        this.pillarBlockItem = blockItem;
        this.pillarDirection = dir;
        this.nextPillarTime = level.getGameTime() + PILLAR_BLOCK_DELAY;
        setChanged();
        sync();
    }

    // ========== Проверки возможности установки порта ==========
    private boolean canPlacePortWithChain(Level level, BlockPos pos, Direction facing) {
        // Ищем твёрдый блок сверху
        for (int i = 1; i <= MAX_CHAIN_LENGTH; i++) {
            BlockPos checkPos = pos.above(i);
            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                int chainLength = i - 1;
                // Проверяем наличие цепей в инвентаре
                int chainCount = 0;
                for (int j = 0; j < inventory.getSlots(); j++) {
                    ItemStack stack = inventory.getStackInSlot(j);
                    if (stack.getItem() == Blocks.CHAIN.asItem()) {
                        chainCount += stack.getCount();
                    }
                }
                long energyNeeded = ENERGY_PER_PORT + chainLength * ENERGY_PER_CHAIN;
                int portSlot = findPortSlot();
                return portSlot != -1 && chainCount >= chainLength && energyStored >= energyNeeded;
            }
        }
        return false;
    }

    private boolean canPlacePortWithPillar(Level level, BlockPos pos, Direction facing) {
        if (miningPortPos == null) return false;
        BlockEntity bePort = level.getBlockEntity(miningPortPos);
        if (!(bePort instanceof MiningPortBlockEntity miningPort)) return false;

        // Ищем твёрдый блок снизу
        for (int i = 1; i <= MAX_CHAIN_LENGTH; i++) {
            BlockPos checkPos = pos.below(i);
            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                int pillarLength = i - 1;
                if (pillarLength == 0) {
                    int portSlot = findPortSlot();
                    return portSlot != -1 && energyStored >= ENERGY_PER_PORT;
                }
                // Проверяем наличие блоков в MiningPort
                IItemHandler portInv = miningPort.getInventory();
                int totalBlocks = 0;
                for (int s = 0; s < portInv.getSlots(); s++) {
                    ItemStack stack = portInv.getStackInSlot(s);
                    if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                        totalBlocks += stack.getCount();
                    }
                }
                long energyNeeded = ENERGY_PER_PORT + pillarLength * ENERGY_PER_PILLAR_BLOCK;
                int portSlot = findPortSlot();
                return portSlot != -1 && totalBlocks >= pillarLength && energyStored >= energyNeeded;
            }
        }
        return false;
    }

    public boolean canPlacePortAt(Level level, BlockPos pos, Direction facing) {
        return canPlacePortWithPillar(level, pos, facing) || canPlacePortWithChain(level, pos, facing);
    }

    public int getShaftsAfterLastPort() {
        return shaftsAfterLastPort;
    }

    private boolean tryPlacePortWithChain(Level level, BlockPos pos, Direction facing) {
        // Ищем твёрдый блок сверху (игнорируем жидкости)
        BlockPos solidPos = null;
        int chainLength = 0;
        for (int i = 1; i <= MAX_CHAIN_LENGTH; i++) {
            BlockPos checkPos = pos.above(i);
            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                solidPos = checkPos;
                chainLength = i - 1;
                break;
            }
        }
        if (solidPos == null) return false; // нет блока сверху

        // Проверяем наличие цепей в инвентаре
        int chainCount = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.getItem() == Blocks.CHAIN.asItem()) {
                chainCount += stack.getCount();
            }
        }
        long energyNeeded = ENERGY_PER_PORT + chainLength * ENERGY_PER_CHAIN;
        int portSlot = findPortSlot();
        if (portSlot == -1 || chainCount < chainLength || energyStored < energyNeeded) return false;

        // Списание ресурсов
        energyStored -= energyNeeded;
        inventory.extractItem(portSlot, 1, false);
        int toExtract = chainLength;
        for (int i = 0; i < inventory.getSlots() && toExtract > 0; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.getItem() == Blocks.CHAIN.asItem()) {
                int extract = Math.min(stack.getCount(), toExtract);
                inventory.extractItem(i, extract, false);
                toExtract -= extract;
            }
        }

        // Установка порта
        BlockState portState = ModBlocks.GEAR_PORT.get().defaultBlockState();
        level.setBlock(pos, portState, 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GearPortBlockEntity gear) {
            gear.setupPorts(facing.getOpposite(), facing);
        }

        // Установка цепей
        for (int i = 0; i < chainLength; i++) {
            level.setBlock(pos.above(i + 1), Blocks.CHAIN.defaultBlockState(), 3);
        }

        setChanged();
        sync();
        return true;
    }

    private boolean tryPlacePortWithPillar(Level level, BlockPos pos, Direction facing) {
        // Проверяем наличие MiningPort
        if (miningPortPos == null) return false;
        BlockEntity bePort = level.getBlockEntity(miningPortPos);
        if (!(bePort instanceof MiningPortBlockEntity miningPort)) return false;

        // Ищем твёрдый блок снизу (игнорируем жидкости)
        BlockPos solidPos = null;
        int pillarLength = 0;
        for (int i = 1; i <= MAX_CHAIN_LENGTH; i++) {
            BlockPos checkPos = pos.below(i);
            BlockState state = level.getBlockState(checkPos);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                solidPos = checkPos;
                pillarLength = i - 1;
                break;
            }
        }
        if (solidPos == null) return false; // нет блока снизу

        // Если блок прямо под портом – ставим порт без опоры
        if (pillarLength == 0) {
            int portSlot = findPortSlot();
            if (portSlot == -1 || energyStored < ENERGY_PER_PORT) return false;
            energyStored -= ENERGY_PER_PORT;
            inventory.extractItem(portSlot, 1, false);
            level.setBlock(pos, ModBlocks.GEAR_PORT.get().defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GearPortBlockEntity gear) {
                gear.setupPorts(facing.getOpposite(), facing);
            }
            setChanged();
            sync();
            return true;
        }

        // Ищем подходящие блоки в MiningPort (может быть несколько слотов)
        IItemHandler portInv = miningPort.getInventory();
        int totalBlocks = 0;
        for (int s = 0; s < portInv.getSlots(); s++) {
            ItemStack stack = portInv.getStackInSlot(s);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                totalBlocks += stack.getCount();
            }
        }
        if (totalBlocks < pillarLength) return false;

        long energyNeeded = ENERGY_PER_PORT + pillarLength * ENERGY_PER_PILLAR_BLOCK;
        int portSlot = findPortSlot();
        if (portSlot == -1 || energyStored < energyNeeded) return false;

// Списание энергии и порта
        energyStored -= energyNeeded;
        inventory.extractItem(portSlot, 1, false);

// Списание блоков из MiningPort (из нескольких слотов)
        int toExtract = pillarLength;
        BlockItem blockItem = null;
        for (int s = 0; s < portInv.getSlots() && toExtract > 0; s++) {
            ItemStack stack = portInv.getStackInSlot(s);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem bi) {
                int extract = Math.min(stack.getCount(), toExtract);
                portInv.extractItem(s, extract, false);
                toExtract -= extract;
                if (blockItem == null) {
                    blockItem = bi; // берём первый попавшийся тип для колонны
                }
            }
        }
        if (blockItem == null) return false; // на всякий случай

// Установка порта (без изменений)
        level.setBlock(pos, ModBlocks.GEAR_PORT.get().defaultBlockState(), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GearPortBlockEntity gear) {
            gear.setupPorts(facing.getOpposite(), facing);
        }

// Запуск постепенного строительства колонны
        startPillarConstruction(level, pos, Direction.DOWN, blockItem, pillarLength);
        return true;
    }

    private void updateMiningPortPos(Level level) {
        Direction facing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);
        BlockEntity be = level.getBlockEntity(frontPos);

        if (be instanceof MiningPortBlockEntity port) {
            if (!port.getBlockPos().equals(this.miningPortPos)) {
                if (this.miningPortPos != null && level.getBlockEntity(this.miningPortPos) instanceof MiningPortBlockEntity oldPort) {
                    oldPort.setPlacerPos(null);
                }
                this.miningPortPos = frontPos;
                port.setPlacerPos(this.worldPosition);
            }
        } else {
            if (this.miningPortPos != null) {
                BlockEntity oldPort = level.getBlockEntity(this.miningPortPos);
                if (oldPort instanceof MiningPortBlockEntity port) {
                    port.setPlacerPos(null);
                }
            }
            this.miningPortPos = null;
        }
    }

    private boolean tryRemoveNextBlock(Level level) {
        Direction facing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        BlockPos currentPos = worldPosition.relative(facing);
        BlockPos lastBlockPos = null;
        BlockState lastState = null;
        int length = 0;
        while (length < 9999) {
            BlockState state = level.getBlockState(currentPos);
            Block block = state.getBlock();
            if (block instanceof ShaftBlock || block instanceof GearPortBlock || block instanceof DrillHeadBlock) {
                lastBlockPos = currentPos;
                lastState = state;
                length++;
                currentPos = currentPos.relative(facing);
            } else if (block instanceof MiningPortBlock) {
                // Пропускаем MiningPortBlock – он не должен удаляться, но не прерываем цикл
                currentPos = currentPos.relative(facing);
            } else {
                break;
            }
        }
        if (lastBlockPos == null) return false;

        // Удаляем блок
        BlockEntity be = level.getBlockEntity(lastBlockPos);
        level.removeBlock(lastBlockPos, false);

        // Добавляем предмет
        ItemStack stack = new ItemStack(lastState.getBlock().asItem());
        ItemStack remainder = addItemToInventory(stack);
        if (!remainder.isEmpty()) {
            BlockPos dropPos = worldPosition.relative(facing, 1);
            Containers.dropItemStack(level, dropPos.getX(), dropPos.getY(), dropPos.getZ(), remainder);
        }

        // Обновляем цепочку
        totalChainLength = length - 1;
        if (totalChainLength == 0) {
            headPos = null;
            hasDrillHead = false;
        } else {
            BlockPos newHead = worldPosition.relative(facing, totalChainLength);
            BlockState headState = level.getBlockState(newHead);
            if (headState.getBlock() instanceof DrillHeadBlock) {
                headPos = newHead;
                hasDrillHead = true;
                if (level.getBlockEntity(newHead) instanceof DrillHeadBlockEntity drill) {
                    drill.setPlacerPos(worldPosition);
                }
            } else {
                headPos = newHead;
                hasDrillHead = false;
            }
        }

        if (lastState.getBlock() instanceof GearPortBlock) {
            if (miningPortPos != null && miningPortPos.equals(lastBlockPos)) {
                miningPortPos = null;
            }
            updateChainInfo(level);
        }

        setChanged();
        sync();
        return true;
    }

    // ========== Ticking ==========
    public static void tick(Level level, BlockPos pos, BlockState state, ShaftPlacerBlockEntity be) {
        if (level.isClientSide) return;

        if (be.retracting) {
            be.updateRetracting(level);
            return;
        }

        EnergyNetworkManager manager = EnergyNetworkManager.get((ServerLevel) level);
        if (!manager.hasNode(pos)) {
            manager.addNode(pos);
        }

        long currentTime = level.getGameTime();

        if (!be.isCacheValid(currentTime)) {
            RotationSource source = RotationNetworkHelper.findSource(be, null);
            be.setCachedSource(source, currentTime);
        }
        RotationSource src = be.getCachedSource();
        long newSpeed = src != null ? src.speed() : 0;
        long newTorque = src != null ? src.torque() : 0;
        if (be.speed != newSpeed || be.torque != newTorque) {
            be.speed = newSpeed;
            be.torque = newTorque;
            be.setChanged();
            be.sync();
        }

        if (!be.isSwitchedOn) return;

        // Обработка строительства колонны
        if (be.isBuildingPillar) {
            if (currentTime >= be.nextPillarTime && be.pillarBlocksPlaced < be.pillarTotalBlocks) {
                BlockPos placePos = be.pillarPortPos.relative(be.pillarDirection, be.pillarBlocksPlaced + 1);
                if (level.isEmptyBlock(placePos) || level.getBlockState(placePos).canBeReplaced()) {
                    level.setBlock(placePos, be.pillarBlockItem.getBlock().defaultBlockState(), 3);
                }
                be.pillarBlocksPlaced++;
                be.nextPillarTime = currentTime + PILLAR_BLOCK_DELAY;
                be.setChanged();
                be.sync();
            }
            if (be.pillarBlocksPlaced >= be.pillarTotalBlocks) {
                be.isBuildingPillar = false;
                be.setChanged();
                be.sync();
            }
            // Пока строим колонну, не выполняем другие действия
            return;
        }

        if (level.getGameTime() % 10 == 0) {
            be.updateChainInfo(level);
        }

        if (!be.hasDrillHead && level.getGameTime() % 10 == 0) {
            be.tryPlaceNext(level);
        }

        if (level.getGameTime() % 20 == 0) {
            be.updateMiningPortPos(level);
        }
    }

    private void updateChainInfo(Level level) {
        Direction facing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        BlockPos currentPos = worldPosition.relative(facing);
        int length = 0;
        boolean foundDrill = false;
        BlockPos lastHeadPos = null;

        while (length < 9999) {
            BlockState state = level.getBlockState(currentPos);
            Block block = state.getBlock();

            if (block instanceof ShaftBlock) {
                if (state.getValue(ShaftBlock.FACING) == facing) {
                    length++;
                    currentPos = currentPos.relative(facing);
                    continue;
                } else {
                    break;
                }
            } else if (block instanceof GearPortBlock || block instanceof MiningPortBlock) {
                length++;
                currentPos = currentPos.relative(facing);
                continue;
            } else if (block instanceof DrillHeadBlock) {
                if (state.getValue(DrillHeadBlock.FACING) == facing) {
                    length++;
                    foundDrill = true;
                    lastHeadPos = currentPos;
                    currentPos = currentPos.relative(facing);
                    continue;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        this.totalChainLength = length;
        this.hasDrillHead = foundDrill;
        this.headPos = lastHeadPos;

        if (foundDrill && lastHeadPos != null && level.getBlockEntity(lastHeadPos) instanceof DrillHeadBlockEntity drill) {
            drill.setPlacerPos(worldPosition);
        }

        setChanged();
    }

    private void tryPlaceNext(Level level) {
        if (headPos != null || isBuildingPillar) return;

        Direction facing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        BlockPos currentPos = worldPosition.relative(facing);

        int existingLength = 0;
        while (existingLength < 9999) {
            BlockState state = level.getBlockState(currentPos);
            Block block = state.getBlock();

            if (block instanceof ShaftBlock) {
                if (state.getValue(ShaftBlock.FACING) == facing) {
                    existingLength++;
                    currentPos = currentPos.relative(facing);
                    continue;
                } else {
                    break;
                }
            } else if (block instanceof GearPortBlock || block instanceof MiningPortBlock) {
                existingLength++;
                currentPos = currentPos.relative(facing);
                continue;
            } else if (block instanceof DrillHeadBlock) {
                return;
            } else {
                break;
            }
        }

        if (existingLength >= 9999) return;

        BlockPos placePos = currentPos;
        if (!level.isEmptyBlock(placePos) && !level.getBlockState(placePos).canBeReplaced()) {
            return;
        }

        boolean needPort = (shaftsAfterLastPort >= 5);

        if (needPort) {
            if (tryPlacePortWithPillar(level, placePos, facing)) {
                shaftsAfterLastPort = 0;
                totalChainLength++;
                updateMiningPortPos(level);
            } else if (tryPlacePortWithChain(level, placePos, facing)) {
                shaftsAfterLastPort = 0;
                totalChainLength++;
                updateMiningPortPos(level);
            } else {
                return;
            }
        }else {
            int slotIndex = findShaftSlot();
            if (slotIndex == -1) return;
            long energyCost = ENERGY_PER_SHAFT;
            if (energyStored < energyCost) return;

            energyStored -= energyCost;
            inventory.extractItem(slotIndex, 1, false);

            BlockState shaftState = ModBlocks.SHAFT_IRON.get().defaultBlockState()
                    .setValue(ShaftBlock.FACING, facing);
            level.setBlock(placePos, shaftState, 3);

            shaftsAfterLastPort++;
            totalChainLength++;
        }

        setChanged();
        sync();
        invalidateNeighborCaches();
    }

    public void placeShaftAt(BlockPos pos, Direction facing) {
        if (level == null || level.isClientSide || isBuildingPillar) return;

        Direction placerFacing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        if (!pos.equals(worldPosition.relative(placerFacing, totalChainLength + 1))) {
            return;
        }

        if (!level.isEmptyBlock(pos) && !level.getBlockState(pos).canBeReplaced()) return;

        int slotIndex = findShaftSlot();
        if (slotIndex == -1) return;

        if (energyStored < ENERGY_PER_SHAFT) return;

        BlockState shaftState = ModBlocks.SHAFT_IRON.get().defaultBlockState()
                .setValue(ShaftBlock.FACING, facing);
        level.setBlock(pos, shaftState, 3);

        energyStored -= ENERGY_PER_SHAFT;
        inventory.extractItem(slotIndex, 1, false);

        shaftsAfterLastPort++;
        totalChainLength++;

        setChanged();
        sync();
        invalidateNeighborCaches();
    }

    public void handleHeadMoved(BlockPos oldHeadPos, BlockPos newHeadPos) {
        if (level == null || level.isClientSide || isBuildingPillar) return;
        if (!isSwitchedOn) return;
        if (!oldHeadPos.equals(this.headPos)) return;

        Direction facing = getBlockState().getValue(ShaftPlacerBlock.FACING);
        if (!level.isEmptyBlock(oldHeadPos) && !level.getBlockState(oldHeadPos).canBeReplaced()) {
            return;
        }

        boolean needPort = (shaftsAfterLastPort >= 5);

        if (needPort) {
            if (tryPlacePortWithPillar(level, oldHeadPos, facing)) {
                shaftsAfterLastPort = 0;
                updateMiningPortPos(level);
                this.headPos = newHeadPos;
                this.totalChainLength++;
            } else if (tryPlacePortWithChain(level, oldHeadPos, facing)) {
                shaftsAfterLastPort = 0;
                updateMiningPortPos(level);
                this.headPos = newHeadPos;
                this.totalChainLength++;
            } else {
                return;
            }
        } else {
            int slotIndex = findShaftSlot();
            if (slotIndex == -1) return;
            if (energyStored < ENERGY_PER_SHAFT) return;

            energyStored -= ENERGY_PER_SHAFT;
            inventory.extractItem(slotIndex, 1, false);

            BlockState shaftState = ModBlocks.SHAFT_IRON.get().defaultBlockState()
                    .setValue(ShaftBlock.FACING, facing);
            level.setBlock(oldHeadPos, shaftState, 3);

            shaftsAfterLastPort++;
            this.headPos = newHeadPos;
            this.totalChainLength++;
        }

        setChanged();
        sync();
        invalidateNeighborCaches();
    }

    // ========== Public helpers ==========
    public ContainerData getDataAccess() { return data; }
    public IItemHandler getInventory() { return inventory; }
    @Nullable public BlockPos getMiningPortPos() { return miningPortPos; }
    public boolean isSwitchedOn() { return isSwitchedOn; }
    public boolean hasDrillHead() { return hasDrillHead; }

    public void togglePower() {
        this.isSwitchedOn = !this.isSwitchedOn;
        setChanged();
        sync();
        invalidateNeighborCaches();
    }

    public void onNeighborChange() {
        if (level != null && !level.isClientSide) {
            updateChainInfo(level);
        }
    }

    // ========== NBT ==========
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", energyStored);
        tag.putBoolean("SwitchedOn", isSwitchedOn);
        tag.putInt("ShaftsAfterPort", shaftsAfterLastPort);
        tag.putInt("TotalLength", totalChainLength);
        tag.putBoolean("HasDrillHead", hasDrillHead);
        tag.put("Inventory", inventory.serializeNBT());
        if (miningPortPos != null) tag.putLong("MiningPortPos", miningPortPos.asLong());
        if (headPos != null) tag.putLong("HeadPos", headPos.asLong());
        tag.putBoolean("BuildingPillar", isBuildingPillar);
        if (isBuildingPillar && pillarBlockItem != null) {
            Block block = pillarBlockItem.getBlock();
            net.minecraft.resources.ResourceLocation registryName = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
            if (registryName != null) {
                tag.putString("PillarBlock", registryName.toString());
            }
        }
        tag.putBoolean("Retracting", retracting);
        tag.putInt("RetractTimer", retractTimer);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStored = tag.getLong("Energy");
        isSwitchedOn = tag.getBoolean("SwitchedOn");
        shaftsAfterLastPort = tag.getInt("ShaftsAfterPort");
        totalChainLength = tag.getInt("TotalLength");
        hasDrillHead = tag.getBoolean("HasDrillHead");
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        if (tag.contains("MiningPortPos")) {
            miningPortPos = BlockPos.of(tag.getLong("MiningPortPos"));
        } else {
            miningPortPos = null;
        }
        cachedSource = null;
        if (tag.contains("HeadPos")) {
            headPos = BlockPos.of(tag.getLong("HeadPos"));
        } else {
            headPos = null;
        }
        isBuildingPillar = tag.getBoolean("BuildingPillar");
        if (tag.contains("PillarBlock")) {
            String blockName = tag.getString("PillarBlock");
            Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(blockName));
            if (block != null && block.asItem() instanceof BlockItem bi) {
                pillarBlockItem = bi;
            }
        }
        retracting = tag.getBoolean("Retracting");
        retractTimer = tag.getInt("RetractTimer");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            updateChainInfo(level);
        }
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

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
    }
}