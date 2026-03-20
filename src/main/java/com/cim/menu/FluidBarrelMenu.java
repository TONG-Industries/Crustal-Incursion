package com.cim.menu;

import com.cim.block.basic.ModBlocks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.SlotItemHandler;
import com.cim.block.entity.fluids.FluidBarrelBlockEntity;

public class FluidBarrelMenu extends AbstractContainerMenu {
    public final FluidBarrelBlockEntity blockEntity;
    private final ContainerData data;

    public FluidBarrelMenu(int pContainerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.FLUID_BARREL_MENU.get(), pContainerId);
        this.blockEntity = (FluidBarrelBlockEntity) entity;
        this.data = data;

        this.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            // 1. Наполнение: ВХОД (пустые/полупустые предметы)
            for (int i = 0; i < 4; i++) {
                this.addSlot(new SlotItemHandler(handler, i, 11, 37 + (i * 18)));
            }
            // 2. Наполнение: ВЫХОД (полные предметы)
            for (int i = 0; i < 4; i++) {
                this.addSlot(new SlotItemHandler(handler, i + 4, 45, 37 + (i * 18)));
            }
            // 3. Опустошение: ВЫХОД (опустошенные предметы)
            for (int i = 0; i < 4; i++) {
                this.addSlot(new SlotItemHandler(handler, i + 8, 115, 37 + (i * 18)));
            }
            // 4. Опустошение: ВХОД (предметы с жидкостью)
            for (int i = 0; i < 4; i++) {
                this.addSlot(new SlotItemHandler(handler, i + 12, 149, 37 + (i * 18)));
            }

            // 5. Слот защитника (индекс 16)
            if (handler.getSlots() > 16) {
                this.addSlot(new SlotItemHandler(handler, 16, 19, 6));
            }
        });

        addPlayerInventory(inv);
        addPlayerHotbar(inv);
        addDataSlots(data);
    }

    public FluidBarrelMenu(int pContainerId, Inventory inv, FriendlyByteBuf extraData) {
        this(pContainerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(1));
    }

    public FluidStack getFluid() {
        return blockEntity.fluidTank.getFluid();
    }

    public int getCapacity() {
        return blockEntity.fluidTank.getCapacity();
    }

    public int getMode() {
        return data.get(0);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, ModBlocks.FLUID_BARREL.get());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 114 + i * 18)); // Изменено с 84 на 114
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 172)); // Изменено с 142 на 172 (114 + 3*18 + 4)
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        // Базовая логика Shift-клика (можно дописать позже)
        return ItemStack.EMPTY;
    }
}