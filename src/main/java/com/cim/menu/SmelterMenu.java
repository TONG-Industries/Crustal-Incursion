package com.cim.menu;


import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.block.basic.ModBlocks;
import com.cim.event.HotItemHandler;
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

        // Верхний ряд (слоты 0-3) – сплавы
        for (int i = 0; i < 4; i++) {
            this.addSlot(new SlotItemHandler(entity.getInventory(), i, 95 + i * 18, 13) {
                @Override
                public boolean mayPlace(ItemStack stack) { return true; }
            });
        }

        // Нижний ряд (слоты 4-7) – обычная плавка + шлак
        for (int i = 0; i < 4; i++) {
            this.addSlot(new SlotItemHandler(entity.getInventory(), 4 + i, 95 + i * 18, 45) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // Шлак или рецепт плавки
                    if (stack.getItem() instanceof com.cim.event.SlagItem) return true;
                    return MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null;
                }
            });
        }

        // Инвентарь игрока
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, 160));
        }

        this.addDataSlots(data);
    }

    public static SmelterMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity entity = inv.player.level().getBlockEntity(pos);
        SimpleContainerData data = new SimpleContainerData(13);
        return new SmelterMenu(id, inv, (SmelterBlockEntity) entity, data);
    }

    public int getTemperature() { return data.get(0); }
    public int getTopProgress() { return data.get(1); }
    public int getTopMaxProgress() { return data.get(2); }
    public boolean isTopSmelting() { return data.get(3) > 0; }
    public int getRequiredTempTop() { return data.get(4); }
    public int getBottomProgress() { return data.get(5); }
    public int getBottomMaxProgress() { return data.get(6); }
    public boolean isBottomSmelting() { return data.get(7) > 0; }
    public int getRequiredTempBottom() { return data.get(8); }
    public boolean hasTopRecipe() { return data.get(9) > 0; }
    public boolean hasBottomRecipe() { return data.get(10) > 0; }
    public int getTopHeatPerTick() { return data.get(11); }
    public int getBottomHeatPerTick() { return data.get(12); }
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

            // Если предмет горячий - данные уже в NBT, просто логируем если нужно
            // НЕТ else здесь! Логика перемещения должна работать всегда.

            if (index < 8) {
                // Из печи в инвентарь
                if (!this.moveItemStackTo(stack, 8, 44, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Из инвентаря в печь
                boolean isSmeltable = MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null;
                boolean isSlag = stack.getItem() instanceof com.cim.event.SlagItem;

                if (isSmeltable || isSlag) {
                    // Сначала пытаемся в нижний ряд (4-7)
                    if (!this.moveItemStackTo(stack, 4, 8, false)) {
                        // Если не получилось – в верхний ряд (0-4)
                        if (!this.moveItemStackTo(stack, 0, 4, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                } else {
                    // Не плавильный предмет – только в верхний ряд
                    if (!this.moveItemStackTo(stack, 0, 4, false)) {
                        return ItemStack.EMPTY;
                    }
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