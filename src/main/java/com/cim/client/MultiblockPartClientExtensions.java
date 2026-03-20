package com.cim.client;

import com.cim.multiblock.system.IMultiblockController;
import com.cim.multiblock.system.IMultiblockPart;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

// Этот класс будет загружаться только на клиенте
public class MultiblockPartClientExtensions implements IClientBlockExtensions {

    public static final MultiblockPartClientExtensions INSTANCE = new MultiblockPartClientExtensions();

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
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
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ctrlState), x, y, z, speedX, speedY, speedZ);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
        if (target instanceof BlockHitResult blockTarget) {
            BlockPos pos = blockTarget.getBlockPos();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMultiblockPart part && part.getControllerPos() != null) {
                BlockState ctrlState = level.getBlockState(part.getControllerPos());
                if (ctrlState.getBlock() instanceof IMultiblockController) {
                    Direction side = blockTarget.getDirection();
                    double x = pos.getX() + 0.5D + side.getStepX() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                    double y = pos.getY() + 0.5D + side.getStepY() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                    double z = pos.getZ() + 0.5D + side.getStepZ() * 0.5D + (level.random.nextDouble() - 0.5D) * 0.5D;
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ctrlState), x, y, z, 0.0D, 0.0D, 0.0D);
                    return true;
                }
            }
        }
        return false;
    }
}
