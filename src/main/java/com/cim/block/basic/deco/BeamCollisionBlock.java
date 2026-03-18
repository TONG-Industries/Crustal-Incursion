package com.cim.block.basic.deco;

import com.cim.block.entity.deco.BeamCollisionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class BeamCollisionBlock extends BaseEntityBlock {
    // Делаем небольшой хитбокс в центре (от 5 до 11 пикселей по всем осям)
    private static final VoxelShape SHAPE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);

    public BeamCollisionBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.INVISIBLE; // Сам куб прозрачный
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new BeamCollisionBlockEntity(pPos, pState);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof BeamCollisionBlockEntity collisionBE) {

                    // Находим Мастера (даже если сломали кусок балки посередине)
                    BlockPos masterPos = collisionBE.isMaster() ? pos : collisionBE.getMasterPos();
                    if (masterPos != null) {
                        BlockEntity masterBE = level.getBlockEntity(masterPos);
                        if (masterBE instanceof BeamCollisionBlockEntity mbe && mbe.isMaster()) {
                            mbe.breakEntireBeam(level);
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    // Срабатывает, когда ломается любой соседний блок (например, наша бетонная/стальная опора)
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BeamCollisionBlockEntity collisionBE) {
            BlockPos masterPos = collisionBE.isMaster() ? pos : collisionBE.getMasterPos();
            if (masterPos != null) {
                BlockEntity masterBE = level.getBlockEntity(masterPos);
                if (masterBE instanceof BeamCollisionBlockEntity mbe && mbe.isMaster()) {

                    // Проверяем, на месте ли опорные блоки?
                    BlockPos startAnchor = BlockPos.containing(mbe.getStartPos());
                    BlockPos endAnchor = BlockPos.containing(mbe.getEndPos());

                    // Если один из опорных блоков стал воздухом (или жидкостью)
                    if (level.isEmptyBlock(startAnchor) || level.isEmptyBlock(endAnchor)) {
                        mbe.breakEntireBeam(level); // Рушим всю балку!
                    }
                }
            }
        }
    }
}