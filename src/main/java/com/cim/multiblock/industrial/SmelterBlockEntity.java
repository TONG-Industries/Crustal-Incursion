package com.cim.multiblock.industrial;

import com.cim.api.metallurgy.system.ISmelter;
import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.api.metallurgy.system.recipe.AlloyRecipe;
import com.cim.api.metallurgy.system.recipe.AlloySlot;
import com.cim.api.metallurgy.system.recipe.SmeltRecipe;
import com.cim.block.entity.ModBlockEntities;
import com.cim.event.HotItemHandler;
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

public class SmelterBlockEntity extends BlockEntity implements MenuProvider, ISmelter {
    public static final int MAX_TEMP = 1600;
    public static final int BLOCK_CAPACITY = 4;
    public static final int TANK_CAPACITY = BLOCK_CAPACITY * MetalUnits2.UNITS_PER_BLOCK;

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
            if (stack.getItem() instanceof SlagItem) return true;
            return MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null;
        }
    };

    @Override
    public BlockEntity asBlockEntity() {
        return this;
    }

    @Override
    public int getSmelterCapacity() {
        return TANK_CAPACITY;
    }
    // === НАГРЕВ ПРЕДМЕТОВ ===
    // Температура каждого слота (только для визуализации и логики)
    private final float[] slotTemperatures = new float[8];
    private static final float HEAT_RATE = 5.0f; // Градусов за тик при нагреве

    private static class SlagSlotData {
        Metal metal;
        int amount;
        int requiredTemp;
        float heatConsumption;
    }

    private final Map<Metal, Integer> metalTank = new LinkedHashMap<>();
    private int totalMetalAmount = 0;
    private float temperature = 0;

    private int lastTopHash = 0;
    private int lastBottomHash = 0;

    // Верхний ряд (сплавы)
    private float topProgress = 0;
    private float topMaxProgress = 0;
    private boolean topSmelting = false;
    private float topHeatConsumption = 0;
    private AlloyRecipe currentAlloyRecipe = null;
    private int requiredTempTop = 0;

    // Нижний ряд (обычная плавка)
    private float bottomProgress = 0;
    private float bottomMaxProgress = 0;
    private boolean bottomSmelting = false;
    private float bottomHeatConsumption = 0;
    private Map<SmeltRecipe, Float> currentBottomRecipes = new HashMap<>();
    private int requiredTempBottom = 0;

    // Поля для синхронной плавки
    private float sharedBottomProgress = 0;      // Общий прогресс для всего ряда
    private float sharedBottomMaxProgress = 0;     // Суммарное тепло всех рецептов
    private boolean allBottomSlotsReady = false; // Все ли слоты нагреты
    private int[] slotIndividualProgress = new int[4]; // Персональный прогресс (0-100%)

    // === СИНХРОННАЯ ПЛАВКА ВЕРХНЕГО РЯДА ===
    private float sharedTopProgress = 0;
    private float sharedTopMaxProgress = 0;
    private boolean allTopSlotsReady = false;
    private final ItemStack[] previousTopStacks = new ItemStack[4];

    private final ContainerData data = new SimpleContainerData(14) {
        @Override
        public void set(int index, int value) {
            super.set(index, value);
        }
    };

    private static class BottomSlotData {
        SmeltRecipe recipe;
        SlagSlotData slagData;
        float progress;
        float maxProgress;
        float heatConsumption;
        boolean active;
        // Температура предмета в этом слоте
        float itemTemperature;
        int targetTemperature;
    }

    private final BottomSlotData[] bottomSlots = new BottomSlotData[4];
    private final ItemStack[] previousBottomStacks = new ItemStack[4];

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER_BE.get(), pos, state);
        for (int i = 0; i < 4; i++) {
            previousBottomStacks[i] = ItemStack.EMPTY;
            previousTopStacks[i] = ItemStack.EMPTY;
        }
        Arrays.fill(slotTemperatures, 20);
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
        float baseCooling = (be.temperature * be.temperature) / 512000f;
        if (baseCooling < 0.1f && be.temperature > 0) baseCooling = 0.1f;
        int thermalNoise = (be.temperature > 200 && baseCooling > 1) ? level.random.nextInt(5) - 2 : 0;
        float cooling = Math.max(0.1f, baseCooling + thermalNoise);

        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof HeaterBlockEntity heater && heater.getTemperature() > be.temperature) {
            float transfer = (heater.getTemperature() - be.temperature) / 10f + 0.5f;
            be.temperature = Math.min(MAX_TEMP, be.temperature + transfer);
        } else if (be.temperature > 0) {
            be.temperature = Math.max(0, be.temperature - cooling);
        }

        // === ДВУСТОРОННИЙ ТЕПЛООБМЕН: ПЕЧЬ ↔ ПРЕДМЕТЫ ===
        be.processItemHeatExchange();

        // Обработка рядов с нагревом предметов
        be.tickTopRow();
        be.tickBottomRow();

        // Синхронизация данных для GUI
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

        // === СОБИРАЕМ СЛОТЫ ===
        ItemStack[] topSlotsStacks = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            topSlotsStacks[i] = inventory.getStackInSlot(i);
        }

        // === ИЩЕМ РЕЦЕПТ ===
        AlloyRecipe recipe = findMatchingAlloyRecipe(topSlotsStacks);
        if (recipe == null) {
            if (topSmelting || currentAlloyRecipe != null) {
                resetTopSmelting();
            }
            return;
        }

        // === ПРОВЕРКА ИЗМЕНЕНИЙ В СЛОТАХ ===
        int currentTopHash = calculateTopHash();
        if (currentTopHash != lastTopHash) {
            lastTopHash = currentTopHash;
            // Сбрасываем только если рецепт изменился (не просто количество)
            if (!recipe.matches(previousTopStacks)) {
                resetTopSmelting();
                // Копируем текущие стеки для отслеживания
                for (int i = 0; i < 4; i++) {
                    previousTopStacks[i] = topSlotsStacks[i].copy();
                }
                return;
            }
        }

        // Сохраняем текущие стеки
        for (int i = 0; i < 4; i++) {
            previousTopStacks[i] = topSlotsStacks[i].copy();
        }

        currentAlloyRecipe = recipe;
        requiredTempTop = recipe.getOutputMetal().getMeltingPoint();

        // === ФАЗА 1: НАГРЕВ И КЛАССИФИКАЦИЯ СЛОТОВ ===
        AlloySlot[] slots = recipe.getSlots();
        boolean allMeltablesHeated = true;
        boolean hasAnyMeltable = false;
        int meltableCount = 0;

        for (int i = 0; i < 4; i++) {
            ItemStack stack = topSlotsStacks[i];
            AlloySlot slotReq = slots[i];

            // Пустой слот в рецепте — пропускаем
            if (slotReq.item() == null) continue;

            // Проверяем что предмет подходит
            if (stack.isEmpty() || stack.getItem() != slotReq.item()) {
                allMeltablesHeated = false;
                continue;
            }

            // === ПРОВЕРЯЕМ, ПЛАВИТСЯ ЛИ ЭТОТ ПРЕДМЕТ ===
            boolean isMeltable = MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null;

            if (isMeltable) {
                hasAnyMeltable = true;
                meltableCount++;

                int targetTemp = getMeltingPointForItem(stack);
                float currentTemp = getItemTemperature(stack);

                // === НАГРЕВ ПЛАВЯЩЕГОСЯ ПРЕДМЕТА ===
                if (currentTemp < targetTemp * 0.95f) {
                    allMeltablesHeated = false;

                    if (temperature > currentTemp) {
                        float heatNeeded = (targetTemp * 0.95f) - currentTemp;
                        float heatTransfer = Math.min(HEAT_RATE * 3, heatNeeded);
                        heatTransfer = Math.min(heatTransfer, temperature * 0.1f);

                        float newTemp = currentTemp + heatTransfer;
                        setItemTemperature(stack, newTemp);
                        slotTemperatures[i] = newTemp;
                        temperature -= heatTransfer * 0.5f;
                    }
                }
                // Катализаторы (неплавящиеся) не нагреваем, они просто ждут
            }
        }

        // === ФАЗА 2: СИНХРОННАЯ ПЛАВКА ===
        if (!hasAnyMeltable) {
            // Только катализаторы — нечего плавить
            resetTopSmelting();
            return;
        }

        // Если не все плавящиеся нагреты или нет температуры печи — ждём
        if (!allMeltablesHeated || temperature < requiredTempTop * 0.9f) {
            topSmelting = false;
            allTopSlotsReady = false;
            return;
        }

        // Все готовы! Запускаем/продолжаем плавку
        if (!allTopSlotsReady) {
            allTopSlotsReady = true;
            if (sharedTopMaxProgress <= 0) {
                sharedTopMaxProgress = recipe.getTotalHeatConsumption();
                topHeatConsumption = recipe.getHeatConsumptionPerTick();
            }
        }

        topSmelting = true;

        // Применяем тепло
        float availableHeat = Math.min(topHeatConsumption, temperature);
        float heatToApply = Math.min(availableHeat, sharedTopMaxProgress - sharedTopProgress);

        sharedTopProgress += heatToApply;
        temperature = Math.max(0, temperature - heatToApply);

        // Обновление GUI
        topProgress = sharedTopProgress;
        topMaxProgress = sharedTopMaxProgress;

        // === ЗАВЕРШЕНИЕ ===
        if (sharedTopProgress >= sharedTopMaxProgress * 0.999f) {
            completeAlloyRecipeSynced(recipe, slots, topSlotsStacks);
            resetTopSmelting();
        }
    }

    private void resetTopSmelting() {
        if (topSmelting || currentAlloyRecipe != null || sharedTopProgress > 0) {
            topSmelting = false;
            currentAlloyRecipe = null;
            topProgress = 0;
            topMaxProgress = 0;
            requiredTempTop = 0;
            // === СБРОС СИНХРОННЫХ ПОЛЕЙ ===
            sharedTopProgress = 0;
            sharedTopMaxProgress = 0;
            allTopSlotsReady = false;
        }
    }

    private void completeAlloyRecipeSynced(AlloyRecipe recipe, AlloySlot[] slots, ItemStack[] stacks) {
        // Списываем ВСЕ предметы рецепта (и плавящиеся, и катализаторы)
        for (int i = 0; i < 4; i++) {
            AlloySlot slotReq = slots[i];
            if (slotReq.item() == null || slotReq.count() <= 0) continue;

            ItemStack stack = stacks[i];
            if (!stack.isEmpty() && stack.getItem() == slotReq.item() && stack.getCount() >= slotReq.count()) {
                stack.shrink(slotReq.count());
                slotTemperatures[i] = 20; // Сброс температуры
            }
        }

        addMetal(recipe.getOutputMetal(), recipe.getOutputUnits());
        setChanged();
    }
    /**
     * Нагревает предметы в верхнем ряду для рецепта сплава
     * @return true если все необходимые предметы нагреты до температуры плавления
     */
    private boolean heatUpSlotsForRecipe(AlloyRecipe recipe, boolean canMelt) {
        boolean allHeated = true;
        AlloySlot[] slots = recipe.getSlots();

        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty() || slots[i].item() == null) {
                slotTemperatures[i] = Math.max(20, slotTemperatures[i] - 2); // Остывание
                continue;
            }

            // Проверяем что предмет подходит для рецепта
            if (stack.getItem() != slots[i].item()) {
                slotTemperatures[i] = Math.max(20, slotTemperatures[i] - 2);
                continue;
            }

            // Целевая температура - температура плавления металла или 300°C для неметаллов
            int targetTemp = getMeltingPointForItem(stack);
            slotTemperatures[i] = Math.min(targetTemp, slotTemperatures[i] + HEAT_RATE);

            if (slotTemperatures[i] < targetTemp * 0.9f) {
                allHeated = false;
            }
        }

        return allHeated;
    }

    private void coolDownSlot(int slot) {
        if (slotTemperatures[slot] > 20) {
            slotTemperatures[slot] = Math.max(20, slotTemperatures[slot] - 5);
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
                    slotTemperatures[i] = 20; // Сброс температуры
                }
            }
        }
        addMetal(recipe.getOutputMetal(), recipe.getOutputUnits());
        setChanged();
    }

    // ==================== НИЖНИЙ РЯД (ПЛАВКА + ШЛАК) ====================

    private void tickBottomRow() {
        // === ПРОВЕРКА ИЗМЕНЕНИЙ В СЛОТАХ ===
        for (int i = 0; i < 4; i++) {
            ItemStack current = inventory.getStackInSlot(4 + i);
            ItemStack prev = previousBottomStacks[i];

            if (!areItemsSameIgnoreHeat(current, prev)) {
                // Слот изменился — сбрасываем ТОЛЬКО этот слот
                if (bottomSlots[i] != null && bottomSlots[i].active) {
                    // Вычитаем его вклад из общего прогресса
                    if (sharedBottomMaxProgress > 0) {
                        float slotShare = bottomSlots[i].maxProgress / sharedBottomMaxProgress;
                        // Уменьшаем общий макс прогресс на вклад этого слота
                        sharedBottomMaxProgress -= bottomSlots[i].maxProgress;
                        // Корректируем текущий прогресс пропорционально
                        sharedBottomProgress = Math.max(0, sharedBottomProgress * (1 - slotShare * 0.5f));
                    }
                }
                bottomSlots[i] = null;
                previousBottomStacks[i] = current.copy();
                // Сбрасываем готовность — нужно перепроверить все слоты
                allBottomSlotsReady = false;
            }
        }

        // === ФАЗА 1: НАГРЕВ И ПРОВЕРКА ГОТОВНОСТИ ===
        boolean hasAnyRecipe = false;
        boolean allHeated = true;
        float totalHeatConsumption = 0;
        float totalMaxProgress = 0;
        int activeSlotsCount = 0;

        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(4 + i);
            int slotIndex = 4 + i;

            if (stack.isEmpty()) continue;

            hasAnyRecipe = true;

            // Инициализация слота если нужно
            if (bottomSlots[i] == null) {
                bottomSlots[i] = new BottomSlotData();
                bottomSlots[i].progress = 0;
                bottomSlots[i].active = false;

                if (stack.getItem() instanceof SlagItem) {
                    Metal slagMetal = SlagItem.getMetal(stack);
                    int slagAmount = SlagItem.getAmount(stack);

                    if (slagMetal != null && slagAmount > 0) {
                        bottomSlots[i].slagData = new SlagSlotData();
                        bottomSlots[i].slagData.metal = slagMetal;
                        bottomSlots[i].slagData.amount = slagAmount;
                        bottomSlots[i].slagData.requiredTemp = slagMetal.getMeltingPoint();
                        bottomSlots[i].slagData.heatConsumption = slagMetal.getHeatConsumptionPerTick();

                        int smeltTime = slagMetal.calculateSmeltTimeForUnits(slagAmount);
                        bottomSlots[i].maxProgress = slagMetal.getHeatConsumptionPerTick() * smeltTime;
                        bottomSlots[i].heatConsumption = slagMetal.getHeatConsumptionPerTick();
                        bottomSlots[i].targetTemperature = slagMetal.getMeltingPoint();
                    } else {
                        bottomSlots[i] = null; // Невалидный шлак
                        continue;
                    }
                } else {
                    SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
                    if (recipe != null) {
                        bottomSlots[i].recipe = recipe;
                        bottomSlots[i].maxProgress = recipe.getTotalHeatConsumption();
                        bottomSlots[i].heatConsumption = recipe.heatConsumption();
                        bottomSlots[i].targetTemperature = recipe.minTemp();
                    } else {
                        bottomSlots[i] = null; // Нет рецепта
                        continue;
                    }
                }
                // Сбрасываем готовность при новом предмете
                allBottomSlotsReady = false;
            }

            BottomSlotData slot = bottomSlots[i];

            // === ПРИНУДИТЕЛЬНЫЙ НАГРЕВ ШЛАКА ===
            float itemTemp = getItemTemperature(stack); // Используем HotItemHandler!

            if (itemTemp < slot.targetTemperature * 0.95f) {
                allHeated = false;
                slot.active = false;

                if (temperature > itemTemp) {
                    float heatNeeded = (slot.targetTemperature * 0.95f) - itemTemp;
                    float heatTransfer = Math.min(HEAT_RATE * 3, heatNeeded);
                    heatTransfer = Math.min(heatTransfer, temperature * 0.1f);

                    float newTemp = itemTemp + heatTransfer;
                    setItemTemperature(stack, newTemp); // ВАЖНО: сохраняем в HotItemHandler!
                    slotTemperatures[slotIndex] = newTemp;
                    temperature -= heatTransfer * 0.5f;
                }
            } else {
                // Проверка места в резервуаре перед плавкой
                int outputAmount = (slot.recipe != null) ? slot.recipe.outputUnits() : slot.slagData.amount;
                if (!hasSpaceFor(outputAmount)) {
                    slot.active = false; // Нет места — ждем
                } else {
                    slot.active = true;
                }
            }

            slot.itemTemperature = getItemTemperature(stack);

            if (slot.active) {
                totalHeatConsumption += slot.heatConsumption;
                totalMaxProgress += slot.maxProgress;
                activeSlotsCount++;
            }
        }

        // === ОБНОВЛЕНИЕ ТРЕБУЕМОЙ ТЕМПЕРАТУРЫ ===
        requiredTempBottom = hasAnyRecipe ? calculateMaxTempForBottomRow() : 0;

        // === ФАЗА 2: СИНХРОННАЯ ПЛАВКА ===
        if (!hasAnyRecipe || activeSlotsCount == 0) {
            resetBottomSmelting();
            return;
        }

        // Если не все нагреты или нет температуры печи — ждём
        if (!allHeated || temperature < requiredTempBottom * 0.9f) {
            bottomSmelting = false;
            allBottomSlotsReady = false;
            // Сбрасываем прогресс если начали греться но ещё не готовы
            if (!allHeated) {
                sharedBottomProgress = 0; // Сброс прогресса если остыли
            }
            updateBottomGUI(activeSlotsCount, totalHeatConsumption);
            return; // ВАЖНО: выходим, не продолжаем плавку!
        }

        // Все готовы! Запускаем/продолжаем плавку
        if (!allBottomSlotsReady) {
            allBottomSlotsReady = true;
            // При первом старте считаем общий макс если он не задан
            if (sharedBottomMaxProgress <= 0) {
                sharedBottomMaxProgress = totalMaxProgress;
            }
        }

        bottomSmelting = true;

        // Применяем тепло к общему прогрессу
        float availableHeat = Math.min(totalHeatConsumption, temperature);
        float heatToApply = Math.min(availableHeat, sharedBottomMaxProgress - sharedBottomProgress);

        sharedBottomProgress += heatToApply;
        temperature = Math.max(0, temperature - heatToApply);

        // === РАСПРЕДЕЛЯЕМ ПРОГРЕСС И ЗАВЕРШАЕМ ГОТОВЫЕ ===
        float progressRatio = sharedBottomMaxProgress > 0 ? sharedBottomProgress / sharedBottomMaxProgress : 0;
        boolean allCompleted = true;

        for (int i = 0; i < 4; i++) {
            if (bottomSlots[i] == null || !bottomSlots[i].active) continue;

            BottomSlotData slot = bottomSlots[i];
            slot.progress = progressRatio * slot.maxProgress;

            // Проверяем завершение этого слота
            if (slot.progress >= slot.maxProgress * 0.999f) {
                completeBottomSlot(i);
            } else {
                allCompleted = false;
            }
        }

        // Обновление GUI
        bottomProgress = (int) sharedBottomProgress;
        bottomMaxProgress = (int) sharedBottomMaxProgress;
        bottomHeatConsumption = (int) totalHeatConsumption;

        // Сброс если всё завершено
        if (allCompleted || sharedBottomProgress >= sharedBottomMaxProgress * 0.999f) {
            resetBottomSmelting();
        }
    }

// === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    private boolean areItemsSameIgnoreHeat(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem() || a.getCount() != b.getCount()) return false;

        if (a.hasTag() == b.hasTag()) {
            if (!a.hasTag()) return true;

            CompoundTag tagA = a.getTag().copy();
            CompoundTag tagB = b.getTag().copy();

            // Удаляем теги нагрева перед сравнением
            tagA.remove("HotTime");
            tagA.remove("HotTimeMax");
            tagA.remove("MeltingPoint");
            tagA.remove("CooledInPot");

            tagB.remove("HotTime");
            tagB.remove("HotTimeMax");
            tagB.remove("MeltingPoint");
            tagB.remove("CooledInPot");

            return tagA.equals(tagB);
        }
        return false;
    }

    private int calculateMaxTempForBottomRow() {
        int maxTemp = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = inventory.getStackInSlot(4 + i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof SlagItem) {
                Metal metal = SlagItem.getMetal(stack);
                if (metal != null) maxTemp = Math.max(maxTemp, metal.getMeltingPoint());
            } else {
                SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
                if (recipe != null) maxTemp = Math.max(maxTemp, recipe.minTemp());
            }
        }
        return maxTemp;
    }

    private void updateBottomGUI(int activeCount, float totalHeat) {
        // Для GUI показываем текущее состояние нагрева
        bottomProgress = (int) sharedBottomProgress;
        bottomMaxProgress = (int) sharedBottomMaxProgress;
        bottomHeatConsumption = (int) totalHeat;
    }

    private void completeBottomSlot(int slotIndex) {
        ItemStack stack = inventory.getStackInSlot(4 + slotIndex);
        BottomSlotData slot = bottomSlots[slotIndex];
        if (slot == null) return;

        // Выдаем металл в зависимости от типа переплавки
        if (slot.recipe != null) {
            stack.shrink(1);
            addMetal(slot.recipe.output(), slot.recipe.outputUnits());
        } else if (slot.slagData != null) {
            stack.shrink(1);
            addMetal(slot.slagData.metal, slot.slagData.amount);
        } else {
            return;
        }

        slotTemperatures[4 + slotIndex] = 20;
        bottomSlots[slotIndex] = null;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void resetBottomSmelting() {
        if (bottomSmelting || !currentBottomRecipes.isEmpty() || sharedBottomProgress > 0) {
            bottomSmelting = false;
            currentBottomRecipes.clear();
            Arrays.fill(bottomSlots, null);
            bottomProgress = 0;
            bottomMaxProgress = 0;
            requiredTempBottom = 0;
            // === СБРОС СИНХРОННЫХ ПОЛЕЙ ===
            sharedBottomProgress = 0;
            sharedBottomMaxProgress = 0;
            allBottomSlotsReady = false;
        }
    }




    /**
     * ДВУСТОРОННИЙ ТЕПЛООБМЕН: ПЕЧЬ ↔ ПРЕДМЕТЫ
     * Предметы нагреваются/остывают до температуры печи через HotItemHandler
     */
    private void processItemHeatExchange() {
        float heatTransferRate = 3.0f; // Скорость передачи тепла

        for (int i = 0; i < 8; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                slotTemperatures[i] = Math.max(20, slotTemperatures[i] - 1);
                continue;
            }

            // Получаем текущую температуру предмета через HotItemHandler
            float itemTemp = getItemTemperature(stack);
            float furnaceTemp = this.temperature;

            // Теплообмен: предмет → печь или печь → предмет
            if (Math.abs(itemTemp - furnaceTemp) > 1.0f) {
                float diff = furnaceTemp - itemTemp;
                // Чем больше разница, тем быстрее передача
                float transfer = Math.signum(diff) * Math.min(Math.abs(diff) * 0.15f, heatTransferRate);

                float newTemp = itemTemp + transfer;

                // Устанавливаем температуру предмета через HotItemHandler (реальный нагрев!)
                setItemTemperature(stack, newTemp);
                slotTemperatures[i] = newTemp;

                // Влияние на температуру печи (обратная связь)
                // Горячий предмет нагревает печь, холодный - остужает
                float furnaceInfluence = -transfer * 0.02f; // 2% от передачи влияет на печь
                this.temperature = Math.max(0, this.temperature + furnaceInfluence);

                // Помечаем инвентарь как изменённый для синхронизации
                if ((int)newTemp % 5 == 0) { // Не каждый тик, чтобы не спамить сеть
                    setChanged();
                }
            }
        }
    }
    /**
     * Получает температуру плавления для предмета
     */
    private int getMeltingPointForItem(ItemStack stack) {
        // Проверяем рецепт плавки
        SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
        if (recipe != null) {
            return recipe.minTemp();
        }

        // Для шлака - температура плавления металла внутри
        if (stack.getItem() instanceof SlagItem) {
            return SlagItem.getMeltingPoint(stack);
        }

        // Сплавы в верхнем ряду - проверяем по рецептам
        for (AlloyRecipe alloy : MetallurgyRegistry.getAllAlloyRecipes()) {
            for (AlloySlot slot : alloy.getSlots()) {
                if (slot.item() == stack.getItem()) {
                    return alloy.getOutputMetal().getMeltingPoint();
                }
            }
        }

        // Для угля и неметаллов - фиксированная температура "нагрева для реакции"
        if (stack.is(net.minecraft.world.item.Items.COAL) ||
                stack.is(net.minecraft.world.item.Items.CHARCOAL)) {
            return 300;
        }

        return 800; // По умолчанию для неизвестных предметов
    }
    /**
     * Получает температуру предмета из HotItemHandler
     */
    private float getItemTemperature(ItemStack stack) {
        if (HotItemHandler.isHot(stack)) {
            return HotItemHandler.getTemperature(stack);
        }
        return HotItemHandler.ROOM_TEMP;
    }

    /**
     * Устанавливает РЕАЛЬНУЮ температуру предмета через HotItemHandler
     * Предмет можно вытащить и он сохранит нагрев!
     */
    private void setItemTemperature(ItemStack stack, float temp) {

        if (temp <= HotItemHandler.ROOM_TEMP) {
            if (HotItemHandler.isHot(stack)) {
                HotItemHandler.clearHotTags(stack);
                // Синхронизируем с клиентом
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
            return;
        }

        // Получаем/вычисляем температуру плавления для предмета
        int meltingPoint = getMeltingPointForItem(stack);
        if (meltingPoint <= 0) meltingPoint = 1000;

        // Вычисляем heatRatio: temp = ROOM_TEMP + heatRatio * (meltingPoint - ROOM_TEMP)
        // heatRatio = (temp - ROOM_TEMP) / (meltingPoint - ROOM_TEMP)
        float heatRatio = (temp - HotItemHandler.ROOM_TEMP) / (meltingPoint - HotItemHandler.ROOM_TEMP);
        heatRatio = Math.max(0, Math.min(1.2f, heatRatio)); // Можем перегреть до 120%!

        // Устанавливаем HotTime в зависимости от heatRatio
        // При heatRatio = 1.0 (температура плавления) - полный HotTime
        int maxTime = HotItemHandler.BASE_COOLING_TIME_HANDS;
        float hotTime = heatRatio * maxTime;

        // Записываем в NBT предмета
        stack.getOrCreateTag().putFloat("HotTime", hotTime);
        stack.getOrCreateTag().putInt("HotTimeMax", maxTime);
        stack.getOrCreateTag().putInt("MeltingPoint", meltingPoint);
        stack.getOrCreateTag().putBoolean("CooledInPot", false); // В печи, не в котле

        // Синхронизируем с клиентом для отображения градиента
        if (level != null && !level.isClientSide && (int)hotTime % 10 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Получает температуру из NBT предмета (если есть)
     */
    private float getSlotTemperatureFromNBT(ItemStack stack) {
        // Проверяем кастомный тег температуры для предметов без HotItemHandler
        if (stack.hasTag() && stack.getTag().contains("SmelterTemp")) {
            return stack.getTag().getFloat("SmelterTemp");
        }
        return HotItemHandler.ROOM_TEMP;
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

    // Геттер для температур слотов (для рендера)
    public float getSlotTemperature(int slot) {
        return slot >= 0 && slot < 8 ? slotTemperatures[slot] : 20;
    }

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
        tag.putFloat("SharedBottomProgress", sharedBottomProgress);
        tag.putFloat("SharedBottomMaxProgress", sharedBottomMaxProgress);
        tag.putBoolean("AllBottomSlotsReady", allBottomSlotsReady);
        tag.putFloat("SharedTopProgress", sharedTopProgress);
        tag.putFloat("SharedTopMaxProgress", sharedTopMaxProgress);
        tag.putBoolean("AllTopSlotsReady", allTopSlotsReady);

        // Сохраняем температуры слотов
        ListTag tempsTag = new ListTag();
        for (float temp : slotTemperatures) {
            CompoundTag t = new CompoundTag();
            t.putFloat("Temp", temp);
            tempsTag.add(t);
        }
        tag.put("SlotTemperatures", tempsTag);

        ListTag bottomSlotsTag = new ListTag();
        for (int i = 0; i < 4; i++) {
            CompoundTag slotTag = new CompoundTag();
            if (bottomSlots[i] != null) {
                slotTag.putBoolean("HasData", true);
                slotTag.putFloat("Progress", bottomSlots[i].progress);
                slotTag.putFloat("MaxProgress", bottomSlots[i].maxProgress);
                slotTag.putFloat("HeatConsumption", bottomSlots[i].heatConsumption);
                slotTag.putBoolean("Active", bottomSlots[i].active);
                slotTag.putFloat("ItemTemperature", bottomSlots[i].itemTemperature);
                slotTag.putInt("TargetTemperature", bottomSlots[i].targetTemperature);

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
        sharedBottomProgress = tag.getFloat("SharedBottomProgress");
        sharedBottomMaxProgress = tag.getFloat("SharedBottomMaxProgress");
        allBottomSlotsReady = tag.getBoolean("AllBottomSlotsReady");
        sharedTopProgress = tag.getFloat("SharedTopProgress");
        sharedTopMaxProgress = tag.getFloat("SharedTopMaxProgress");
        allTopSlotsReady = tag.getBoolean("AllTopSlotsReady");

        // Загружаем температуры слотов
        if (tag.contains("SlotTemperatures")) {
            ListTag tempsTag = tag.getList("SlotTemperatures", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(8, tempsTag.size()); i++) {
                slotTemperatures[i] = tempsTag.getCompound(i).getFloat("Temp");
            }
        }

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
                    bottomSlots[i].itemTemperature = slotTag.getFloat("ItemTemperature");
                    bottomSlots[i].targetTemperature = slotTag.getInt("TargetTemperature");

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