package com.cim.datagen.recipes;

import com.cim.item.ModItems;
import com.cim.main.ResourceRegistry;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;
import com.cim.main.CrustalIncursionMod;
import com.cim.block.basic.ModBlocks;

import java.util.List;
import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public ModRecipeProvider(PackOutput output) {
        super(output);
        ResourceRegistry.init();
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        ResourceRecipeHelper.generateRecipes(writer);

        // --- КРАФТЫ ИЗ КАЛА ---
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.FIRE_SMES.get())
                .requires(Items.CLAY_BALL, 3)
                .requires(ModItems.BAUXITE_POWDER.get())
                .unlockedBy("has_bauxite_powder", has(ModItems.BAUXITE_POWDER.get()))
                .save(writer);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DOLOMITE_SMES.get())
                .requires(ModItems.DOLOMITE_POWDER.get(), 3)
                .requires(Items.CLAY_BALL)
                .unlockedBy("has_dolomite_powder", has(ModItems.DOLOMITE_POWDER.get()))
                .save(writer);


        // --- ПЕРЕПЛАВКА ---
        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(ModItems.FIRE_SMES.get()),
                        RecipeCategory.MISC,
                        ModItems.FIREBRICK.get(),
                        0.3f,
                        200)
                .unlockedBy("has_fire_smes", has(ModItems.FIRE_SMES.get()))
                .save(writer, CrustalIncursionMod.MOD_ID + ":firebrick_from_smelting");

        SimpleCookingRecipeBuilder.smelting(
                        Ingredient.of(ModItems.DOLOMITE_SMES.get()),
                        RecipeCategory.MISC,
                        ModItems.REINFORCEDBRICK.get(),
                        0.3f,
                        200)
                .unlockedBy("has_dolomite_smes", has(ModItems.DOLOMITE_SMES.get()))
                .save(writer, CrustalIncursionMod.MOD_ID + ":dolomite_brick_from_smelting");


        // --- ОСТАЛЬНЫЕ РЕЦЕПТЫ ---
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.DET_MINER.get())
                .pattern("III")
                .pattern("IDI")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('D', Items.TNT)
                .unlockedBy("has_tnt", has(Items.TNT))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.JERNOVA.get())
                .pattern("  I")
                .pattern("###")
                .pattern("@@@")
                .define('I', Items.STICK)
                .define('#', Blocks.SMOOTH_STONE_SLAB)
                .define('@', Blocks.SMOOTH_STONE)
                .unlockedBy("has_smooth_stone", has(Blocks.SMOOTH_STONE))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.SMALL_SMELTER.get())
                .pattern("###")
                .pattern("#I#")
                .pattern("@@@")
                .define('I', ModItems.DOLOMITE_SMES.get())
                .define('#', ModItems.FIREBRICK.get())
                .define('@', ModItems.REINFORCEDBRICK.get())
                .unlockedBy("has_reinforcedbrick", has(ModItems.REINFORCEDBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.SMELTER.get())
                .pattern("###")
                .pattern("#I#")
                .pattern("@@@")
                .define('I', ModItems.DOLOMITE_SMES.get())
                .define('#', ModBlocks.FIREBRICK_BLOCK.get())
                .define('@', ModBlocks.REINFORCEDBRICK_BLOCK.get())
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.HEATER.get())
                .pattern("###")
                .pattern("@I@")
                .pattern("@@@")
                .define('I', Blocks.COPPER_BLOCK)
                .define('#', ModItems.FIREBRICK.get())
                .define('@', ModBlocks.REINFORCEDBRICK_BLOCK.get())
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.CASTING_DESCENT.get())
                .pattern("   ")
                .pattern("@ @")
                .pattern(" @ ")
                .define('@', ModItems.REINFORCEDBRICK.get())
                .unlockedBy("has_reinforcedbrick", has(ModItems.REINFORCEDBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.POKER.get())
                .pattern("@@ ")
                .pattern(" @ ")
                .pattern(" @ ")
                .define('@', Items.IRON_INGOT)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.CASTING_POT.get())
                .pattern("   ")
                .pattern("$#$")
                .pattern("@@@")
                .define('$', ModItems.FIREBRICK.get())
                .define('@', ModItems.REINFORCEDBRICK.get())
                .define('#', ModItems.DOLOMITE_SMES.get())
                .unlockedBy("has_reinforcedbrick", has(ModItems.REINFORCEDBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOLD_NUGGET.get())
                .pattern("@@@")
                .pattern("@#@")
                .pattern("@@@")
                .define('@', ModItems.FIREBRICK.get())
                .define('#', ModItems.DOLOMITE_SMES.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOLD_INGOT.get())
                .pattern("@@@")
                .pattern("###")
                .pattern("@@@")
                .define('@', ModItems.FIREBRICK.get())
                .define('#', ModItems.DOLOMITE_SMES.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.MOLD_BLOCK.get())
                .pattern("@#@")
                .pattern("###")
                .pattern("@#@")
                .define('@', ModItems.FIREBRICK.get())
                .define('#', ModItems.DOLOMITE_SMES.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        // === FIREBRICK ===
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FIREBRICK_BLOCK.get(), 1)
                .pattern("II")
                .pattern("II")
                .define('I', ModItems.FIREBRICK.get())
                .unlockedBy("has_firebrick", has(ModItems.FIREBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FIREBRICK_SLAB.get(), 6)
                .pattern("III")
                .define('I', ModBlocks.FIREBRICK_BLOCK.get())
                .unlockedBy("has_firebrick_block", has(ModBlocks.FIREBRICK_BLOCK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.FIREBRICK_STAIRS.get(), 4)
                .pattern("I  ")
                .pattern("II ")
                .pattern("III")
                .define('I', ModBlocks.FIREBRICK_BLOCK.get())
                .unlockedBy("has_firebrick_block", has(ModBlocks.FIREBRICK_BLOCK.get()))
                .save(writer);

        SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(ModBlocks.FIREBRICK_BLOCK.get()),
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.FIREBRICK_SLAB.get(), 2)
                .unlockedBy("has_firebrick_block", has(ModBlocks.FIREBRICK_BLOCK.get()))
                .save(writer, CrustalIncursionMod.MOD_ID + ":firebrick_slab_from_stonecutting");

        SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(ModBlocks.FIREBRICK_BLOCK.get()),
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.FIREBRICK_STAIRS.get(), 1)
                .unlockedBy("has_firebrick_block", has(ModBlocks.FIREBRICK_BLOCK.get()))
                .save(writer, CrustalIncursionMod.MOD_ID + ":firebrick_stairs_from_stonecutting");


// === REINFORCED BRICK ===
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.REINFORCEDBRICK_BLOCK.get(), 1)
                .pattern("II")
                .pattern("II")
                .define('I', ModItems.REINFORCEDBRICK.get())
                .unlockedBy("has_reinforcedbrick", has(ModItems.REINFORCEDBRICK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.REINFORCEDBRICK_SLAB.get(), 6)
                .pattern("III")
                .define('I', ModBlocks.REINFORCEDBRICK_BLOCK.get())
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.REINFORCEDBRICK_STAIRS.get(), 4)
                .pattern("I  ")
                .pattern("II ")
                .pattern("III")
                .define('I', ModBlocks.REINFORCEDBRICK_BLOCK.get())
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer);

        SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(ModBlocks.REINFORCEDBRICK_BLOCK.get()),
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.REINFORCEDBRICK_SLAB.get(), 2)
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer, CrustalIncursionMod.MOD_ID + ":reinforcedbrick_slab_from_stonecutting");

        SingleItemRecipeBuilder.stonecutting(
                        Ingredient.of(ModBlocks.REINFORCEDBRICK_BLOCK.get()),
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.REINFORCEDBRICK_STAIRS.get(), 1)
                .unlockedBy("has_reinforcedbrick_block", has(ModBlocks.REINFORCEDBRICK_BLOCK.get()))
                .save(writer, CrustalIncursionMod.MOD_ID + ":reinforcedbrick_stairs_from_stonecutting");

    }

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