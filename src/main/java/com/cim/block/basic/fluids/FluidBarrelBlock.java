package com.cim.block.basic.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraftforge.registries.ForgeRegistries;
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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);

        // 1. Если кликаем Идентификатором
        if (stack.getItem() instanceof com.cim.item.tools.FluidIdentifierItem) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof com.cim.block.entity.fluids.FluidBarrelBlockEntity be) {
                String selectedFluidId = com.cim.item.tools.FluidIdentifierItem.getSelectedFluid(stack);

                be.setFilter(selectedFluidId); // Поддерживает и "none", и обычные ID

                if (selectedFluidId.equals("none")) {
                    player.displayClientMessage(Component.literal("§eBarrel filter reset (Closed)"), true);
                    level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 0.8F);
                } else {
                    net.minecraft.world.level.material.Fluid fluidToSet = ForgeRegistries.FLUIDS.getValue(new net.minecraft.resources.ResourceLocation(selectedFluidId));
                    String fluidName = fluidToSet != null ? Component.translatable(fluidToSet.getFluidType().getDescriptionId()).getString() : selectedFluidId;
                    player.displayClientMessage(Component.literal("§aBarrel Filter: §f" + fluidName), true);
                    level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.get(), SoundSource.BLOCKS, 1.0F, 1.2F);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 2. Стандартный код (открытие GUI бочки)
        if (!level.isClientSide) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof com.cim.block.entity.fluids.FluidBarrelBlockEntity) {
                net.minecraftforge.network.NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, (com.cim.block.entity.fluids.FluidBarrelBlockEntity) entity, pos);
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

    // ==========================================
    // СОХРАНЕНИЕ ДАННЫХ ПРИ СЛОМЕ И УСТАНОВКЕ
    // ==========================================

    // 1. Установка бочки (восстановление жидкости, фильтра и инвентаря)
    @Override
    public void setPlacedBy(net.minecraft.world.level.Level pLevel, net.minecraft.core.BlockPos pPos, net.minecraft.world.level.block.state.BlockState pState, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity pPlacer, net.minecraft.world.item.ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);

        if (!pLevel.isClientSide) {
            net.minecraft.world.level.block.entity.BlockEntity be = pLevel.getBlockEntity(pPos);
            if (be instanceof com.cim.block.entity.fluids.FluidBarrelBlockEntity barrelBE) {
                net.minecraft.nbt.CompoundTag itemNbt = pStack.getTag();
                // Если в предмете есть сохраненные внутренности — загружаем их!
                if (itemNbt != null && itemNbt.contains("BlockEntityTag")) {
                    barrelBE.load(itemNbt.getCompound("BlockEntityTag"));
                    barrelBE.setChanged();
                }
            }
        }
    }

    // 2. Дроп бочки с сохранением NBT при разрушении киркой (игнорирует стандартный лут)
    @Override
    @SuppressWarnings("deprecation")
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(net.minecraft.world.level.block.state.BlockState pState, net.minecraft.world.level.storage.loot.LootParams.Builder pParams) {
        net.minecraft.world.level.block.entity.BlockEntity blockEntity = pParams.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);

        if (blockEntity instanceof com.cim.block.entity.fluids.FluidBarrelBlockEntity barrel) {
            net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(this);
            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();

            // Сохраняем все жидкости, инвентарь и режимы в тег
            barrel.saveAdditional(nbt);
            itemStack.addTagElement("BlockEntityTag", nbt);

            return java.util.Collections.singletonList(itemStack);
        }

        return super.getDrops(pState, pParams);
    }

    // 3. Красивый тултип в инвентаре (как у твоей батарейки)
    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack pStack, @org.jetbrains.annotations.Nullable net.minecraft.world.level.BlockGetter pLevel, java.util.List<net.minecraft.network.chat.Component> pTooltip, net.minecraft.world.item.TooltipFlag pFlag) {
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);

        pTooltip.add(net.minecraft.network.chat.Component.literal("§7Хранилище жидкостей"));

        net.minecraft.nbt.CompoundTag nbt = pStack.getTag();
        if (nbt != null && nbt.contains("BlockEntityTag")) {
            net.minecraft.nbt.CompoundTag beTag = nbt.getCompound("BlockEntityTag");

            // Читаем жидкость
            if (beTag.contains("FluidName")) {
                String fluidName = beTag.getString("FluidName");
                int amount = beTag.getInt("Amount");

                if (!fluidName.equals("minecraft:empty") && amount > 0) {
                    net.minecraft.world.level.material.Fluid fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(new net.minecraft.resources.ResourceLocation(fluidName));
                    if (fluid != null) {
                        String localizedName = net.minecraft.network.chat.Component.translatable(fluid.getFluidType().getDescriptionId()).getString();
                        pTooltip.add(net.minecraft.network.chat.Component.literal("§bЖидкость: §f" + localizedName));
                        pTooltip.add(net.minecraft.network.chat.Component.literal("§eОбъем: §f" + amount + " / 16000 mB"));
                    }
                } else {
                    pTooltip.add(net.minecraft.network.chat.Component.literal("§bЖидкость: §7Пусто"));
                }
            } else {
                pTooltip.add(net.minecraft.network.chat.Component.literal("§bЖидкость: §7Пусто"));
            }

            // Читаем фильтр
            String filter = beTag.getString("FluidFilter");
            if (filter != null && !filter.isEmpty() && !filter.equals("none")) {
                net.minecraft.world.level.material.Fluid f = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(new net.minecraft.resources.ResourceLocation(filter));
                if (f != null) {
                    String fName = net.minecraft.network.chat.Component.translatable(f.getFluidType().getDescriptionId()).getString();
                    pTooltip.add(net.minecraft.network.chat.Component.literal("§aФильтр: §f" + fName));
                }
            } else {
                pTooltip.add(net.minecraft.network.chat.Component.literal("§aФильтр: §cЗакрыто"));
            }
        } else {
            pTooltip.add(net.minecraft.network.chat.Component.literal("§bЖидкость: §7Пусто"));
            pTooltip.add(net.minecraft.network.chat.Component.literal("§aФильтр: §cЗакрыто"));
        }
    }
}