package com.cim.block.basic.industrial;

import com.cim.block.entity.ModBlockEntities;

import com.cim.block.entity.industrial.MillstoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MillstoneBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Форма жерновов - немного ниже целого блока
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public MillstoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MillstoneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.MILLSTONE.get(), MillstoneBlockEntity::tick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MillstoneBlockEntity millstone)) return InteractionResult.PASS;

        ItemStack heldItem = player.getItemInHand(hand);

        // Забрать результат
        if (heldItem.isEmpty()) {
            ItemStack result = millstone.extractResult();
            if (!result.isEmpty()) {
                player.addItem(result);
                return InteractionResult.CONSUME;
            }
        }

        // Положить предмет
        if (!heldItem.isEmpty()) {
            ItemStack remaining = millstone.insertItem(heldItem.copy());
            if (remaining.getCount() != heldItem.getCount()) {
                player.setItemInHand(hand, remaining);
                return InteractionResult.CONSUME;
            }
        }

        // Помол с кулдауном
        if (millstone.canGrind()) {
            if (millstone.doGrind()) {
                // Звук скрипа камня
                level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS,
                        0.6f, 0.5f + level.random.nextFloat() * 0.3f);

                return InteractionResult.CONSUME;
            }
        } else if (millstone.isProcessing() && millstone.getCooldownProgress() < MillstoneBlockEntity.GRIND_COOLDOWN) {
            // Пока кулдаун - показываем сообщение или частицы "отбоя"
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.3f, 0.8f);
        }

        return InteractionResult.PASS;
    }

    // Поддержка воронок (извлечение результата сверху или снизу)
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MillstoneBlockEntity millstone) {
            return millstone.getComparatorSignal();
        }
        return 0;
    }
}