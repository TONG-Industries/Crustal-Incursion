package com.cim.block.basic.industrial.casting;

import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.industrial.casting.CastingPotBlockEntity;
import com.cim.event.HotItemHandler;
import com.cim.item.ModItems;
import com.cim.event.SlagItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

        // === КОЧЕРГА - обрабатывается в PokerItem.useOn() ===
        if (heldItem.is(ModItems.POKER.get())) {
            return InteractionResult.PASS; // Позволяем PokerItem обработать
        }

        // === ВОЗВРАТ ГОРЯЧЕГО ПРЕДМЕТА В КОТЁЛ ===
        if (!heldItem.isEmpty() && HotItemHandler.isHot(heldItem)) {
            if (pot.tryInsertHotItem(heldItem)) {
                heldItem.shrink(1);
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.0f);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.literal("§cНельзя поместить: котёл занят или нет формы"), true);
            return InteractionResult.PASS;
        }

        // === ИЗВЛЕЧЕНИЕ ШЛАКА ===
        if (pot.hasSlag()) {
            // Проверяем, остыл ли шлак
            ItemStack slagStack = pot.getSlagStack();
            boolean isHot = slagStack.hasTag() &&
                    slagStack.getTag().contains("HotTime") &&
                    slagStack.getTag().getFloat("HotTime") > 0.5f;

            // Если остыл - можно рукой, если горячий - только кочергой
            if (!isHot && heldItem.isEmpty()) {
                return extractSlag(level, pos, player, hand, pot);
            }
            // Если горячий и не кочерга - сообщение
            if (isHot && !heldItem.is(ModItems.POKER.get())) {
                player.displayClientMessage(Component.literal("§cШлак горячий! Используйте кочергу."), true);
                return InteractionResult.PASS;
            }
        }

        // === ДОСТАВАНИЕ ГОТОВОГО ПРЕДМЕТА (рукой - только остывшего) ===
        if (!pot.getOutputItem().isEmpty()) {
            return extractOutputItemByHand(level, pos, player, hand, pot);
        }

        // === РАБОТА С ФОРМОЙ ===
        return handleMoldInteraction(level, pos, player, hand, pot, heldItem);

    }

    /**
     * Извлечение предмета рукой (только если остыл)
     */
    private InteractionResult extractOutputItemByHand(Level level, BlockPos pos, Player player, InteractionHand hand,
                                                      CastingPotBlockEntity pot) {
        ItemStack output = pot.getOutputItem();

        // Проверяем горячесть
        if (HotItemHandler.isHot(output)) {
            float heatRatio = HotItemHandler.getHeatRatio(output);
            int percent = (int)(heatRatio * 100);
            int temp = HotItemHandler.getTemperature(output);

            player.displayClientMessage(
                    Component.literal(String.format("§cСлишком горячо! %d°C (%d%%) Используйте кочергу.", temp, percent)),
                    true
            );
            return InteractionResult.PASS;
        }

        // Остывший - можно забирать
        ItemStack drop = pot.takeOutput();
        ItemStack heldItem = player.getItemInHand(hand);

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

    /**
     * Сброс всего содержимого в виде шлака (Shift + ПКМ с кочергой)
     */
    private InteractionResult handleDumpContents(Level level, BlockPos pos, Player player, CastingPotBlockEntity pot) {
        boolean droppedSomething = false;

        // Шлак
        if (pot.hasSlag()) {
            ItemStack slag = pot.extractSlag();
            if (!slag.isEmpty()) {
                ensureHot(slag);
                popResource(level, pos, slag);
                droppedSomething = true;
            }
        }

        // Жидкий металл → шлак
        if (pot.getStoredUnits() > 0 && pot.getCurrentMetal() != null) {
            ItemStack slag = SlagItem.createSlag(pot.getCurrentMetal(), pot.getStoredUnits());
            ensureHot(slag);
            popResource(level, pos, slag);
            pot.clearMetal();
            droppedSomething = true;
        }

        // Горячий предмет
        if (!pot.getOutputItem().isEmpty()) {
            ItemStack drop = pot.takeOutput();
            popResource(level, pos, drop);
            droppedSomething = true;
        }

        // Форма
        if (!pot.getMold().isEmpty()) {
            popResource(level, pos, pot.getMold());
            pot.setMold(ItemStack.EMPTY);
            droppedSomething = true;
        }

        if (droppedSomething) {
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 0.8f);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * Извлечение шлака
     */
    private InteractionResult extractSlag(Level level, BlockPos pos, Player player, InteractionHand hand, CastingPotBlockEntity pot) {
        ItemStack slag = pot.extractSlag();
        if (slag.isEmpty()) return InteractionResult.PASS;

        ItemStack heldItem = player.getItemInHand(hand);

        if (heldItem.isEmpty()) {
            player.setItemInHand(hand, slag);
        } else {
            if (!player.getInventory().add(slag)) {
                player.drop(slag, false);
            }
        }

        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
        return InteractionResult.CONSUME;
    }

    /**
     * Извлечение готового предмета
     */
    private InteractionResult extractOutputItem(Level level, BlockPos pos, Player player, InteractionHand hand,
                                                CastingPotBlockEntity pot, boolean isPoker) {

        // Проверяем горячесть через HotItemHandler
        ItemStack output = pot.getOutputItem();
        boolean isHot = HotItemHandler.isHot(output);
        float heatRatio = isHot ? HotItemHandler.getHeatRatio(output) : 0f;

        // Кочергой - можно достать даже горячий
        if (isPoker) {
            ItemStack drop = pot.takeOutput();
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        // Рукой - только если остыл
        if (isHot && heatRatio > 0.1f) {
            int percent = (int)(heatRatio * 100);
            player.displayClientMessage(
                    Component.literal(String.format("§cСлишком горячо! (%d%%) Используйте кочергу.", percent)),
                    true
            );
            return InteractionResult.PASS;
        }

        // Обычная выдача
        ItemStack drop = pot.takeOutput();
        ItemStack heldItem = player.getItemInHand(hand);

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

    /**
     * Работа с формой (вставка/извлечение)
     */
    private InteractionResult handleMoldInteraction(Level level, BlockPos pos, Player player, InteractionHand hand,
                                                    CastingPotBlockEntity pot, ItemStack heldItem) {
        ItemStack moldStack = pot.getMold();

        // Отвёртка - извлечение
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
        // Вставка формы
        if (moldStack.isEmpty()) {
            if (heldItem.is(ModItems.MOLD_INGOT.get()) ||
                    heldItem.is(ModItems.MOLD_NUGGET.get()) ||
                    heldItem.is(ModItems.MOLD_BLOCK.get())) {

                ItemStack toInsert = heldItem.copy();
                toInsert.setCount(1);
                pot.setMold(toInsert);
                heldItem.shrink(1);
                level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
        } else {
            // Забор пустой рукой
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

    /**
     * Убеждается что шлак горячий
     */
    private void ensureHot(ItemStack slag) {
        if (!slag.hasTag() || !slag.getTag().contains("HotTime")) {
            // Если шлак не горячий - нагреваем его
            int meltingPoint = 1000;
            if (slag.getTag() != null && slag.getTag().contains(SlagItem.TAG_MELTING_POINT)) {
                meltingPoint = slag.getTag().getInt(SlagItem.TAG_MELTING_POINT);
            }
            HotItemHandler.setHot(slag, meltingPoint, false); // В руках - обычное охлаждение
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CastingPotBlockEntity pot) {
                // Форма
                if (!pot.getMold().isEmpty()) {
                    popResource(level, pos, pot.getMold());
                }

                // Предмет с сохранением горячести
                if (!pot.getOutputItem().isEmpty()) {
                    ItemStack drop = pot.getOutputItem().copy();

                    // Копируем данные о горячести если есть
                    if (HotItemHandler.isHot(pot.getOutputItem())) {
                        float hotTime = HotItemHandler.getHotTime(pot.getOutputItem());
                        int maxTime = HotItemHandler.getHotTimeMax(pot.getOutputItem());
                        int meltingPoint = HotItemHandler.getMeltingPoint(pot.getOutputItem());

                        drop.getOrCreateTag().putFloat("HotTime", hotTime);
                        drop.getOrCreateTag().putInt("HotTimeMax", maxTime);
                        drop.getOrCreateTag().putInt("MeltingPoint", meltingPoint);
                    }

                    popResource(level, pos, drop);
                }

                // Жидкий металл → шлак при разрушении
                if (pot.getStoredUnits() > 0 && pot.getCurrentMetal() != null) {
                    ItemStack slag = SlagItem.createSlag(pot.getCurrentMetal(), pot.getStoredUnits());
                    ensureHot(slag);
                    popResource(level, pos, slag);
                }

                // Шлак
                if (pot.hasSlag()) {
                    ItemStack slag = pot.extractSlag();
                    if (!slag.isEmpty()) {
                        ensureHot(slag);
                        popResource(level, pos, slag);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}