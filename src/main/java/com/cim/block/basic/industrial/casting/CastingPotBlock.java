package com.cim.block.basic.industrial.casting;

import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.industrial.casting.CastingPotBlockEntity;
import com.cim.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CastingPotBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public CastingPotBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Устанавливается противоположно направлению взгляда игрока
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.box(0, 0, 0, 1, 0.5, 1);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getShape(state, level, pos, CollisionContext.empty());
    }

   

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }



    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastingPotBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.CASTING_POT.get(), CastingPotBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof CastingPotBlockEntity pot)) {
            return InteractionResult.PASS;
        }

        ItemStack heldItem = player.getItemInHand(hand);
        boolean isPickaxe = heldItem.getItem() instanceof PickaxeItem;

        // 1. ВОЗВРАТ ГОРЯЧЕГО ПРЕДМЕТА обратно в котел
        if (!heldItem.isEmpty() && heldItem.hasTag() && heldItem.getTag().contains("HotTime")) {
            if (pot.tryInsertHotItem(heldItem)) {
                heldItem.shrink(1);
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.0f);
                return InteractionResult.CONSUME;
            }
            // Если не получилось вставить (например, котёл занят), показываем сообщение
            player.displayClientMessage(Component.literal("§cНельзя поместить: котёл занят или нет формы"), true);
            return InteractionResult.PASS;
        }

        // 2. ДОСТАВАНИЕ ПРЕДМЕТА (киркой или рукой)
        if (!pot.getOutputItem().isEmpty()) {
            // Киркой - достаём даже если горячий
            if (isPickaxe) {
                ItemStack drop = pot.takeOutput();
                // takeOutput уже сохраняет оставшееся время остывания в NBT если нужно

                if (!player.getInventory().add(drop)) {
                    player.drop(drop, false);
                }
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }

            // Рукой - только если остыл
            if (pot.getCoolingTimer() > 0) {
                // Показываем прогресс остывания
                int percent = (int)((pot.getCoolingTimer() / (float)CastingPotBlockEntity.BASE_COOLING_TIME) * 100);
                player.displayClientMessage(Component.literal("§cСлишком горячо! (" + percent + "%) Используйте кирку."), true);
                return InteractionResult.PASS;
            }

            // Обычная выдача остывшего предмета
            ItemStack drop = pot.takeOutput();
            if (heldItem.isEmpty()) {
                player.setItemInHand(hand, drop);
            } else {
                if (!player.getInventory().add(drop)) {
                    player.drop(drop, false);
                }
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        ItemStack moldStack = pot.getMold();

        // Отвёртка извлекает форму
        if (heldItem.is(ModItems.SCREWDRIVER.get())) {
            if (!moldStack.isEmpty() && pot.canRemoveMold()) {
                if (!player.getInventory().add(moldStack.copy())) {
                    player.drop(moldStack.copy(), false);
                }
                pot.setMold(ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
            if (!moldStack.isEmpty() && !pot.canRemoveMold()) {
                player.displayClientMessage(Component.literal("§cНельзя извлечь форму: есть металл или предмет"), true);
            }
            return InteractionResult.PASS;
        }

        // Вставка формы
        if (moldStack.isEmpty()) {
            if (heldItem.is(ModItems.MOLD_INGOT.get())) {
                ItemStack toInsert = heldItem.copy();
                toInsert.setCount(1);
                pot.setMold(toInsert);
                heldItem.shrink(1);
                level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
        } else {
            // Забор пустой рукой только если нет металла и предмета
            if (heldItem.isEmpty() && pot.canRemoveMold()) {
                player.setItemInHand(hand, moldStack.copy());
                pot.setMold(ItemStack.EMPTY);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
            if (heldItem.isEmpty() && !pot.canRemoveMold()) {
                player.displayClientMessage(Component.literal("§cНельзя извлечь форму: есть металл или предмет"), true);
                return InteractionResult.PASS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CastingPotBlockEntity pot) {
                // Выбрасываем форму
                if (!pot.getMold().isEmpty()) {
                    popResource(level, pos, pot.getMold());
                }

                // Выбрасываем предмет с сохранением "горячести"
                if (!pot.getOutputItem().isEmpty()) {
                    ItemStack drop = pot.getOutputItem().copy();
                    // Сохраняем оставшееся время остывания если котёл сломали во время остывания
                    if (pot.getCoolingTimer() > 0) {
                        drop.getOrCreateTag().putInt("HotTime", pot.getCoolingTimer());
                        drop.getOrCreateTag().putInt("HotTimeMax", CastingPotBlockEntity.BASE_COOLING_TIME);
                    }
                    popResource(level, pos, drop);
                }
                // Металл теряется при разрушении (логика остаётся)
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}