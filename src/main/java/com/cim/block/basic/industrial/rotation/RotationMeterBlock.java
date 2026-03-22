package com.cim.block.basic.industrial.rotation;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
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
import com.cim.block.entity.industrial.rotation.RotationMeterBlockEntity;

public class RotationMeterBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING; // или FACING? Пусть горизонтальный, передняя сторона смотрит на игрока

    public RotationMeterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        // Получаем BlockEntity
        if (level.getBlockEntity(pos) instanceof RotationMeterBlockEntity be) {
            boolean hasSource = be.hasSource();
            ItemStack itemInHand = player.getItemInHand(hand);
            boolean isBlockInHand = itemInHand.getItem() instanceof BlockItem;

            // Если есть источник и в руке НЕ блок — показываем информацию
            if (hasSource && !isBlockInHand) {
                if (!level.isClientSide) {
                    long speed = be.getSpeed();
                    long torque = be.getTorque();
                    player.sendSystemMessage(Component.literal("Speed: " + speed + ", Torque: " + torque));
                }
                return InteractionResult.SUCCESS; // предотвращаем дальнейшее взаимодействие (размещение блока)
            } else {
                // В остальных случаях (нет источника или в руке блок) пропускаем,
                // чтобы можно было разместить блок или использовать предмет.
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.PASS;
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite(); // ставим передней стороной к игроку
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // обычная блочная модель
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RotationMeterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.ROTATION_METER_BE.get(), RotationMeterBlockEntity::tick);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof RotationMeterBlockEntity be) {
                be.invalidateCache();
            }
        }
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            // Инвалидируем кеш у всех соседей
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                if (level.getBlockEntity(neighborPos) instanceof RotationalNode node) {
                    node.invalidateCache();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
    // При обновлении соседей можно проверять подключение валов, но для функциональности это не обязательно
}