package com.cim.block.entity.industrial.casting;

import com.cim.api.metallurgy.system.ISmelter;
import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.api.metallurgy.system.recipe.SmeltRecipe;
import com.cim.block.entity.ModBlockEntities;
import com.cim.event.HotItemHandler;
import com.cim.event.SlagItem;
import com.cim.item.ModItems;
import com.cim.menu.SmallSmelterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
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
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.*;

public class SmallSmelterBlockEntity extends BlockEntity implements MenuProvider, ISmelter {
    // ========== КОНСТАНТЫ ==========
    public static final int MAX_TEMP = 1100;
    public static final int CAPACITY_UNITS = 81;   // 1 блок металла
    private static final int SLOT_FUEL = 0;
    private static final int SLOT_ASH = 1;
    private static final int SLOT_SMELT = 2;
    private static final int INVENTORY_SIZE = 3;

    // Параметры нагрева (из HeaterBlockEntity)
    private static final float[][] TIER_STATS = {
            {1f, 125},   // тир 0
            {2f, 250},   // тир 1
            {3f, 500},   // тир 2
            {4f, 800},   // тир 3
            {6f, 1200},  // тир 4
            {8f, 2400}   // тир 5
    };
    private static final int[] ASH_CHANCES = {0, 0, 40, 60, 80, 100};

