package com.cim.api.metallurgy.system.recipe;

import com.cim.api.metallurgy.system.Metal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class MoldRecipeRegistry {
    private static final Map<Item, MoldRecipe> RECIPES = new HashMap<>();

    public static void register(MoldRecipe recipe) {
        RECIPES.put(recipe.getMoldItem(), recipe);
    }

    public static void register(Item moldItem, int requiredUnits, java.util.function.Function<Metal, ItemStack> outputFactory) {
        register(new MoldRecipe(moldItem, requiredUnits, outputFactory));
    }

    public static MoldRecipe getRecipe(Item moldItem) {
        return RECIPES.get(moldItem);
    }

    public static boolean hasRecipe(Item moldItem) {
        return RECIPES.containsKey(moldItem);
    }
}