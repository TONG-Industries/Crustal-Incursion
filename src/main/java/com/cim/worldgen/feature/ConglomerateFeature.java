package com.cim.worldgen.feature;

import com.cim.api.vein.VeinCompositionGenerator;
import com.cim.api.vein.VeinManager;
import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.conglomerate.ConglomerateBlockEntity;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class ConglomerateVeinFeature extends Feature<ConglomerateVeinConfiguration> {

    public ConglomerateVeinFeature(Codec<ConglomerateVeinConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<ConglomerateVeinConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        ConglomerateVeinConfiguration cfg = context.config();

        if (!(level instanceof ServerLevel serverLevel)) return false;

        float range = cfg.maxY() - cfg.minY();
        float t = range > 0 ? Mth.clamp((origin.getY() - cfg.minY()) / range, 0f, 1f) : 0.5f;
        int size = Mth.floor(Mth.lerp(t, cfg.minSize(), cfg.maxSize()));
        if (size <= 0) return false;

        int rx = size;
        int ry = Math.max(2, size / 2 + random.nextInt(3));
        int rz = size;

        Set<BlockPos> normalBlocks = new HashSet<>();
        Set<BlockPos> depletedBlocks = new HashSet<>();

        for (int x = -rx; x <= rx; x++) {
            for (int y = -ry; y <= ry; y++) {
                for (int z = -rz; z <= rz; z++) {
                    double dist = (x * x) / (double) (rx * rx)
                            + (y * y) / (double) (ry * ry)
                            + (z * z) / (double) (rz * rz);
                    if (dist > 1.0) continue;

                    BlockPos pos = origin.offset(x, y, z);
                    if (!isReplaceable(level, pos)) continue;
                    if (random.nextFloat() > cfg.density()) continue;

                    if (random.nextFloat() < cfg.depletionChance()) {
                        depletedBlocks.add(pos.immutable());
                    } else {
                        normalBlocks.add(pos.immutable());
                    }
                }
            }
        }

        if (normalBlocks.isEmpty()) return false;

        var composition = VeinCompositionGenerator.generate(origin.getY(), random);
        UUID veinId = VeinManager.get(serverLevel).registerVein(normalBlocks, composition, origin.getY());

        for (BlockPos pos : depletedBlocks) {
            level.setBlock(pos, ModBlocks.DEPLETED_CONGLOMERATE.get().defaultBlockState(), 2);
        }

        for (BlockPos pos : normalBlocks) {
            level.setBlock(pos, ModBlocks.CONGLOMERATE.get().defaultBlockState(), 2);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ConglomerateBlockEntity cbe) {
                cbe.setVeinId(veinId);
            }
        }

        return true;
    }

    private boolean isReplaceable(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD);
    }
}