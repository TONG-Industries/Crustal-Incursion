package com.cim.api.metallurgy.system;


public final class MetalUnits2 {
    public static final int UNITS_PER_NUGGET = 1;
    public static final int UNITS_PER_INGOT = 9;
    public static final int UNITS_PER_BLOCK = 81;

    private MetalUnits2() {}

    public static int convertToUnits(int blocks, int ingots, int nuggets) {
        return blocks * UNITS_PER_BLOCK + ingots * UNITS_PER_INGOT + nuggets * UNITS_PER_NUGGET;
    }

    public static MetalStack convertFromUnits(int totalUnits) {
        if (totalUnits <= 0) return new MetalStack(0, 0, 0, totalUnits);
        int blocks = totalUnits / UNITS_PER_BLOCK;
        int rem = totalUnits % UNITS_PER_BLOCK;
        int ingots = rem / UNITS_PER_INGOT;
        int nuggets = rem % UNITS_PER_INGOT;
        return new MetalStack(blocks, ingots, nuggets, totalUnits);
    }

    public record MetalStack(int blocks, int ingots, int nuggets, int totalUnits) {
        public boolean isEmpty() { return totalUnits == 0; }
    }
}