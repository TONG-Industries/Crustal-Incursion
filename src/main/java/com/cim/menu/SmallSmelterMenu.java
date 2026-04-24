package com.cim.menu;

import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.industrial.casting.SmallSmelterBlockEntity;
import com.cim.event.SlagItem;
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

public class SmallSmelterMenu extends AbstractContainerMenu {
    private final SmallSmelterBlockEntity blockEntity;
    private final ContainerData data;
    private final ContainerLevelAccess levelAccess;

    public SmallSmelterMenu(int id, Inventory inv, SmallSmelterBlockEntity entity, ContainerData data) {
        super(ModMenuTypes.SMALL_SMELTER_MENU.get(), id);
        this.blockEntity = entity;
        this.data = data;
        this.levelAccess = ContainerLevelAccess.create(entity.getLevel(), entity.getBlockPos());

        // Слот топлива (61,12)
        this.addSlot(new SlotItemHandler(entity.getInventory(), 0, 61, 12) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return entity.isFuel(stack);
            }
        });
        // Слот золы (61,40) - только извлечение
        this.addSlot(new SlotItemHandler(entity.getInventory(), 1, 61, 40) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        // Слот плавки (121,13) — рецепт или шлак
        this.addSlot(new SlotItemHandler(entity.getInventory(), 2, 121, 13) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return com.cim.api.metallurgy.system.MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null
                        || stack.getItem() instanceof SlagItem;
            }
        });

        // Инвентарь игрока (8,86) - 3 строки
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 86 + row * 18));
            }
        }
        // Хотбар (8,144)
        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(inv, i, 8 + i * 18, 144));
        }

        this.addDataSlots(data);
    }

    public static SmallSmelterMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        BlockEntity entity = inv.player.level().getBlockEntity(pos);
        if (entity instanceof SmallSmelterBlockEntity smelter) {
            return new SmallSmelterMenu(id, inv, smelter, smelter.getData());
        }
        return new SmallSmelterMenu(id, inv, (SmallSmelterBlockEntity) entity, new SimpleContainerData(8));
    }

    public int getTemperature() { return data.get(SmallSmelterBlockEntity.DATA_TEMP); }
    public int getBurnTime() { return data.get(SmallSmelterBlockEntity.DATA_BURN_TIME); }
    public int getTotalBurnTime() { return data.get(SmallSmelterBlockEntity.DATA_TOTAL_BURN_TIME); }
    public boolean isBurning() { return data.get(SmallSmelterBlockEntity.DATA_IS_BURNING) > 0; }
    public int getSmeltProgress() { return data.get(SmallSmelterBlockEntity.DATA_SMELT_PROGRESS); }
    public int getSmeltMaxProgress() { return data.get(SmallSmelterBlockEntity.DATA_SMELT_MAX_PROGRESS); }
    public boolean isSmelting() { return data.get(SmallSmelterBlockEntity.DATA_IS_SMELTING) > 0; }
    public boolean hasRecipe() { return data.get(SmallSmelterBlockEntity.DATA_HAS_RECIPE) > 0; }
    public SmallSmelterBlockEntity getBlockEntity() { return blockEntity; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(levelAccess, player, ModBlocks.SMALL_SMELTER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack returnStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            returnStack = stack.copy();

            // Слоты: 0-топливо,1-зола,2-плавка, 3-38 инвентарь игрока
            if (index < 3) {
                // Из печи в инвентарь
                if (!this.moveItemStackTo(stack, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Из инвентаря в печь
                if (blockEntity.isFuel(stack)) {
                    if (!this.moveItemStackTo(stack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (com.cim.api.metallurgy.system.MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null
                        || stack.getItem() instanceof SlagItem) {
                    if (!this.moveItemStackTo(stack, 2, 3, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return returnStack;
    }
}