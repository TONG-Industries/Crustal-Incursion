package com.cim.api.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

public class FluidNetwork {
    private final FluidNetworkManager manager;
    private final Set<FluidNode> nodes = new HashSet<>();
    private final UUID id = UUID.randomUUID();

    public FluidNetwork(FluidNetworkManager manager) {
        this.manager = manager;
    }

    // Тот самый метод, где мы будем качать жидкость! (Напишем его следующим шагом)
    public void tick(ServerLevel level) {
        if (nodes.isEmpty()) return;

        // TODO: Собираем список всех баков/машин вокруг труб
        // TODO: Выкачиваем жидкость из источников
        // TODO: Распределяем по потребителям поровну
    }

    public void addNode(FluidNode node) {
        node.setNetwork(this);
        nodes.add(node);
    }

    public void removeNode(FluidNode node) {
        nodes.remove(node);
        node.setNetwork(null);
        if (!nodes.isEmpty()) {
            rebuildNetwork(); // Перестраиваем сеть, вдруг она разорвалась на две части
        } else {
            manager.removeNetwork(this);
        }
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    // ==================== АЛГОРИТМ РАЗДЕЛЕНИЯ (SPLIT) ====================
    private void rebuildNetwork() {
        if (nodes.isEmpty()) return;

        Set<FluidNode> allReachableNodes = new HashSet<>();
        Queue<FluidNode> queue = new LinkedList<>();

        FluidNode startNode = nodes.iterator().next();
        queue.add(startNode);
        allReachableNodes.add(startNode);

        while (!queue.isEmpty()) {
            FluidNode current = queue.poll();
            BlockPos pos = current.getPos();

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                // Проверяем, есть ли сосед в нашей текущей сети
                for (FluidNode potentialNeighbor : nodes) {
                    if (potentialNeighbor.getPos().equals(neighborPos) && !allReachableNodes.contains(potentialNeighbor)) {

                        // Проверяем, могут ли они всё ещё логически соединяться
                        if (canConnectLogically(current, potentialNeighbor)) {
                            allReachableNodes.add(potentialNeighbor);
                            queue.add(potentialNeighbor);
                        }
                    }
                }
            }
        }

        // Если мы не смогли дотянуться до всех узлов, значит сеть разорвалась!
        if (allReachableNodes.size() < nodes.size()) {
            Set<FluidNode> lostNodes = new HashSet<>(nodes);
            lostNodes.removeAll(allReachableNodes);
            nodes.removeAll(lostNodes);

            // Выкидываем оторванные узлы и просим менеджера собрать из них новую сеть
            for (FluidNode lostNode : lostNodes) {
                lostNode.setNetwork(null);
                manager.reAddNode(lostNode.getPos(), this);
            }

            // Если в этой сети остался 1 или 0 узлов, распускаем и её
            if (nodes.size() < 2) {
                for (FluidNode remainingNode : nodes) {
                    remainingNode.setNetwork(null);
                    manager.reAddNode(remainingNode.getPos(), this);
                }
                nodes.clear();
                manager.removeNetwork(this);
            }
        }
    }

    // ==================== СЛИЯНИЕ СЕТЕЙ (MERGE) ====================
    public void merge(FluidNetwork other) {
        if (this == other) return;

        // Оптимизация: вливаем меньшую сеть в большую
        if (other.nodes.size() > this.nodes.size()) {
            other.merge(this);
            return;
        }
        for (FluidNode node : other.nodes) {
            node.setNetwork(this);
            this.nodes.add(node);
        }
        other.nodes.clear();
        manager.removeNetwork(other);
    }

    private boolean canConnectLogically(FluidNode n1, FluidNode n2) {
        // Упрощенная проверка, т.к. фильтры проверяются на уровне менеджера
        return true;
    }

    // Метод для нашего логгера, чтобы он знал размер сети
    public int getNodeCount() {
        return nodes.size();
    }

    // Отдает список всех труб (узлов) в этой сети
    public java.util.Set<FluidNode> getNodes() {
        return nodes;
    }
}
