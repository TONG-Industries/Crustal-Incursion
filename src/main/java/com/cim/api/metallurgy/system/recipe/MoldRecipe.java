package com.cim.api.metallurgy.system.recipe;

import com.cim.api.metallurgy.system.Metal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public class MoldRecipe {
    private final Item moldItem;
    private final int requiredUnits;
    private final Function<Metal, ItemStack> outputFactory;

    public MoldRecipe(Item moldItem, int requiredUnits, Function<Metal, ItemStack> outputFactory) {
        this.moldItem = moldItem;
        this.requiredUnits = requiredUnits;
        this.outputFactory = outputFactory;
    }

    public Item getMoldItem() {
        return moldItem;
    }

    public int getRequiredUnits() {
        return requiredUnits;
    }

    public ItemStack createOutput(Metal metal) {
        return outputFactory.apply(metal);
    }

    public boolean isValidFor(Metal metal) {
        return !outputFactory.apply(metal).isEmpty();
    }
}