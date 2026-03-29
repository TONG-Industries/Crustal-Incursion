package com.cim.block.basic.fluids;

import com.cim.api.fluids.FluidNetwork;
import com.cim.api.fluids.FluidNetworkManager;
import com.cim.api.fluids.FluidNode;
import com.cim.api.fluids.PipeTier;
import com.cim.block.entity.fluids.FluidPipeBlockEntity;
import com.cim.item.tools.FluidIdentifierItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

public class FluidPipeBlock extends Block implements EntityBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NONE = BooleanProperty.create("none");

    private final PipeTier tier;

    // ==========================================
    // VOXEL SHAPES (ИДЕАЛЬНАЯ 3D-КОЛЛИЗИЯ)
    // ==========================================
    private static final VoxelShape CORE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape UP_SHAPE = Block.box(5.0D, 11.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape DOWN_SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 5.0D, 11.0D);
    private static final VoxelShape NORTH_SHAPE = Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 5.0D);
    private static final VoxelShape SOUTH_SHAPE = Block.box(5.0D, 5.0D, 11.0D, 11.0D, 11.0D, 16.0D);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0D, 5.0D, 5.0D, 5.0D, 11.0D, 11.0D);
    private static final VoxelShape EAST_SHAPE = Block.box(11.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D);

    public FluidPipeBlock(PipeTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false)
                .setValue(NONE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, NONE);
    }

    // ==========================================
    // УМНЫЙ VOXELSHAPE (ИСПРАВЛЕННЫЙ)
    // ==========================================
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 1. Центральное ядро всегда
        VoxelShape shape = CORE;

        // --- ФИКС 1: ОДИНОКАЯ КРЕСТОВИНА (Issue 1) ---
        // Если труба никуда не подключена (NONE=true), коллизия должна быть во все стороны!
        if (state.getValue(NONE)) {
            return Shapes.or(shape, NORTH_SHAPE, SOUTH_SHAPE, EAST_SHAPE, WEST_SHAPE, UP_SHAPE, DOWN_SHAPE);
        }

        boolean n = state.getValue(NORTH);
        boolean s = state.getValue(SOUTH);
        boolean e = state.getValue(EAST);
        boolean w = state.getValue(WEST);
        boolean u = state.getValue(UP);
        boolean d = state.getValue(DOWN);

        // 2. Стандартная логика коллизии (по подключенным сторонам)
        if (n) shape = Shapes.or(shape, NORTH_SHAPE);
        if (s) shape = Shapes.or(shape, SOUTH_SHAPE);
        if (e) shape = Shapes.or(shape, EAST_SHAPE);
        if (w) shape = Shapes.or(shape, WEST_SHAPE);
        if (u) shape = Shapes.or(shape, UP_SHAPE);
        if (d) shape = Shapes.or(shape, DOWN_SHAPE);

        // --- ФИКС 2: "END-CAP" ПРОТИВОПОЛОЖНЫЕ РУКАВА (Issue 2) ---
        // Модель рендерит противоположный рукав, чтобы скрыть ядро, если труба тупиковая.
        // Мы добавляем коллизию этому "визуальному" рукаву, чтобы хитбокс соответствовал модели.

        // Условие: подключено с NORTH, но не с SOUTH, и нет других горизонтальных подключений.
        if (n && !s && !(e || w || u || d)) shape = Shapes.or(shape, SOUTH_SHAPE);
        // Зеркально для SOUTH
        if (s && !n && !(e || w || u || d)) shape = Shapes.or(shape, NORTH_SHAPE);

        // Зеркально для EAST-WEST
        if (e && !w && !(n || s || u || d)) shape = Shapes.or(shape, WEST_SHAPE);
        if (w && !e && !(n || s || u || d)) shape = Shapes.or(shape, EAST_SHAPE);

        // Зеркально для UP-DOWN
        if (u && !d && !(n || s || e || w)) shape = Shapes.or(shape, DOWN_SHAPE);
        if (d && !u && !(n || s || e || w)) shape = Shapes.or(shape, UP_SHAPE);

        return shape;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(pos, state);
    }

    // ==========================================
    // ЛОГИКА СЕТИ (ОБЫЧНАЯ УСТАНОВКА)
    // ==========================================
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide && !oldState.is(this)) {
            FluidNetworkManager.get((ServerLevel) level).addNode(pos);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            FluidNetworkManager.get((ServerLevel) level).removeNode(pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ==========================================
    // ИДЕНТИФИКАТОР + ШИФТ (ОПТИМИЗИРОВАННЫЙ РЕБИЛД СЕТИ)
    // ==========================================
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && stack.getItem() instanceof FluidIdentifierItem) {
            ServerLevel serverLevel = (ServerLevel) level;
            String selectedFluidId = FluidIdentifierItem.getSelectedFluid(stack);

            // 1. Резолвим жидкость, которую хотим установить
            Fluid fluidToSet = Fluids.EMPTY;
            if (!selectedFluidId.equals("none")) {
                fluidToSet = ForgeRegistries.FLUIDS.getValue(new net.minecraft.resources.ResourceLocation(selectedFluidId));
                if (fluidToSet == null) fluidToSet = Fluids.EMPTY;
            }

            if (player.isCrouching()) {
                // === ОБНОВЛЕНИЕ ВСЕЙ СЕТИ (Shift+ПКМ) ===
                FluidNetwork network = FluidNetworkManager.get(serverLevel).getNetwork(pos);
                if (network != null && !network.isEmpty()) {

                    java.util.List<BlockPos> positionsToUpdate = new java.util.ArrayList<>();
                    for (com.cim.api.fluids.FluidNode node : network.getNodes()) {
                        positionsToUpdate.add(node.getPos());
                    }

                    FluidNetworkManager manager = FluidNetworkManager.get(serverLevel);

                    // Выводим все трубы из сети
                    for (BlockPos nodePos : positionsToUpdate) {
                        manager.removeNode(nodePos);
                    }

                    int updateCount = 0;
                    for (BlockPos nodePos : positionsToUpdate) {
                        if (serverLevel.isLoaded(nodePos)) {
                            BlockEntity nodeBe = serverLevel.getBlockEntity(nodePos);

                            // Обновляем фильтр и у ТРУБ, и у БОЧЕК в сети!
                            if (nodeBe instanceof FluidPipeBlockEntity pipeBE) {
                                pipeBE.setFilterFluid(fluidToSet);
                                updateCount++;
                            } else if (nodeBe instanceof com.cim.block.entity.fluids.FluidBarrelBlockEntity barrelBE) {
                                barrelBE.setFilter(selectedFluidId);
                                updateCount++;
                            }

                            manager.addNode(nodePos);
                            serverLevel.getBlockState(nodePos).updateNeighbourShapes(serverLevel, nodePos, 3);
                        }
                    }

                    // Обратная связь
                    boolean isResetting = selectedFluidId.equals("none");
                    level.playSound(null, pos, isResetting ? SoundEvents.ENDERMAN_TELEPORT : SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, isResetting ? 0.8F : 1.2F);

                    String fluidName = isResetting ? "EMPTY" : Component.translatable(fluidToSet.getFluidType().getDescriptionId()).getString();
                    String msg = isResetting ? "Network filter reset." : "Network filter set: " + fluidName;
                    player.displayClientMessage(Component.literal("§a" + msg + " §7(" + updateCount + " nodes)"), true);
                }
            } else {
                // === ОБНОВЛЕНИЕ ТОЛЬКО ОДНОЙ ТРУБЫ (Обычный ПКМ) ===
                if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity be) {
                    FluidNetworkManager manager = FluidNetworkManager.get(serverLevel);

                    manager.removeNode(pos);
                    be.setFilterFluid(fluidToSet); // Ставит EMPTY, если выбрано "none"
                    manager.addNode(pos);
                    level.setBlock(pos, this.updateConnections(level, pos, state), 3);

                    if (selectedFluidId.equals("none")) {
                        player.displayClientMessage(Component.literal("§eFilter reset (Pipe accepts all)"), true);
                        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.8F);
                    } else {
                        String fluidName = Component.translatable(fluidToSet.getFluidType().getDescriptionId()).getString();
                        player.displayClientMessage(Component.literal("§aFilter: §f" + fluidName), true);
                        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS, 1.0F, 1.2F);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    // ==========================================
    // ЛОГИКА СОЕДИНЕНИЙ И ПРОВЕРКА ФИЛЬТРОВ
    // ==========================================
    private BlockState updateConnections(LevelAccessor level, BlockPos pos, BlockState state) {
        boolean n = canConnectTo(level, pos, pos.north(), Direction.NORTH);
        boolean s = canConnectTo(level, pos, pos.south(), Direction.SOUTH);
        boolean e = canConnectTo(level, pos, pos.east(), Direction.EAST);
        boolean w = canConnectTo(level, pos, pos.west(), Direction.WEST);
        boolean u = canConnectTo(level, pos, pos.above(), Direction.UP);
        boolean d = canConnectTo(level, pos, pos.below(), Direction.DOWN);
        boolean none = !(n || s || e || w || u || d);

        return state.setValue(NORTH, n).setValue(SOUTH, s)
                .setValue(EAST, e).setValue(WEST, w)
                .setValue(UP, u).setValue(DOWN, d)
                .setValue(NONE, none);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        return updateConnections(level, currentPos, state);
    }

    private boolean canConnectTo(LevelAccessor level, BlockPos myPos, BlockPos neighborPos, Direction dir) {
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getBlock() instanceof FluidPipeBlock) {
            if (level.getBlockEntity(myPos) instanceof FluidPipeBlockEntity myBE &&
                    level.getBlockEntity(neighborPos) instanceof FluidPipeBlockEntity neighborBE) {

                Fluid myFluid = myBE.getFilterFluid();
                Fluid neighborFluid = neighborBE.getFilterFluid();

                // === ИЗМЕНЕНО: Строгое равенство! Пустые липнут только к пустым, лава только к лаве ===
                return myFluid == neighborFluid;
            }
        }

        BlockEntity neighborBE = level.getBlockEntity(neighborPos);
        if (neighborBE != null) {
            return neighborBE.getCapability(ForgeCapabilities.FLUID_HANDLER, dir.getOpposite()).isPresent();
        }
        return false;
    }
}