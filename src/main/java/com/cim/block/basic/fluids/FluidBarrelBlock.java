package com.cim.block.basic.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.fluids.FluidBarrelBlockEntity;

public class FluidBarrelBlock extends BaseEntityBlock {

    // Хитбокс 12x12x16 (центрированный)
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);

    public FluidBarrelBlock(Properties properties) {
        super(properties);
    }

    // ====================== ХИТБОКС И КОЛЛИЗИЯ ======================
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ====================== ПРИВЯЗКА BLOCK ENTITY ======================
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidBarrelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Регистрируем метод tick из нашего FluidBarrelBlockEntity
        return createTickerHelper(type, ModBlockEntities.FLUID_BARREL_BE.get(), FluidBarrelBlockEntity::tick);
    }

    // ====================== РЕНДЕР ======================
    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Если используешь GeckoLib для бочки, смени на ENTITYBLOCK_ANIMATED
        // Если обычная JSON-модель, оставляй MODEL
        return RenderShape.MODEL;
    }

    // ====================== ВЗАИМОДЕЙСТВИЕ (ОТКРЫТИЕ GUI) ======================
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        // Если в руке идентификатор - НЕ ОТКРЫВАЕМ GUI бочки, а ставим фильтр
        if (heldItem.getItem() instanceof com.cim.item.tools.FluidIdentifierItem) {
            if (!level.isClientSide) {
                String selected = com.cim.item.tools.FluidIdentifierItem.getSelectedFluid(heldItem);
                BlockEntity be = level.getBlockEntity(pos);

                if (be instanceof FluidBarrelBlockEntity barrel) {
                    barrel.setFilter(selected); // Устанавливаем фильтр и (если нужно) стираем жидкость

                    // Красивое сообщение в чат
                    String msgName = selected.equals("none") ? "Ничего (Сброс фильтра)" : selected.replace("minecraft:", "").replace("cim:", "");
                    player.displayClientMessage(Component.literal("§aФильтр бочки установлен: §e" + msgName), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FluidBarrelBlockEntity barrel) {
                // Открываем GUI
                NetworkHooks.openScreen((ServerPlayer) player, barrel, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ====================== РАЗРУШЕНИЕ БЛОКА ======================
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FluidBarrelBlockEntity barrel) {
                // Выбрасываем предметы (вёдра) из слотов GUI
                barrel.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getStackInSlot(i));
                    }
                });

                // (Опционально) Если хочешь, чтобы при разрушении бочки с жидкостью она выливалась в мир лужей,
                // это можно добавить здесь, проверяя barrel.fluidTank.getFluid().
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}