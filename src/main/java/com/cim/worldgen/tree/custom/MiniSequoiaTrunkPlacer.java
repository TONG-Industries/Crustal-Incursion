package com.cim.worldgen.tree.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import com.cim.block.basic.ModBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class MiniSequoiaTrunkPlacer extends TrunkPlacer {

    public static final Codec<MiniSequoiaTrunkPlacer> CODEC = RecordCodecBuilder.create(instance ->
            trunkPlacerParts(instance).apply(instance, MiniSequoiaTrunkPlacer::new));

    public MiniSequoiaTrunkPlacer(int baseHeight, int randomHeightA, int randomHeightB) {
        super(baseHeight, randomHeightA, randomHeightB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModTrunkPlacerTypes.MINI_SEQUOIA_TRUNK.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter, RandomSource random, int height, BlockPos pos, TreeConfiguration config) {

        // === 0. РАДАР СВОБОДНОГО МЕСТА ===
        // Если в радиусе 4 блоков есть чужая кора - глушим рост, чтобы не врастать в другие деревья!
        int radarRadius = 4;
        for (int x = -radarRadius; x <= radarRadius; x++) {
            for (int z = -radarRadius; z <= radarRadius; z++) {
                for (int y = 0; y <= 5; y++) {
                    if (level.isStateAtPosition(pos.offset(x, y, z), state ->
                            state.is(ModBlocks.SEQUOIA_BARK.get()) ||
                                    state.is(ModBlocks.SEQUOIA_BARK_MOSSY.get()) ||
                                    state.is(ModBlocks.SEQUOIA_BARK_DARK.get()) ||
                                    state.is(ModBlocks.SEQUOIA_BARK_LIGHT.get()))) {
                        return new ArrayList<>(); // Отмена генерации!
                    }
                }
            }
        }

        setDirtAt(level, blockSetter, random, pos.below(), config);
        List<FoliagePlacer.FoliageAttachment> foliage = new ArrayList<>();

        // 1. Ствол
        for (int i = 0; i < height; i++) {
            placeLog(level, blockSetter, random, pos.above(i), config);
        }

        // 2. Макушка
        foliage.add(new FoliagePlacer.FoliageAttachment(pos.above(height), 0, false));

        // 3. Ветки крестом
        int branchStart = 6;
        for (int i = branchStart; i < height - 1; i += 2) {
            int branchLength = (i >= height - 3) ? 1 : 2;

            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos branchPos = pos.above(i);
                for (int b = 1; b <= branchLength; b++) {
                    branchPos = branchPos.relative(dir);
                    placeLog(level, blockSetter, random, branchPos, config);
                }
                foliage.add(new FoliagePlacer.FoliageAttachment(branchPos.above(), 0, false));
            }

            BlockPos trunkTierPos = pos.above(i + 1);
            foliage.add(new FoliagePlacer.FoliageAttachment(trunkTierPos.offset(1, 0, 1), 0, false));
            foliage.add(new FoliagePlacer.FoliageAttachment(trunkTierPos.offset(-1, 0, 1), 0, false));
            foliage.add(new FoliagePlacer.FoliageAttachment(trunkTierPos.offset(1, 0, -1), 0, false));
            foliage.add(new FoliagePlacer.FoliageAttachment(trunkTierPos.offset(-1, 0, -1), 0, false));
        }
        return foliage;
    }
}