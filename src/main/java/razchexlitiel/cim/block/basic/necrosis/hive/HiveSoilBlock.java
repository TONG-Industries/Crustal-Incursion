package razchexlitiel.cim.block.basic.necrosis.hive;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import razchexlitiel.cim.api.hive.HiveNetworkManager;
import razchexlitiel.cim.api.hive.HiveNetworkMember;
import razchexlitiel.cim.block.entity.hive.DepthWormNestBlockEntity;
import razchexlitiel.cim.block.entity.hive.HiveSoilBlockEntity;

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


    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof HiveNetworkMember member) {
                UUID netId = member.getNetworkId(); // Получаем UUID сети из BlockEntity

                if (netId != null) {
                    HiveNetworkManager manager = HiveNetworkManager.get(level);
                    if (manager != null) {
                        // 1. Удаляем этот блок из списка узлов менеджера
                        manager.removeNode(netId, pos, level);

                        // 2. Проверяем, не нужно ли распустить сеть (если это было последнее ядро)
                        manager.validateNetwork(netId, level);
                    }
                }

                // Если это блок гнезда, вызываем выпуск червей (только для DepthWormNestBlock)
                if (be instanceof DepthWormNestBlockEntity nest) {
                    nest.releaseWormsAndNotify();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

}
