package com.cim.multiblock.system;

import com.cim.multiblock.system.IMultiblockController;
import com.cim.multiblock.system.IMultiblockPart;
import com.cim.multiblock.system.MultiblockPartEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MultiblockPartBlock extends BaseEntityBlock {

    public MultiblockPartBlock(Properties properties) {
        super(properties.strength(1.0f, 6.0f).noOcclusion());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockState ctrlState = level.getBlockState(part.getControllerPos());
            if (ctrlState.getBlock() instanceof IMultiblockController controller) {
                net.minecraft.core.Direction facing = ctrlState.hasProperty(HorizontalDirectionalBlock.FACING)
                        ? ctrlState.getValue(HorizontalDirectionalBlock.FACING) : net.minecraft.core.Direction.NORTH;
                VoxelShape masterShape = controller.getStructureHelper().generateShapeFromParts(facing);
                BlockPos offset = part.getControllerPos().subtract(pos);
                return masterShape.move(offset.getX(), offset.getY(), offset.getZ());
            }
        }
        return Shapes.block();
    }

    // ВАЖНО: Этот метод перехватывает и ОТМЕНЯЕТ ванильные частицы на клиенте
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientBlockExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientBlockExtensions() {

            // Отменяем фиолетовые частицы при РАЗРУШЕНИИ
            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, net.minecraft.client.particle.ParticleEngine manager) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                    BlockState ctrlState = level.getBlockState(part.getControllerPos());
                    if (ctrlState.getBlock() instanceof IMultiblockController) {
                        for (int i = 0; i < 8; ++i) {
                            double x = pos.getX() + level.random.nextDouble();
                            double y = pos.getY() + level.random.nextDouble();
                            double z = pos.getZ() + level.random.nextDouble();
                            double speedX = (level.random.nextDouble() - 0.5D) * 0.15D;
                            double speedY = level.random.nextDouble() * 0.15D;
                            double speedZ = (level.random.nextDouble() - 0.5D) * 0.15D;
                            level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ctrlState), x, y, z, speedX, speedY, speedZ);
                        }
                    }
                }
                // TRUE означает: "Майнкрафт, я сам нарисовал частицы, твои фиолетовые квадраты не нужны!"
                return true;
            }

            // Отменяем фиолетовые частицы при УДАРЕ ПО БЛОКУ
            @Override
            public boolean addHitEffects(BlockState state, Level level, net.minecraft.world.phys.HitResult target, net.minecraft.client.particle.ParticleEngine manager) {
                if (target instanceof BlockHitResult blockTarget) {
                    BlockPos pos = blockTarget.getBlockPos();
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                        BlockState ctrlState = level.getBlockState(part.getControllerPos());
                        if (ctrlState.getBlock() instanceof IMultiblockController) {
                            net.minecraft.core.Direction side = blockTarget.getDirection();
                            double x = pos.getX() + 0.5D + side.getStepX() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                            double y = pos.getY() + 0.5D + side.getStepY() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                            double z = pos.getZ() + 0.5D + side.getStepZ() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                            level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, ctrlState), x, y, z, 0.0D, 0.0D, 0.0D);
                        }
                    }
                }
                return true;
            }
        });
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
            BlockPos ctrlPos = part.getControllerPos();
            BlockState ctrlState = level.getBlockState(ctrlPos);
            if (ctrlState.getBlock() instanceof IMultiblockController) {
                return ctrlState.use(level, player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), ctrlPos, hit.isInside()));
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                BlockPos ctrlPos = part.getControllerPos();
                BlockState ctrlState = level.getBlockState(ctrlPos);
                if (ctrlState.getBlock() instanceof IMultiblockController controller) {
                    Block.popResource(level, ctrlPos, new ItemStack(ctrlState.getBlock()));
                    net.minecraft.core.Direction facing = ctrlState.hasProperty(HorizontalDirectionalBlock.FACING)
                            ? ctrlState.getValue(HorizontalDirectionalBlock.FACING) : net.minecraft.core.Direction.NORTH;
                    controller.getStructureHelper().destroyStructure(level, ctrlPos, facing);
                    level.removeBlock(ctrlPos, false);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartEntity(pos, state);
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }
}