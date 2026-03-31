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
            if (stack.is(ModItems.SLAG.get())) return true;
            return MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null;
        }
    };

    private final Map<Metal, Integer> metalTank = new LinkedHashMap<>();
    private int totalMetalAmount = 0;
    private int temperature = 0;
    // Разделенное отслеживание изменений по рядам (фикс #2)
    private int lastTopHash = 0;
    private int lastBottomHash = 0;

    // Верхний ряд (сплавы)
    private int topProgress = 0;
    private int topMaxProgress = 0;
    private boolean topSmelting = false;
    private int topHeatPerTick = 0;
    private AlloyRecipe currentAlloyRecipe = null;
    private int requiredTempTop = 0;

    // Нижний ряд (обычная плавка)
    private int bottomProgress = 0;
    private int bottomMaxProgress = 0;
    private boolean bottomSmelting = false;
    private int bottomHeatPerTick = 0;
    private Map<SmeltRecipe, Integer> currentBottomRecipes = new HashMap<>();
    private int requiredTempBottom = 0;

    private final ContainerData data = new SimpleContainerData(14) { // +1 для флага переполнения
        @Override
        public void set(int index, int value) {
            super.set(index, value);
        }
    };

    private static class BottomSlotData {
        SmeltRecipe recipe;
        int progress;
        int maxProgress;
        int heatPerTick;
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
        // Раздельное отслеживание изменений (фикс #2)
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

        // Теплообмен
        int baseCooling = (be.temperature * be.temperature) / 512000;
        if (baseCooling == 0 && be.temperature > 0) baseCooling = 1;
        int thermalNoise = (be.temperature > 200 && baseCooling > 1) ? level.random.nextInt(5) - 2 : 0;
        int cooling = Math.max(1, baseCooling + thermalNoise);

        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof HeaterBlockEntity heater && heater.getTemperature() > be.temperature) {
            int transfer = (heater.getTemperature() - be.temperature) / 10 + 1;
            be.temperature = Math.min(MAX_TEMP, be.temperature + transfer);
        } else if (be.temperature > 0) {
            be.temperature = Math.max(0, be.temperature - cooling);
        }

        // Умная проверка переполнения (фикс #4)
        be.processSmartSmeltingLogic();

        be.tickTopRow();
        be.tickBottomRow();

        // Синхронизация данных для GUI
        be.data.set(0, be.temperature);
        be.data.set(1, be.topProgress);
        be.data.set(2, be.topMaxProgress);
        be.data.set(3, be.topSmelting ? 1 : 0);
        be.data.set(4, be.requiredTempTop);
        be.data.set(5, be.bottomProgress);
        be.data.set(6, be.bottomMaxProgress);
        be.data.set(7, be.bottomSmelting ? 1 : 0);
        be.data.set(8, be.requiredTempBottom);
        be.data.set(9, be.currentAlloyRecipe != null ? 1 : 0);
        be.data.set(10, !be.currentBottomRecipes.isEmpty() ? 1 : 0);
        be.data.set(11, be.topHeatPerTick);
        be.data.set(12, be.bottomHeatPerTick);
        be.data.set(13, be.isTankFull() ? 1 : 0); // Индикатор переполнения (фикс #1)

        if (be.topSmelting || be.bottomSmelting || be.temperature > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // Раздельное хеширование инвентаря (фикс #2)
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

    // Раздельный сброс (фикс #2)
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

    // Умная логика выбора ряда (фикс #4)
    private void processSmartSmeltingLogic() {
        if (totalMetalAmount >= TANK_CAPACITY) return;

        int remainingSpace = TANK_CAPACITY - totalMetalAmount;

        // Проверяем верхний ряд
        int topOutput = getTopRowPredictedOutput();
        // Проверяем нижний ряд
        int bottomOutput = getBottomRowPredictedOutput();

        // Если оба ряда не помещаются, выбираем тот, что помещается (меньший)
        // Реализация в tick методах через проверку isTankFull() или hasSpaceFor()
    }

    private int getTopRowPredictedOutput() {
        if (currentAlloyRecipe != null) return currentAlloyRecipe.getOutputUnits();
        ItemStack[] topSlots = new ItemStack[4];
        for (int i = 0; i < 4; i++) topSlots[i] = inventory.getStackInSlot(i);
        AlloyRecipe recipe = findMatchingAlloyRecipe(topSlots);
        return recipe != null ? recipe.getOutputUnits() : 0;
    }

    private int getBottomRowPredictedOutput() {
        int total = 0;
        for (int i = 0; i < 4; i++) {
            if (bottomSlots[i] != null && bottomSlots[i].recipe != null) {
                total += bottomSlots[i].recipe.outputUnits();
            } else {
                ItemStack stack = inventory.getStackInSlot(4 + i);
                if (!stack.isEmpty()) {
                    SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
                    if (recipe != null) total += recipe.outputUnits();
                }
            }
        }
        return total;
    }

    public boolean isTankFull() {
        return totalMetalAmount >= TANK_CAPACITY * 0.95f; // 95% = почти полный
    }

    public boolean hasSpaceFor(int units) {
        return totalMetalAmount + units <= TANK_CAPACITY;
    }

    // ==================== Верхний ряд (сплавы) ====================

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
            topMaxProgress = recipe.getTotalHeat();
            return;
        }

        // Проверка места с учетом будущего выхода (фикс #4)
        if (!hasSpaceFor(recipe.getOutputUnits())) {
            topSmelting = false; // Ждем освобождения места
            return;
        }

        if (!topSmelting) {
            topSmelting = true;
            topProgress = 0;
            topMaxProgress = recipe.getTotalHeat();
            topHeatPerTick = recipe.getHeatPerTick();
            currentAlloyRecipe = recipe;
        }

        if (topMaxProgress > 0) {
            int heatPerTick = Math.min(topHeatPerTick, temperature / 20 + 1);
            heatPerTick = Math.min(heatPerTick, temperature);
            topHeatPerTick = heatPerTick;
            topProgress += heatPerTick;
            temperature = Math.max(0, temperature - (heatPerTick / 2));

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

    // ==================== Нижний ряд (партийная плавка + переплавка шлака) ====================

    private void tickBottomRow() {
        bottomProgress = 0;
        bottomMaxProgress = 0;
        bottomHeatPerTick = 0;
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

        // ВЫЧИСЛЯЕМ ТРЕБУЕМУЮ ТЕМПЕРАТУРУ ДЛЯ ВСЕХ ВАЛИДНЫХ СЛОТОВ
        int maxTempRequired = 0;
        boolean hasAnyValidRecipe = false;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(4 + i);
            if (!stack.isEmpty()) {
                // Шлак не требует температуры для переплавки (или можно сделать минимальную)
                if (stack.getItem() instanceof SlagItem) {
                    hasAnyValidRecipe = true;
                    continue;
                }
                SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
                if (recipe != null) {
                    maxTempRequired = Math.max(maxTempRequired, recipe.minTemp());
                    hasAnyValidRecipe = true;
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

            // === ОБРАБОТКА ШЛАКА (мгновенная переплавка) ===
            if (stack.getItem() instanceof SlagItem) {
                Metal slagMetal = SlagItem.getMetal(stack);
                int slagAmount = SlagItem.getAmount(stack);

                if (slagMetal != null && slagAmount > 0) {
                    // Проверяем есть ли место в баке
                    if (hasSpaceFor(slagAmount)) {
                        // Переплавляем шлак мгновенно (он уже был расплавлен)
                        addMetal(slagMetal, slagAmount);
                        stack.shrink(1); // Уменьшаем количество шлака
                        setChanged();
                        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                    }
                    // Если места нет - просто пропускаем, ждем освобождения
                    continue;
                } else {
                    // Пустой шлак - удаляем
                    stack.shrink(1);
                    continue;
                }
            }

            // === ОБЫЧНАЯ ПЛАВКА ===
            SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
            if (recipe == null) {
                bottomSlots[i] = null;
                continue;
            }

            // Проверка места в баке с учетом текущего содержимого
            if (!hasSpaceFor(recipe.outputUnits())) {
                if (bottomSlots[i] == null) continue;
            }

            if (temperature < recipe.minTemp()) {
                if (bottomSlots[i] != null) bottomSlots[i].active = false;
                continue;
            }

            // Инициализация слота если нужно
            if (bottomSlots[i] == null) {
                bottomSlots[i] = new BottomSlotData();
                bottomSlots[i].recipe = recipe;
                bottomSlots[i].maxProgress = recipe.totalHeat();
                bottomSlots[i].heatPerTick = recipe.heatPerTick();
                bottomSlots[i].progress = 0;
                bottomSlots[i].active = true;
            }

            BottomSlotData slot = bottomSlots[i];
            if (!slot.active) {
                if (temperature >= slot.recipe.minTemp()) slot.active = true;
                else continue;
            }

            // Проверка места перед завершением
            if (slot.progress >= slot.maxProgress && !hasSpaceFor(slot.recipe.outputUnits())) {
                slot.active = false;
                continue;
            }

            // Расчет тепла
            int heat = Math.min(slot.heatPerTick, temperature / 20 + 1);
            heat = Math.min(heat, temperature);
            slot.progress += heat;
            temperature = Math.max(0, temperature - (heat / 2));

            // Завершение плавки (партийное)
            if (slot.progress >= slot.maxProgress) {
                ItemStack currentStack = inventory.getStackInSlot(4 + i);
                if (!currentStack.isEmpty()) {
                    currentStack.shrink(1);
                    addMetal(slot.recipe.output(), slot.recipe.outputUnits());
                }

                // Если остались предметы - начинаем новую партию
                if (!inventory.getStackInSlot(4 + i).isEmpty()) {
                    slot.progress = 0;
                    slot.active = true;
                } else {
                    bottomSlots[i] = null;
                }
            }

            // Накопление данных для GUI
            if (bottomSlots[i] != null && bottomSlots[i].active) {
                bottomProgress += bottomSlots[i].progress;
                bottomMaxProgress += bottomSlots[i].maxProgress;
                bottomHeatPerTick += bottomSlots[i].heatPerTick;
                bottomSmelting = true;
                // requiredTempBottom уже установлен выше как максимум всех температур
            }
        }
    }

    // ==================== Работа с металлами ====================

    private void addMetal(Metal metal, int units) {
        if (units <= 0) return;
        if (totalMetalAmount + units > TANK_CAPACITY) {
            units = TANK_CAPACITY - totalMetalAmount; // Обрезаем (фикс #1 - теперь с индикатором)
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
    /**
     * Выбрасывает весь металл из бака в виде шлака
     * @return список стаков шлака
     */
    public List<ItemStack> dumpMetalAsSlag() {
        List<ItemStack> slagItems = new ArrayList<>();

        metalTank.forEach((metal, amount) -> {
            if (amount > 0) {
                ItemStack slag = SlagItem.createSlag(metal, amount);
                // Убеждаемся что горячесть есть (createSlag уже добавляет, но на всякий случай)
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

    /**
     * Проверяет есть ли металл для выброса
     */
    public boolean hasMetal() {
        return totalMetalAmount > 0;
    }



    // ==================== Геттеры ====================

    public Metal getBottomMetal() {return getMetalForCasting(null);}
    public ItemStackHandler getInventory() { return inventory; }
    public ContainerData getData() { return data; }
    public int getTemperature() { return temperature; }
    public Map<Metal, Integer> getMetalTank() { return Collections.unmodifiableMap(metalTank); }
    public int getTotalMetalAmount() { return totalMetalAmount; }
    public int getBlockCapacity() { return BLOCK_CAPACITY; }
    public int getRequiredTempTop() { return requiredTempTop; }
    public int getRequiredTempBottom() { return requiredTempBottom; }
    public int getTopProgress() { return topProgress; }
    public int getTopMaxProgress() { return topMaxProgress; }
    public boolean isTopSmelting() { return topSmelting; }
    public int getBottomProgress() { return bottomProgress; }
    public int getBottomMaxProgress() { return bottomMaxProgress; }
    public boolean isBottomSmelting() { return bottomSmelting; }
    public List<MetalStack> getMetalStacks() {
        List<MetalStack> list = new ArrayList<>();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) list.add(new MetalStack(metal, amount));
        });
        // FIFO - не сортируем! LinkedHashMap сохраняет порядок вставки
        return list;
    }

    /**
     * Получает металл для литья с учетом приоритета котлов внизу
     * @param preferredMetals металлы, которые уже есть в котлах (приоритетные)
     */
    public Metal getMetalForCasting(List<Metal> preferredMetals) {
        if (metalTank.isEmpty()) return null;

        // Если есть предпочтительные металлы (уже есть в котлах) - берем их первыми
        if (preferredMetals != null && !preferredMetals.isEmpty()) {
            for (Metal preferred : preferredMetals) {
                if (metalTank.containsKey(preferred) && metalTank.get(preferred) > 0) {
                    return preferred;
                }
            }
        }

        // Иначе FIFO - первый вошел, первый вышел (порядок LinkedHashMap)
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

    // ==================== NBT (фикс #3 - сохранение прогресса) ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Temperature", temperature);
        tag.putInt("TopProgress", topProgress);
        tag.putInt("TopMaxProgress", topMaxProgress);
        tag.putInt("BottomProgress", bottomProgress);
        tag.putInt("BottomMaxProgress", bottomMaxProgress);
        tag.putInt("RequiredTempTop", requiredTempTop);
        tag.putInt("RequiredTempBottom", requiredTempBottom);

        // Сохраняем прогресс нижних слотов (фикс #3)
        ListTag bottomSlotsTag = new ListTag();
        for (int i = 0; i < 4; i++) {
            CompoundTag slotTag = new CompoundTag();
            if (bottomSlots[i] != null) {
                slotTag.putBoolean("HasData", true);
                slotTag.putInt("Progress", bottomSlots[i].progress);
                slotTag.putInt("MaxProgress", bottomSlots[i].maxProgress);
                slotTag.putInt("HeatPerTick", bottomSlots[i].heatPerTick);
                slotTag.putBoolean("Active", bottomSlots[i].active);
                if (bottomSlots[i].recipe != null) {
                    slotTag.putString("RecipeItem", ForgeRegistries.ITEMS.getKey(bottomSlots[i].recipe.input()).toString());
                }
            } else {
                slotTag.putBoolean("HasData", false);
            }
            bottomSlotsTag.add(slotTag);
        }
        tag.put("BottomSlots", bottomSlotsTag);

        // Сохраняем хеши для предотвращения ложного сброса при загрузке
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
        temperature = tag.getInt("Temperature");
        topProgress = tag.getInt("TopProgress");
        topMaxProgress = tag.getInt("TopMaxProgress");
        bottomProgress = tag.getInt("BottomProgress");
        bottomMaxProgress = tag.getInt("BottomMaxProgress");
        requiredTempTop = tag.getInt("RequiredTempTop");
        requiredTempBottom = tag.getInt("RequiredTempBottom");

        // Загружаем прогресс нижних слотов (фикс #3)
        if (tag.contains("BottomSlots")) {
            ListTag bottomSlotsTag = tag.getList("BottomSlots", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(4, bottomSlotsTag.size()); i++) {
                CompoundTag slotTag = bottomSlotsTag.getCompound(i);
                if (slotTag.getBoolean("HasData")) {
                    bottomSlots[i] = new BottomSlotData();
                    bottomSlots[i].progress = slotTag.getInt("Progress");
                    bottomSlots[i].maxProgress = slotTag.getInt("MaxProgress");
                    bottomSlots[i].heatPerTick = slotTag.getInt("HeatPerTick");
                    bottomSlots[i].active = slotTag.getBoolean("Active");

                    if (slotTag.contains("RecipeItem")) {
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

        // Восстанавливаем хеши
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