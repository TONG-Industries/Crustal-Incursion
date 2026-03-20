//package com.cim.multiblock.industrial;
//
//import com.cim.block.entity.ModBlockEntities;
//import com.cim.multiblock.system.MultiblockBlock;
//import com.cim.multiblock.system.MultiblockBlockEntity;
//import com.cim.multiblock.system.MultiblockPattern;
//import net.minecraft.core.BlockPos;
//import net.minecraft.core.Direction;
//import net.minecraft.world.InteractionHand;
//import net.minecraft.world.InteractionResult;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.item.context.BlockPlaceContext;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.entity.BlockEntity;
//import net.minecraft.world.level.block.entity.BlockEntityTicker;
//import net.minecraft.world.level.block.entity.BlockEntityType;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.block.state.StateDefinition;
//import net.minecraft.world.level.block.state.properties.BlockStateProperties;
//import net.minecraft.world.level.block.state.properties.DirectionProperty;
//import net.minecraft.world.phys.BlockHitResult;
//import org.jetbrains.annotations.Nullable;
//
//public class HeaterBlock extends MultiblockBlock {
//
//    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
//
//    public HeaterBlock(Properties properties) {
//        super(properties, createPattern());
//        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
//    }
//
//    @Override
//    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
//        builder.add(FACING);
//    }
//
//    @Override
//    public BlockState getStateForPlacement(BlockPlaceContext context) {
//        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
//    }
//
//    private static MultiblockPattern createPattern() {
//        return MultiblockPattern.fromLayers(
//                """
//                ###
//                #O#
//                ###
//                """
//        );
//    }
//
//    @Override
//    protected InteractionResult onMultiblockUse(MultiblockBlockEntity controller, Player player,
//                                                InteractionHand hand, BlockHitResult hit) {
//        if (controller instanceof HeaterBlockEntity heater) {
//            return heater.onMultiblockUse(player, hand, hit);
//        }
//        return InteractionResult.PASS;
//    }
//
//    @Nullable
//    @Override
//    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
//        return new HeaterBlockEntity(pos, state);
//    }
//
//    @Nullable
//    @Override
//    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
//                                                                  BlockEntityType<T> type) {
//        // ИСПРАВЛЕНИЕ: Используем унаследованный createTickerHelper из BaseEntityBlock
//        return level.isClientSide ? null :
//                createTickerHelper(type, ModBlockEntities.HEATER_BE.get(), HeaterBlockEntity::serverTick);
//    }
//
//    // ИСПРАВЛЕНИЕ: Статический метод для проверки размещения (для HeaterBlockItem)
//    public static boolean canPlace(Level level, BlockPos pos) {
//        // Получаем паттерн для проверки
//        MultiblockPattern pattern = MultiblockPattern.fromLayers(
//                """
//                ###
//                #O#
//                ###
//                """
//        );
//
//        // Находим origin относительно pos (предполагаем, что pos - это позиция контроллера)
//        BlockPos origin = null;
//        for (int y = 0; y < pattern.getHeight(); y++) {
//            for (int x = 0; x < pattern.getWidth(); x++) {
//                for (int z = 0; z < pattern.getDepth(); z++) {
//                    if (pattern.pattern[y][x][z] == MultiblockPattern.PatternEntry.CONTROLLER) {
//                        origin = pos.offset(-x, -y, -z);
//                        break;
//                    }
//                }
//            }
//        }
//
//        if (origin == null) {
//            origin = pos.offset(-1, 0, -1); // дефолт для 3x3
//        }
//
//        // Проверяем, что все позиции свободны
//        for (int y = 0; y < pattern.getHeight(); y++) {
//            for (int x = 0; x < pattern.getWidth(); x++) {
//                for (int z = 0; z < pattern.getDepth(); z++) {
//                    BlockPos checkPos = origin.offset(x, y, z);
//                    if (checkPos.equals(pos)) continue;
//
//                    BlockState state = level.getBlockState(checkPos);
//                    if (!state.isAir() && !state.canBeReplaced()) {
//                        return false;
//                    }
//                }
//            }
//        }
//        return true;
//    }
//}