package com.cim.block.basic.necrosis.hive;

import com.cim.block.entity.ModBlockEntities;
import com.cim.block.entity.hive.DepthWormNestBlockEntity;
import com.cim.api.hive.HiveNetworkManager;
import com.cim.api.hive.HiveNetworkMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DepthWormNestBlock extends BaseEntityBlock {
    public DepthWormNestBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DepthWormNestBlockEntity nest) {
                nest.releaseWormsAndNotify();

                UUID netId = nest.getNetworkId();
                if (netId != null) {
                    HiveNetworkManager manager = HiveNetworkManager.get(level);
                    if (manager != null) {
                        manager.removeNode(netId, pos, level);
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepthWormNestBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.DEPTH_WORM_NEST.get(), DepthWormNestBlockEntity::tick);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (level.isClientSide) return;

        BlockEntity existingBE = level.getBlockEntity(pos);
        if (existingBE instanceof DepthWormNestBlockEntity nest) {
            UUID existingId = nest.getNetworkId();
            if (existingId != null) {
                HiveNetworkManager manager = HiveNetworkManager.get(level);
                if (manager != null) {
                    manager.addNode(existingId, pos, true);
                }
                return;
            }
        }

        UUID finalNetId = null;
        HiveNetworkManager manager = HiveNetworkManager.get(level);

        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor instanceof HiveNetworkMember member) {
                UUID neighborId = member.getNetworkId();
                if (neighborId == null) continue;

                if (finalNetId == null) {
                    finalNetId = neighborId;
                } else if (!finalNetId.equals(neighborId)) {
                    manager.mergeNetworks(finalNetId, neighborId, level);
                }
            }
        }

        if (finalNetId == null) {
            System.out.println("[Hive] WARNING: Nest placed without network neighbors at " + pos);
            finalNetId = UUID.randomUUID();
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HiveNetworkMember member) {
            member.setNetworkId(finalNetId);
            manager.addNode(finalNetId, pos, true);
        }
    }
}