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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

public class FluidPipeBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NONE = BooleanProperty.create("none");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

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
                .setValue(NONE, true)
                .setValue(WATERLOGGED, false))
        ;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN, NONE, WATERLOGGED); // <--- ДОБАВИЛ СЮДА
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
            com.cim.api.fluids.FluidNetworkManager.get((ServerLevel) level).addNode(pos);
            // Запускаем отложенную проверку среды (чтобы не лагало при массовой застройке)
            level.scheduleTick(pos, this, 2);
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

    public PipeTier getTier() {
        return this.tier;
    }

    // ==========================================
    // ИДЕНТИФИКАТОР + ШИФТ (ОБЫЧНАЯ НАСТРОЙКА БЕЗ ВЗРЫВОВ)
    // ==========================================
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && stack.getItem() instanceof FluidIdentifierItem) {
            ServerLevel serverLevel = (ServerLevel) level;
            String selectedFluidId = FluidIdentifierItem.getSelectedFluid(stack);

            Fluid fluidToSet = Fluids.EMPTY;
            if (!selectedFluidId.equals("none")) {
                fluidToSet = ForgeRegistries.FLUIDS.getValue(new net.minecraft.resources.ResourceLocation(selectedFluidId));
                if (fluidToSet == null) fluidToSet = Fluids.EMPTY;
            }

            if (player.isCrouching()) {
                FluidNetwork network = FluidNetworkManager.get(serverLevel).getNetwork(pos);
                if (network != null && !network.isEmpty()) {

                    java.util.List<BlockPos> positionsToUpdate = new java.util.ArrayList<>();
                    for (com.cim.api.fluids.FluidNode node : network.getNodes()) {
                        positionsToUpdate.add(node.getPos());
                    }

                    FluidNetworkManager manager = FluidNetworkManager.get(serverLevel);
                    for (BlockPos nodePos : positionsToUpdate) {
                        manager.removeNode(nodePos);
                    }

                    int updateCount = 0;
                    for (BlockPos nodePos : positionsToUpdate) {
                        if (serverLevel.isLoaded(nodePos)) {
                            BlockEntity nodeBe = serverLevel.getBlockEntity(nodePos);

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

                    boolean isResetting = selectedFluidId.equals("none");
                    level.playSound(null, pos, isResetting ? SoundEvents.ENDERMAN_TELEPORT : SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, isResetting ? 0.8F : 1.2F);
                    String fluidName = isResetting ? "EMPTY" : Component.translatable(fluidToSet.getFluidType().getDescriptionId()).getString();
                    String msg = isResetting ? "Network filter reset." : "Network filter set: " + fluidName;
                    player.displayClientMessage(Component.literal("§a" + msg + " §7(" + updateCount + " nodes)"), true);
                }
            } else {
                if (level.getBlockEntity(pos) instanceof FluidPipeBlockEntity be) {
                    FluidNetworkManager manager = FluidNetworkManager.get(serverLevel);

                    manager.removeNode(pos);
                    be.setFilterFluid(fluidToSet);
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
    public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (checkExternalMeltdown(level, pos)) {
            return; // Если труба расплавилась, прерываем выполнение
        }
    }

    private boolean checkExternalMeltdown(ServerLevel level, BlockPos pos) {
        com.cim.api.fluids.PipeTier tier = this.getTier();

        // Собираем список для проверки: сам блок трубы + 6 соседей
        java.util.List<BlockPos> positionsToCheck = new java.util.ArrayList<>();
        positionsToCheck.add(pos);
        for (Direction dir : Direction.values()) {
            positionsToCheck.add(pos.relative(dir));
        }

        for (BlockPos checkPos : positionsToCheck) {
            net.minecraft.world.level.material.FluidState fluidState = level.getFluidState(checkPos);

            if (!fluidState.isEmpty()) {
                Fluid fluid = fluidState.getType();
                int temp = fluid.getFluidType().getTemperature();
                int acid = 0;


                if (fluid.getFluidType() instanceof com.cim.api.fluids.BaseFluidType base) {
                    acid = base.getAcidity();

                } else if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
                    temp = 1300; // Температура ванильной лавы
                }

                // Если агрессивная среда превышает лимиты трубы
                if (temp > tier.getMaxTemperature() || acid > tier.getMaxAcidity()) {

                    // ВЗРЫВ! (Плавление от внешней среды)
                    level.destroyBlock(pos, false);

                    // Оставляем на месте трубы ту же жидкость, которая её расплавила!
                    BlockState fluidBlock = fluidState.createLegacyBlock();
                    if (!fluidBlock.isAir()) {
                        level.setBlock(pos, fluidBlock, 3);
                    }

                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        // Если труба затоплена, рендерим внутри нее блок воды
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        // Поддерживаем течение воды, если труба затоплена
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        // Если рядом что-то разлили/поставили - проверяем среду!
        if (!level.isClientSide()) {
            level.scheduleTick(currentPos, this, 2);
        }

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

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluidstate.getType() == Fluids.WATER);
    }

    // ==========================================
    // ВИЗУАЛЬНЫЕ ЭФФЕКТЫ (КЛИЕНТ-САЙД, 0 ЛАГОВ)
    // ==========================================
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        // Проверяем, затоплена ли труба
        if (!state.getValue(WATERLOGGED)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FluidPipeBlockEntity pipeBE) {
            // Проверяем, текла ли по ней жидкость
            if (pipeBE.hasFlowed()) {
                Fluid fluid = pipeBE.getFilterFluid();

                // Проверяем, что жидкость реально горячая.
                // Вода в Forge = 300 Кельвинов. Значит всё, что больше 300 - заставит воду кипеть!
                if (fluid != Fluids.EMPTY && fluid.getFluidType().getTemperature() > 300) {

                    // Спавним пузырьки с шансом (чтобы не перегружать экран белым шумом)
                    if (random.nextInt(3) == 0) {
                        double d0 = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.5D; // Случайный X внутри трубы
                        double d1 = pos.getY() + 0.6D + (random.nextDouble() * 0.4D);        // Чуть выше центра
                        double d2 = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.5D; // Случайный Z внутри трубы

                        // Добавляем ванильные пузырьки, которые поднимаются вверх
                        level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP, d0, d1, d2, 0.0D, 0.05D, 0.0D);
                    }
                }
            }
        }
    }
}