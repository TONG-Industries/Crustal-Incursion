package com.cim.multiblock.industrial;

import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.api.metallurgy.system.recipe.AlloyRecipe;
import com.cim.api.metallurgy.system.recipe.AlloySlot;
import com.cim.api.metallurgy.system.recipe.SmeltRecipe;
import com.cim.block.entity.ModBlockEntities;
import com.cim.event.SlagItem;
import com.cim.item.ModItems;
import com.cim.menu.SmelterMenu;
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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

public class SmelterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_TEMP = 1600;
    public static final int BLOCK_CAPACITY = 4;
    public static final int TANK_CAPACITY = BLOCK_CAPACITY * MetalUnits2.UNITS_PER_BLOCK; // 324

    private final ItemStackHandler inventory = new ItemStackHandler(8) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 4) return true;
            // Нижний ряд - шлак ИЛИ рецепт плавки
            if (stack.getItem() instanceof SlagItem) return true;
            return MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null;
        }
    };

    private static class SlagSlotData {
        Metal metal;
        int amount;
        int requiredTemp;
        float heatConsumption; // Потребление шлака (берётся из металла)
    }

    private final Map<Metal, Integer> metalTank = new LinkedHashMap<>();
    private int totalMetalAmount = 0;
    private float temperature = 0; // Теперь float для точности!

    // Разделенное отслеживание изменений по рядам
    private int lastTopHash = 0;
    private int lastBottomHash = 0;

    // Верхний ряд (сплавы)
    private float topProgress = 0; // Теперь float - накопленное тепло!
    private float topMaxProgress = 0; // Требуемое тепло
    private boolean topSmelting = false;
    private float topHeatConsumption = 0; // Потребление за тик
    private AlloyRecipe currentAlloyRecipe = null;
    private int requiredTempTop = 0;

    // Нижний ряд (обычная плавка)
    private float bottomProgress = 0; // Накопленное тепло
    private float bottomMaxProgress = 0; // Требуемое тепло
    private boolean bottomSmelting = false;
    private float bottomHeatConsumption = 0; // Среднее потребление активных слотов
    private Map<SmeltRecipe, Float> currentBottomRecipes = new HashMap<>(); // recipe -> progress
    private int requiredTempBottom = 0;

    private final ContainerData data = new SimpleContainerData(14) {
        @Override
        public void set(int index, int value) {
            super.set(index, value);
        }
    };

    private static class BottomSlotData {
        SmeltRecipe recipe;
        SlagSlotData slagData;
        float progress; // Накопленное тепло
        float maxProgress; // Требуемое тепло
        float heatConsumption; // Потребление за тик
        boolean active;
    }

    private final BottomSlotData[] bottomSlots = new BottomSlotData[4];
    private final ItemStack[] previousBottomStacks = new ItemStack[4];

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER_BE.get(), pos, state);
        for (int i = 0; i < 4; i++) {
            previousBottomStacks[i] = ItemStack.EMPTY;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmelterBlockEntity be) {
        // Раздельное отслеживание изменений
        int currentTopHash = be.calculateTopHash();
        int currentBottomHash = be.calculateBottomHash();

        if (currentTopHash != be.lastTopHash) {
            be.lastTopHash = currentTopHash;
            be.resetTopSmelting();
        }

        if (currentBottomHash != be.lastBottomHash) {
            be.lastBottomHash = currentBottomHash;
            be.resetBottomSmelting();
        }

        // === ТЕПЛООБМЕН ===
        // Охлаждение: квадратичная зависимость от температуры
        float baseCooling = (be.temperature * be.temperature) / 512000f;
        if (baseCooling < 0.1f && be.temperature > 0) baseCooling = 0.1f;
        int thermalNoise = (be.temperature > 200 && baseCooling > 1) ? level.random.nextInt(5) - 2 : 0;
        float cooling = Math.max(0.1f, baseCooling + thermalNoise);

        // Нагрев от HeaterBlockEntity снизу
        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof HeaterBlockEntity heater && heater.getTemperature() > be.temperature) {
            float transfer = (heater.getTemperature() - be.temperature) / 10f + 0.5f;
            be.temperature = Math.min(MAX_TEMP, be.temperature + transfer);
        } else if (be.temperature > 0) {
            be.temperature = Math.max(0, be.temperature - cooling);
        }

        // Обработка рядов
        be.tickTopRow();
        be.tickBottomRow();

        // Синхронизация данных для GUI (округляем для отображения)
        be.data.set(0, (int) be.temperature);
        be.data.set(1, (int) be.topProgress);
        be.data.set(2, (int) be.topMaxProgress);
        be.data.set(3, be.topSmelting ? 1 : 0);
        be.data.set(4, be.requiredTempTop);
        be.data.set(5, (int) be.bottomProgress);
        be.data.set(6, (int) be.bottomMaxProgress);
        be.data.set(7, be.bottomSmelting ? 1 : 0);
        be.data.set(8, be.requiredTempBottom);
        be.data.set(9, be.currentAlloyRecipe != null ? 1 : 0);
        be.data.set(10, !be.currentBottomRecipes.isEmpty() ? 1 : 0);
        be.data.set(11, (int) be.topHeatConsumption);
        be.data.set(12, (int) be.bottomHeatConsumption);
        be.data.set(13, be.isTankFull() ? 1 : 0);

        if (be.topSmelting || be.bottomSmelting || be.temperature > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // ==================== ВЕРХНИЙ РЯД (СПЛАВЫ) ====================

    private void tickTopRow() {
        if (totalMetalAmount >= TANK_CAPACITY) {
            if (requiredTempTop != 0) requiredTempTop = 0;
            return;
        }

        ItemStack[] topSlots = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            topSlots[i] = inventory.getStackInSlot(i);
        }

        AlloyRecipe recipe = findMatchingAlloyRecipe(topSlots);
        if (recipe == null) {
            if (topSmelting || currentAlloyRecipe != null) {
                resetTopSmelting();
            }
            return;
        }

        requiredTempTop = recipe.getOutputMetal().getMeltingPoint();

        if (temperature < requiredTempTop) {
            topSmelting = false;
            topProgress = 0;
            // Рассчитываем требуемое тепло: потребление * время
            topMaxProgress = recipe.getTotalHeatConsumption();
            return;
        }

        // Проверка места
        if (!hasSpaceFor(recipe.getOutputUnits())) {
            topSmelting = false;
            return;
        }

        if (!topSmelting) {
            topSmelting = true;
            topProgress = 0;
            topMaxProgress = recipe.getTotalHeatConsumption();
            topHeatConsumption = recipe.getHeatConsumptionPerTick();
            currentAlloyRecipe = recipe;
        }

        // ПЛАВКА: тратим температуру и накапливаем прогресс
        if (topMaxProgress > 0) {
            // Сколько тепла можем потратить этот тик
            float availableHeat = Math.min(topHeatConsumption, temperature);
            // Но не больше чем осталось до завершения
            float heatToApply = Math.min(availableHeat, topMaxProgress - topProgress);

            topProgress += heatToApply;
            temperature = Math.max(0, temperature - heatToApply);

            if (topProgress >= topMaxProgress) {
                completeAlloyRecipe(currentAlloyRecipe);
                resetTopSmelting();
            }
        }
    }

    private AlloyRecipe findMatchingAlloyRecipe(ItemStack[] slots) {
        for (AlloyRecipe recipe : MetallurgyRegistry.getAllAlloyRecipes()) {
            if (recipe.matches(slots)) {
                return recipe;
            }
        }
        return null;
    }

    private void completeAlloyRecipe(AlloyRecipe recipe) {
        for (int i = 0; i < 4; i++) {
            AlloySlot slotReq = recipe.getSlots()[i];
            if (slotReq.item() != null && slotReq.count() > 0) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() == slotReq.item() && stack.getCount() >= slotReq.count()) {
                    stack.shrink(slotReq.count());
                }
            }
        }
        addMetal(recipe.getOutputMetal(), recipe.getOutputUnits());
        setChanged();
    }

    // ==================== НИЖНИЙ РЯД (ПЛАВКА + ШЛАК) ====================

    private void tickBottomRow() {
        bottomProgress = 0;
        bottomMaxProgress = 0;
        bottomHeatConsumption = 0;
        bottomSmelting = false;

        // Проверяем изменения в слотах
        for (int i = 0; i < 4; i++) {
            ItemStack current = inventory.getStackInSlot(4 + i);
            ItemStack prev = previousBottomStacks[i];
            if (!ItemStack.matches(current, prev)) {
                bottomSlots[i] = null;
                previousBottomStacks[i] = current.copy();
            }
        }

        // Вычисляем максимальную требуемую температуру для всех валидных слотов
        int maxTempRequired = 0;
        boolean hasAnyValidRecipe = false;

        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(4 + i);
            if (!stack.isEmpty()) {
                if (stack.getItem() instanceof SlagItem) {
                    Metal slagMetal = SlagItem.getMetal(stack);
                    if (slagMetal != null) {
                        maxTempRequired = Math.max(maxTempRequired, slagMetal.getMeltingPoint());
                        hasAnyValidRecipe = true;
                    }
                } else {
                    SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
                    if (recipe != null) {
                        maxTempRequired = Math.max(maxTempRequired, recipe.minTemp());
                        hasAnyValidRecipe = true;
                    }
                }
            }
        }
        requiredTempBottom = hasAnyValidRecipe ? maxTempRequired : 0;

        // Обрабатываем каждый слот
        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(4 + i);
            if (stack.isEmpty()) {
                bottomSlots[i] = null;
                continue;
            }

            // === ШЛАК ===
            if (stack.getItem() instanceof SlagItem) {
                processSlagSlot(i, stack);
                continue;
            }

            // === ОБЫЧНАЯ ПЛАВКА ===
            SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
            if (recipe == null) {
                bottomSlots[i] = null;
                continue;
            }

            // Проверка места
            if (!hasSpaceFor(recipe.outputUnits())) {
                if (bottomSlots[i] == null) continue;
            }

            if (temperature < recipe.minTemp()) {
                if (bottomSlots[i] != null) bottomSlots[i].active = false;
                continue;
            }

            // Инициализация слота
            if (bottomSlots[i] == null) {
                bottomSlots[i] = new BottomSlotData();
                bottomSlots[i].recipe = recipe;
                // Требуемое тепло = потребление * время
                bottomSlots[i].maxProgress = recipe.getTotalHeatConsumption();
                bottomSlots[i].heatConsumption = recipe.heatConsumption();
                bottomSlots[i].progress = 0;
                bottomSlots[i].active = true;
            }

            BottomSlotData slot = bottomSlots[i];
            if (!slot.active) {
                if (temperature >= slot.recipe.minTemp()) slot.active = true;
                else continue;
            }

            // Проверяем, хватит ли места для завершения
            if (slot.progress >= slot.maxProgress && !hasSpaceFor(slot.recipe.outputUnits())) {
                slot.active = false;
                continue;
            }

            // ПЛАВКА
            float availableHeat = Math.min(slot.heatConsumption, temperature);
            float heatToApply = Math.min(availableHeat, slot.maxProgress - slot.progress);

            slot.progress += heatToApply;
            temperature = Math.max(0, temperature - heatToApply);

            if (slot.progress >= slot.maxProgress) {
                stack.shrink(1);
                addMetal(slot.recipe.output(), slot.recipe.outputUnits());

                // Проверяем, есть ли ещё такой же предмет в слоте
                ItemStack nextStack = inventory.getStackInSlot(4 + i);
                if (!nextStack.isEmpty() && nextStack.getItem() == slot.recipe.input()) {
                    slot.progress = 0;
                    slot.active = true;
                } else {
                    bottomSlots[i] = null;
                }
            }

            // Накопление для GUI
            if (slot.active) {
                bottomProgress += slot.progress;
                bottomMaxProgress += slot.maxProgress;
                bottomHeatConsumption += slot.heatConsumption;
                bottomSmelting = true;
            }
        }
    }

    /**
     * Обработка шлака с динамическим временем плавки
     * Время зависит от количества металла, максимум 30 секунд (600 тиков)
     */
    private void processSlagSlot(int slotIndex, ItemStack stack) {
        if (bottomSlots[slotIndex] == null) {
            Metal slagMetal = SlagItem.getMetal(stack);
            int slagAmount = SlagItem.getAmount(stack);

            if (slagMetal != null && slagAmount > 0) {
                bottomSlots[slotIndex] = new BottomSlotData();
                bottomSlots[slotIndex].slagData = new SlagSlotData();
                bottomSlots[slotIndex].slagData.metal = slagMetal;
                bottomSlots[slotIndex].slagData.amount = slagAmount;
                bottomSlots[slotIndex].slagData.requiredTemp = slagMetal.getMeltingPoint();
                bottomSlots[slotIndex].slagData.heatConsumption = slagMetal.getHeatConsumptionPerTick();

                // Динамическое время: базовое время металла на количество, но не более 600 тиков
                int smeltTime = slagMetal.calculateSmeltTimeForUnits(slagAmount);
                bottomSlots[slotIndex].maxProgress = slagMetal.getHeatConsumptionPerTick() * smeltTime;
                bottomSlots[slotIndex].heatConsumption = slagMetal.getHeatConsumptionPerTick();
                bottomSlots[slotIndex].progress = 0;
                bottomSlots[slotIndex].active = true;
            }
        }

        BottomSlotData slot = bottomSlots[slotIndex];
        if (slot == null || slot.slagData == null) return;

        if (!slot.active) {
            if (temperature >= slot.slagData.requiredTemp) slot.active = true;
            else return;
        }

        // Проверка места
        if (!hasSpaceFor(slot.slagData.amount)) {
            slot.active = false;
            return;
        }

        // ПЛАВКА ШЛАКА
        float availableHeat = Math.min(slot.heatConsumption, temperature);
        float heatToApply = Math.min(availableHeat, slot.maxProgress - slot.progress);

        slot.progress += heatToApply;
        temperature = Math.max(0, temperature - heatToApply);

        if (slot.progress >= slot.maxProgress) {
            addMetal(slot.slagData.metal, slot.slagData.amount);
            stack.shrink(1);
            bottomSlots[slotIndex] = null;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }

        // Накопление для GUI
        if (slot.active) {
            bottomProgress += slot.progress;
            bottomMaxProgress += slot.maxProgress;
            bottomHeatConsumption += slot.heatConsumption;
            bottomSmelting = true;
        }
    }

    // ==================== УТИЛИТЫ ====================

    private int calculateTopHash() {
        int hash = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() + stack.getCount());
        }
        return hash;
    }

    private int calculateBottomHash() {
        int hash = 0;
        for (int i = 4; i < 8; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() + stack.getCount());
        }
        return hash;
    }

    private void resetTopSmelting() {
        if (topSmelting || currentAlloyRecipe != null) {
            topSmelting = false;
            currentAlloyRecipe = null;
            topProgress = 0;
            topMaxProgress = 0;
            requiredTempTop = 0;
        }
    }

    private void resetBottomSmelting() {
        if (bottomSmelting || !currentBottomRecipes.isEmpty()) {
            bottomSmelting = false;
            currentBottomRecipes.clear();
            Arrays.fill(bottomSlots, null);
            bottomProgress = 0;
            bottomMaxProgress = 0;
            requiredTempBottom = 0;
        }
    }

    private void addMetal(Metal metal, int units) {
        if (units <= 0) return;
        if (totalMetalAmount + units > TANK_CAPACITY) {
            units = TANK_CAPACITY - totalMetalAmount;
            if (units <= 0) return;
        }
        metalTank.merge(metal, units, Integer::sum);
        recalculateTotal();
    }

    private void recalculateTotal() {
        totalMetalAmount = metalTank.values().stream().mapToInt(Integer::intValue).sum();
        if (totalMetalAmount > TANK_CAPACITY) {
            int excess = totalMetalAmount - TANK_CAPACITY;
            for (var entry : metalTank.entrySet()) {
                if (entry.getValue() >= excess) {
                    entry.setValue(entry.getValue() - excess);
                    break;
                }
            }
            recalculateTotal();
        }
    }

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

    public boolean hasMetal() {
        return totalMetalAmount > 0;
    }

    public boolean isTankFull() {
        return totalMetalAmount >= TANK_CAPACITY * 0.95f;
    }

    public boolean hasSpaceFor(int units) {
        return totalMetalAmount + units <= TANK_CAPACITY;
    }

    // ==================== ГЕТТЕРЫ ====================

    public Metal getBottomMetal() { return getMetalForCasting(null); }
    public ItemStackHandler getInventory() { return inventory; }
    public ContainerData getData() { return data; }
    public float getTemperature() { return temperature; }
    public Map<Metal, Integer> getMetalTank() { return Collections.unmodifiableMap(metalTank); }
    public int getTotalMetalAmount() { return totalMetalAmount; }
    public int getBlockCapacity() { return BLOCK_CAPACITY; }
    public int getRequiredTempTop() { return requiredTempTop; }
    public int getRequiredTempBottom() { return requiredTempBottom; }
    public float getTopProgress() { return topProgress; }
    public float getTopMaxProgress() { return topMaxProgress; }
    public boolean isTopSmelting() { return topSmelting; }
    public float getBottomProgress() { return bottomProgress; }
    public float getBottomMaxProgress() { return bottomMaxProgress; }
    public boolean isBottomSmelting() { return bottomSmelting; }

    public List<MetalStack> getMetalStacks() {
        List<MetalStack> list = new ArrayList<>();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) list.add(new MetalStack(metal, amount));
        });
        return list;
    }

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

    public static class MetalStack {
        public final Metal metal;
        public final int amount;
        public MetalStack(Metal metal, int amount) { this.metal = metal; this.amount = amount; }
        public String getFormattedAmount() {
            return MetalUnits2.convertFromUnits(amount).totalUnits() + " ед.";
        }
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putFloat("Temperature", temperature);
        tag.putFloat("TopProgress", topProgress);
        tag.putFloat("TopMaxProgress", topMaxProgress);
        tag.putFloat("BottomProgress", bottomProgress);
        tag.putFloat("BottomMaxProgress", bottomMaxProgress);
        tag.putInt("RequiredTempTop", requiredTempTop);
        tag.putInt("RequiredTempBottom", requiredTempBottom);

        // Сохраняем прогресс нижних слотов
        ListTag bottomSlotsTag = new ListTag();
        for (int i = 0; i < 4; i++) {
            CompoundTag slotTag = new CompoundTag();
            if (bottomSlots[i] != null) {
                slotTag.putBoolean("HasData", true);
                slotTag.putFloat("Progress", bottomSlots[i].progress);
                slotTag.putFloat("MaxProgress", bottomSlots[i].maxProgress);
                slotTag.putFloat("HeatConsumption", bottomSlots[i].heatConsumption);
                slotTag.putBoolean("Active", bottomSlots[i].active);

                if (bottomSlots[i].recipe != null) {
                    slotTag.putString("RecipeItem", ForgeRegistries.ITEMS.getKey(bottomSlots[i].recipe.input()).toString());
                } else if (bottomSlots[i].slagData != null) {
                    slotTag.putBoolean("IsSlag", true);
                    slotTag.putString("SlagMetal", bottomSlots[i].slagData.metal.getId().toString());
                    slotTag.putInt("SlagAmount", bottomSlots[i].slagData.amount);
                    slotTag.putInt("SlagRequiredTemp", bottomSlots[i].slagData.requiredTemp);
                    slotTag.putFloat("SlagHeatConsumption", bottomSlots[i].slagData.heatConsumption);
                }
            } else {
                slotTag.putBoolean("HasData", false);
            }
            bottomSlotsTag.add(slotTag);
        }
        tag.put("BottomSlots", bottomSlotsTag);

        tag.putInt("LastTopHash", lastTopHash);
        tag.putInt("LastBottomHash", lastBottomHash);

        ListTag metals = new ListTag();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) {
                CompoundTag mt = new CompoundTag();
                mt.putString("Metal", metal.getId().toString());
                mt.putInt("Amount", amount);
                metals.add(mt);
            }
        });
        tag.put("Metals", metals);
        tag.putInt("TotalMetal", totalMetalAmount);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        temperature = tag.getFloat("Temperature");
        topProgress = tag.getFloat("TopProgress");
        topMaxProgress = tag.getFloat("TopMaxProgress");
        bottomProgress = tag.getFloat("BottomProgress");
        bottomMaxProgress = tag.getFloat("BottomMaxProgress");
        requiredTempTop = tag.getInt("RequiredTempTop");
        requiredTempBottom = tag.getInt("RequiredTempBottom");

        if (tag.contains("BottomSlots")) {
            ListTag bottomSlotsTag = tag.getList("BottomSlots", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(4, bottomSlotsTag.size()); i++) {
                CompoundTag slotTag = bottomSlotsTag.getCompound(i);
                if (slotTag.getBoolean("HasData")) {
                    bottomSlots[i] = new BottomSlotData();
                    bottomSlots[i].progress = slotTag.getFloat("Progress");
                    bottomSlots[i].maxProgress = slotTag.getFloat("MaxProgress");
                    bottomSlots[i].heatConsumption = slotTag.getFloat("HeatConsumption");
                    bottomSlots[i].active = slotTag.getBoolean("Active");

                    if (slotTag.getBoolean("IsSlag")) {
                        bottomSlots[i].slagData = new SlagSlotData();
                        ResourceLocation metalId = new ResourceLocation(slotTag.getString("SlagMetal"));
                        int index = i;
                        MetallurgyRegistry.get(metalId).ifPresent(metal -> bottomSlots[index].slagData.metal = metal);
                        bottomSlots[i].slagData.amount = slotTag.getInt("SlagAmount");
                        bottomSlots[i].slagData.requiredTemp = slotTag.getInt("SlagRequiredTemp");
                        bottomSlots[i].slagData.heatConsumption = slotTag.getFloat("SlagHeatConsumption");
                    } else if (slotTag.contains("RecipeItem")) {
                        ResourceLocation itemId = new ResourceLocation(slotTag.getString("RecipeItem"));
                        Item item = ForgeRegistries.ITEMS.getValue(itemId);
                        if (item != null) {
                            bottomSlots[i].recipe = MetallurgyRegistry.getSmeltRecipe(item);
                        }
                    }
                } else {
                    bottomSlots[i] = null;
                }
            }
        }

        lastTopHash = tag.getInt("LastTopHash");
        lastBottomHash = tag.getInt("LastBottomHash");

        metalTank.clear();
        ListTag metals = tag.getList("Metals", Tag.TAG_COMPOUND);
        for (int i = 0; i < metals.size(); i++) {
            CompoundTag mt = metals.getCompound(i);
            ResourceLocation id = new ResourceLocation(mt.getString("Metal"));
            int amt = mt.getInt("Amount");
            MetallurgyRegistry.get(id).ifPresent(metal -> metalTank.put(metal, amt));
        }
        recalculateTotal();
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
        return Component.translatable("block.cim.smelter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SmelterMenu(id, inv, this, data);
    }
}