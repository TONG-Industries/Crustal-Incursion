package com.cim.worldgen.tree.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

public class MediumSequoiaFoliagePlacer extends FoliagePlacer {

    public static final Codec<MediumSequoiaFoliagePlacer> CODEC = RecordCodecBuilder.create(instance ->
            foliagePlacerParts(instance).apply(instance, MediumSequoiaFoliagePlacer::new));

    public MediumSequoiaFoliagePlacer(IntProvider radius, IntProvider offset) {
        super(radius, offset);
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return ModFoliagePlacerTypes.MEDIUM_SEQUOIA_FOLIAGE_PLACER.get();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliageSetter setter, RandomSource random, TreeConfiguration config, int maxFreeTreeHeight, FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset) {
        // Строим 5 слоев листвы: от -1 (чуть ниже ветки) до +3 (высоко над веткой)
        for (int y = -1; y <= 3; y++) {
            int currentRadius = foliageRadius;

            // Сужаем шапку на самом нижнем и самом верхнем слое (чтобы получился овал/ромб)
            if (y == -1 || y == 3) {
                currentRadius -= 1;
            }
            if (currentRadius < 0) currentRadius = 0;

            this.placeLeavesRow(level, setter, random, config, attachment.pos(), currentRadius, y, attachment.doubleTrunk());
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return 5; // Жестко указываем общую высоту = 5
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        // Срезаем углы, чтобы листва формировала красивый крестик/звездочку, а не тупой квадрат
        return localX == range && localZ == range && range > 0;
    }
}
