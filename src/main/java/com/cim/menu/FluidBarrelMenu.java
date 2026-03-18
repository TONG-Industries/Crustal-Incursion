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
        super(ModMenuTypes.FLUID_BARREL_MENU.get(), pContainerId); // Зарегистрируй меню!
        this.blockEntity = (FluidBarrelBlockEntity) entity;
        this.data = data;

        // Слоты бочки согласно твоему чертежу
        this.blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            this.addSlot(new SlotItemHandler(handler, 0, 35, 17)); // Full In
            this.addSlot(new SlotItemHandler(handler, 1, 35, 53)); // Empty Out
            this.addSlot(new SlotItemHandler(handler, 2, 125, 17)); // Empty In
            this.addSlot(new SlotItemHandler(handler, 3, 125, 53)); // Full Out
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
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        // Базовая логика Shift-клика (можно дописать позже)
        return ItemStack.EMPTY;
    }
}
