package com.cim.menu;


import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.PacketDistributor;
import com.cim.api.energy.ILongEnergyMenu;
import com.cim.block.basic.energy.MachineBatteryBlock;
import com.cim.block.entity.energy.MachineBatteryBlockEntity;
import com.cim.network.ModPacketHandler;
import com.cim.network.packet.energy.PacketSyncEnergy;

import java.util.Optional;

public class MachineBatteryMenu extends AbstractContainerMenu implements ILongEnergyMenu {
    public final MachineBatteryBlockEntity blockEntity;
    private final Level level;
    private final ContainerData data;

    private static final int PLAYER_INVENTORY_START = 0;
    private static final int PLAYER_INVENTORY_END = 36;
    private static final int TE_CHARGE_INPUT_START = 36;
    private static final int TE_CHARGE_INPUT_END = 40;
    private static final int TE_CHARGE_OUTPUT_START = 40;
    private static final int TE_CHARGE_OUTPUT_END = 44;
    private static final int TE_DISCHARGE_INPUT_START = 44;
    private static final int TE_DISCHARGE_INPUT_END = 48;
    private static final int TE_DISCHARGE_OUTPUT_START = 48;
    private static final int TE_DISCHARGE_OUTPUT_END = 52;

    // Смещение инвентаря игрока вниз на 28 пикселей
    private static final int PLAYER_INV_Y_OFFSET = 30;

    private final Player player;

    private long clientEnergy;
    private long clientMaxEnergy;
    private long clientDelta;
    private long clientChargingSpeed;
    private long clientUnchargingSpeed;
    private int clientFilledCellCount;

    private long lastSyncedEnergy = -1;
    private long lastSyncedMaxEnergy = -1;
    private long lastSyncedDelta = -1;
    private long lastSyncedChargingSpeed = -1;
    private long lastSyncedUnchargingSpeed = -1;
    private int lastSyncedFilledCellCount = -1;

    // Серверный конструктор
    public MachineBatteryMenu(int pContainerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.MACHINE_BATTERY_MENU.get(), pContainerId);
        checkContainerSize(inv, MachineBatteryBlockEntity.TOTAL_ITEM_SLOTS);
        checkContainerDataCount(data, 2);

        if (!(entity instanceof MachineBatteryBlockEntity)) {
            throw new IllegalArgumentException("Wrong BlockEntity type!");
        }

        blockEntity = (MachineBatteryBlockEntity) entity;
        this.level = inv.player.level();
        this.data = data;
        this.player = inv.player;

        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        this.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            // 4 слота charge input: x=8, y=37,55,73,91
            this.addSlot(new SlotItemHandler(handler, 0, 8, 37));
            this.addSlot(new SlotItemHandler(handler, 1, 8, 55));
            this.addSlot(new SlotItemHandler(handler, 2, 8, 73));
            this.addSlot(new SlotItemHandler(handler, 3, 8, 91));

            // 4 слота charge output: x=42, y=37,55,73,91
            this.addSlot(new SlotItemHandler(handler, 4, 42, 37));
            this.addSlot(new SlotItemHandler(handler, 5, 42, 55));
            this.addSlot(new SlotItemHandler(handler, 6, 42, 73));
            this.addSlot(new SlotItemHandler(handler, 7, 42, 91));

            // 4 слота discharge input: x=152, y=37,55,73,91
            this.addSlot(new SlotItemHandler(handler, 8, 152, 37));
            this.addSlot(new SlotItemHandler(handler, 9, 152, 55));
            this.addSlot(new SlotItemHandler(handler, 10, 152, 73));
            this.addSlot(new SlotItemHandler(handler, 11, 152, 91));

