package com.cim.api.energy;

import com.cim.block.entity.energy.ConnectorBlockEntity;
import com.cim.capability.ModCapabilities;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;

public class EnergyNetworkManager extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "cim_energy_networks";

    private final ServerLevel level;
    private final Long2ObjectMap<EnergyNode> allNodes = new Long2ObjectOpenHashMap<>();
    private final Set<EnergyNetwork> networks = Sets.newHashSet();

    public EnergyNetworkManager(ServerLevel level, CompoundTag nbt) {
        this(level);
        if (nbt.contains("nodes")) {
            long[] nodePositions = nbt.getLongArray("nodes");

            for (long posLong : nodePositions) {
                BlockPos pos = BlockPos.of(posLong);
                allNodes.put(pos.asLong(), new EnergyNode(pos));
            }
        }
    }

    public EnergyNetworkManager(ServerLevel level) {
        this.level = level;
    }

    public static EnergyNetworkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                (nbt) -> new EnergyNetworkManager(level, nbt),
                () -> new EnergyNetworkManager(level),
                DATA_NAME
        );
    }

    /**
     * Полностью перестраивает все сети.
     * Вызывается при загрузке мира, чтобы исправить любые сломанные состояния.
     */
    public void rebuildAllNetworks() {
        LOGGER.info("[NETWORK] Starting full network rebuild for dimension {}...", level.dimension().location());

        // 1. Очищаем старые сети и сбрасываем узлы
        networks.clear();
        for (EnergyNode node : allNodes.values()) {
            node.setNetwork(null);
        }

        // 2. Чистим allNodes от невалидных узлов
        int totalNodes = allNodes.size();
        allNodes.values().removeIf(node -> !node.isValid(level));
        int validNodes = allNodes.size();
        LOGGER.info("[NETWORK] Pruned node list: {} total -> {} valid.", totalNodes, validNodes);

        // 3. Используем Set для отслеживания уже обработанных узлов
        Set<EnergyNode> processedNodes = new HashSet<>();

        // 4. Проходим по очищенному allNodes
        for (EnergyNode startNode : allNodes.values()) {

            if (processedNodes.contains(startNode)) {
                continue;
            }

            EnergyNetwork newNetwork = new EnergyNetwork(this);
            networks.add(newNetwork);

            Queue<EnergyNode> queue = new LinkedList<>();
            queue.add(startNode);
            processedNodes.add(startNode);

            while (!queue.isEmpty()) {
                EnergyNode currentNode = queue.poll();
                newNetwork.addNode(currentNode);

                for (Direction dir : Direction.values()) {
                    EnergyNode neighbor = allNodes.get(currentNode.getPos().relative(dir).asLong());

                    if (neighbor != null && !processedNodes.contains(neighbor)) {
                        processedNodes.add(neighbor);
                        queue.add(neighbor);
                    }
                }

                // P2P-сосед через провод (ИСПРАВЛЕНО ДЛЯ МНОЖЕСТВА ПОДКЛЮЧЕНИЙ)
                if (level.isLoaded(currentNode.getPos())) {
                    BlockEntity be = level.getBlockEntity(currentNode.getPos());
                    if (be instanceof com.cim.block.entity.energy.ConnectorBlockEntity connector) {
                        for (BlockPos linkedPos : connector.getConnections()) {
                            if (linkedPos != null) {
                                EnergyNode linkedNeighbor = allNodes.get(linkedPos.asLong());
                                if (linkedNeighbor != null && !processedNodes.contains(linkedNeighbor)) {
                                    processedNodes.add(linkedNeighbor);
                                    queue.add(linkedNeighbor);
                                }
                            }
                        }
                    }
                }
            }
        }

        LOGGER.info("[NETWORK] Rebuild completed. Found {} networks.", networks.size());
        setDirty();
    }

    public void tick() {
        new HashSet<>(networks).forEach(network -> network.tick(level));
    }

    // ==================== ДОБАВЛЕНИЕ УЗЛОВ (БЕЗ РЕКУРСИИ) ====================

    public void addNode(BlockPos pos) {
        addNode(pos, null);
    }

    /**
     * Обёртка: создаёт очередь и запускает итеративную обработку.
     * Это заменяет рекурсивный вызов addNode → addNode → addNode...
     */
    private void addNode(BlockPos pos, @Nullable EnergyNetwork networkToAvoid) {
        Queue<AddNodeRequest> pendingNodes = new LinkedList<>();
        pendingNodes.add(new AddNodeRequest(pos, networkToAvoid));

        while (!pendingNodes.isEmpty()) {
            AddNodeRequest request = pendingNodes.poll();
            addNodeInternal(request.pos, request.networkToAvoid, pendingNodes);
        }
    }

    /**
     * Внутренний метод добавления одного узла.
     * Найденных "потерянных" соседей складывает в pendingNodes вместо рекурсии.
     */
    /**
     * Внутренний метод добавления одного узла.
     * Найденных "потерянных" соседей складывает в pendingNodes вместо рекурсии.
     */
    private void addNodeInternal(BlockPos pos, @Nullable EnergyNetwork networkToAvoid,
                                 Queue<AddNodeRequest> pendingNodes) {
        long posLong = pos.asLong();

        // 1. Защита от дубликатов и проверка существования
        if (allNodes.containsKey(posLong)) {
            EnergyNode existingNode = allNodes.get(posLong);
            if (existingNode != null && existingNode.getNetwork() != null) {
                return;
            }
        }

        // 2. Создаем и проверяем валидность
        EnergyNode newNode = new EnergyNode(pos);
        if (!newNode.isValid(level)) {
            allNodes.remove(posLong);
            return;
        }

        // 3. Сохраняем узел
        allNodes.put(posLong, newNode);

        // 4. Ищем соседей (с функцией АВТО-ПОЧИНКИ)
        Set<EnergyNetwork> adjacentNetworks = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            long neighborLong = neighborPos.asLong();

            EnergyNode neighbor = allNodes.get(neighborLong);

            // [АВТО-ПОЧИНКА] — итеративная, без рекурсии
            if (neighbor == null && level.isLoaded(neighborPos)) {
                BlockEntity be = level.getBlockEntity(neighborPos);
                if (be != null) {
                    boolean isEnergyBlock = be.getCapability(ModCapabilities.ENERGY_PROVIDER).isPresent() ||
                            be.getCapability(ModCapabilities.ENERGY_RECEIVER).isPresent() ||
                            be.getCapability(ModCapabilities.ENERGY_CONNECTOR).isPresent();

                    if (isEnergyBlock) {
                        pendingNodes.add(new AddNodeRequest(neighborPos, null));
                        continue;
                    }
                }
            }

            // Стандартная логика соединения
            if (neighbor != null && neighbor.getNetwork() != null) {
                if (neighbor.getNetwork() != networkToAvoid) {
                    adjacentNetworks.add(neighbor.getNetwork());
                }
            }
        }

        // ============================================================
        // 4.5 [НОВОЕ] P2P-ПРОВОД: виртуальный сосед через коннектор (ИСПРАВЛЕНО ДЛЯ МНОЖЕСТВА ПОДКЛЮЧЕНИЙ)
        // ============================================================
        if (level.isLoaded(pos)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.cim.block.entity.energy.ConnectorBlockEntity connector) {
                for (BlockPos linkedPos : connector.getConnections()) {
                    if (linkedPos != null && level.isLoaded(linkedPos)) {
                        long linkedLong = linkedPos.asLong();
                        EnergyNode linkedNeighbor = allNodes.get(linkedLong);

                        if (linkedNeighbor == null) {
                            BlockEntity linkedBe = level.getBlockEntity(linkedPos);
                            if (linkedBe instanceof com.cim.block.entity.energy.ConnectorBlockEntity) {
                                pendingNodes.add(new AddNodeRequest(linkedPos, null));
                            }
                        } else if (linkedNeighbor.getNetwork() != null) {
                            if (linkedNeighbor.getNetwork() != networkToAvoid) {
                                adjacentNetworks.add(linkedNeighbor.getNetwork());
                            }
                        }
                    }
                }
            }
        }
        // ============================================================

        // 5. Слияние или создание сети
        if (adjacentNetworks.isEmpty()) {
            EnergyNetwork newNetwork = new EnergyNetwork(this);
            networks.add(newNetwork);
            newNetwork.addNode(newNode);
        } else {
            Iterator<EnergyNetwork> it = adjacentNetworks.iterator();
            EnergyNetwork main = it.next();
            main.addNode(newNode);

            while (it.hasNext()) {
                EnergyNetwork next = it.next();

                // ИСПРАВЛЕНИЕ: Безопасное слияние, предотвращающее появление сетей-призраков!
                // Если соседняя сеть больше, она поглощает нашу, и МЫ ДОЛЖНЫ обновить ссылку main,
                // чтобы продолжить впитывать остальные блоки в выжившую сеть.
                if (next.getNodeCount() > main.getNodeCount()) {
                    next.merge(main);
                    main = next; // <-- Эта строчка спасает всю сеть
                } else {
                    main.merge(next);
                }
            }
        }
        setDirty();
    }

    /**
     * Контейнер для параметров отложенного добавления узла.
     */
    private record AddNodeRequest(BlockPos pos, @Nullable EnergyNetwork networkToAvoid) {}

    // ==================== УДАЛЕНИЕ / ПЕРЕДОБАВЛЕНИЕ ====================

    public void removeNode(BlockPos pos) {
        long posLong = pos.asLong();
        EnergyNode node = allNodes.remove(posLong);

        if (node == null) {
            return;
        }

        EnergyNetwork network = node.getNetwork();
        if (network != null) {
            network.removeNode(node);
        }

        setDirty();
    }

    void reAddNode(BlockPos pos, @Nullable EnergyNetwork networkToAvoid) {
        EnergyNode node = allNodes.get(pos.asLong());
        if (node != null) {
            node.setNetwork(null);
        }

        allNodes.remove(pos.asLong());
        addNode(pos, networkToAvoid);
    }

    // ==================== СОХРАНЕНИЕ ====================

    @Override
    public CompoundTag save(CompoundTag nbt) {
        long[] nodePositions = allNodes.keySet().toLongArray();
        nbt.putLongArray("nodes", nodePositions);
        return nbt;
    }

    // ==================== УТИЛИТЫ ====================

    public boolean hasNode(BlockPos pos) { return allNodes.containsKey(pos.asLong()); }
    public EnergyNode getNode(BlockPos pos) { return allNodes.get(pos.asLong()); }
    void addNetwork(EnergyNetwork network) { networks.add(network); }
    void removeNetwork(EnergyNetwork network) { networks.remove(network); }
    public ServerLevel getLevel() {
        return this.level;
    }
}