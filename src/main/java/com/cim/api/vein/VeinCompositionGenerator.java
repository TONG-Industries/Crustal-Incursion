package com.cim.api.vein;

import net.minecraft.util.RandomSource;

import java.util.*;

public class VeinCompositionGenerator {

    private static final List<MetalEntry> METALS = List.of(
            // Common — везде, высокий вес
            new MetalEntry("iron", 0.0f, 1.0f, 35),
            new MetalEntry("copper", 0.0f, 1.0f, 30),
            new MetalEntry("lead", 0.0f, 1.0f, 25),
            new MetalEntry("zinc", 0.0f, 1.0f, 25),
            new MetalEntry("tin", 0.0f, 1.0f, 20),
            new MetalEntry("aluminum", 0.0f, 1.0f, 20),

            // Uncommon — ниже Y=40 (глубже 25% от диапазона)
            new MetalEntry("silver", 0.25f, 1.0f, 15),
            new MetalEntry("gold", 0.25f, 1.0f, 12),
            new MetalEntry("nickel", 0.25f, 1.0f, 12),

            // Rare — только глубоко (ниже Y=0)
            new MetalEntry("uranium", 0.55f, 1.0f, 6),
            new MetalEntry("titanium", 0.55f, 1.0f, 6),
            new MetalEntry("tungsten", 0.55f, 1.0f, 5),
            new MetalEntry("rare_earth", 0.65f, 1.0f, 4)
    );

    public static VeinComposition generate(int y, RandomSource random) {
        // depthFactor: 0.0 на Y=320, 1.0 на Y=-64
        float depthFactor = 1.0f - ((y + 64.0f) / 384.0f);
        depthFactor = Math.max(0.0f, Math.min(1.0f, depthFactor));

        List<MetalEntry> available = new ArrayList<>();
        for (MetalEntry entry : METALS) {
            if (depthFactor >= entry.minDepth && depthFactor <= entry.maxDepth) {
                available.add(entry);
            }
        }

        if (available.isEmpty()) {
            available.add(METALS.get(0)); // fallback iron
        }

        // Выбираем 2-4 металла
        int count = 2 + random.nextInt(3);
        Collections.shuffle(available, new java.util.Random(random.nextLong()));
        List<MetalEntry> selected = new ArrayList<>(available.subList(0, Math.min(count, available.size())));

        // Распределяем проценты
        int totalWeight = selected.stream().mapToInt(e -> e.weight).sum();
        Map<String, Integer> composition = new LinkedHashMap<>();
        int remaining = 100;

        for (int i = 0; i < selected.size(); i++) {
            MetalEntry entry = selected.get(i);
            int percent;
            if (i == selected.size() - 1) {
                percent = remaining;
            } else {
                percent = (entry.weight * 90) / totalWeight;
                int variation = Math.max(2, percent / 4);
                percent = percent - variation + random.nextInt(variation * 2 + 1);
                percent = Math.max(5, Math.min(remaining - 5 * (selected.size() - i - 1), percent));
            }
            composition.put(entry.name, percent);
            remaining -= percent;
        }

        return new VeinComposition(composition);
    }

    private record MetalEntry(String name, float minDepth, float maxDepth, int weight) {}
}