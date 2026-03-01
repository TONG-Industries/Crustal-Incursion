package razchexlitiel.cim.block.basic.necrosis.hive;


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
import razchexlitiel.cim.api.hive.HiveNetworkManager;
import razchexlitiel.cim.api.hive.HiveNetworkMember;
import razchexlitiel.cim.block.entity.ModBlockEntities;
import razchexlitiel.cim.block.entity.hive.DepthWormNestBlockEntity;

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
                // 1. СНАЧАЛА выпускаем червей, пока BE в мире
                nest.releaseWormsAndNotify();

                // 2. ЗАТЕМ работаем с менеджером
                UUID netId = nest.getNetworkId();
                if (netId != null) {
                    HiveNetworkManager manager = HiveNetworkManager.get(level);
                    if (manager != null) {
                        manager.removeNode(netId, pos, level);
                        manager.validateNetwork(netId, level);
                    }
                }
            }
            // 3. ПОСЛЕДНИМ вызываем super, который удалит BlockEntity
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

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HiveNetworkMember currentBlock) {
            for (Direction dir : Direction.values()) {
                BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
                if (neighbor instanceof HiveNetworkMember other && other.getNetworkId() != null) {

                    if (currentBlock.getNetworkId() == null) {
                        // Если мы "пустые", просто примыкаем
                        currentBlock.setNetworkId(other.getNetworkId());
                        HiveNetworkManager.get(level).addNode(other.getNetworkId(), pos);
                    } else {
                        // Если у нас УЖЕ есть сеть, и мы коснулись ДРУГОЙ — объединяем!
                        // Мастер-сетью станет та, чью почву мы коснулись сейчас
                        HiveNetworkManager.get(level).mergeNetworks(other.getNetworkId(), currentBlock.getNetworkId(), level);
                    }
                }
            }
            // Если это Ядро и оно все еще пустое (не коснулось никого)
            if (currentBlock instanceof DepthWormNestBlockEntity nest && nest.getNetworkId() == null) {
                nest.setNetworkId(UUID.randomUUID());
                HiveNetworkManager.get(level).addNode(nest.getNetworkId(), pos);
            }
            be.setChanged();
        }
    }


}
