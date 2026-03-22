package com.cim.multiblock.industrial;

import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.ModBlockEntities;
import com.cim.multiblock.system.IMultiblockController;
import com.cim.multiblock.system.MultiblockStructureHelper;
import com.cim.multiblock.system.PartRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public class HeaterBlock extends BaseEntityBlock implements IMultiblockController {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static MultiblockStructureHelper helper;

    public HeaterBlock(Properties properties) {
        super(properties.noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // === ИНИЦИАЛИЗАЦИЯ ПАТТЕРНА ===
    @Override
    public MultiblockStructureHelper getStructureHelper() {
        if (helper == null) {
            // Карта блоков
            Map<Character, Supplier<BlockState>> symbols = Map.of(
                    '#', () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    'O', () -> this.defaultBlockState()
            );
            // Карта ролей
            Map<Character, PartRole> roles = Map.of(
                    '#', PartRole.DEFAULT,
                    'O', PartRole.CONTROLLER
            );
            // Создаем структуру 3x1x3 с контроллером в центре
            helper = MultiblockStructureHelper.createFromLayersWithRoles(
                    new String[][]{
                            {"###", "#O#", "###"}
                    },
                    symbols,
                    () -> ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(),
                    roles
            );
        }
        return helper;
    }

    @Override
    public PartRole getPartRole(BlockPos localOffset) {
        return PartRole.DEFAULT;
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        net.minecraft.core.Direction facing = state.getValue(FACING);
        // Берем готовую объединенную форму из Ядра
        return getStructureHelper().generateShapeFromParts(facing);
    }

    // === ПОСТРОЕНИЕ И РАЗРУШЕНИЕ ===
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            getStructureHelper().placeStructure(level, pos, facing, this);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof HeaterBlockEntity heater) {
                // Дропаем содержимое инвентаря вручную
                ItemStackHandler inventory = heater.getInventory();
                for (int i = 0; i < inventory.getSlots(); i++) {
                    ItemStack stack = inventory.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
            }

            // Удаляем структуру мультиблока
            Direction facing = state.getValue(FACING);
            getStructureHelper().destroyStructure(level, pos, facing);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // === ВЗАИМОДЕЙСТВИЕ И TILE ENTITY ===
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HeaterBlockEntity heater) {
            net.minecraftforge.network.NetworkHooks.openScreen(
                    (net.minecraft.server.level.ServerPlayer) player,
                    heater,
                    pos
            );
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeaterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.HEATER_BE.get(), HeaterBlockEntity::serverTick);
    }
}