package com.cim.datagen.recipes;

import com.cim.main.ResourceRegistry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;
import com.cim.main.CrustalIncursionMod;
import com.cim.block.basic.ModBlocks;

import java.util.List;
import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public ModRecipeProvider(PackOutput output) {
        super(output);
        // !!! ВАЖНО: Инициализируем ResourceRegistry !!!
        ResourceRegistry.init();
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        // --- 1. АВТОМАТИЧЕСКИЕ РЕЦЕПТЫ ДЛЯ РЕСУРСОВ ---
        ResourceRecipeHelper.generateRecipes(writer);

        // --- 2. РУЧНЫЕ РЕЦЕПТЫ ---
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.DET_MINER.get())
                .pattern("III")
                .pattern("IDI")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('D', Items.TNT)
                .unlockedBy("has_tnt", has(Items.TNT))
                .save(writer);


        // --- 2. КРАФТ БЕЗ ФОРМЫ (Shapeless) ---
        // Пример: Разбор блока обратно в ресурсы
        /*
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.NECROTIC_FRAGMENT.get(), 9)
                .requires(ModBlocks.NECROTIC_BLOCK.get())
                .unlockedBy("has_necrotic_block", has(ModBlocks.NECROTIC_BLOCK.get()))
                .save(writer);
        */

        // --- 3. ПЕРЕПЛАВКА (Smelting & Blasting) ---
        // Пример: Переплавка руды в слитки
        // oreSmelting(writer, List.of(ModItems.RAW_URANIUM.get()), RecipeCategory.MISC, ModItems.URANIUM_INGOT.get(), 0.7f, 200, "uranium");
    }

    // Вспомогательный метод для массовой переплавки (как в ванилле)
    protected static void oreSmelting(Consumer<FinishedRecipe> writer, List<ItemLike> ingredients, RecipeCategory category, ItemLike result, float experience, int cookingTime, String group) {
        oreCooking(writer, RecipeSerializer.SMELTING_RECIPE, ingredients, category, result, experience, cookingTime, group, "_from_smelting");
    }

    protected static void oreCooking(Consumer<FinishedRecipe> writer, RecipeSerializer<? extends AbstractCookingRecipe> serializer, List<ItemLike> ingredients, RecipeCategory category, ItemLike result, float experience, int cookingTime, String group, String suffix) {
        for(ItemLike itemlike : ingredients) {
            SimpleCookingRecipeBuilder.generic(Ingredient.of(itemlike), category, result, experience, cookingTime, serializer)
                    .group(group).unlockedBy(getHasName(itemlike), has(itemlike))
                    .save(writer, CrustalIncursionMod.MOD_ID + ":" + getItemName(result) + suffix + "_" + getItemName(itemlike));
        }
    }
}