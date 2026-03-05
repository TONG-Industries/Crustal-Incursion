package com.cim.worldgen.tree.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

// Размещать в: src/main/java/razchexlitiel/cim/worldgen/tree/custom/GiantSequoiaFoliagePlacer.java
public class GiantSequoiaFoliagePlacer extends FoliagePlacer {

    // Кодек для чтения/записи в JSON (DataGen)
    public static final Codec<GiantSequoiaFoliagePlacer> CODEC = RecordCodecBuilder.create(instance ->
            foliagePlacerParts(instance).apply(instance, GiantSequoiaFoliagePlacer::new));

    public GiantSequoiaFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return ModFoliagePlacerTypes.GIANT_SEQUOIA_FOLIAGE_PLACER.get();
    }

    // Главный метод: вызывается для КАЖДОГО конца ветки, который мы передали из TrunkPlacer
    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliageSetter setter, RandomSource random, TreeConfiguration config, int maxFreeTreeHeight, FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset) {
        // Мы делаем массивные шапки листвы, которые слегка "свисают" вниз с веток
        int dropDown = 2; // На сколько блоков листва уходит вниз от ветки
        int reachUp = 2;  // На сколько блоков листва возвышается над веткой

        for (int y = -dropDown; y <= reachUp; y++) {
            // Делаем крону слегка закругленной: верхний и нижний слои будут ýже
            int currentLayerRadius = foliageRadius;
            if (y == -dropDown || y == reachUp) {
                currentLayerRadius -= 1;
            }

            // Метод placeLeavesRow сам строит круг из листвы на высоте 'y'
            // и вызывает метод shouldSkipLocation для проверки формы
            this.placeLeavesRow(level, setter, random, config, attachment.pos(), currentLayerRadius, y, attachment.doubleTrunk());
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 5; // Общая базовая высота одного кластера листвы
    }

    // Этот метод определяет форму кроны (срезает углы у квадрата, чтобы получился круг/ромб)
    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        // Если блок слишком далеко от центра (срезаем жесткие углы)
        if (localX + localZ > range) {
            return true;
        }
        // Случайным образом "выкусываем" краевые блоки, чтобы листва выглядела пушистой и органичной, а не как идеальный шар
        return localX == range && localZ == range && range > 0 && random.nextBoolean();
    }
}