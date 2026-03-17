package com.cim.block.basic.necrosis.hive;

import com.cim.block.basic.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public class HiveRootsBlock extends Block implements BonemealableBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");

    // ⭐ НАСТРОЙКИ РОСТА
    private static final int GROWTH_CHANCE = 4; // 25% шанс (1/4) как у лиан
    private static final int MAX_LENGTH = 25; // Как twisting/weeping vines
    private static final int BONE_MEAL_GROWTH = 3; // Сколько блоков добавляет костная мука

    public HiveRootsBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(HANGING, false));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        boolean hanging = state.getValue(HANGING);

        if (hanging) {
            BlockPos abovePos = pos.above();
            BlockState above = level.getBlockState(abovePos);
            return above.is(ModBlocks.HIVE_SOIL.get())
                    || above.is(ModBlocks.DEPTH_WORM_NEST.get())
                    || (above.is(this) && above.getValue(HANGING));
        } else {
            BlockPos belowPos = pos.below();
            BlockState below = level.getBlockState(belowPos);
            return below.is(ModBlocks.HIVE_SOIL.get())
                    || below.is(ModBlocks.DEPTH_WORM_NEST.get())
                    || (below.is(this) && !below.getValue(HANGING));
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();

        boolean hanging = false;

        if (face == Direction.DOWN) {
            hanging = true;
        } else if (face == Direction.UP) {
            hanging = false;
        } else {
            BlockState above = level.getBlockState(pos.above());
            BlockState below = level.getBlockState(pos.below());

            if (above.is(ModBlocks.HIVE_SOIL.get()) || above.is(ModBlocks.DEPTH_WORM_NEST.get()) ||
                    (above.is(this) && above.getValue(HANGING))) {
                hanging = true;
            } else {
                hanging = false;
            }
        }

        BlockPos supportPos = hanging ? pos.above() : pos.below();
        BlockState support = level.getBlockState(supportPos);
        boolean validSupport = support.is(ModBlocks.HIVE_SOIL.get())
                || support.is(ModBlocks.DEPTH_WORM_NEST.get())
                || support.is(this);

        if (!validSupport) {
            return null;
        }

        return this.defaultBlockState().setValue(HANGING, hanging);
    }

    // ⭐ КОСТНАЯ МУКА: Клик мукой
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);

        if (stack.is(Items.BONE_MEAL)) {
            if (!level.isClientSide) {
                if (this.isValidBonemealTarget(level, pos, state, false)) {
                    this.performBonemeal((ServerLevel) level, level.getRandom(), pos, state);
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.levelEvent(1505, pos, 0);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    // ⭐ ЕСТЕСТВЕННЫЙ РОСТ: 25% шанс как у лиан
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 25% шанс роста (1/4) — как у лиан в джунглях
        if (random.nextInt(GROWTH_CHANCE) != 0) return;

        if (isMaxLength(level, pos, state.getValue(HANGING))) return;

        if (canGrowFurther(level, pos, state)) {
            grow(level, pos, state, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
        return canGrowFurther((Level) level, pos, state)
                && !isMaxLength((Level) level, pos, state.getValue(HANGING));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    // ⭐ КОСТНАЯ МУКА: Мгновенный рост на 2-3 блока
    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        // Растём на BONE_MEAL_GROWTH блоков сразу
        BlockState currentState = state;
        BlockPos currentPos = pos;

        for (int i = 0; i < BONE_MEAL_GROWTH; i++) {
            if (!canGrowFurther(level, currentPos, currentState)) break;
            if (isMaxLength(level, currentPos, currentState.getValue(HANGING))) break;

            grow(level, currentPos, currentState, random);

            // Находим новый конец цепочки для следующей итерации
            boolean hanging = currentState.getValue(HANGING);
            Direction growDir = hanging ? Direction.DOWN : Direction.UP;
            currentPos = currentPos.relative(growDir);
            currentState = level.getBlockState(currentPos);

            // Если не смогли поставить блок, прерываемся
            if (!currentState.is(this)) break;
        }
    }

    private boolean canGrowFurther(Level level, BlockPos pos, BlockState state) {
        boolean hanging = state.getValue(HANGING);
        Direction growDir = hanging ? Direction.DOWN : Direction.UP;
        BlockPos growPos = pos.relative(growDir);
        return level.getBlockState(growPos).isAir();
    }

    private boolean isMaxLength(Level level, BlockPos pos, boolean hanging) {
        return getLength(level, pos, hanging) >= MAX_LENGTH;
    }

    private void grow(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        boolean hanging = state.getValue(HANGING);
        Direction growDir = hanging ? Direction.DOWN : Direction.UP;
        BlockPos growPos = pos.relative(growDir);

        if (!level.getBlockState(growPos).isAir()) return;
        if (isMaxLength(level, pos, hanging)) return;

        BlockState newState = this.defaultBlockState()
                .setValue(HANGING, hanging)
                .setValue(AGE, Math.min(state.getValue(AGE) + 1, 3));

        level.setBlock(growPos, newState, 3);

        // Обновляем текущий блок — теперь у него есть продолжение
        if (hanging) {
            level.setBlock(pos, state.setValue(DOWN, true), 3);
        } else {
            level.setBlock(pos, state.setValue(UP, true), 3);
        }

        // Частицы роста
        level.levelEvent(1505, growPos, 0);
    }

    // Подсчёт длины цепочки в направлении роста
    private int getLength(LevelReader level, BlockPos pos, boolean hanging) {
        int length = 1;
        Direction dir = hanging ? Direction.DOWN : Direction.UP;
        BlockPos checkPos = pos.relative(dir);

        while (level.getBlockState(checkPos).is(this)) {
            length++;
            checkPos = checkPos.relative(dir);
            if (length > MAX_LENGTH + 5) break; // Защита
        }

        return length;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            boolean hanging = state.getValue(HANGING);
            Direction breakDir = hanging ? Direction.DOWN : Direction.UP;

            BlockPos breakPos = pos.relative(breakDir);
            while (level.getBlockState(breakPos).is(this)) {
                BlockState breakState = level.getBlockState(breakPos);
                if (breakState.getValue(HANGING) == hanging) {
                    level.destroyBlock(breakPos, true);
                    breakPos = breakPos.relative(breakDir);
                } else {
                    break;
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean hanging = state.getValue(HANGING);
        Direction supportDir = hanging ? Direction.UP : Direction.DOWN;

        if (direction == supportDir) {
            if (!canSurvive(state, level, pos)) {
                return Blocks.AIR.defaultBlockState();
            }
        }

        if (direction == Direction.UP && !hanging) {
            boolean hasUp = neighborState.is(this) && !neighborState.getValue(HANGING);
            return state.setValue(UP, hasUp);
        }
        if (direction == Direction.DOWN && !hanging) {
            boolean hasDown = neighborState.is(this) && !neighborState.getValue(HANGING);
            return state.setValue(DOWN, hasDown);
        }
        if (direction == Direction.UP && hanging) {
            boolean hasUp = neighborState.is(this) && neighborState.getValue(HANGING);
            return state.setValue(UP, hasUp);
        }
        if (direction == Direction.DOWN && hanging) {
            boolean hasDown = neighborState.is(this) && neighborState.getValue(HANGING);
            return state.setValue(DOWN, hasDown);
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE, UP, DOWN, HANGING);
    }
}