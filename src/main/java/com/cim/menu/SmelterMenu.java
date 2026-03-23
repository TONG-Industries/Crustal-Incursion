package com.cim.menu;

import com.cim.block.basic.ModBlocks;
import com.cim.multiblock.industrial.SmelterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;

public class SmelterMenu extends AbstractContainerMenu {
    private final SmelterBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess levelAccess;

    public SmelterMenu(int id, Inventory inv, SmelterBlockEntity entity, ContainerData data) {
        super(ModMenuTypes.SMELTER_MENU.get(), id);
        this.blockEntity = entity;
        this.data = data;
        this.levelAccess = ContainerLevelAccess.create(entity.getLevel(), entity.getBlockPos());

        // Верхний ряд (0-3): x95 y13, шаг 18
        for (int i = 0; i < 4; i++) {
            this.addSlot(new SlotItemHandler(entity.getInventory(), i, 95 + i * 18, 13) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return SmelterBlockEntity.getSmeltingResult(stack) != null;
                }
            });
        }

        // Нижний ряд (4-7): x95 y45
        for (int i = 0; i < 4; i++) {
            this.addSlot(new SlotItemHandler(entity.getInventory(), 4 + i, 95 + i * 18, 45) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return SmelterBlockEntity.getSmeltingResult(stack) != null;
                }
            });
        }

        // Инвентарь игрока (сдвинут вниз из-за большого GUI)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        // Хотбар
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, 160));
        }

        this.addDataSlots(data);
    }

    public static SmelterMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity entity = inv.player.level().getBlockEntity(pos);
        SimpleContainerData data = new SimpleContainerData(5);
        return new SmelterMenu(id, inv, (SmelterBlockEntity) entity, data);
    }

    public int getTemperature() { return data.get(0); }
    public int getProgressTop() { return data.get(1); }
    public int getProgressBottom() { return data.get(2); }
    public boolean isSmeltingTop() { return data.get(3) > 0; }
    public boolean isSmeltingBottom() { return data.get(4) > 0; }

    public SmelterBlockEntity getBlockEntity() { return blockEntity; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(levelAccess, player, ModBlocks.SMELTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack returnStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            returnStack = stack.copy();

            if (index < 8) {
                // Из плавильни в инвентарь
                if (!this.moveItemStackTo(stack, 8, 44, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // В плавильню
                if (SmelterBlockEntity.getSmeltingResult(stack) != null) {
                    // Пытаемся в верхний, потом в нижний
                    if (!this.moveItemStackTo(stack, 0, 4, false)) {
                        if (!this.moveItemStackTo(stack, 4, 8, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                } else if (index >= 8 && index < 35) {
                    if (!this.moveItemStackTo(stack, 35, 44, false)) return ItemStack.EMPTY;
                } else if (index >= 35 && !this.moveItemStackTo(stack, 8, 35, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return returnStack;
    }
}