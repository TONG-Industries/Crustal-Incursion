package com.cim.block.basic.fluids;

import com.cim.api.fluids.PipeTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class FluidPipeBlock extends BaseEntityBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;


    // Показывает полную крестовину, если никуда не подключено
    public static final BooleanProperty NONE = BooleanProperty.create("none");
    // Канал (цвет) трубы для визуального разделения сетей
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 0, 15);

    private final PipeTier tier;

    // Хитбоксы для толщины трубы в 6 пикселей (5 - 11)
    private static final VoxelShape CORE_SHAPE = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);
    private static final Map<Direction, VoxelShape> ARM_SHAPES = Map.of(
            Direction.NORTH, Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0),
            Direction.SOUTH, Block.box(5.0, 5.0, 11.0, 11.0, 11.0, 16.0),
            Direction.WEST, Block.box(0.0, 5.0, 5.0, 5.0, 11.0, 11.0),
            Direction.EAST, Block.box(11.0, 5.0, 5.0, 16.0, 11.0, 11.0),
            Direction.UP, Block.box(5.0, 11.0, 5.0, 11.0, 16.0, 11.0),
            Direction.DOWN, Block.box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0)
    );

    public FluidPipeBlock(PipeTier tier, Properties properties) {
        super(properties);
        this.tier = tier; // Сохраняем тир при создании блока

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false)
                .setValue(NONE, true)
                .setValue(CHANNEL, 0)
        );
    }

    // Метод, чтобы Сеть могла узнать характеристики этой трубы
    public PipeTier getTier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, NONE, CHANNEL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE_SHAPE;

        if (state.getValue(NONE)) {
            for (VoxelShape arm : ARM_SHAPES.values()) shape = Shapes.or(shape, arm);
            return shape;
        }

        // Для удобства чтения кода
        boolean n = state.getValue(NORTH);
        boolean s = state.getValue(SOUTH);
        boolean e = state.getValue(EAST);
        boolean w = state.getValue(WEST);
        boolean u = state.getValue(UP);
        boolean d = state.getValue(DOWN);

        // 1. Стандартные хитбоксы подключений
        if (n) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.NORTH));
        if (s) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.SOUTH));
        if (e) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.EAST));
        if (w) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.WEST));
        if (u) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.UP));
        if (d) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.DOWN));

        // 2. Хитбоксы "заглушек" (если подключение только с одной стороны)
        if (n && !s && !e && !w && !u && !d) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.SOUTH));
        if (!n && s && !e && !w && !u && !d) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.NORTH));
        if (!n && !s && e && !w && !u && !d) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.WEST));
        if (!n && !s && !e && w && !u && !d) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.EAST));
        if (!n && !s && !e && !w && u && !d) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.DOWN));
        if (!n && !s && !e && !w && !u && d) shape = Shapes.or(shape, ARM_SHAPES.get(Direction.UP));

        return shape;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateConnections(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        return updateConnections(state, level, currentPos);
    }

    private BlockState updateConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        boolean north = canConnectTo(level, pos, Direction.NORTH);
        boolean south = canConnectTo(level, pos, Direction.SOUTH);
        boolean east = canConnectTo(level, pos, Direction.EAST);
        boolean west = canConnectTo(level, pos, Direction.WEST);
        boolean up = canConnectTo(level, pos, Direction.UP);
        boolean down = canConnectTo(level, pos, Direction.DOWN);

        boolean none = !(north || south || east || west || up || down);

        return state.setValue(NORTH, north).setValue(SOUTH, south)
                .setValue(EAST, east).setValue(WEST, west)
                .setValue(UP, up).setValue(DOWN, down)
                .setValue(NONE, none);
    }

    private boolean canConnectTo(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        BlockState myState = level.getBlockState(pos);

        // 1. Соединяемся с другой трубой, ТОЛЬКО если совпадают каналы
        if (neighborState.getBlock() instanceof FluidPipeBlock) {
            int myChannel = myState.hasProperty(CHANNEL) ? myState.getValue(CHANNEL) : 0;
            int neighborChannel = neighborState.getValue(CHANNEL);
            return myChannel == neighborChannel;
        }

        // Позже здесь мы добавим проверку на цистерны и помпы
        // BlockEntity be = level.getBlockEntity(neighborPos);
        // ...

        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // Говорим игре загрузить нашу JSON-солянку
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.cim.block.entity.fluids.FluidPipeBlockEntity(pos, state);
    }
}