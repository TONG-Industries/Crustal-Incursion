package com.cim.api.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;

public class FluidNetwork {
    private final FluidNetworkManager manager;
    private final Set<FluidNode> nodes = new HashSet<>();
    private final UUID id = UUID.randomUUID();

    public FluidNetwork(FluidNetworkManager manager) {
        this.manager = manager;
    }

    // ==========================================
    // 4-ФАЗНЫЙ АЛГОРИТМ ПЕРЕКАЧКИ И БАЛАНСИРОВКИ
    // ==========================================
    public void tick(ServerLevel level) {
        if (nodes.isEmpty()) return;

        nodes.removeIf(node -> !node.isValid(level) || node.getNetwork() != this);
        if (nodes.size() < 2) return;

        List<IFluidHandler> pureProviders = new ArrayList<>();
        List<IFluidHandler> pureReceivers = new ArrayList<>();
        List<IFluidHandler> buffers = new ArrayList<>(); // Бочки в режиме "Оба"

        // 1. Сортируем все баки в сети по их ролям
        for (FluidNode node : nodes) {
            BlockPos pos = node.getPos();
            if (!level.isLoaded(pos)) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || be instanceof com.cim.block.entity.fluids.FluidPipeBlockEntity) continue;

            be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(handler -> {
                if (be instanceof com.cim.block.entity.fluids.FluidBarrelBlockEntity barrel) {
                    if (barrel.mode == 1) pureReceivers.add(handler);      // Режим: ТОЛЬКО ВХОД
                    else if (barrel.mode == 2) pureProviders.add(handler); // Режим: ТОЛЬКО ВЫХОД
                    else if (barrel.mode == 0) buffers.add(handler);       // Режим: ОБА (Буфер)
                } else {
                    // Обычные машины (помпы, генераторы, крафтеры) могут быть и тем и другим
                    pureProviders.add(handler);
                    pureReceivers.add(handler);
                }
            });
        }

        // ФАЗА 1: Источники -> Потребители (Прямая передача)
        transfer(pureProviders, pureReceivers);

        // ФАЗА 2: Источники -> Буферы (Сохраняем излишки в Бочки "Оба")
        transfer(pureProviders, buffers);

        // ФАЗА 3: Буферы -> Потребители (Бочки "Оба" отдают тем, кто "Только Вход")
        transfer(buffers, pureReceivers);

        // ФАЗА 4: Балансировка Буферов (Выравниваем уровни между Бочками "Оба")
        balance(buffers);
    }

    // --- УНИВЕРСАЛЬНЫЙ МЕТОД ПЕРЕДАЧИ ---
    private void transfer(List<IFluidHandler> sources, List<IFluidHandler> destinations) {
        for (IFluidHandler source : sources) {
            FluidStack available = source.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (available.isEmpty() || available.getAmount() <= 0) continue;

            int remaining = available.getAmount();

            for (IFluidHandler dest : destinations) {
                if (remaining <= 0) break;
                if (source == dest) continue;

                int accepted = dest.fill(new FluidStack(available.getFluid(), remaining), IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    FluidStack drained = source.drain(new FluidStack(available.getFluid(), accepted), IFluidHandler.FluidAction.EXECUTE);
                    if (!drained.isEmpty()) {
                        dest.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                        remaining -= drained.getAmount();
                    }
                }
            }
        }
    }

    // --- УМНАЯ БАЛАНСИРОВКА ---
    private void balance(List<IFluidHandler> buffers) {
        if (buffers.size() < 2) return;

        long totalFluid = 0;
        int validBuffers = 0;
        net.minecraft.world.level.material.Fluid type = null;

        // Считаем общий объем жидкости
        for (IFluidHandler buf : buffers) {
            FluidStack fs = buf.getFluidInTank(0);
            if (!fs.isEmpty()) {
                totalFluid += fs.getAmount();
                if (type == null) type = fs.getFluid();
            }
        }

        // Считаем количество бочек, в которых лежит эта же жидкость (или пустых)
        for (IFluidHandler buf : buffers) {
            FluidStack fs = buf.getFluidInTank(0);
            if (fs.isEmpty() || fs.getFluid() == type) {
                validBuffers++;
            }
        }

        if (totalFluid == 0 || type == null || validBuffers < 2) return;

        // Вычисляем Идеальное Среднее Значение
        int avg = (int) (totalFluid / validBuffers);

        List<IFluidHandler> donors = new ArrayList<>();
        List<IFluidHandler> receivers = new ArrayList<>();

        // Распределяем кто отдает, а кто принимает
        for (IFluidHandler buf : buffers) {
            FluidStack fs = buf.getFluidInTank(0);
            if (fs.isEmpty() || fs.getFluid() == type) {
                int amt = fs.isEmpty() ? 0 : fs.getAmount();
                // Зазор +-5 mB, чтобы бочки не гоняли 1 mB туда-сюда бесконечно (предотвращает лаги)
                if (amt > avg + 5) donors.add(buf);
                else if (amt < avg - 5) receivers.add(buf);
            }
        }

        // Переливаем из полных в пустые до достижения среднего значения
        for (IFluidHandler donor : donors) {
            int donorExcess = donor.getFluidInTank(0).getAmount() - avg;
            if (donorExcess <= 0) continue;

            for (IFluidHandler receiver : receivers) {
                int receiverAmt = receiver.getFluidInTank(0).isEmpty() ? 0 : receiver.getFluidInTank(0).getAmount();
                int receiverDeficit = avg - receiverAmt;
                if (receiverDeficit <= 0) continue;

                int acceptedSim = receiver.fill(new FluidStack(type, Math.min(donorExcess, receiverDeficit)), IFluidHandler.FluidAction.SIMULATE);

                if (acceptedSim > 0) {
                    FluidStack drained = donor.drain(new FluidStack(type, acceptedSim), IFluidHandler.FluidAction.EXECUTE);
                    if (!drained.isEmpty()) {
                        receiver.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                        donorExcess -= drained.getAmount();
                    }
                }
                if (donorExcess <= 0) break;
            }
        }
    }

    // ==========================================
    // ЛОГИКА УЗЛОВ И РАЗДЕЛЕНИЯ (БЕЗ ИЗМЕНЕНИЙ)
    // ==========================================
    public void addNode(FluidNode node) {
        node.setNetwork(this);
        nodes.add(node);
    }

    public void removeNode(FluidNode node) {
        nodes.remove(node);
        node.setNetwork(null);
        if (!nodes.isEmpty()) {
            rebuildNetwork();
        } else {
            manager.removeNetwork(this);
        }
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

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
                for (FluidNode potentialNeighbor : nodes) {
                    if (potentialNeighbor.getPos().equals(neighborPos) && !allReachableNodes.contains(potentialNeighbor)) {
                        allReachableNodes.add(potentialNeighbor);
                        queue.add(potentialNeighbor);
                    }
                }
            }
        }

        if (allReachableNodes.size() < nodes.size()) {
            Set<FluidNode> lostNodes = new HashSet<>(nodes);
            lostNodes.removeAll(allReachableNodes);
            nodes.removeAll(lostNodes);

            for (FluidNode lostNode : lostNodes) {
                lostNode.setNetwork(null);
                manager.reAddNode(lostNode.getPos(), this);
            }

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

    public void merge(FluidNetwork other) {
        if (this == other) return;
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

    public int getNodeCount() {
        return nodes.size();
    }

    public Set<FluidNode> getNodes() {
        return nodes;
    }
}