package com.cim.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class SpecialVeinFeature extends Feature<SpecialVeinConfiguration> {

    public SpecialVeinFeature(Codec<SpecialVeinConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<SpecialVeinConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        SpecialVeinConfiguration cfg = context.config();

        // === ОПРЕДЕЛЯЕМ ГРАНИЦЫ ТЕКУЩЕГО ЧАНКА ===
        int chunkX = SectionPos.blockToSectionCoord(origin.getX());
        int chunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        int minBlockX = SectionPos.sectionToBlockCoord(chunkX);
        int minBlockZ = SectionPos.sectionToBlockCoord(chunkZ);
        int maxBlockX = minBlockX + 15;
        int maxBlockZ = minBlockZ + 15;

        // --- Интерполяция размера по Y ---
        float range = cfg.maxY() - cfg.minY();
        float t = range > 0 ? Mth.clamp((origin.getY() - cfg.minY()) / range, 0f, 1f) : 0.5f;
        int size = Mth.floor(Mth.lerp(t, cfg.minSize(), cfg.maxSize()));
        if (size <= 0) return false;

        int rx = size;
        int ry = Math.max(1, size / 2 + random.nextInt(2));
        int rz = size;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int x = -rx; x <= rx; x++) {
            for (int y = -ry; y <= ry; y++) {
                for (int z = -rz; z <= rz; z++) {
                    double dist = (x * x) / (double) (rx * rx)
                            + (y * y) / (double) (ry * ry)
                            + (z * z) / (double) (rz * rz);
                    if (dist > 1.0) continue;

                    int absX = origin.getX() + x;
                    int absZ = origin.getZ() + z;

                    pos.set(absX, origin.getY() + y, absZ);

                    // Разрезание воздухом
                    BlockState existing = level.getBlockState(pos);
                    if (cfg.respectAir() && existing.isAir()) {
                        continue;
                    }

                    // Плотность
                    if (random.nextFloat() > cfg.density()) continue;

                    // Замена через RuleTest
                    for (OreConfiguration.TargetBlockState target : cfg.targets()) {
                        if (target.target.test(existing, random)) {
                            level.setBlock(pos, cfg.state(), 2);
                            placed = true;
                            break;
                        }
                    }
                }
            }
        }
        return placed;
    }
}