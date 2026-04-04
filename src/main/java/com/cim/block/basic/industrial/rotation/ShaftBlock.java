package com.cim.block.basic.industrial.rotation;

import com.cim.api.rotation.KineticNetworkManager;
import com.cim.api.rotation.ShaftDiameter;
import com.cim.api.rotation.ShaftMaterial;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ShaftBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    // НОВЫЕ ПЕРЕМЕННЫЕ
    private final ShaftMaterial material;
    private final ShaftDiameter diameter;

    // НОВЫЙ КОНСТРУКТОР НА 3 АРГУМЕНТА
    public ShaftBlock(Properties properties, ShaftMaterial material, ShaftDiameter diameter) {
        super(properties);
        this.material = material;
        this.diameter = diameter;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // Геттеры для сущности (BlockEntity)
    public ShaftMaterial getMaterial() { return material; }
    public ShaftDiameter getDiameter() { return diameter; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // ДИНАМИЧЕСКИЙ ХИТБОКС (зависит от диаметра)
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double p = diameter.pixels / 2.0;
        double min = 8.0 - p;
        double max = 8.0 + p;

        return switch (state.getValue(FACING).getAxis()) {
            case X -> Block.box(0.0, min, min, 16.0, max, max);
            case Y -> Block.box(min, 0.0, min, max, 16.0, max);
            case Z -> Block.box(min, min, 0.0, max, max, 16.0);
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos clickedPos = context.getClickedPos().relative(context.getClickedFace().getOpposite());
        BlockState clickedState = context.getLevel().getBlockState(clickedPos);

        if (clickedState.getBlock() instanceof ShaftBlock) {
            return this.defaultBlockState().setValue(FACING, clickedState.getValue(FACING));
        }
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
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

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterPlace(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterRemove(pos);
            return;
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}