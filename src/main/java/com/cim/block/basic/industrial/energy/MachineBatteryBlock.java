package com.cim.block.basic.industrial.energy;


import com.cim.item.energy.EnergyCellItem;
import com.cim.item.tools.ScrewdriverItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkHooks;
import com.cim.api.energy.EnergyNetworkManager;
import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.industrial.energy.MachineBatteryBlockEntity;
import com.cim.util.EnergyFormatter;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Универсальный класс блока каркаса энергохранилища.
 * Каркас сам по себе имеет 0 ёмкости. Параметры определяются энергоячейками.
 *
 * На лицевой стороне блока расположены 4 слота (2x2) для энергоячеек.
 * ПКМ с ячейкой в руке -> вставка.
 * ПКМ с отвёрткой -> извлечение.
 * Пустая рука ПКМ -> открытие GUI.
 */
public class MachineBatteryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MachineBatteryBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBatteryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.MACHINE_BATTERY_BE.get(), MachineBatteryBlockEntity::tick);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            EnergyNetworkManager.get((ServerLevel) level).removeNode(pos);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineBatteryBlockEntity battery) {
                // Извлекаем ячейки через extractCell — каждая впитает энергию из каркаса,
                // точно так же как при извлечении отвёрткой
                for (int i = 0; i < MachineBatteryBlockEntity.CELL_SLOT_COUNT; i++) {
                    if (!battery.isCellEmpty(i)) {
                        ItemStack cell = battery.extractCell(i);
                        if (!cell.isEmpty()) {
                            popResource(level, pos, cell);
                        }
                    }
                }

                // Дропаем все предметы из слотов батареек (зарядка/разрядка)
                battery.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack stack = handler.getStackInSlot(slot);
                        if (!stack.isEmpty()) {
                            popResource(level, pos, stack.copy());
                        }
                    }
                });
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof MachineBatteryBlockEntity battery)) return InteractionResult.PASS;

        ItemStack heldItem = player.getItemInHand(hand);
        Direction facing = state.getValue(FACING);
        Direction hitFace = hit.getDirection();

        // Взаимодействие с ячейками только с лицевой стороны
        if (hitFace == facing) {
            int cellSlot = getCellSlotFromHit(hit, pos, facing);

            if (cellSlot >= 0) {
                // --- ОТВЁРТКА: извлечение ячейки ---
                if (heldItem.getItem() instanceof ScrewdriverItem) {
                    if (!battery.isCellEmpty(cellSlot)) {
                        ItemStack extracted = battery.extractCell(cellSlot);
                        if (!extracted.isEmpty()) {
                            if (!player.getInventory().add(extracted)) {
                                popResource(level, pos.relative(facing), extracted);
                            }
                            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.6f, 1.2f);
                            heldItem.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
                            player.displayClientMessage(
                                    Component.literal("§eЯчейка извлечена из слота " + (cellSlot + 1)), true);
                            return InteractionResult.CONSUME;
                        }
                    } else {
                        player.displayClientMessage(
                                Component.literal("§7Слот " + (cellSlot + 1) + " пуст"), true);
                        return InteractionResult.CONSUME;
                    }
                }

                // --- ЭНЕРГОЯЧЕЙКА В РУКЕ: вставка ---
                if (heldItem.getItem() instanceof EnergyCellItem) {
                    if (battery.isCellEmpty(cellSlot)) {
                        if (battery.insertCell(cellSlot, heldItem)) {
                            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 0.9f);
                            player.displayClientMessage(
                                    Component.literal("§aЯчейка вставлена в слот " + (cellSlot + 1)), true);
                            return InteractionResult.CONSUME;
                        }
                    } else {
                        player.displayClientMessage(
                                Component.literal("§cСлот " + (cellSlot + 1) + " уже занят!"), true);
                        return InteractionResult.CONSUME;
                    }
                }
            }
        }

        // --- ПУСТАЯ РУКА или другие предметы: открываем GUI ---
        if (heldItem.isEmpty() || (!(heldItem.getItem() instanceof EnergyCellItem) && !(heldItem.getItem() instanceof ScrewdriverItem))) {
            NetworkHooks.openScreen((ServerPlayer) player, battery, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Определяет номер слота ячейки (0-3) по координатам клика на лицевой стороне.
     *
     * Лицевая сторона блока разделена на сетку 2x2:
     *   [0] [1]     (верхний левый, верхний правый)
     *   [2] [3]     (нижний левый, нижний правый)
     *
     * Координаты рассчитываются относительно направления facing.
     * Возвращает -1 если клик вне зоны слотов.
     */
    private int getCellSlotFromHit(BlockHitResult hit, BlockPos pos, Direction facing) {
        Vec3 hitVec = hit.getLocation();

        // Локальные координаты внутри блока (0.0 - 1.0)
        double localX = hitVec.x - pos.getX();
        double localY = hitVec.y - pos.getY();
        double localZ = hitVec.z - pos.getZ();

        // Определяем горизонтальную (u) и вертикальную (v) координату на лицевой стороне
        // u = 0 слева, 1 справа (с точки зрения игрока, смотрящего на лицо)
        // v = 0 снизу, 1 сверху
        double u, v;
        v = localY; // Вертикаль всегда по Y

        switch (facing) {
            case NORTH -> u = 1.0 - localX; // Игрок смотрит на юг -> X инвертирован
            case SOUTH -> u = localX;
            case WEST -> u = localZ;
            case EAST -> u = 1.0 - localZ;
            default -> { return -1; }
        }

        // Зона слотов: центральная часть лицевой стороны
        // Отступы: 0.15 по краям (слоты занимают 0.15..0.85 по u и 0.15..0.85 по v)
        double minU = 0.15, maxU = 0.85;
        double minV = 0.15, maxV = 0.85;

        if (u < minU || u > maxU || v < minV || v > maxV) {
            return -1; // Клик вне зоны слотов
        }

        // Нормализуем в пределах зоны слотов (0..1)
        double normU = (u - minU) / (maxU - minU);
        double normV = (v - minV) / (maxV - minV);

        // Определяем колонку (0 = левая, 1 = правая)
        int col = normU < 0.5 ? 0 : 1;

        // Определяем строку (0 = верхняя, 1 = нижняя)
        // v=1 это верх блока, v=0 это низ
        int row = normV >= 0.5 ? 0 : 1;

        // slot = row * 2 + col
        //  [0][1]   верх
        //  [2][3]   низ
        return row * 2 + col;
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);

        if (!pLevel.isClientSide) {
            BlockEntity be = pLevel.getBlockEntity(pPos);
            if (be instanceof MachineBatteryBlockEntity batteryBE) {
                CompoundTag itemNbt = pStack.getTag();
                if (itemNbt != null && itemNbt.contains("BlockEntityTag")) {
                    batteryBE.load(itemNbt.getCompound("BlockEntityTag"));
                    batteryBE.setChanged();
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable BlockGetter pLevel, List<Component> pTooltip, TooltipFlag pFlag) {
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);

        long energy = 0;
        CompoundTag nbt = pStack.getTag();
        if (nbt != null && nbt.contains("BlockEntityTag")) {
            energy = nbt.getCompound("BlockEntityTag").getLong("Energy");
        }

        String energyStr = EnergyFormatter.format(energy);
        pTooltip.add(Component.literal("§7Каркас энергохранилища"));
        pTooltip.add(Component.literal("§eЭнергия: " + energyStr + " HE"));
        pTooltip.add(Component.literal("§8Вставьте энергоячейки для увеличения параметров"));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
}