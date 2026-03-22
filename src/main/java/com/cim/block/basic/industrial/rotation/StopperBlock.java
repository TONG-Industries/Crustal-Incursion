package com.cim.block.basic.industrial.rotation;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.industrial.rotation.StopperBlockEntity;

public class StopperBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING; // все 6 сторон
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled"); // true = пропускает (stopper_off), false = не пропускает (stopper_on)

    public StopperBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ENABLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENABLED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Передняя сторона блока должна смотреть на игрока
        Direction facing = context.getNearestLookingDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        // Работаем только на сервере и для основной руки
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            ItemStack itemInHand = player.getItemInHand(hand);
            boolean isBlockInHand = itemInHand.getItem() instanceof BlockItem;

            // Если в руке блок — пропускаем, чтобы можно было разместить
            if (isBlockInHand) {
                return InteractionResult.PASS;
            }

            // Иначе переключаем состояние рубильника
            boolean current = state.getValue(ENABLED);
            level.setBlock(pos, state.setValue(ENABLED, !current), 3);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // обычная блочная модель (будет меняться через блокстейт)
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StopperBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.STOPPER_BE.get(), StopperBlockEntity::tick);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof StopperBlockEntity be) {
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
}