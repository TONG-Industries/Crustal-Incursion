package com.cim.block.basic.industrial.rotation;

import com.cim.api.rotation.KineticNetworkManager;
import com.cim.api.rotation.ShaftDiameter;
import com.cim.api.rotation.ShaftMaterial;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import com.cim.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ShaftBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty GEAR_SIZE = IntegerProperty.create("gear_size", 0, 3);
    public static final IntegerProperty PULLEY_SIZE = IntegerProperty.create("pulley_size", 0, 3);

    // НОВЫЕ ПЕРЕМЕННЫЕ
    private final ShaftMaterial material;
    private final ShaftDiameter diameter;

    // НОВЫЙ КОНСТРУКТОР НА 3 АРГУМЕНТА
    public ShaftBlock(Properties properties, ShaftMaterial material, ShaftDiameter diameter) {
        super(properties);
        this.material = material;
        this.diameter = diameter;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(GEAR_SIZE, 0)
                .setValue(PULLEY_SIZE, 0)); // По умолчанию вал пустой
    }

    // Геттеры для сущности (BlockEntity)
    public ShaftMaterial getMaterial() { return material; }
    public ShaftDiameter getDiameter() { return diameter; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GEAR_SIZE, PULLEY_SIZE);
    }

    // ДИНАМИЧЕСКИЙ ХИТБОКС (зависит от диаметра)
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double p = diameter.pixels / 2.0;
        double min = 8.0 - p;
        double max = 8.0 + p;

        // Хитбокс самого вала
        VoxelShape shaftShape = switch (state.getValue(FACING).getAxis()) {
            case X -> Block.box(0.0, min, min, 16.0, max, max);
            case Y -> Block.box(min, 0.0, min, max, 16.0, max);
            case Z -> Block.box(min, min, 0.0, max, max, 16.0);
        };

        int gearSize = state.getValue(GEAR_SIZE);
        int pulleySize = state.getValue(PULLEY_SIZE);

        VoxelShape attachmentShape = Shapes.empty();
        double tMin = 6.0, tMax = 10.0; // Толщина детали по центру оси (4 пикселя)

        // 1. Хитбокс шкива (Динамический, основан на размере)
        if (pulleySize > 0) {
            // Размеры в пикселях: 1 = 12px, 2 = 24px, 3 = 36px
            double pulleyRadius = (pulleySize == 1 ? 6.0 : (pulleySize == 2 ? 12.0 : 18.0));
            double pMin = 8.0 - pulleyRadius;
            double pMax = 8.0 + pulleyRadius;

            attachmentShape = switch (state.getValue(FACING).getAxis()) {
                case X -> Block.box(tMin, pMin, pMin, tMax, pMax, pMax);
                case Y -> Block.box(pMin, tMin, pMin, pMax, tMax, pMax);
                case Z -> Block.box(pMin, pMin, tMin, pMax, pMax, tMax);
            };
            return Shapes.or(shaftShape, attachmentShape);
        }

        // 2. Хитбокс шестерни (твой старый код)
        if (gearSize == 1) {
            double gMin = 0.0, gMax = 16.0;
            attachmentShape = switch (state.getValue(FACING).getAxis()) {
                case X -> Block.box(tMin, gMin, gMin, tMax, gMax, gMax);
                case Y -> Block.box(gMin, tMin, gMin, gMax, tMax, gMax);
                case Z -> Block.box(gMin, gMin, tMin, gMax, gMax, tMax);
            };
            return Shapes.or(shaftShape, attachmentShape);
        } else if (gearSize >= 2) {
            double gMin = -8.0, gMax = 24.0;
            attachmentShape = switch (state.getValue(FACING).getAxis()) {
                case X -> Block.box(tMin, gMin, gMin, tMax, gMax, gMax);
                case Y -> Block.box(gMin, tMin, gMin, gMax, tMax, gMax);
                case Z -> Block.box(gMin, gMin, tMin, gMax, gMax, tMax);
            };
            return Shapes.or(shaftShape, attachmentShape);
        }

        return shaftShape;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).is(ModItems.SCREWDRIVER.get())) {
            boolean isGear = state.getValue(GEAR_SIZE) > 0;
            boolean isPulley = state.getValue(PULLEY_SIZE) > 0;

            if (isGear || isPulley) {
                if (!level.isClientSide) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof ShaftBlockEntity shaftBE) {

                        if (isGear && shaftBE.hasGear()) {
                            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), shaftBE.getAttachedGear());
                            shaftBE.setAttachedGear(net.minecraft.world.item.ItemStack.EMPTY);
                        } else if (isPulley && shaftBE.hasPulley()) {
                            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), shaftBE.getAttachedPulley());
                            shaftBE.setAttachedPulley(net.minecraft.world.item.ItemStack.EMPTY);

                            // ОБРЫВ РЕМНЯ: Чистим память у связанного вала!
                            BlockPos connected = shaftBE.getConnectedPulley();
                            if (connected != null && level.getBlockEntity(connected) instanceof ShaftBlockEntity otherBE) {
                                otherBE.setConnectedPulley(null);
                            }
                            shaftBE.setConnectedPulley(null);
                        }

                        level.setBlock(pos, state.setValue(GEAR_SIZE, 0).setValue(PULLEY_SIZE, 0), 3);
                        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);

                        // Пересчет
                        KineticNetworkManager manager = KineticNetworkManager.get((ServerLevel) level);
                        manager.updateNetworkAfterRemove(pos);
                        manager.updateNetworkAfterPlace(pos);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        // Получаем позицию блока, по которому кликнул игрок
        BlockPos posAgainst = pos.relative(clickedFace.getOpposite());
        BlockState stateAgainst = level.getBlockState(posAgainst);
        BlockEntity beAgainst = level.getBlockEntity(posAgainst);

        // 1. ЖЕСТКИЙ ЗАПРЕТ: Разрешаем ставить только на кинетические блоки!
        // Поскольку все наши механизмы (мотор, подшипник, вал) имеют BlockEntity
        // с интерфейсом Rotational, эта проверка отсекает вообще всё лишнее (землю, камень, воздух).
        if (!(beAgainst instanceof com.cim.api.rotation.Rotational)) {
            return null; // Отменяем установку
        }

        // Изначально предполагаем направление по взгляду игрока
        Direction placementFacing = context.getNearestLookingDirection().getOpposite();

        // --- ЛОГИКА ВЫРАВНИВАНИЯ И ПРОВЕРКИ ДИАМЕТРОВ ---
        if (stateAgainst.getBlock() instanceof ShaftBlock clickedShaft) {
            // Если мы пытаемся продолжить линию вала (кликаем в торец)
            if (stateAgainst.getValue(FACING).getAxis() == clickedFace.getAxis()) {
                if (clickedShaft.getDiameter() != this.diameter) {
                    return null; // Разные диаметры соединять нельзя
                }
                placementFacing = stateAgainst.getValue(FACING);
            } else {
                // Если кликнули сбоку по валу, делаем новый вал параллельным
                placementFacing = stateAgainst.getValue(FACING);
            }
        } else if (stateAgainst.getBlock() instanceof BearingBlock) {
            // Перенимаем ось подшипника
            placementFacing = stateAgainst.getValue(BearingBlock.FACING);
        } else if (stateAgainst.getBlock() instanceof MotorElectroBlock) {
            // Перенимаем ось мотора
            placementFacing = stateAgainst.getValue(MotorElectroBlock.FACING);
        }

        // --- ЗАЩИТА ОТ СОСЕДЕЙ ---
        // Проверяем, не упрется ли новый вал в вал другого диаметра с другой стороны
        for (Direction dir : new Direction[]{placementFacing, placementFacing.getOpposite()}) {
            BlockState neighborState = level.getBlockState(pos.relative(dir));

            if (neighborState.getBlock() instanceof ShaftBlock neighborShaft) {
                if (neighborState.getValue(FACING).getAxis() == placementFacing.getAxis()) {
                    if (neighborShaft.getDiameter() != this.diameter) {
                        return null; // С другой стороны стоит вал другого размера!
                    }
                }
            }
        }

        return this.defaultBlockState().setValue(FACING, placementFacing);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShaftBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterPlace(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShaftBlockEntity shaftBE) {
                if (shaftBE.hasGear()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), shaftBE.getAttachedGear());
                }
                if (shaftBE.hasPulley()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), shaftBE.getAttachedPulley());

                    // ОБРЫВ РЕМНЯ при ломании блока киркой
                    BlockPos connected = shaftBE.getConnectedPulley();
                    if (connected != null && level.getBlockEntity(connected) instanceof ShaftBlockEntity otherBE) {
                        otherBE.setConnectedPulley(null);
                    }
                }
            }
            if (!level.isClientSide) {
                KineticNetworkManager.get((ServerLevel) level).updateNetworkAfterRemove(pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}