    // ========== ПОЛЯ ==========
    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_FUEL) return isFuel(stack);
            if (slot == SLOT_ASH) return false; // только извлечение
            if (slot == SLOT_SMELT) return MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null || stack.getItem() instanceof SlagItem;
            return false;
        }
    };

    private float temperature = 0f;
    private int burnTime = 0;
    private int totalBurnTime = 0;
    private int fuelTier = 0;

    // Плавка
    private SmeltRecipe currentRecipe = null;
    private float smeltProgress = 0f;
    private float smeltMaxProgress = 0f;
    private float smeltHeatPerTick = 0f;
    private int requiredTemp = 0;
    private boolean isSmelting = false;

    // Шлак
    private boolean isSlagRecipe = false;
    private Metal slagMetal = null;
    private int slagAmount = 0;

    // Резервуар металла
    private final Map<Metal, Integer> metalTank = new LinkedHashMap<>();
    private int totalMetalAmount = 0;

    // Данные для GUI
    private final ContainerData data = new SimpleContainerData(8) {
        @Override
        public void set(int index, int value) {
            super.set(index, value);
        }
    };
    public static final int DATA_TEMP = 0;
    public static final int DATA_BURN_TIME = 1;
    public static final int DATA_TOTAL_BURN_TIME = 2;
    public static final int DATA_IS_BURNING = 3;
    public static final int DATA_SMELT_PROGRESS = 4;
    public static final int DATA_SMELT_MAX_PROGRESS = 5;
    public static final int DATA_IS_SMELTING = 6;
    public static final int DATA_HAS_RECIPE = 7;

    public SmallSmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_SMELTER_BE.get(), pos, state);
    }

    // ========== ТИКИ ==========
    public static void serverTick(Level level, BlockPos pos, BlockState state, SmallSmelterBlockEntity be) {
        boolean changed = false;

        // 1. Охлаждение (как в Heater)
        float baseCooling = (be.temperature * be.temperature) / 512000f;
        if (baseCooling < 0.05f && be.temperature > 0) baseCooling = 0.05f;
        float thermalNoise = (be.temperature > 200 && baseCooling > 0.5f) ? (level.random.nextFloat() * 0.4f - 0.2f) : 0f;
        float cooling = Math.max(0.05f, baseCooling + thermalNoise);
        if (be.temperature > 0) {
            be.temperature = Math.max(0, be.temperature - cooling);
            changed = true;
        }

        // 2. Горение топлива (нагрев)
        if (be.burnTime > 0) {
            be.burnTime--;
            float heatPerTick = TIER_STATS[be.fuelTier][0];
            be.temperature = Math.min(MAX_TEMP, be.temperature + heatPerTick);
            changed = true;

            if (be.burnTime == 0 && be.fuelTier >= 2) {
                int chance = ASH_CHANCES[be.fuelTier];
                if (level.random.nextInt(100) < chance) {
                    ItemStack ash = new ItemStack(ModItems.FUEL_ASH.get(), 1);
                    ItemStack remaining = be.inventory.insertItem(SLOT_ASH, ash, false);
                    if (!remaining.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remaining);
                    }
                }
            }
        } else {
            // Взять новое топливо
            ItemStack fuel = be.inventory.getStackInSlot(SLOT_FUEL);
            if (!fuel.isEmpty()) {
                int tier = be.getFuelTier(fuel);
                if (tier >= 0) {
                    be.fuelTier = tier;
                    be.burnTime = (int) TIER_STATS[tier][1];
                    be.totalBurnTime = be.burnTime;

                    ItemStack remainder = fuel.getCraftingRemainingItem();
                    fuel.shrink(1);
                    if (fuel.isEmpty() && !remainder.isEmpty()) {
                        be.inventory.setStackInSlot(SLOT_FUEL, remainder);
                    } else if (!remainder.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
                    }
                    changed = true;
                }
            }
        }

        // 3. Плавка
        ItemStack smeltItem = be.inventory.getStackInSlot(SLOT_SMELT);
        if (!smeltItem.isEmpty()) {
            SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(smeltItem.getItem());
            boolean isSlag = smeltItem.getItem() instanceof SlagItem;

            if (recipe != null || isSlag) {
                if (recipe != null) {
                    // Обычный рецепт
                    if (be.currentRecipe != recipe) {
                        be.resetSmelting();
                        be.currentRecipe = recipe;
                        be.requiredTemp = recipe.minTemp();
                        be.smeltMaxProgress = recipe.getTotalHeatConsumption();
                        be.smeltHeatPerTick = recipe.heatConsumption();
                    }
                } else {
                    // Шлак
                    Metal slagMetal = SlagItem.getMetal(smeltItem);
                    int slagAmount = SlagItem.getAmount(smeltItem);
                    if (slagMetal != null && slagAmount > 0) {
                        if (be.currentRecipe != null || be.slagMetal != slagMetal || be.slagAmount != slagAmount) {
                            be.resetSmelting();
                            be.isSlagRecipe = true;
                            be.slagMetal = slagMetal;
                            be.slagAmount = slagAmount;
                            be.requiredTemp = slagMetal.getMeltingPoint();
                            be.smeltMaxProgress = slagMetal.getHeatConsumptionPerTick() * slagMetal.calculateSmeltTimeForUnits(slagAmount);
                            be.smeltHeatPerTick = slagMetal.getHeatConsumptionPerTick();
                        }
                    } else {
                        if (be.currentRecipe != null || be.isSlagRecipe) be.resetSmelting();
                    }
                }

                // Проверяем, что у нас есть активный рецепт или шлак
                if (be.currentRecipe == null && !be.isSlagRecipe) {
                    be.isSmelting = false;
                } else {
                    // Нагрев предмета до температуры плавления
                    float itemTemp = be.getItemTemperature(smeltItem);
                    int targetTemp = be.requiredTemp;
                    if (itemTemp < targetTemp - 1.0f) {
                        // нагрев
                        if (be.temperature > itemTemp) {
                            float heatNeeded = (targetTemp - 1.0f) - itemTemp;
                            float heatTransfer = Math.min(5f, heatNeeded);
                            heatTransfer = Math.min(heatTransfer, be.temperature * 0.1f);
                            float newTemp = itemTemp + heatTransfer;
                            be.setItemTemperature(smeltItem, newTemp);
                            be.temperature -= heatTransfer * 0.5f;
                            changed = true;
                        }
                        be.isSmelting = false;
                    } else {
                        // достаточно нагрето – начинаем плавку
                        int outputUnits = be.isSlagRecipe ? be.slagAmount : be.currentRecipe.outputUnits();
                        if (be.hasSpaceFor(outputUnits) && be.temperature >= be.requiredTemp * 0.9f) {
                            be.isSmelting = true;
                            // Применяем тепло к прогрессу
                            float availableHeat = Math.min(be.smeltHeatPerTick, be.temperature);
                            float heatToApply = Math.min(availableHeat, be.smeltMaxProgress - be.smeltProgress);
                            be.smeltProgress += heatToApply;
                            be.temperature -= heatToApply;
                            changed = true;

                            if (be.smeltProgress >= be.smeltMaxProgress * 0.999f) {
                                if (be.isSlagRecipe) {
                                    be.addMetal(be.slagMetal, be.slagAmount);
                                } else {
                                    be.completeSmelting(be.currentRecipe);
                                }
                                be.resetSmelting();
                                smeltItem.shrink(1);
                                if (smeltItem.isEmpty()) {
                                    be.inventory.setStackInSlot(SLOT_SMELT, ItemStack.EMPTY);
                                }
                                changed = true;
                            }
                        } else {
                            be.isSmelting = false;
                        }
                    }
                }
            } else {
                // Если предмет не плавится, сбрасываем прогресс
                if (be.currentRecipe != null || be.isSlagRecipe) be.resetSmelting();
            }
        } else {
            if (be.currentRecipe != null || be.isSlagRecipe) be.resetSmelting();
        }

        // 4. Обновление данных для GUI
        be.data.set(DATA_TEMP, (int)(be.temperature * 10f));
        be.data.set(DATA_BURN_TIME, be.burnTime);
        be.data.set(DATA_TOTAL_BURN_TIME, be.totalBurnTime);
        be.data.set(DATA_IS_BURNING, be.burnTime > 0 ? 1 : 0);
        be.data.set(DATA_SMELT_PROGRESS, (int)be.smeltProgress);
        be.data.set(DATA_SMELT_MAX_PROGRESS, (int)be.smeltMaxProgress);
        be.data.set(DATA_IS_SMELTING, be.isSmelting ? 1 : 0);
        be.data.set(DATA_HAS_RECIPE, (be.currentRecipe != null || be.isSlagRecipe) ? 1 : 0);

        if (changed) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void resetSmelting() {
        currentRecipe = null;
        smeltProgress = 0;
        smeltMaxProgress = 0;
        smeltHeatPerTick = 0;
        requiredTemp = 0;
        isSmelting = false;
        isSlagRecipe = false;
        slagMetal = null;
        slagAmount = 0;
    }

    private void completeSmelting(SmeltRecipe recipe) {
        addMetal(recipe.output(), recipe.outputUnits());
    }

    // ========== УПРАВЛЕНИЕ МЕТАЛЛОМ (интерфейс ISmelter) ==========
    @Override
    public int extractMetal(Metal metal, int maxUnits) {
        Integer current = metalTank.get(metal);
        if (current == null || current <= 0) return 0;
        int toExtract = Math.min(maxUnits, current);
        if (toExtract <= 0) return 0;
        if (current <= toExtract) {
            metalTank.remove(metal);
        } else {
            metalTank.put(metal, current - toExtract);
        }
        recalculateTotal();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
        return toExtract;
    }

    @Override
    public Metal getMetalForCasting(List<Metal> preferredMetals) {
        if (metalTank.isEmpty()) return null;
        if (preferredMetals != null && !preferredMetals.isEmpty()) {
            for (Metal preferred : preferredMetals) {
                if (metalTank.containsKey(preferred) && metalTank.get(preferred) > 0) {
                    return preferred;
                }
            }
        }
        return metalTank.keySet().iterator().next();
    }

    @Override
    public boolean hasMetal() {
        return totalMetalAmount > 0;
    }

    @Override
    public int getTotalMetalAmount() {
        return totalMetalAmount;
    }

    @Override
    public int getSmelterCapacity() {
        return CAPACITY_UNITS;
    }

    @Override
    public BlockEntity asBlockEntity() {
        return this;
    }

    public List<ItemStack> dumpMetalAsSlag() {
        List<ItemStack> slagItems = new ArrayList<>();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) {
                ItemStack slag = SlagItem.createSlag(metal, amount);
                if (!slag.getTag().contains("HotTime")) {
                    slag.getOrCreateTag().putInt("HotTime", SlagItem.BASE_COOLING_TIME);
                    slag.getOrCreateTag().putInt("HotTimeMax", SlagItem.BASE_COOLING_TIME);
                }
                slagItems.add(slag);
            }
        });
        metalTank.clear();
        totalMetalAmount = 0;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
        return slagItems;
    }

    private void addMetal(Metal metal, int units) {
        if (units <= 0) return;
        if (totalMetalAmount + units > CAPACITY_UNITS) {
            units = CAPACITY_UNITS - totalMetalAmount;
            if (units <= 0) return;
        }
        metalTank.merge(metal, units, Integer::sum);
        recalculateTotal();
    }

    private void recalculateTotal() {
        totalMetalAmount = metalTank.values().stream().mapToInt(Integer::intValue).sum();
        if (totalMetalAmount > CAPACITY_UNITS) {
            // Срезаем лишнее (на всякий случай)
            int excess = totalMetalAmount - CAPACITY_UNITS;
            for (var entry : metalTank.entrySet()) {
                if (entry.getValue() >= excess) {
                    entry.setValue(entry.getValue() - excess);
                    break;
                }
            }
            recalculateTotal();
        }
    }

    public boolean hasSpaceFor(int units) {
        return totalMetalAmount + units <= CAPACITY_UNITS;
    }

    // ========== ТОПЛИВО (копия из HeaterBlockEntity) ==========
    public boolean isFuel(ItemStack stack) {
        return getFuelTier(stack) >= 0;
    }

    public int getFuelTier(ItemStack stack) {
        Item item = stack.getItem();
        // Тир 0: деревяшки
        if (item == Items.STICK) return 0;
        if (item == Items.SCAFFOLDING) return 0;
        if (item == Items.OAK_PLANKS || item == Items.SPRUCE_PLANKS || item == Items.BIRCH_PLANKS ||
                item == Items.JUNGLE_PLANKS || item == Items.ACACIA_PLANKS || item == Items.DARK_OAK_PLANKS ||
                item == Items.MANGROVE_PLANKS || item == Items.CHERRY_PLANKS || item == Items.BAMBOO_PLANKS ||
                item == Items.BAMBOO_MOSAIC) return 0;
        if (item == Items.OAK_SLAB || item == Items.SPRUCE_SLAB || item == Items.BIRCH_SLAB ||
                item == Items.JUNGLE_SLAB || item == Items.ACACIA_SLAB || item == Items.DARK_OAK_SLAB ||
                item == Items.MANGROVE_SLAB || item == Items.CHERRY_SLAB || item == Items.BAMBOO_SLAB ||
                item == Items.BAMBOO_MOSAIC_SLAB) return 0;
        if (item == Items.OAK_LOG || item == Items.SPRUCE_LOG || item == Items.BIRCH_LOG ||
                item == Items.JUNGLE_LOG || item == Items.ACACIA_LOG || item == Items.DARK_OAK_LOG ||
                item == Items.MANGROVE_LOG || item == Items.CHERRY_LOG || item == Items.BAMBOO_BLOCK) return 0;
        if (item == Items.CHARCOAL) return 1;
        if (item == Items.COAL) return 1;
        if (item == Items.BLAZE_POWDER) return 1;
        if (item == Items.BLAZE_ROD) return 2;
        if (item == Items.MAGMA_CREAM) return 2;
        if (item == Items.PORKCHOP) return 2;
        if (item == Items.COAL_BLOCK) return 3;
        if (item == Items.LAVA_BUCKET) return 4;
        if (item == ModItems.MORY_LAH.get() || item == Items.DRAGON_BREATH) return 5;
        return -1;
    }

    // ========== РАБОТА С ТЕМПЕРАТУРОЙ ПРЕДМЕТОВ (HotItemHandler) ==========
    private float getItemTemperature(ItemStack stack) {
        if (HotItemHandler.isHot(stack)) {
            return HotItemHandler.getTemperature(stack);
        }
        return HotItemHandler.ROOM_TEMP;
    }

    private int getMeltingPointForItem(ItemStack stack) {
        if (stack.getItem() instanceof SlagItem) {
            return SlagItem.getMeltingPoint(stack);
        }
        SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
        if (recipe != null) return recipe.minTemp();
        return 1000; // запасное значение
    }

    private void setItemTemperature(ItemStack stack, float temp) {
        if (temp <= HotItemHandler.ROOM_TEMP) {
            if (HotItemHandler.isHot(stack)) {
                HotItemHandler.clearHotTags(stack);
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
            return;
        }
        int meltingPoint = getMeltingPointForItem(stack);
        if (meltingPoint <= 0) meltingPoint = 1000;
        int maxTime = HotItemHandler.BASE_COOLING_TIME_HANDS;
        float heatRatio = (temp - HotItemHandler.ROOM_TEMP) / (meltingPoint - HotItemHandler.ROOM_TEMP);
        heatRatio = Math.max(0, Math.min(1.2f, heatRatio));
        float hotTime = heatRatio * maxTime;
        stack.getOrCreateTag().putFloat("HotTime", hotTime);
        stack.getOrCreateTag().putInt("HotTimeMax", maxTime);
        stack.getOrCreateTag().putInt("MeltingPoint", meltingPoint);
        stack.getOrCreateTag().putBoolean("CooledInPot", false);
        if (level != null && !level.isClientSide && (int)hotTime % 10 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ========== ГЕТТЕРЫ ==========
    public ItemStackHandler getInventory() { return inventory; }
    public ContainerData getData() { return data; }
    public float getTemperature() { return temperature; }
    public int getBurnTime() { return burnTime; }
    public int getTotalBurnTime() { return totalBurnTime; }
    public boolean isBurning() { return burnTime > 0; }
    public SmeltRecipe getCurrentRecipe() { return currentRecipe; }
    public float getSmeltProgress() { return smeltProgress; }
    public float getSmeltMaxProgress() { return smeltMaxProgress; }
    public boolean isSmelting() { return isSmelting; }
    public int getRequiredTemp() { return requiredTemp; }
    public boolean isSlagRecipe() { return isSlagRecipe; }
    public float getSmeltHeatPerTick() { return smeltHeatPerTick; }
    public Map<Metal, Integer> getMetalTank() { return Collections.unmodifiableMap(metalTank); }
    public List<MetalStack> getMetalStacks() {
        List<MetalStack> list = new ArrayList<>();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) list.add(new MetalStack(metal, amount));
        });
        return list;
    }

    public static class MetalStack {
        public final Metal metal;
        public final int amount;
        public MetalStack(Metal metal, int amount) { this.metal = metal; this.amount = amount; }
        public String getFormattedAmount() {
            return com.cim.api.metallurgy.system.MetalUnits2.convertFromUnits(amount).totalUnits() + " ед.";
        }
    }

    // ========== NBT ==========
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putFloat("Temperature", temperature);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("TotalBurnTime", totalBurnTime);
        tag.putInt("FuelTier", fuelTier);
        tag.putFloat("SmeltProgress", smeltProgress);
        tag.putFloat("SmeltMaxProgress", smeltMaxProgress);
        if (currentRecipe != null) {
            tag.putString("CurrentRecipeItem", currentRecipe.input().toString());
        }
        tag.putInt("RequiredTemp", requiredTemp);
        tag.putBoolean("IsSmelting", isSmelting);

        // Шлак
        tag.putBoolean("IsSlagRecipe", isSlagRecipe);
        if (slagMetal != null) {
            tag.putString("SlagMetal", slagMetal.getId().toString());
            tag.putInt("SlagAmount", slagAmount);
        }

        ListTag metals = new ListTag();
        metalTank.forEach((metal, amount) -> {
            CompoundTag mt = new CompoundTag();
            mt.putString("Metal", metal.getId().toString());
            mt.putInt("Amount", amount);
            metals.add(mt);
        });
        tag.put("Metals", metals);
        tag.putInt("TotalMetal", totalMetalAmount);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        temperature = tag.getFloat("Temperature");
        burnTime = tag.getInt("BurnTime");
        totalBurnTime = tag.getInt("TotalBurnTime");
        fuelTier = tag.getInt("FuelTier");
        smeltProgress = tag.getFloat("SmeltProgress");
        smeltMaxProgress = tag.getFloat("SmeltMaxProgress");
        if (tag.contains("CurrentRecipeItem")) {
            ResourceLocation itemId = new ResourceLocation(tag.getString("CurrentRecipeItem"));
            Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null) currentRecipe = MetallurgyRegistry.getSmeltRecipe(item);
        }
        requiredTemp = tag.getInt("RequiredTemp");
        isSmelting = tag.getBoolean("IsSmelting");

        // Шлак
        isSlagRecipe = tag.getBoolean("IsSlagRecipe");
        if (tag.contains("SlagMetal")) {
            ResourceLocation slagId = new ResourceLocation(tag.getString("SlagMetal"));
            MetallurgyRegistry.get(slagId).ifPresent(m -> slagMetal = m);
            slagAmount = tag.getInt("SlagAmount");
        }

        metalTank.clear();
        ListTag metals = tag.getList("Metals", Tag.TAG_COMPOUND);
        for (int i = 0; i < metals.size(); i++) {
            CompoundTag mt = metals.getCompound(i);
            ResourceLocation id = new ResourceLocation(mt.getString("Metal"));
            int amt = mt.getInt("Amount");
            MetallurgyRegistry.get(id).ifPresent(metal -> metalTank.put(metal, amt));
        }
        recalculateTotal();

        // Обновим data
        data.set(DATA_TEMP, (int)(temperature * 10f));
        data.set(DATA_BURN_TIME, burnTime);
        data.set(DATA_TOTAL_BURN_TIME, totalBurnTime);
        data.set(DATA_IS_BURNING, burnTime > 0 ? 1 : 0);
        data.set(DATA_SMELT_PROGRESS, (int)smeltProgress);
        data.set(DATA_SMELT_MAX_PROGRESS, (int)smeltMaxProgress);
        data.set(DATA_IS_SMELTING, isSmelting ? 1 : 0);
        data.set(DATA_HAS_RECIPE, (currentRecipe != null || isSlagRecipe) ? 1 : 0);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cim.small_smelter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SmallSmelterMenu(id, inv, this, data);
    }
}