            // 4 слота discharge output: x=118, y=37,55,73,91
            this.addSlot(new SlotItemHandler(handler, 12, 118, 37));
            this.addSlot(new SlotItemHandler(handler, 13, 118, 55));
            this.addSlot(new SlotItemHandler(handler, 14, 118, 73));
            this.addSlot(new SlotItemHandler(handler, 15, 118, 91));
        });

        addDataSlots(data);
    }

    // Клиентский конструктор
    public MachineBatteryMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv,
                inv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(2));
    }

    @Override
    public void setEnergy(long energy, long maxEnergy, long delta) {
        this.clientEnergy = energy;
        this.clientMaxEnergy = maxEnergy;
        this.clientDelta = delta;
    }

    public void setExtraData(long chargingSpeed, long unchargingSpeed, int filledCellCount) {
        this.clientChargingSpeed = chargingSpeed;
        this.clientUnchargingSpeed = unchargingSpeed;
        this.clientFilledCellCount = filledCellCount;
    }

    @Override
    public long getEnergyStatic() {
        return blockEntity.getEnergyStored();
    }

    @Override
    public long getMaxEnergyStatic() {
        return blockEntity.getMaxEnergyStored();
    }

    @Override
    public long getEnergyDeltaStatic() {
        return blockEntity.getEnergyDelta();
    }

    public long getEnergy() {
        if (blockEntity != null && !level.isClientSide) {
            return blockEntity.getEnergyStored();
        }
        return clientEnergy;
    }

    public long getMaxEnergy() {
        if (blockEntity != null && !level.isClientSide) {
            return blockEntity.getMaxEnergyStored();
        }
        return clientMaxEnergy;
    }

    public long getEnergyDelta() {
        if (blockEntity != null && !level.isClientSide) {
            return blockEntity.getEnergyDelta();
        }
        return clientDelta;
    }

    public long getChargingSpeed() {
        if (blockEntity != null && !level.isClientSide) {
            return blockEntity.getChargingSpeed();
        }
        return clientChargingSpeed;
    }

    public long getUnchargingSpeed() {
        if (blockEntity != null && !level.isClientSide) {
            return blockEntity.getUnchargingSpeed();
        }
        return clientUnchargingSpeed;
    }

    public int getFilledCellCount() {
        if (blockEntity != null && !level.isClientSide) {
            return blockEntity.getFilledCellCount();
        }
        return clientFilledCellCount;
    }

    public int getMode() {
        return this.data.get(0);
    }

    public int getPriorityOrdinal() {
        return this.data.get(1);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (blockEntity != null && !this.level.isClientSide) {
            long currentEnergy = blockEntity.getEnergyStored();
            long currentMax = blockEntity.getMaxEnergyStored();
            long currentDelta = blockEntity.getEnergyDelta();
            long currentChargingSpeed = blockEntity.getChargingSpeed();
            long currentUnchargingSpeed = blockEntity.getUnchargingSpeed();
            int currentFilledCellCount = blockEntity.getFilledCellCount();

            if (currentEnergy != lastSyncedEnergy ||
                    currentMax != lastSyncedMaxEnergy ||
                    currentDelta != lastSyncedDelta ||
                    currentChargingSpeed != lastSyncedChargingSpeed ||
                    currentUnchargingSpeed != lastSyncedUnchargingSpeed ||
                    currentFilledCellCount != lastSyncedFilledCellCount) {

                ModPacketHandler.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> (ServerPlayer) this.player),
                        new PacketSyncEnergy(
                                this.containerId,
                                currentEnergy,
                                currentMax,
                                currentDelta,
                                currentChargingSpeed,
                                currentUnchargingSpeed,
                                currentFilledCellCount
                        )
                );

                lastSyncedEnergy = currentEnergy;
                lastSyncedMaxEnergy = currentMax;
                lastSyncedDelta = currentDelta;
                lastSyncedChargingSpeed = currentChargingSpeed;
                lastSyncedUnchargingSpeed = currentUnchargingSpeed;
                lastSyncedFilledCellCount = currentFilledCellCount;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        Slot sourceSlot = slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
            Optional<IEnergyStorage> energyCapability = sourceStack.getCapability(ForgeCapabilities.ENERGY).resolve();

            if (energyCapability.isPresent()) {
                IEnergyStorage itemEnergy = energyCapability.get();
                boolean moved = false;

                if (itemEnergy.canExtract() && itemEnergy.getEnergyStored() > 0) {
                    if (moveItemStackTo(sourceStack, TE_DISCHARGE_INPUT_START, TE_DISCHARGE_INPUT_END, false)) {
                        moved = true;
                    }
                }

                if (!moved && itemEnergy.canReceive()) {
                    if (moveItemStackTo(sourceStack, TE_CHARGE_INPUT_START, TE_CHARGE_INPUT_END, false)) {
                        moved = true;
                    }
                }

                if (!moved) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }

        } else if (index >= TE_CHARGE_INPUT_START && index < TE_DISCHARGE_OUTPUT_END) {
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(playerIn, sourceStack);
        return copyOfSourceStack;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        return ContainerLevelAccess.create(level, blockEntity.getBlockPos()).evaluate((level, pos) -> {
            Block block = level.getBlockState(pos).getBlock();
            if (!(block instanceof MachineBatteryBlock)) {
                return false;
            }
            return pPlayer.distanceToSqr((double)pos.getX() + 0.5D, (double)pos.getY() + 0.5D, (double)pos.getZ() + 0.5D) <= 64.0D;
        }, true);
    }

    // Инвентарь игрока сдвинут на +28 по Y (было 84, стало 112)
    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18 + PLAYER_INV_Y_OFFSET));
            }
        }
    }

    // Хотбар сдвинут на +28 по Y (было 142, стало 170)
    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142 + PLAYER_INV_Y_OFFSET));
        }
    }
}