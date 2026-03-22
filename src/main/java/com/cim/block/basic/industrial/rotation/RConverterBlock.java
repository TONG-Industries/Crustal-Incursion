package com.cim.block.basic.industrial.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.industrial.rotation.RConverterBlockEntity;

public class RConverterBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public RConverterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // можно потом сделать модель
    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RConverterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Блок ставится лицевой стороной (выход энергии) к игроку
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof RConverterBlockEntity be) {
            ItemStack itemInHand = player.getItemInHand(hand);
            boolean isBlockInHand = itemInHand.getItem() instanceof net.minecraft.world.item.BlockItem;

            // Если в руке не блок — показываем информацию
            if (!isBlockInHand) {
                if (!level.isClientSide) {
                    long speed = be.getSpeed();
                    long torque = be.getTorque();
                    long energyPerTick = be.getCurrentEnergyPerTick();
                    long energyPerSecond = energyPerTick * 20;
                    player.displayClientMessage(
                            Component.literal(String.format("Speed: %d, Torque: %d, Energy: %d RF/t (%.0f RF/s)",
                                    speed, torque, energyPerTick, (double) energyPerSecond)),
                            false // false = сообщение в чат
                    );
                }
                return InteractionResult.SUCCESS; // предотвращаем дальнейшее взаимодействие
            } else {
                // В руке блок — пропускаем, чтобы можно было разместить блок
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.R_CONVERTER_BE.get(), RConverterBlockEntity::tick);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RConverterBlockEntity be) {
            be.invalidateCache();
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
}