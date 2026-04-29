package com.cim.api.metallurgy.system;

import com.cim.api.metallurgy.system.recipe.AlloyRecipe;
import com.cim.api.metallurgy.system.recipe.SmeltRecipe;
import com.cim.main.CrustalIncursionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.*;

public class MetallurgyRegistry {
    private static final Map<ResourceLocation, Metal> METALS = new LinkedHashMap<>();
    private static final Map<Item, SmeltRecipe> SMELT_RECIPES = new HashMap<>();
    private static final List<AlloyRecipe> ALLOY_RECIPES = new ArrayList<>();

    // СТАНДАРТНЫЕ ВРЕМЕНА ПЛАВКИ (в тиках, 20 тиков = 1 секунда)
    public static final int INGOT_SMELT_TIME = 60;   // 3 секунды
    public static final int BLOCK_SMELT_TIME = 180;  // 9 секунд
    public static final int NUGGET_SMELT_TIME = 20;  // 1 секунда

    public static Metal registerMetal(String name, int color, int meltingPoint,
                                      int baseUnits, int smallUnits, int blockUnits,
                                      float heatConsumptionPerTick) {
        ResourceLocation id = new ResourceLocation(CrustalIncursionMod.MOD_ID, name);
        Metal metal = new Metal(id, color, meltingPoint, baseUnits, smallUnits, blockUnits,
                heatConsumptionPerTick);
        METALS.put(id, metal);
        return metal;
    }
    public static Collection<SmeltRecipe> getAllSmeltRecipes() {
        return Collections.unmodifiableCollection(SMELT_RECIPES.values());
    }
    /**
     * Генерирует стандартные рецепты для всех зарегистрированных металлов
     * Использует фиксированные времена плавки + потребление из металла
     */
    public static void generateStandardRecipes() {
        for (Metal metal : METALS.values()) {
            // Слиток: 3 секунды, потребление из металла
            if (metal.getIngot() != null) {
                addSmeltRecipe(metal.getIngot(), metal, metal.getBaseUnits(),
                        metal.getMeltingPoint(), metal.getHeatConsumptionPerTick(),
                        INGOT_SMELT_TIME);
            }

            // Самородок: 1 секунда, потребление пропорционально (1/3 от слитка)
            if (metal.getNugget() != null && metal.getSmallUnits() > 0) {
                float nuggetHeat = metal.getHeatConsumptionPerTick() / 3.0f;
                addSmeltRecipe(metal.getNugget(), metal, metal.getSmallUnits(),
                        metal.getMeltingPoint(), nuggetHeat, NUGGET_SMELT_TIME);
            }

            // Блок: 9 секунд, потребление ×3
            if (metal.getBlock() != null) {
                float blockHeat = metal.getHeatConsumptionPerTick() * 3.0f;
                addSmeltRecipe(metal.getBlock().asItem(), metal, metal.getBlockUnits(),
                        metal.getMeltingPoint(), blockHeat, BLOCK_SMELT_TIME);
            }
        }
    }

    /**
     * Добавляет кастомный рецепт плавки с полным контролем
     * @param outputUnits Точное количество единиц металла на выходе
     * @param heatConsumption Потребление температуры за тик
     * @param timeTicks Время плавки в тиках
     */
    public static void addSmeltRecipe(Item input, Metal output, int outputUnits,
                                      int minTemp, float heatConsumption, int timeTicks) {
        SmeltRecipe recipe = new SmeltRecipe(input, output, outputUnits, minTemp,
                heatConsumption, timeTicks);
        SMELT_RECIPES.put(input, recipe);
    }

    /**
     * Упрощённый метод для обратной совместимости (генерирует время из металла)
     * Не рекомендуется для нового кода
     */
    @Deprecated
    public static void addSmeltRecipe(Item input, Metal output, int outputUnits, int timeTicks) {
        addSmeltRecipe(input, output, outputUnits, output.getMeltingPoint(),
                output.getHeatConsumptionPerTick(), timeTicks);
    }

    public static void addAlloyRecipe(AlloyRecipe recipe) {
        ALLOY_RECIPES.add(recipe);
    }

    public static SmeltRecipe getSmeltRecipe(Item input) {
        return SMELT_RECIPES.get(input);
    }

    public static List<AlloyRecipe> getAllAlloyRecipes() {
        return Collections.unmodifiableList(ALLOY_RECIPES);
    }

    public static Optional<Metal> get(ResourceLocation id) {
        return Optional.ofNullable(METALS.get(id));
    }

    public static Collection<Metal> getAllMetals() {
        return Collections.unmodifiableCollection(METALS.values());
    }
}