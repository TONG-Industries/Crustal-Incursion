package com.cim.block.basic.industrial.rotation;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import com.cim.api.energy.EnergyNetworkManager;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.cim.menu.MotorElectroMenu;

public class MotorElectroBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MotorElectroBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // Стандартные формы блока (оставляем Shapes.block() как в оригинале)
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MotorElectroBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MotorElectroBlockEntity motor) {
                // ВАЖНО: Используем NetworkHooks для открытия экрана и передаем данные из BE
                NetworkHooks.openScreen((ServerPlayer) player,
                        new SimpleMenuProvider(
                                (id, inv, p) -> new MotorElectroMenu(id, inv, motor, motor.getDataAccess()),
                                Component.translatable("container.motor_electro_menu")
                        ), pos);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.MOTOR_ELECTRO_BE.get(), MotorElectroBlockEntity::tick);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MotorElectroBlockEntity be) {
            be.invalidateCache();
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) level).addNode(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            // Удаление из энергосети
            EnergyNetworkManager.get((ServerLevel) level).removeNode(pos);

            // Инвалидация кеша вращения у соседей
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