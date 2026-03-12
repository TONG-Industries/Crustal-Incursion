package com.cim.block.basic.necrosis.hive;

import com.cim.block.entity.hive.DepthWormNestBlockEntity;
import com.cim.block.entity.hive.HiveSoilBlockEntity;
import com.cim.api.hive.HiveNetworkManager;
import com.cim.api.hive.HiveNetworkMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class HiveSoilBlock extends Block implements EntityBlock {
    public HiveSoilBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HiveSoilBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (level.isClientSide) return;

        BlockEntity existingBE = level.getBlockEntity(pos);
        if (existingBE instanceof HiveSoilBlockEntity soil) {
            UUID existingId = soil.getNetworkId();
            if (existingId != null) {
                HiveNetworkManager manager = HiveNetworkManager.get(level);
                if (manager != null) {
                    manager.addNode(existingId, pos, false);
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

        if (finalNetId == null) finalNetId = UUID.randomUUID();

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HiveNetworkMember member) {
            member.setNetworkId(finalNetId);
            manager.addNode(finalNetId, pos, false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof HiveNetworkMember member) {
                UUID netId = member.getNetworkId();
                if (netId != null) {
                    HiveNetworkManager manager = HiveNetworkManager.get(level);
                    if (manager != null) {
                        manager.removeNode(netId, pos, level);
                    }
                }

                if (be instanceof DepthWormNestBlockEntity nest) {
                    nest.releaseWormsAndNotify();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}