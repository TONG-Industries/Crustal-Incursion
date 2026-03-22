package com.cim.block.basic.industrial.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.industrial.rotation.GearPortBlockEntity;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;

public class ShaftBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED; // Добавлено

    private final ShaftType shaftType; // хранит все характеристики

    public ShaftBlock(Properties properties, ShaftType shaftType) {
        super(properties);
        this.shaftType = shaftType;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)); // Добавлено
    }

    public ShaftType getShaftType() {
        return shaftType;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShaftBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos placePos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        BlockPos targetPos = placePos.relative(clickedFace.getOpposite());
        BlockState targetState = level.getBlockState(targetPos);
        Block targetBlock = targetState.getBlock();

        boolean canPlace = false;
        Direction shaftFacing = clickedFace;

        // ... (все проверки на соседей, без изменений) ...
        if (targetBlock instanceof MotorElectroBlock) {
            Direction motorFacing = targetState.getValue(MotorElectroBlock.FACING);
            if (clickedFace == motorFacing) {
                canPlace = true;
                shaftFacing = motorFacing;
            }
        }
        else if (targetBlock instanceof ShaftBlock) {
            Direction existingFacing = targetState.getValue(ShaftBlock.FACING);
            if (clickedFace == existingFacing || clickedFace == existingFacing.getOpposite()) {
                canPlace = true;
                shaftFacing = existingFacing;
            }
        }
        // 3. ПОРТ (GearPort) - Проверяем наличие порта на этой стороне
        else if (targetBlock instanceof GearPortBlock) {
            if (level.getBlockEntity(targetPos) instanceof GearPortBlockEntity gear) {
                if (gear.hasPortOnSide(clickedFace)) {
                    canPlace = true;
                    shaftFacing = clickedFace;
                }
            }
        }
        // 4. Сумматор (AdderBlock) - Входы по бокам, выход сзади
        else if (targetBlock instanceof AdderBlock) {
            Direction adderFacing = targetState.getValue(AdderBlock.FACING);
            Direction[] sides = getPerpendicularSides(adderFacing);
            Direction outputSide = adderFacing.getOpposite();

            if (clickedFace == outputSide || clickedFace == sides[0] || clickedFace == sides[1]) {
                canPlace = true;
                shaftFacing = clickedFace;
            }
        }
        // 5. Логические блоки (Meter, Stopper, Tachometer)
        else if (targetBlock instanceof RotationMeterBlock ||
                targetBlock instanceof StopperBlock ||
                targetBlock instanceof TachometerBlock) {

            Direction blockFacing = null;

            if (targetState.hasProperty(BlockStateProperties.FACING)) {
                blockFacing = targetState.getValue(BlockStateProperties.FACING);
            } else if (targetState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                blockFacing = targetState.getValue(BlockStateProperties.HORIZONTAL_FACING);
            }

            if (blockFacing != null) {
                Direction[] sides = getPerpendicularSides(blockFacing);
                if (clickedFace == sides[0] || clickedFace == sides[1]) {
                    canPlace = true;
                    shaftFacing = clickedFace;
                }
            }
        }
        // 6. Флюгер (WindGen) - только снизу
        else if (targetBlock instanceof WindGenFlugerBlock) {
            if (clickedFace == Direction.DOWN) {
                canPlace = true;
                shaftFacing = Direction.DOWN;
            }
        }
        // 7. Конвертер - только сзади
        else if (targetBlock instanceof RConverterBlock) {
            Direction converterFacing = targetState.getValue(RConverterBlock.FACING);
            if (clickedFace == converterFacing.getOpposite()) {
                canPlace = true;
                shaftFacing = clickedFace;
            }
        }
        else if (targetBlock instanceof ShaftPlacerBlock) {
            Direction placerFacing = targetState.getValue(ShaftPlacerBlock.FACING);
            if (clickedFace == placerFacing) {
                canPlace = true;
                shaftFacing = placerFacing;
            } else if (clickedFace == placerFacing.getOpposite()) {
                canPlace = true;
                shaftFacing = placerFacing.getOpposite();
            }
        }
        else if (targetBlock instanceof MiningPortBlock) {
            Direction portFacing = targetState.getValue(MiningPortBlock.FACING);
            if (clickedFace == portFacing) {
                canPlace = true;
                shaftFacing = portFacing;
            } else if (clickedFace == portFacing.getOpposite()) {
                canPlace = true;
                shaftFacing = portFacing.getOpposite();
            }
        }

        if (!canPlace) {
            if (level.isClientSide) {
                spawnErrorParticles(level, placePos, clickedFace);
            }
            return null;
        }

        // Определяем водонасыщение
        boolean waterlogged = level.getFluidState(placePos).getType() == Fluids.WATER;

        return this.defaultBlockState()
                .setValue(FACING, shaftFacing)
                .setValue(WATERLOGGED, waterlogged);
    }

    // Добавляем метод для обработки обновлений соседей (вода)
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // Добавляем метод для получения FluidState
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    // Вспомогательный метод для визуального фидбека
    private void spawnErrorParticles(Level level, BlockPos pos) {
        for (int i = 0; i < 5; i++) {
            double d0 = (double)pos.getX() + level.random.nextDouble();
            double d1 = (double)pos.getY() + level.random.nextDouble();
            double d2 = (double)pos.getZ() + level.random.nextDouble();
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * Вспомогательный метод для поиска перпендикулярных сторон.
     * Если блок смотрит на СЕВЕР, вернет ЗАПАД и ВОСТОК.
     * Если смотрит ВВЕРХ, вернет СЕВЕР и ЮГ.
     */
    private Direction[] getPerpendicularSides(Direction facing) {
        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.Y) {
            return new Direction[]{Direction.NORTH, Direction.SOUTH};
        } else if (axis == Direction.Axis.X) {
            return new Direction[]{Direction.NORTH, Direction.SOUTH};
        } else { // Axis.Z
            return new Direction[]{Direction.WEST, Direction.EAST};
        }
    }

    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(6.75, 6.75, 0, 9.25, 9.25, 16);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(0, 6.75, 6.75, 16, 9.25, 9.25);
    private static final VoxelShape SHAPE_UP_DOWN = Block.box(6.75, 0, 6.75, 9.25, 16, 9.25);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (facing) {
            case NORTH, SOUTH -> SHAPE_NORTH_SOUTH;
            case EAST, WEST -> SHAPE_EAST_WEST;
            case UP, DOWN -> SHAPE_UP_DOWN;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, null);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED); // Добавлено WATERLOGGED
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.SHAFT_BLOCK_BE.get(), ShaftBlockEntity::tick);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof ShaftBlockEntity be) {
                be.invalidateCache();
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                if (level.getBlockEntity(neighborPos) instanceof RotationalNode node) {
                    node.invalidateCache();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void spawnErrorParticles(Level level, BlockPos pos, Direction side) {
        double x = pos.getX() + 0.5 + side.getStepX() * 0.4;
        double y = pos.getY() + 0.5 + side.getStepY() * 0.4;
        double z = pos.getZ() + 0.5 + side.getStepZ() * 0.4;

        for (int i = 0; i < 8; i++) {
            level.addParticle(
                    net.minecraft.core.particles.ParticleTypes.SMOKE,
                    x + (level.random.nextDouble() - 0.5) * 0.3,
                    y + (level.random.nextDouble() - 0.5) * 0.3,
                    z + (level.random.nextDouble() - 0.5) * 0.3,
                    0, 0.02, 0
            );
        }
    }
}