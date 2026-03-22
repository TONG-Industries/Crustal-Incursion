package com.cim.menu;

import com.cim.block.basic.ModBlocks;
import com.cim.multiblock.industrial.HeaterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class HeaterMenu extends AbstractContainerMenu {
    private final HeaterBlockEntity blockEntity;
    private final ContainerLevelAccess levelAccess;
    private final ContainerData data;

    public HeaterMenu(int id, Inventory inv, HeaterBlockEntity entity, ContainerData data) {
        super(ModMenuTypes.HEATER_MENU.get(), id);
        this.blockEntity = entity;
        this.levelAccess = ContainerLevelAccess.create(entity.getLevel(), entity.getBlockPos());
        this.data = data;

        // Слот топлива (x85, y12)
        this.addSlot(new SlotItemHandler(entity.getInventory(), 0, 85, 12) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return entity.isFuel(stack);
            }
        });

        // Слот золы (x85, y40) - только для извлечения
        this.addSlot(new SlotItemHandler(entity.getInventory(), 1, 85, 40) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Инвентарь игрока (стандартный)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(inv, j + i * 9 + 9, 8 + j * 18, 86 + i * 18)); // было 84
            }
        }
        for (int k = 0; k < 9; k++) {
            this.addSlot(new Slot(inv, k, 8 + k * 18, 144)); // было 142
        }

        this.addDataSlots(data);
    }

    // Фабрика для сети
    // Фабрика для сети
    public static HeaterMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity entity = inv.player.level().getBlockEntity(pos);
        // !!! ИСПРАВЛЕНИЕ: Используем данные из сущности, а не создаем пустые !!!
        if (entity instanceof HeaterBlockEntity heater) {
            return new HeaterMenu(id, inv, heater, heater.getData());
        }
        // Fallback (не должен произойти, но для безопасности)
        return new HeaterMenu(id, inv, (HeaterBlockEntity) entity, new SimpleContainerData(4));
    }

    public int getTemperature() {
        return data.get(HeaterBlockEntity.DATA_TEMP);
    }

    public int getBurnTime() {
        return data.get(HeaterBlockEntity.DATA_BURN_TIME);
    }

    public boolean isBurning() {
        return data.get(HeaterBlockEntity.DATA_IS_BURNING) > 0;
    }

    public int getTotalBurnTime() {
        return data.get(HeaterBlockEntity.DATA_TOTAL_BURN_TIME);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(levelAccess, player, ModBlocks.HEATER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack returnStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            returnStack = stack.copy();

            if (index == 0 || index == 1) {
                // Из нагревателя в инвентарь
                if (!this.moveItemStackTo(stack, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // В нагреватель
                if (blockEntity.isFuel(stack)) {
                    if (!this.moveItemStackTo(stack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 2 && index < 29) {
                    if (!this.moveItemStackTo(stack, 29, 38, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= 29 && index < 38 && !this.moveItemStackTo(stack, 2, 29, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return returnStack;
    }
}