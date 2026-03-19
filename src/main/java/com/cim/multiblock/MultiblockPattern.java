package com.cim.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public class MultiblockPattern {
    private final int width;
    private final int height;
    private final int depth;
    public final PatternEntry[][][] pattern; // [y][x][z]

    public MultiblockPattern(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.pattern = new PatternEntry[height][width][depth];

        // Заполняем по умолчанию — любой блок
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    pattern[y][x][z] = PatternEntry.ANY_BLOCK;
                }
            }
        }
    }

    // Конструктор из строк — для удобного создания в коде
    public static MultiblockPattern fromLayers(String... layers) {
        if (layers.length == 0) throw new IllegalArgumentException("No layers");

        String[] firstLayer = layers[0].split("\n");
        int height = layers.length;
        int depth = firstLayer.length;
        int width = firstLayer[0].length();

        MultiblockPattern pattern = new MultiblockPattern(width, height, depth);

        for (int y = 0; y < height; y++) {
            String[] rows = layers[y].split("\n");
            for (int z = 0; z < depth; z++) {
                String row = rows[z];
                for (int x = 0; x < width; x++) {
                    char c = row.charAt(x);
                    switch (c) {
                        case ' ' -> pattern.setEmpty(x, y, z);
                        case '#' -> pattern.setEntry(x, y, z, PatternEntry.ANY_BLOCK);
                        case 'O' -> pattern.setEntry(x, y, z, PatternEntry.CONTROLLER); // Контроллер (главный блок)
                        case 'A' -> pattern.setEntry(x, y, z, PatternEntry.AIR);
                        default -> pattern.setEntry(x, y, z, PatternEntry.ANY_BLOCK);
                    }
                }
            }
        }
        return pattern;
    }

    public void setEntry(int x, int y, int z, PatternEntry entry) {
        if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth) {
            pattern[y][x][z] = entry;
        }
    }

    public void setEntry(int x, int y, int z, Block requiredBlock) {
        setEntry(x, y, z, PatternEntry.block(requiredBlock));
    }

    public void setEmpty(int x, int y, int z) {
        setEntry(x, y, z, PatternEntry.EMPTY);
    }

    public boolean matches(Level level, BlockPos origin) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    BlockPos checkPos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    PatternEntry entry = pattern[y][x][z];

                    if (!entry.test(state)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public BlockPos findControllerPos(BlockPos origin) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    if (pattern[y][x][z] == PatternEntry.CONTROLLER) {
                        return origin.offset(x, y, z);
                    }
                }
            }
        }
        // Если контроллер не найден, используем центр структуры
        int centerX = width / 2;
        int centerZ = depth / 2;
        return origin.offset(centerX, 0, centerZ);
    }

    public boolean isControllerPos(BlockPos origin, BlockPos pos) {
        return pos.equals(findControllerPos(origin));
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getDepth() { return depth; }

    public PatternEntry getEntry(int x, int y, int z) {
        if (x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < depth) {
            return pattern[y][x][z];
        }
        return PatternEntry.EMPTY;
    }

    public static class PatternEntry {
        public static final PatternEntry ANY_BLOCK = new PatternEntry(state -> !state.isAir());
        public static final PatternEntry EMPTY = new PatternEntry(state -> true); // Пропускаем проверку
        public static final PatternEntry AIR = new PatternEntry(BlockState::isAir);
        public static final PatternEntry CONTROLLER = new PatternEntry(state -> !state.isAir()); // Контроллер — любой блок

        private final Predicate<BlockState> validator;

        public PatternEntry(Predicate<BlockState> validator) {
            this.validator = validator;
        }

        public static PatternEntry block(Block block) {
            return new PatternEntry(state -> state.is(block));
        }

        public static PatternEntry blocks(Block... blocks) {
            return new PatternEntry(state -> {
                for (Block b : blocks) {
                    if (state.is(b)) return true;
                }
                return false;
            });
        }

        public boolean test(BlockState state) {
            return validator.test(state);
        }
    }
}