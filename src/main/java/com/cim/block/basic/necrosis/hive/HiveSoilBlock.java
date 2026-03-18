// HiveSoilBlock.java — усиленная защита от создания сиротских сетей
package com.cim.block.basic.necrosis.hive;

import com.cim.block.basic.ModBlocks;
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

            // ⭐ Если есть ID из NBT — проверяем валидность
            if (existingId != null) {
                HiveNetworkManager manager = HiveNetworkManager.get(level);
                if (manager != null && manager.getNetwork(existingId) != null) {
                    // Сеть существует — подключаемся
                    manager.addNode(existingId, pos, false);
                    System.out.println("[Hive] Soil reconnected to network " + existingId + " at " + pos);
                    return;
                }
                // Сеть мертва — сбрасываем ID
                soil.setNetworkId(null);
            }
        }

        // ⭐ Ищем соседнюю сеть — ТОЛЬКО если есть соседи-колонисты
        UUID finalNetId = null;
        HiveNetworkManager manager = HiveNetworkManager.get(level);
        boolean hasColonyNeighbor = false;

        // Сначала ищем гнезда (приоритет)
        for (Direction dir : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
            if (neighbor instanceof HiveNetworkMember member) {
                UUID neighborId = member.getNetworkId();
                if (neighborId == null) continue;

                hasColonyNeighbor = true;

                // Если это гнездо — берём его сеть сразу
                if (level.getBlockState(pos.relative(dir)).is(ModBlocks.DEPTH_WORM_NEST.get())) {
                    finalNetId = neighborId;
                    break;
                }
                // Иначе запоминаем но продолжаем искать гнездо
                if (finalNetId == null) {
                    finalNetId = neighborId;
                } else if (!finalNetId.equals(neighborId)) {
                    // Конфликт сетей — мержим
                    manager.mergeNetworks(finalNetId, neighborId, level);
                }
            }
        }

        // ⭐ КРИТИЧНО: Если не нашли соседей-колонистов — НЕ создаём сеть!
        // Почва остаётся "мертвой" — не тикает, не расходует ресурсы
        if (!hasColonyNeighbor) {
            System.out.println("[Hive] Orphan soil at " + pos + " — no colony neighbors, remains dormant");
            // НЕ устанавливаем ID — почва будет без сети
            return;
        }

        // Если нашли соседей но не определили сеть (только другая почва без гнёзд)
        if (finalNetId == null) {
            System.out.println("[Hive] Soil at " + pos + " — neighbors have no network, remains dormant");
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HiveNetworkMember member) {
            member.setNetworkId(finalNetId);
            manager.addNode(finalNetId, pos, false);
            System.out.println("[Hive] Soil connected to network " + finalNetId + " at " + pos);
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
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}