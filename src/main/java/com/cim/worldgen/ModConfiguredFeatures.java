package com.cim.worldgen;

import com.cim.worldgen.tree.custom.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext; // В официальных маппингах может быть опечатка BootstapContext, проверь у себя!
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import com.cim.block.basic.ModBlocks;
import com.cim.main.CrustalIncursionMod;
import com.cim.worldgen.tree.custom.*;

// Размещать в: src/main/java/razchexlitiel/cim/worldgen/ModConfiguredFeatures.java
public class ModConfiguredFeatures {

    // 1. Создаем уникальный ключ для нашего дерева
    public static final ResourceKey<ConfiguredFeature<?, ?>> GIANT_SEQUOIA_KEY = registerKey("giant_sequoia");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SMALL_SEQUOIA_KEY = registerKey("small_sequoia");
    public static final ResourceKey<ConfiguredFeature<?, ?>> MEDIUM_SEQUOIA_KEY = registerKey("medium_sequoia");

    // 2. Метод Bootstrap для DataGen (Сборка дерева)
    public static void bootstrap(BootstapContext<ConfiguredFeature<?, ?>> context) {

        // Собираем нашу гигантскую Секвойю!
        register(context, GIANT_SEQUOIA_KEY, Feature.TREE, new TreeConfiguration.TreeConfigurationBuilder(
                // Блок ствола (Пока юзаем тропическое дерево для тестов)
                BlockStateProvider.simple(ModBlocks.SEQUOIA_BARK.get()),

                // Наш кастомный алгоритм ствола:
                // Базовая высота 80 + рандом(10) + рандом(10) = Дерево будет от 80 до 100 блоков в высоту!
                new GiantSequoiaTrunkPlacer(150, 10, 10),

                // Блок листвы
                BlockStateProvider.simple(ModBlocks.SEQUOIA_LEAVES.get()),

                // Наш кастомный алгоритм листвы:
                // Радиус шапки 3 блока, смещение 0
                new GiantSequoiaFoliagePlacer(ConstantInt.of(3), ConstantInt.of(0)),

                // Это нужно игре для проверки свободного места, если дерево растет из саженца
                new TwoLayersFeatureSize(1, 0, 2)
        ).build());

        FeatureUtils.register(context, SMALL_SEQUOIA_KEY, Feature.TREE, new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(ModBlocks.SEQUOIA_BARK.get()),
                new MiniSequoiaTrunkPlacer(12, 0, 0),
                BlockStateProvider.simple(ModBlocks.SEQUOIA_LEAVES.get()),

                // --- ИСПОЛЬЗУЕМ НАШ НОВЫЙ КЛАСС ---
                // Радиус 1 (сделает крестики 3х3), смещение 0
                new MiniSequoiaFoliagePlacer(ConstantInt.of(1), ConstantInt.of(0)),

                new TwoLayersFeatureSize(2, 0, 2)).build());

        // 2. СРЕДНЯЯ СЕКВОЙЯ
        FeatureUtils.register(context, MEDIUM_SEQUOIA_KEY, Feature.TREE, new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(ModBlocks.SEQUOIA_BARK.get()),
                new MediumSequoiaTrunkPlacer(35, 0, 0), // Базовая высота 35
                BlockStateProvider.simple(ModBlocks.SEQUOIA_LEAVES.get()),
                new MediumSequoiaFoliagePlacer(ConstantInt.of(1), ConstantInt.of(0)), // Радиус 1 даст нам аккуратные крестики
                new TwoLayersFeatureSize(3, 0, 3)).build());
    }

    // --- Вспомогательные методы ---
    public static ResourceKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation(CrustalIncursionMod.MOD_ID, name));
    }

    private static <FC extends FeatureConfiguration, F extends Feature<FC>> void register(BootstapContext<ConfiguredFeature<?, ?>> context,
                                                                                          ResourceKey<ConfiguredFeature<?, ?>> key, F feature, FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
}