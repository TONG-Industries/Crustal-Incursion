package com.cim.menu;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity;

public class MotorElectroMenu extends AbstractContainerMenu {
    private final MotorElectroBlockEntity blockEntity;
    private final ContainerData data;

    // Конструктор для клиента
    public MotorElectroMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(6));
    }

    // Конструктор для сервера
    public MotorElectroMenu(int containerId, Inventory inv, BlockEntity entity, ContainerData data) {
        super(ModMenuTypes.MOTOR_ELECTRO_MENU.get(), containerId);
        checkContainerDataCount(data, 6); // Теперь мы проверяем 6
        this.blockEntity = (MotorElectroBlockEntity) entity;
        this.data = data;

        // Добавляем слоты игрока
        addPlayerInventory(inv);
        addPlayerHotbar(inv);

        // Синхронизируем данные
        addDataSlots(data);
    }

    // Геттеры для GUI
    public int getEnergy() { return data.get(0); }
    public int getMaxEnergy() { return data.get(1); }
    public boolean isSwitchedOn() { return data.get(2) == 1; }
    public boolean isGeneratorMode() { return data.get(5) == 1; }
    // В классе MotorElectroMenu (Page 30)
    public int getRotationValue() {
        return data.get(4); // Теперь здесь всегда актуальное число 0-100000
    }

    public BlockPos getPos() { return blockEntity.getBlockPos(); }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, blockEntity.getBlockState().getBlock());
    }

    // Стандартные методы для инвентаря (quickMoveStack и т.д.) ...
    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 98));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 156));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}