package com.cim.multiblock.industrial;

import com.cim.multiblock.MultiblockPattern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class HeaterBlock extends Block implements EntityBlock {
    private static final MultiblockPattern PATTERN = createPattern();

    public HeaterBlock(Properties properties) {
        super(properties);
    }

    private static MultiblockPattern createPattern() {
        return MultiblockPattern.fromLayers(
                   """
                   ###
                   #0#
                   ###
                   """
        );
    }

    public static boolean canPlace(Level level, BlockPos pos) {
        // Вычисляем origin (нижний-левый-ближний угол) относительно позиции контроллера
        BlockPos origin = getOriginFromControllerPos(pos);

        for (int y = 0; y < PATTERN.getHeight(); y++) {
            for (int x = 0; x < PATTERN.getWidth(); x++) {
                for (int z = 0; z < PATTERN.getDepth(); z++) {
                    BlockPos checkPos = origin.offset(x, y, z);
                    // Пропускаем позицию контроллера — там уже стоит блок
                    if (checkPos.equals(pos)) continue;

                    BlockState state = level.getBlockState(checkPos);
                    if (!state.isAir() && !state.canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // Вычисляем origin (нижний-левый угол) из позиции контроллера
    public static BlockPos getOriginFromControllerPos(BlockPos controllerPos) {
        // Находим позицию 0 в паттерне
        for (int y = 0; y < PATTERN.getHeight(); y++) {
            for (int x = 0; x < PATTERN.getWidth(); x++) {
                for (int z = 0; z < PATTERN.getDepth(); z++) {
                    if (PATTERN.pattern[y][x][z] == MultiblockPattern.PatternEntry.CONTROLLER) {
                        // origin = controllerPos - смещение_контроллера
                        return controllerPos.offset(-x, -y, -z);
                    }
                }
            }
        }
        // Fallback — считаем что контроллер в центре
        int centerX = PATTERN.getWidth() / 2;
        int centerZ = PATTERN.getDepth() / 2;
        return controllerPos.offset(-centerX, 0, -centerZ);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            if (!canPlace(level, pos)) {
                level.removeBlock(pos, false);
                if (placer instanceof Player player) {
                    player.addItem(new ItemStack(this.asItem()));
                }
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HeaterBlockEntity heaterBe) {
                // Передаем origin, а не pos
                BlockPos origin = getOriginFromControllerPos(pos);
                heaterBe.createMultiblock(level, origin, pos);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HeaterBlockEntity heaterBe) {
                // Проверяем, что это не пересоздание при destroyMultiblockFromPart
                if (!heaterBe.isDestroying()) {
                    // Дропаем предмет здесь, так как это контроллер
                    Block.popResource(level, pos, new ItemStack(this.asItem()));
                }
                heaterBe.destroyMultiblock();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HeaterBlockEntity heaterBe) {
            return heaterBe.onUse(player, hand, hit, pos);
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeaterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof HeaterBlockEntity heaterBe) {
                heaterBe.tick(lvl, pos, st, heaterBe);
            }
        };
    }

    public static MultiblockPattern getPattern() {
        return PATTERN;
    }
}