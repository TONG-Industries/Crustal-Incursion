package com.cim.api.metallurgy.system.recipe;

import net.minecraft.world.item.Item;

public record AlloySlot(Item item, int count) {
    public static final AlloySlot EMPTY = new AlloySlot(null, 0);
}