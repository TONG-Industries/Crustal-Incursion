package com.cim.datagen.recipes;

import com.cim.main.ResourceRegistry;
import com.cim.main.ResourceRegistry.ResourceEntry;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/**
 * Автоматическая генерация рецептов для ресурсов из ResourceRegistry.
 * Создает рецепты:
 * - 9 мелких единиц → 1 основная
 * - 1 основная → 9 мелких
 * - 9 основных → 1 блок
 * - 1 блок → 9 основных
 */
public class ResourceRecipeHelper {

    /**
     * Генерирует все рецепты для всех зарегистрированных ресурсов
     */
    public static void generateRecipes(Consumer<FinishedRecipe> writer) {
        for (ResourceEntry resource : ResourceRegistry.getResources()) {
            generateRecipesForResource(writer, resource);
        }
    }

    /**
     * Генерирует рецепты для конкретного ресурса
     */
    private static void generateRecipesForResource(Consumer<FinishedRecipe> writer, ResourceEntry resource) {
        String name = resource.name;

        // 1. Мелкая единица → Основная (9:1) - если есть мелкая единица
        if (resource.hasSmallUnit()) {
            // 9 самородков/кусочков/осколков = 1 слиток/гранула/кристалл
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, resource.mainUnit.get())
                    .requires(resource.smallUnit.get(), 9)
                    .unlockedBy("has_" + resource.getSmallUnitId(), has(resource.smallUnit.get()))
                    .save(writer, getRecipeId(name, "small_to_main"));

            // Обратно: 1 слиток = 9 самородков
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, resource.smallUnit.get(), 9)
                    .requires(resource.mainUnit.get())
                    .unlockedBy("has_" + resource.getMainUnitId(), has(resource.mainUnit.get()))
                    .save(writer, getRecipeId(name, "main_to_small"));
        }

        // 2. Основная единица → Блок (9:1) - если есть блок
        if (resource.hasBlock()) {
            // 9 слитков/гранул = 1 блок
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, resource.block.get())
                    .pattern("###")
                    .pattern("###")
                    .pattern("###")
                    .define('#', resource.mainUnit.get())
                    .unlockedBy("has_" + resource.getMainUnitId(), has(resource.mainUnit.get()))
                    .save(writer, getRecipeId(name, "main_to_block"));

            // Обратно: блок → 9 слитков
            ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, resource.mainUnit.get(), 9)
                    .requires(resource.block.get())
                    .unlockedBy("has_" + resource.getBlockId(), has(resource.block.get()))
                    .save(writer, getRecipeId(name, "block_to_main"));
        }

        // 3. Если есть и мелкая единица, и блок - создаем прямой крафт блока из мелких (81:1)
        if (resource.hasSmallUnit() && resource.hasBlock()) {
            // 81 мелких = 1 блок (через слитки, но для удобства добавим прямой рецепт)
            // Это опционально - можно убрать если избыточно
            ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, resource.block.get())
                    .pattern("###")
                    .pattern("###")
                    .pattern("###")
                    .define('#', resource.smallUnit.get())
                    .unlockedBy("has_" + resource.getSmallUnitId(), has(resource.smallUnit.get()))
                    .save(writer, getRecipeId(name, "small_to_block_direct"));
        }
    }

    /**
     * Генерирует рецепты переплавки (если есть руда → основная единица)
     * Вызывать отдельно если нужны рецепты плавки
     */
    public static void generateSmeltingRecipes(Consumer<FinishedRecipe> writer,
                                               ResourceEntry resource,
                                               RegistryObject<Item> oreItem,
                                               float experience,
                                               int cookingTime) {

        // Печь
        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(oreItem.get()),
                        RecipeCategory.MISC,
                        resource.mainUnit.get(),
                        experience,
                        cookingTime
                )
                .unlockedBy("has_" + oreItem.getId().getPath(), has(oreItem.get()))
                .save(writer, getRecipeId(resource.name, "from_smelting"));

        // Плавильная печь (быстрее)
        SimpleCookingRecipeBuilder.blasting(
                        Ingredient.of(oreItem.get()),
                        RecipeCategory.MISC,
                        resource.mainUnit.get(),
                        experience,
                        cookingTime / 2
                )
                .unlockedBy("has_" + oreItem.getId().getPath(), has(oreItem.get()))
                .save(writer, getRecipeId(resource.name, "from_blasting"));
    }

    // ============ Вспомогательные методы ============

    private static String getRecipeId(String resourceName, String recipeType) {
        return "cim:" + resourceName + "_" + recipeType;
    }

    private static String has(RegistryObject<Item> item) {
        return "has_" + item.getId().getPath();
    }

    private static net.minecraft.advancements.critereon.InventoryChangeTrigger.TriggerInstance
    has(ItemLike item) {
        return net.minecraft.advancements.critereon.InventoryChangeTrigger.TriggerInstance
                .hasItems(item);
    }
}