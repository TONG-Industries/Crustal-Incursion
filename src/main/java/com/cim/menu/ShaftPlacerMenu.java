package com.cim.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import com.cim.block.entity.industrial.rotation.ShaftPlacerBlockEntity;

public class ShaftPlacerMenu extends AbstractContainerMenu {
    private final ShaftPlacerBlockEntity blockEntity;
    private final ContainerData data;

    // Клиент
    public ShaftPlacerMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(8));
    }

    public boolean canPlaceNext() {
        return data.get(5) == 1;
    }

    public boolean hasDrillHead() {
        return data.get(6) == 1;
    }

    // Сервер
    public ShaftPlacerMenu(int containerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.SHAFT_PLACER_MENU.get(), containerId);
        checkContainerDataCount(data, 8);
        this.blockEntity = (ShaftPlacerBlockEntity) entity;
        this.data = data;

        // Инвентарь игрока
        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        // Слоты блока (3x3)
        IItemHandler handler = blockEntity.getInventory();
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 6; ++col) {  // 6 столбцов
                this.addSlot(new SlotItemHandler(handler, col + row * 6, 30 + col * 18, 44 + row * 18));
            }
        }

        addDataSlots(data);
    }

    public int getEnergy() { return data.get(0); }
    public int getMaxEnergy() { return data.get(1); }
    public boolean isSwitchedOn() { return data.get(2) == 1; }
    public int getShaftsAfterPort() { return data.get(3); } // количество валов после последнего порта
    public int getTotalLength() { return data.get(4); }     // общая длина цепочки

    public BlockPos getPos() { return blockEntity.getBlockPos(); }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, blockEntity.getBlockState().getBlock());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 106 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 164));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Простая реализация (можно доработать)
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (index < 36) { // Инвентарь игрока
                if (!this.moveItemStackTo(stack, 36, 45, false)) { // Слоты блока
                    return ItemStack.EMPTY;
                }
            } else { // Слоты блока
                if (!this.moveItemStackTo(stack, 0, 36, true)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    public boolean isRetracting() {
        return data.get(7) == 1;
    }
}