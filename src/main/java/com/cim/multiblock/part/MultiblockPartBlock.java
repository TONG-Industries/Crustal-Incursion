package com.cim.multiblock.part;

import com.cim.block.basic.ModBlocks;
import com.cim.multiblock.industrial.HeaterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MultiblockPartBlock extends Block implements EntityBlock {

    public MultiblockPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MultiblockPartEntity part) {
            BlockPos controllerPos = part.getControllerPos();
            if (controllerPos != null) {
                BlockEntity controllerBe = level.getBlockEntity(controllerPos);
                if (controllerBe instanceof HeaterBlockEntity heater) {
                    return heater.getFullMultiblockShape(pos);
                }
            }
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of();
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MultiblockPartEntity part) {
                BlockPos controllerPos = part.getControllerPos();
                if (controllerPos != null) {
                    BlockEntity controllerBe = level.getBlockEntity(controllerPos);
                    if (controllerBe instanceof HeaterBlockEntity controller && !controller.isDestroying()) {
                        // Дропаем предмет контроллера здесь
                        Block.popResource(level, pos, new ItemStack(ModBlocks.HEATER.get().asItem()));
                        // Уничтожаем весь мультиблок
                        controller.destroyMultiblockFromPart(pos);
                        return;
                    }
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MultiblockPartEntity part) {
            BlockPos controllerPos = part.getControllerPos();
            if (controllerPos != null) {
                BlockEntity controllerBe = level.getBlockEntity(controllerPos);
                if (controllerBe instanceof IMultiblockController controller) {
                    return controller.onUse(player, hand, hit, pos);
                }
            }
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartEntity(pos, state);
    }
}