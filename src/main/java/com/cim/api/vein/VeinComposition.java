package com.cim.api.vein;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VeinComposition {
    private final Map<String, Integer> metals; // metal -> percent
    private final int slagPercent;

    public VeinComposition(Map<String, Integer> metals) {
        this.metals = new HashMap<>(metals);
        int total = this.metals.values().stream().mapToInt(Integer::intValue).sum();
        this.slagPercent = Math.max(0, 100 - total);
    }

    public Map<String, Integer> getMetals() {
        return Collections.unmodifiableMap(metals);
    }

    public int getSlagPercent() {
        return slagPercent;
    }

    public Map<String, Integer> getFullComposition() {
        Map<String, Integer> full = new HashMap<>(metals);
        if (slagPercent > 0) full.put("slag", slagPercent);
        return full;
    }

    public String getPrimaryMetal() {
        return metals.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("mixed");
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        metals.forEach(tag::putInt);
        return tag;
    }

    public static VeinComposition deserialize(CompoundTag tag) {
        Map<String, Integer> metals = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            metals.put(key, tag.getInt(key));
        }
        return new VeinComposition(metals);
    }
}