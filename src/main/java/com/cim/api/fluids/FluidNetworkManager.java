package com.cim.api.fluids;

import com.cim.block.entity.fluids.FluidPipeBlockEntity;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class FluidNetworkManager extends SavedData {
    private static final String DATA_NAME = "cim_fluid_networks";

    private final ServerLevel level;
    private final Long2ObjectMap<FluidNode> allNodes = new Long2ObjectOpenHashMap<>();
    private final Set<FluidNetwork> networks = Sets.newHashSet();

    public FluidNetworkManager(ServerLevel level, CompoundTag nbt) {
        this(level);
        if (nbt.contains("nodes")) {
            long[] nodePositions = nbt.getLongArray("nodes");
            for (long posLong : nodePositions) {
                allNodes.put(posLong, new FluidNode(BlockPos.of(posLong)));
            }
        }
    }

    public FluidNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static FluidNetworkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                nbt -> new FluidNetworkManager(level, nbt),
                () -> new FluidNetworkManager(level),
                DATA_NAME
        );
    }

    public void tick() {
        Set<FluidNetwork> emptyNetworks = new HashSet<>();
        for (FluidNetwork network : networks) {
            if (network.isEmpty()) {
                emptyNetworks.add(network);
            } else {
                network.tick(level);
            }
        }
        networks.removeAll(emptyNetworks);
    }

    // ==================== ЛОГИКА ДОБАВЛЕНИЯ УЗЛА ====================
    public void addNode(BlockPos pos) {
        addNode(pos, null);
    }

    private void addNode(BlockPos pos, @Nullable FluidNetwork networkToAvoid) {
        if (allNodes.containsKey(pos.asLong())) return;

        FluidNode newNode = new FluidNode(pos);
        allNodes.put(pos.asLong(), newNode);
        setDirty();

        FluidNetwork assignedNetwork = null;

        // Ищем соседей (6 сторон)
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            FluidNode neighborNode = allNodes.get(neighborPos.asLong());

            if (neighborNode != null && neighborNode.getNetwork() != networkToAvoid) {
                // ПРОВЕРКА ФИЛЬТРОВ! Соединяем сети, только если жидкости совпадают (или одна пустая)
                if (canConnectLogically(pos, neighborPos)) {
                    FluidNetwork neighborNetwork = neighborNode.getNetwork();
                    if (neighborNetwork != null) {
                        if (assignedNetwork == null) {
                            assignedNetwork = neighborNetwork;
                            assignedNetwork.addNode(newNode);
                        } else if (assignedNetwork != neighborNetwork) {
                            // --- ЗАЩИТА ОТ РАЗРЫВОВ ПРИ СЛИЯНИИ ---
                            if (neighborNetwork.getNodeCount() > assignedNetwork.getNodeCount()) {
                                neighborNetwork.merge(assignedNetwork);
                                assignedNetwork = neighborNetwork; // <--- ОБЯЗАТЕЛЬНО!
                            } else {
                                assignedNetwork.merge(neighborNetwork);
                            }
                        }
                    }
                }
            }
        }

        if (assignedNetwork == null) {
            assignedNetwork = new FluidNetwork(this);
            assignedNetwork.addNode(newNode);
            networks.add(assignedNetwork);
        }
    }

    // ==================== ПРОВЕРКА ФИЛЬТРОВ И МАШИН ====================
    private boolean canConnectLogically(BlockPos pos1, BlockPos pos2) {
        BlockEntity be1 = level.getBlockEntity(pos1);
        BlockEntity be2 = level.getBlockEntity(pos2);

        boolean isPipe1 = be1 instanceof com.cim.block.entity.fluids.FluidPipeBlockEntity;
        boolean isPipe2 = be2 instanceof com.cim.block.entity.fluids.FluidPipeBlockEntity;

        // 1. Если это две трубы - они должны иметь одинаковый фильтр
        if (isPipe1 && isPipe2) {
            net.minecraft.world.level.material.Fluid f1 = ((com.cim.block.entity.fluids.FluidPipeBlockEntity) be1).getFilterFluid();
            net.minecraft.world.level.material.Fluid f2 = ((com.cim.block.entity.fluids.FluidPipeBlockEntity) be2).getFilterFluid();
            return f1 == f2;
        }

        // 2. Если Труба соединяется с Бочкой (или любой другой машиной с баком)
        if (isPipe1 && be2 != null) {
            return be2.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).isPresent();
        }
        if (isPipe2 && be1 != null) {
            return be1.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).isPresent();
        }

        // 3. Если две бочки/машины стоят впритык друг к другу
        if (!isPipe1 && !isPipe2 && be1 != null && be2 != null) {
            return be1.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).isPresent() &&
                    be2.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER).isPresent();
        }

        return false;
    }

    // ==================== УДАЛЕНИЕ УЗЛА ====================
    public void removeNode(BlockPos pos) {
        FluidNode node = allNodes.remove(pos.asLong());
        if (node == null) return;

        FluidNetwork network = node.getNetwork();
        if (network != null) {
            network.removeNode(node);
        }
        setDirty();
    }

    void reAddNode(BlockPos pos, @Nullable FluidNetwork networkToAvoid) {
        allNodes.remove(pos.asLong());
        addNode(pos, networkToAvoid);
    }

    public void removeNetwork(FluidNetwork network) {
        networks.remove(network);
    }

    // ==================== УТИЛИТЫ И ОТЛАДКА ====================
    public boolean hasNode(BlockPos pos) {
        return allNodes.containsKey(pos.asLong());
    }

    public void debugLog() {
        if (networks.isEmpty()) return;

        System.out.println("=== FLUID NETWORK STATUS ===");
        System.out.println("Total registered pipes (nodes): " + allNodes.size());
        System.out.println("Active networks: " + networks.size());

        int i = 1;
        for (FluidNetwork net : networks) {
            System.out.println("  Network #" + i + " | Nodes: " + net.getNodeCount());
            i++;
        }
        System.out.println("==============================");
    }

    public FluidNetwork getNetwork(BlockPos pos) {
        FluidNode node = allNodes.get(pos.asLong());
        return node != null ? node.getNetwork() : null;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag nbt) {
        nbt.putLongArray("nodes", allNodes.keySet().toLongArray());
        return nbt;
    }
}
