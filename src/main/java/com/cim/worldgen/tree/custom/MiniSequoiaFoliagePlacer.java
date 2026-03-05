package com.cim.worldgen.tree.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

public class MiniSequoiaFoliagePlacer extends FoliagePlacer {

    // Кодек для датагена
    public static final Codec<MiniSequoiaFoliagePlacer> CODEC = RecordCodecBuilder.create(instance ->
            foliagePlacerParts(instance).apply(instance, MiniSequoiaFoliagePlacer::new));

    public MiniSequoiaFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return ModFoliagePlacerTypes.MINI_SEQUOIA_FOLIAGE_PLACER.get();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliageSetter setter, RandomSource random, TreeConfiguration config, int maxFreeTreeHeight, FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset) {

        // ПРОВЕРЯЕМ НАШ СЕКРЕТНЫЙ ФЛАГ
        if (attachment.doubleTrunk()) {

            // === ЛОГИКА ДЛЯ УГЛОВ: ПУЗАТЫЕ СФЕРЫ ===
            int r = foliageRadius; // Сюда уже пришел наш динамический радиус из TrunkPlacer!
            for (int y = -r; y <= r; y++) {
                // Математика идеального шара: радиус слоя зависит от высоты
                int currentLayerRadius = (int) Math.round(Math.sqrt(r * r - y * y));
                // Передаем false в конце, чтобы шар строился ровно вокруг центра
                this.placeLeavesRow(level, setter, random, config, attachment.pos(), currentLayerRadius, y, false);
            }

        } else {

            // === ЛОГИКА ДЛЯ ВЕТОК: ВЫТЯНУТЫЕ СВЕЧИ ===
            for (int y = -1; y <= 3; y++) {
                int currentRadius = foliageRadius;
                // Сужаем сверху и снизу
                if (y == -1 || y == 3) {
                    currentRadius -= 1;
                }
                if (currentRadius < 0) currentRadius = 0;
                this.placeLeavesRow(level, setter, random, config, attachment.pos(), currentRadius, y, false);
            }

        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 3; // Общая высота нашего микро-кустика из листьев (от -1 до 1 = 3 блока)
    }

    // Этот метод "срезает" углы. Если радиус 1, то обрезав углы, мы получим красивый крестик (+), а не квадрат
    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        if (range > 1) {
            // Для больших угловых шаров: скругляем края по формуле круга (x^2 + z^2 > r^2)
            return localX * localX + localZ * localZ > range * range;
        } else {
            // Для свечей на ветках: срезаем 4 угла, делая аккуратный крестик
            return localX == range && localZ == range && range > 0;
        }
    }
}
