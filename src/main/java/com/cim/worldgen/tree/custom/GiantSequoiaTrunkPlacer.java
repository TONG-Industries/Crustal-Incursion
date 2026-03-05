package com.cim.worldgen.tree.custom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
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

public class GiantSequoiaTrunkPlacer extends TrunkPlacer {

    public static final Codec<GiantSequoiaTrunkPlacer> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.intRange(0, 250).fieldOf("base_height").forGetter(placer -> placer.baseHeight),
                    Codec.intRange(0, 50).fieldOf("height_rand_a").forGetter(placer -> placer.heightRandA),
                    Codec.intRange(0, 50).fieldOf("height_rand_b").forGetter(placer -> placer.heightRandB)
            ).apply(instance, GiantSequoiaTrunkPlacer::new));

    public GiantSequoiaTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModTrunkPlacerTypes.GIANT_SEQUOIA_TRUNK_PLACER.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter, RandomSource random, int freeTreeHeight, BlockPos pos, TreeConfiguration config) {
        List<FoliagePlacer.FoliageAttachment> foliageAttachments = new ArrayList<>();

        int height = freeTreeHeight;
        int baseRadius = 8;

        for (int y = -25; y < height; y++) {
            float progress = y > 0 ? (float) y / height : 0f;
            float currentRadiusF = baseRadius * (1.0f - progress);

            // Формируем широкую "юбку" у основания
            if (y < 15) {
                currentRadiusF += (15 - y) * 0.3f;
            }

            int currentRadius = Math.max(1, Math.round(currentRadiusF));

            // --- ЗАЩИТА ОТ КРАЕВ ЧАНКА ---
            // Жестко ограничиваем ширину корней (максимум 14 блоков в радиусе),
            // чтобы они не обрезались по краям при генерации в глубоких ущельях.
            currentRadius = Math.min(currentRadius, 14);

            for (int x = -currentRadius; x <= currentRadius; x++) {
                for (int z = -currentRadius; z <= currentRadius; z++) {
                    if (x * x + z * z <= currentRadius * currentRadius) {
                        BlockPos currentPos = pos.offset(x, y, z);
                        boolean isEdge = (x * x + z * z >= (currentRadius - 1.5) * (currentRadius - 1.5));

                        if (y < 0) {
                            // --- КОРНИ (ПОДСТАВКА) ---
                            // Проверяем, есть ли тут пустота
                            boolean isAirOrReplaceable = level.isStateAtPosition(currentPos, state ->
                                    state.isAir() || state.canBeReplaced() || state.liquid() || state.is(BlockTags.LEAVES));

                            if (isAirOrReplaceable) {
                                // Бросаем кубик от 0.0 до 1.0.
                                // Если выпадает меньше 0.3 (шанс 30%), ставим замшелые корни
                                if (random.nextFloat() < 0.3f) {
                                    // ЗАМЕНИ MOSSY_SEQUOIA_ROOTS на точное название твоего блока замшелых корней!

                                    blockSetter.accept(currentPos, ModBlocks.SEQUOIA_BARK_MOSSY.get().defaultBlockState());
                                } else {
                                    // Иначе ставим обычные (шанс 70%)
                                    blockSetter.accept(currentPos, ModBlocks.SEQUOIA_BARK.get().defaultBlockState());

                                }
                            }
                        } else {
                            // --- СТВОЛ (НАД ЗЕМЛЕЙ) ---
                            if (isEdge) {
                                // Генерируем случайное число для коры
                                float barkChance = random.nextFloat();

                                // 1. Замшелая кора (спавнится только на нижних 20 блоках с шансом 30%)
                                if (y < 20 && random.nextFloat() < 0.3f) {
                                    blockSetter.accept(currentPos, ModBlocks.SEQUOIA_BARK_MOSSY.get().defaultBlockState());
                                }
                                // 2. Темная/обгоревшая кора (шанс 15%)
                                else if (barkChance < 0.15f) {
                                    blockSetter.accept(currentPos, ModBlocks.SEQUOIA_BARK_DARK.get().defaultBlockState());
                                }
                                // 3. Светлая/старая кора (шанс 15%)
                                else if (barkChance < 0.30f) {
                                    blockSetter.accept(currentPos, ModBlocks.SEQUOIA_BARK_LIGHT.get().defaultBlockState());
                                }
                                // 4. Обычная кора (остальные 70%)
                                else {
                                    blockSetter.accept(currentPos, ModBlocks.SEQUOIA_BARK.get().defaultBlockState());
                                }
                            } else {
                                // Сердцевина внутри всегда одинаковая
                                blockSetter.accept(currentPos, ModBlocks.SEQUOIA_HEARTWOOD.get().defaultBlockState());
                            }
                        }
                    }
                }
            }

            // 2. СТАТИЧНАЯ ГЕНЕРАЦИЯ ВЕТОК (Начинаем с 65% высоты)
            int branchStartHeight = (int) (height * 0.65f);
            if (y >= branchStartHeight && y < height - 5) {
                int tier = y - branchStartHeight;

                // Чередуем Прямой и Диагональный крест каждые 5 блоков (Итого интервал одной ветки = 10)
                if (tier % 10 == 0) {
                    // Прямой крест (+)
                    generateBranch(level, blockSetter, startPos(pos, y), currentRadius, foliageAttachments, y, height, 0, -1, random); // С
                    generateBranch(level, blockSetter, startPos(pos, y), currentRadius, foliageAttachments, y, height, 0, 1, random);  // Ю
                    generateBranch(level, blockSetter, startPos(pos, y), currentRadius, foliageAttachments, y, height, 1, 0, random);  // В
                    generateBranch(level, blockSetter, startPos(pos, y), currentRadius, foliageAttachments, y, height, -1, 0, random); // З
                } else if (tier % 10 == 5) {
                    // Диагональный крест (X)
                    generateBranch(level, blockSetter, startPos(pos, y), currentRadius, foliageAttachments, y, height, 1, -1, random);  // СВ
                    generateBranch(level, blockSetter, startPos(pos, y), currentRadius, foliageAttachments, y, height, -1, -1, random); // СЗ
                    generateBranch(level, blockSetter, startPos(pos, y), currentRadius, foliageAttachments, y, height, 1, 1, random);   // ЮВ
                    generateBranch(level, blockSetter, startPos(pos, y), currentRadius, foliageAttachments, y, height, -1, 1, random);  // ЮЗ
                }
            }
        }

        foliageAttachments.add(new FoliagePlacer.FoliageAttachment(pos.above(height), 5, false));
        return foliageAttachments;
    }

    private BlockPos startPos(BlockPos pos, int y) {
        return pos.offset(0, y, 0);
    }

    // Переделали метод на координаты dx и dz
    private void generateBranch(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter,
                                BlockPos startPos, int trunkRadius,
                                List<FoliagePlacer.FoliageAttachment> foliageAttachments,
                                int currentY, int totalHeight, int dx, int dz, RandomSource random) {

        int branchStartHeight = (int) (totalHeight * 0.65f);
        float heightProgress = (float) (currentY - branchStartHeight) / (totalHeight - branchStartHeight);

        int maxSafeRadius = 22;
        int maxFoliageRadius = 6;

        int maxAllowedBranchLength = maxSafeRadius - trunkRadius - maxFoliageRadius;

        boolean isDiagonal = (dx != 0 && dz != 0);
        if (isDiagonal) {
            maxAllowedBranchLength = (int) (maxAllowedBranchLength / 1.41f);
        }

        int idealMaxLength = 18;
        int minLength = 4;

        int actualMaxLength = Math.min(idealMaxLength, maxAllowedBranchLength);

        int randomOffset = random.nextInt(3) - 1;
        int branchLength = (int) (actualMaxLength - (actualMaxLength - minLength) * heightProgress) + randomOffset;
        branchLength = Math.max(minLength, Math.min(branchLength, maxAllowedBranchLength));

        // Начинаем строить
        BlockPos currentPos = startPos.offset(dx * (trunkRadius - 1), 0, dz * (trunkRadius - 1));

        for (int i = 0; i < branchLength; i++) {

            // 1. Ставим сердцевину в центр
            blockSetter.accept(currentPos, ModBlocks.SEQUOIA_HEARTWOOD.get().defaultBlockState());

            // 2. БРОНЯ ИЗ КОРЫ: Оборачиваем со ВСЕХ сторон!
            // Это автоматически зальет все диагональные дырки
            blockSetter.accept(currentPos.above(), ModBlocks.SEQUOIA_BARK.get().defaultBlockState());
            blockSetter.accept(currentPos.below(), ModBlocks.SEQUOIA_BARK.get().defaultBlockState());
            blockSetter.accept(currentPos.offset(1, 0, 0), ModBlocks.SEQUOIA_BARK.get().defaultBlockState());  // Восток
            blockSetter.accept(currentPos.offset(-1, 0, 0), ModBlocks.SEQUOIA_BARK.get().defaultBlockState()); // Запад
            blockSetter.accept(currentPos.offset(0, 0, 1), ModBlocks.SEQUOIA_BARK.get().defaultBlockState());  // Юг
            blockSetter.accept(currentPos.offset(0, 0, -1), ModBlocks.SEQUOIA_BARK.get().defaultBlockState()); // Север

            // Двигаемся дальше по вектору
            currentPos = currentPos.offset(dx, 0, dz);

            // Изгиб вверх
            if (i % 3 == 0) {
                currentPos = currentPos.above();
            }

            // Обрастание листвой
            if (i >= 3 && i % 3 == 0) {
                int radiusAlongBranch = Math.min(4, 2 + (i / 4));
                foliageAttachments.add(new FoliagePlacer.FoliageAttachment(currentPos.above(), radiusAlongBranch, false));
            }
        }

        // Финальная шапка
        foliageAttachments.add(new FoliagePlacer.FoliageAttachment(currentPos, maxFoliageRadius, false));
    }
}