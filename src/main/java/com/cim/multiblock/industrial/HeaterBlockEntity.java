package com.cim.multiblock.industrial;

import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.ModBlockEntities;
import com.cim.item.ModItems;
import com.cim.menu.HeaterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nullable;

public class HeaterBlockEntity extends BlockEntity implements MenuProvider {
    // Инвентарь с синхронизацией
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // === ФИКС 1: Синхронизация инвентаря с клиентом ===
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0) return isFuel(stack);
            return false;
        }
    };

    // Шансы выпадения золы по тирам (в процентах)
    private static final int[] ASH_CHANCES = {
            0,    // Тир 0: 0%
            0,    // Тир 1: 0%
            40,   // Тир 2: 40%
            60,   // Тир 3: 60%
            80,   // Тир 4: 80%
            100   // Тир 5: 100%
    };

    // Данные для GUI
    private final SimpleContainerData data = new SimpleContainerData(4);
    public static final int DATA_TEMP = 0;
    public static final int DATA_BURN_TIME = 1;
    public static final int DATA_TOTAL_BURN_TIME = 2;
    public static final int DATA_IS_BURNING = 3;

    public static final int MAX_TEMP = 1600;

    // {heatPerTick, burnTicks}
    private static final int[][] TIER_STATS = {
            {1, 100},   // 0: 1°C/тик (было 5), 5 сек
            {2, 150},   // 1: 2°C/тик (было 8), 7.5 сек
            {3, 400},   // 2: 3°C/тик (было 12), 20 сек, +зола
            {4, 600},   // 3: 4°C/тик (было 20), 30 сек, +зола
            {6, 800},   // 4: 6°C/тик (было 30), 40 сек, +зола
            {10, 1200}  // 5: 10°C/тик (было 50), 60 сек, +зола
    };

    private int temperature = 0;
    private int burnTime = 0;
    private int totalBurnTime = 0;
    private int fuelTier = 0;

    public HeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEATER_BE.get(), pos, state);
    }


    public static void serverTick(Level level, BlockPos pos, BlockState state, HeaterBlockEntity be) {
        boolean changed = false;

        // === ОХЛАЖДЕНИЕ С ЕСТЕСТВЕННЫМИ КОЛЕБАНИЯМИ ===
        int baseCooling = (be.temperature * be.temperature) / 512000;

        // === ФИКС 3: Минимальное охлажение чтобы температура падала до 0 ===
        if (baseCooling == 0 && be.temperature > 0) {
            baseCooling = 1;
        }

        // Термический шум только при высоких температурах
        int thermalNoise = 0;
        if (be.temperature > 200 && baseCooling > 1) {
            thermalNoise = level.random.nextInt(5) - 2;
        }

        int cooling = Math.max(1, baseCooling + thermalNoise);

        if (be.temperature > 0) {
            be.temperature = Math.max(0, be.temperature - cooling);
            changed = true; // Всегда отмечаем изменение при охлаждении
        }

        // === НАГРЕВ ===
        if (be.burnTime > 0) {
            be.burnTime--;
            int heatPerTick = TIER_STATS[be.fuelTier][0];
            be.temperature = Math.min(MAX_TEMP, be.temperature + heatPerTick);
            changed = true;

            // === ФИКС 2: Зола выпадает по шансу ===
            if (be.burnTime == 0 && be.fuelTier >= 2) {
                int chance = ASH_CHANCES[be.fuelTier];
                if (level.random.nextInt(100) < chance) {
                    ItemStack ash = new ItemStack(ModItems.FUEL_ASH.get(), 1);
                    ItemStack remaining = be.inventory.insertItem(1, ash, false);
                    if (!remaining.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remaining);
                    }
                }
            }
        } else {
            // Пытаемся взять новое топливо
            ItemStack fuel = be.inventory.getStackInSlot(0);
            if (!fuel.isEmpty()) {
                int tier = be.getFuelTier(fuel);
                if (tier >= 0) {
                    be.fuelTier = tier;
                    be.burnTime = TIER_STATS[tier][1];
                    be.totalBurnTime = be.burnTime;

                    // !!! НОВОЕ: Обработка остатка (ведро, бутылка и т.д.) !!!
                    ItemStack remainder = fuel.getCraftingRemainingItem();

                    fuel.shrink(1);

                    // Если слот опустел и есть остаток - кладем его на место топлива
                    if (fuel.isEmpty() && !remainder.isEmpty()) {
                        be.inventory.setStackInSlot(0, remainder);
                    } else if (!remainder.isEmpty()) {
                        // Если в слоте еще что-то есть (стаки?), выбрасываем остаток
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
                    }

                    changed = true;
                }
            }
        }

        // Синхронизация данных
        be.data.set(DATA_TEMP, be.temperature);
        be.data.set(DATA_BURN_TIME, be.burnTime);
        be.data.set(DATA_TOTAL_BURN_TIME, be.totalBurnTime);
        be.data.set(DATA_IS_BURNING, be.burnTime > 0 ? 1 : 0);

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }


    public boolean isFuel(ItemStack stack) {
        return getFuelTier(stack) >= 0;
    }

    public int getFuelTier(ItemStack stack) {
        Item item = stack.getItem();

        // Тир 0: дешевое топливо
        if (item == Items.STICK ||
                item == Items.OAK_PLANKS || item == Items.SPRUCE_PLANKS ||
                item == Items.BIRCH_PLANKS || item == Items.JUNGLE_PLANKS ||
                item == Items.ACACIA_PLANKS || item == Items.DARK_OAK_PLANKS ||
                item == Items.MANGROVE_PLANKS || item == Items.CHERRY_PLANKS ||
                item == Items.BAMBOO_PLANKS || item == Items.OAK_SLAB ||
                item == Item.byBlock(Blocks.OAK_LOG)) return 0;

        // Тир 1: обычное топливо
        if (item == Items.COAL || item == Items.CHARCOAL) return 1;

        // Тир 2: блазе род
        if (item == Items.BLAZE_ROD|| item == Items.PORKCHOP) return 2;

        // Тир 3: угольный блок
        if (item == Item.byBlock(Blocks.COAL_BLOCK)) return 3;

        // Тир 4: лава
        if (item == Items.LAVA_BUCKET) return 4;

        // Тир 5: специальное (можно добавить кастомные предметы)
         if (item == ModItems.MORY_LAH.get()) return 5;

        return -1;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getData() {
        return data;
    }

    public int getTemperature() { return temperature; }
    public int getBurnTime() { return burnTime; }
    public int getTotalBurnTime() { return totalBurnTime; }
    public boolean isBurning() { return burnTime > 0; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Temperature", temperature);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("TotalBurnTime", totalBurnTime);
        tag.putInt("FuelTier", fuelTier);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        temperature = tag.getInt("Temperature");
        burnTime = tag.getInt("BurnTime");
        totalBurnTime = tag.getInt("TotalBurnTime");
        fuelTier = tag.getInt("FuelTier");

        // Восстанавливаем данные для GUI
        data.set(DATA_TEMP, temperature);
        data.set(DATA_BURN_TIME, burnTime);
        data.set(DATA_TOTAL_BURN_TIME, totalBurnTime);
        data.set(DATA_IS_BURNING, burnTime > 0 ? 1 : 0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cim.heater");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new HeaterMenu(id, inv, this, data);
    }

    // === СИНХРОНИЗАЦИЯ ДЛЯ HUD (критически важно!) ===
    // === СИНХРОНИЗАЦИЯ ДЛЯ HUD (критически важно!) ===
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        // !!! ДОБАВИТЬ ЭТО: Сериализация инвентаря для клиента !!!
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Temperature", temperature);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("TotalBurnTime", totalBurnTime);
        tag.putInt("FuelTier", fuelTier);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        // !!! ДОБАВИТЬ ЭТО: Десериализация инвентаря на клиенте !!!
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(tag.getCompound("Inventory"));
        }
        if (tag.contains("Temperature")) {
            temperature = tag.getInt("Temperature");
            burnTime = tag.getInt("BurnTime");
            totalBurnTime = tag.getInt("TotalBurnTime");
            fuelTier = tag.getInt("FuelTier");

            // Обновляем данные для GUI/HUD
            data.set(DATA_TEMP, temperature);
            data.set(DATA_BURN_TIME, burnTime);
            data.set(DATA_TOTAL_BURN_TIME, totalBurnTime);
            data.set(DATA_IS_BURNING, burnTime > 0 ? 1 : 0);
        }
    }

    // Для немедленной синхронизации когда игрок смотрит на блок
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
    }


}