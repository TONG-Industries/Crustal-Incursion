package com.cim.worldgen.feature;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.*;
import com.cim.main.CrustalIncursionMod;

import java.util.List;

// Размещать в: src/main/java/razchexlitiel/cim/worldgen/ModPlacedFeatures.java
public class ModPlacedFeatures {

    // 1. Уникальный ключ для размещенной структуры
    public static final ResourceKey<PlacedFeature> GIANT_SEQUOIA_PLACED_KEY = registerKey("giant_sequoia_placed");
    public static final ResourceKey<PlacedFeature> SMALL_SEQUOIA_PLACED_KEY = registerKey("small_sequoia_placed");
    public static final ResourceKey<PlacedFeature> MEDIUM_SEQUOIA_PLACED_KEY = registerKey("medium_sequoia_placed");

    // 2. Сборка (DataGen)
    public static void bootstrap(BootstapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        // === АВТО-РАЗМЕЩЕНИЕ РУД (вставлять СЮДА) ===
        for (OreVeinRegistry.OreEntry ore : OreVeinRegistry.ORES) {
            Holder<ConfiguredFeature<?, ?>> configured = configuredFeatures.getOrThrow(ore.configuredKey);
            register(context, ore.placedKey, configured, List.of(
                    CountPlacement.of(ore.countPerChunk),
                    InSquarePlacement.spread(),
                    HeightRangePlacement.uniform(
                            VerticalAnchor.absolute(ore.minY),
                            VerticalAnchor.absolute(ore.maxY)
                    ),
                    BiomeFilter.biome()
            ));
        }

        // === СПЕЦ-ЖИЛЫ: шанс раз в N чанков ===
        for (OreVeinRegistry.SpecialOreEntry ore : OreVeinRegistry.SPECIAL_ORES) {
            Holder<ConfiguredFeature<?, ?>> configured = configuredFeatures.getOrThrow(ore.configuredKey);
            register(context, ore.placedKey, configured, List.of(
                    RarityFilter.onAverageOnceEvery(ore.rarity), // вот он — настраиваемый шанс!
                    InSquarePlacement.spread(),
                    HeightRangePlacement.uniform(
                            VerticalAnchor.absolute(ore.minY),
                            VerticalAnchor.absolute(ore.maxY)
                    ),
                    BiomeFilter.biome()
            ));
        }

        var smallSequoia = context.lookup(Registries.CONFIGURED_FEATURE).getOrThrow(ModConfiguredFeatures.SMALL_SEQUOIA_KEY);
        var mediumSequoia = context.lookup(Registries.CONFIGURED_FEATURE).getOrThrow(ModConfiguredFeatures.MEDIUM_SEQUOIA_KEY);
        // === КОНГЛОМЕРАТЫ ===
        for (OreVeinRegistry.ConglomerateEntry entry : OreVeinRegistry.CONGLOMERATES) {
            Holder<ConfiguredFeature<?, ?>> configured = configuredFeatures.getOrThrow(entry.configuredKey);
            register(context, entry.placedKey, configured, List.of(
                    RarityFilter.onAverageOnceEvery(entry.rarity),
                    InSquarePlacement.spread(),
                    HeightRangePlacement.uniform(
                            VerticalAnchor.absolute(entry.minY),
                            VerticalAnchor.absolute(entry.maxY)
                    ),
                    BiomeFilter.biome()
            ));
        }

        // Регистрируем размещение нашей секвойи
        register(context, GIANT_SEQUOIA_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.GIANT_SEQUOIA_KEY),
                // PlacementUtils.treePlacement - это удобный ванильный метод, который сам добавляет:
                // 1) Поиск поверхности, 2) Проверку, что под деревом земля/трава, 3) Проверку биома
                List.of(
                        RarityFilter.onAverageOnceEvery(5),                     // 1. Редкость (1 раз на 5 чанков)
                        InSquarePlacement.spread(),                             // 2. Случайный сдвиг по X и Z внутри чанка
                        SurfaceWaterDepthFilter.forMaxDepth(0),                 // 3. Запрещаем спавн в воде
                        PlacementUtils.HEIGHTMAP_OCEAN_FLOOR,                   // 4. Ищем самый верхний твердый блок
                        PlacementUtils.filteredByBlockSurvival(Blocks.OAK_SAPLING), // 5. Проверка: может ли тут стоять саженец (чтобы не спавнилось на камне/льду)
                        BiomeFilter.biome()                                     // 6. Строгое правило: только в нашем биоме
                ));

        PlacementUtils.register(context, SMALL_SEQUOIA_PLACED_KEY, smallSequoia,
                VegetationPlacements.treePlacement(PlacementUtils.countExtra(1, 0.1f, 1), Blocks.SPRUCE_SAPLING));

        // Спавн Средних Секвой
        PlacementUtils.register(context, MEDIUM_SEQUOIA_PLACED_KEY, mediumSequoia,
                VegetationPlacements.treePlacement(PlacementUtils.countExtra(0, 0.25f, 1), Blocks.SPRUCE_SAPLING));
    }

    // --- Вспомогательные методы ---
    private static ResourceKey<PlacedFeature> registerKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, new ResourceLocation(CrustalIncursionMod.MOD_ID, name));
    }

    private static void register(BootstapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }
}
