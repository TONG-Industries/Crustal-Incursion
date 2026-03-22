package com.cim.api.energy;

import com.cim.block.entity.industrial.energy.ConnectorBlockEntity;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import com.cim.block.entity.industrial.energy.MachineBatteryBlockEntity;
import com.cim.capability.ModCapabilities;

import java.util.*;

/**
 * ===================================================================
 * EnergyNetwork.java - ВЕРСИЯ 7.0 (Cascading Priority Flow)
 * Решает проблемы перетока энергии между приоритетами.
 * РАПТОР ЕСЛИ ТЫ ХОЧЕШЬ СПИЗДИТЬ ЭТОТ КОД ТО ТЫ ПЕТУХ
 * АНАЛЬНЫЙ ЧЕРВЯЧОК ПРИДЁТ К ТЕБЕ И ...
 * ===================================================================
 */
public class EnergyNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final EnergyNetworkManager manager;
    private final Set<EnergyNode> nodes = new HashSet<>();
    private final UUID id = UUID.randomUUID();



    public EnergyNetwork(EnergyNetworkManager manager) {
        this.manager = manager;
    }

    public void tick(ServerLevel level) {
        // 1. Валидация узлов
        int sizeBefore = nodes.size();
        nodes.removeIf(node -> !node.isValid(level) || node.getNetwork() != this);

        if (nodes.size() < sizeBefore) {
            verifyConnectivity();
        }

        if (nodes.size() < 2) {
            return;
        }

        // ====================================================================
        // ШАГ 1: Классификация участников (С ЗАЩИТОЙ ОТ ДУБЛИКАТОВ)
        // ====================================================================

        // Используем Set, чтобы один и тот же генератор не попал в список дважды
        // (IdentityHashMap важна, чтобы различать разные инстансы capability)
        Set<IEnergyProvider> uniqueGenerators = Collections.newSetFromMap(new IdentityHashMap<>());

        // Для машин и батарей тоже используем мапы для дедупликации по Capability
        Map<IEnergyReceiver, IEnergyReceiver> uniqueMachines = new IdentityHashMap<>();
        Map<IEnergyReceiver, BatteryInfo> uniqueBatteries = new IdentityHashMap<>();

        for (EnergyNode node : nodes) {
            if (!level.isLoaded(node.getPos())) continue;
            BlockEntity be = level.getBlockEntity(node.getPos());
            if (be == null) continue;

            Optional<IEnergyProvider> providerCap = be.getCapability(ModCapabilities.ENERGY_PROVIDER).resolve();
            Optional<IEnergyReceiver> receiverCap = be.getCapability(ModCapabilities.ENERGY_RECEIVER).resolve();

            boolean isProvider = providerCap.isPresent();
            boolean isReceiver = receiverCap.isPresent();

            if (isProvider && isReceiver) {
                // БАТАРЕЯ
                int mode = (be instanceof MachineBatteryBlockEntity batteryBE) ? batteryBE.getCurrentMode() : 0;
                IEnergyReceiver rec = receiverCap.get();
                IEnergyProvider prov = providerCap.get();

                BatteryInfo info = new BatteryInfo(node.getPos(), rec, prov, mode);

                if (mode == 2) {
                    // OUTPUT ONLY -> Чистый генератор
                    if (prov.canExtract()) {
                        uniqueGenerators.add(prov); // Set сам отсеет дубли
                    }
                } else {
                    // BOTH или INPUT -> Сохраняем (перезаписываем, если уже есть такой receiver)
                    uniqueBatteries.put(rec, info);
                }
            } else if (isProvider) {
                // ГЕНЕРАТОР
                IEnergyProvider prov = providerCap.get();
                if (prov.canExtract()) {
                    uniqueGenerators.add(prov);
                }
            } else if (isReceiver) {
                // МАШИНА
                IEnergyReceiver rec = receiverCap.get();
                if (rec.canReceive()) {
                    uniqueMachines.put(rec, rec);
                }
            }
        }

        // Превращаем уникальные коллекции в списки для работы алгоритма
        List<IEnergyProvider> pureGenerators = new ArrayList<>(uniqueGenerators);

        if (level.getGameTime() % 60 == 0) {
            LOGGER.info("=== СТАТУС СЕТИ [{}] ===", id.toString().substring(0, 5));
            LOGGER.info("1. Всего узлов (nodes): {}", nodes.size());
            LOGGER.info("2. Найдено чистых Генераторов: {}", pureGenerators.size());
            LOGGER.info("3. Найдено Машин (потребителей): {}", uniqueMachines.size());
            LOGGER.info("4. Найдено Батарей: {}", uniqueBatteries.size());
        }

        // Группируем машины по приоритетам
        Map<IEnergyReceiver.Priority, List<IEnergyReceiver>> machinesByPriority = new EnumMap<>(IEnergyReceiver.Priority.class);
        for (IEnergyReceiver.Priority p : IEnergyReceiver.Priority.values()) machinesByPriority.put(p, new ArrayList<>());
        for (IEnergyReceiver r : uniqueMachines.keySet()) machinesByPriority.get(r.getPriority()).add(r);

        // Группируем батареи по приоритетам
        Map<IEnergyReceiver.Priority, List<BatteryInfo>> batteriesByPriority = new EnumMap<>(IEnergyReceiver.Priority.class);
        for (IEnergyReceiver.Priority p : IEnergyReceiver.Priority.values()) batteriesByPriority.put(p, new ArrayList<>());
        for (BatteryInfo b : uniqueBatteries.values()) batteriesByPriority.get(b.receiver.getPriority()).add(b);


        // ====================================================================
        // ШАГ 2: Сбор начальной энергии от чистых генераторов
        // ====================================================================

        long floatingEnergy = 0;

        Map<IEnergyProvider, Long> activeProviders = new IdentityHashMap<>();
        for (IEnergyProvider gen : pureGenerators) {
            // ТЕПЕРЬ здесь нет дубликатов, сумма будет верной (1000, а не 3000)
            long cap = Math.min(gen.getEnergyStored(), gen.getProvideSpeed());
            if (cap > 0) {
                activeProviders.put(gen, cap);
                floatingEnergy += cap;
            }
        }

        // ====================================================================
        // ШАГ 3: Водопад приоритетов (HIGH -> NORMAL -> LOW)
        // ====================================================================

        IEnergyReceiver.Priority[] priorities = {
                IEnergyReceiver.Priority.HIGH,
                IEnergyReceiver.Priority.NORMAL,
                IEnergyReceiver.Priority.LOW
        };

        for (IEnergyReceiver.Priority currentPriority : priorities) {

            List<IEnergyReceiver> consumers = machinesByPriority.get(currentPriority);
            List<BatteryInfo> batteries = batteriesByPriority.get(currentPriority);

            Map<IEnergyReceiver, Long> demands = new IdentityHashMap<>();
            long totalLevelDemand = 0;

            // Машины
            for (IEnergyReceiver r : consumers) {
                long needed = Math.min(r.getMaxEnergyStored() - r.getEnergyStored(), r.getReceiveSpeed());
                if (needed > 0) {
                    demands.put(r, needed);
                    totalLevelDemand += needed;
                }
            }

            // Батареи
            for (BatteryInfo bat : batteries) {
                if (bat.canInput() && bat.receiver.canReceive()) {
                    long needed = Math.min(bat.receiver.getMaxEnergyStored() - bat.receiver.getEnergyStored(), bat.receiver.getReceiveSpeed());
                    if (needed > 0) {
                        demands.put(bat.receiver, needed);
                        totalLevelDemand += needed;
                    }
                }
            }

            if (totalLevelDemand == 0) continue;

            // Если энергии не хватает, грабим нижние уровни
            if (floatingEnergy < totalLevelDemand) {
                long deficit = totalLevelDemand - floatingEnergy;
                long stolenEnergy = stealFromLowerPriorities(currentPriority, deficit, batteriesByPriority, activeProviders);
                floatingEnergy += stolenEnergy;
            }

            // Раздаем энергию
            long energyToDistribute = Math.min(floatingEnergy, totalLevelDemand);

            if (energyToDistribute > 0) {
                Set<IEnergyReceiver> allReceivers = new HashSet<>(demands.keySet());
                long used = distributeProportionally(energyToDistribute, allReceivers, demands, activeProviders);
                floatingEnergy -= used;
            }
        }

        // ====================================================================
        // ШАГ 4: Балансировка батарей
        // ====================================================================
        for (List<BatteryInfo> group : batteriesByPriority.values()) {
            balanceSamePriorityGroup(group);
        }
    }

    /**
     * Ищет батареи в приоритетах НИЖЕ указанного и заставляет их отдать энергию.
     */
    /**
     * Ищет батареи в приоритетах НИЖЕ (и в ТЕКУЩЕМ) и заставляет их отдать энергию.
     */
    private long stealFromLowerPriorities(IEnergyReceiver.Priority currentPriority, long amountNeeded,
                                          Map<IEnergyReceiver.Priority, List<BatteryInfo>> allBatteries,
                                          Map<IEnergyProvider, Long> providerPool) {
        long gathered = 0;

        // ИСПРАВЛЕНИЕ: Порядок "грабежа".
        // Сначала забираем у самых неважных (LOW), а если не хватило - берем у батарей СВОЕГО же уровня.
        List<IEnergyReceiver.Priority> prioritiesToSearch = new ArrayList<>();
        if (currentPriority == IEnergyReceiver.Priority.HIGH) {
            prioritiesToSearch.add(IEnergyReceiver.Priority.LOW);
            prioritiesToSearch.add(IEnergyReceiver.Priority.NORMAL);
            prioritiesToSearch.add(IEnergyReceiver.Priority.HIGH); // Свои
        } else if (currentPriority == IEnergyReceiver.Priority.NORMAL) {
            prioritiesToSearch.add(IEnergyReceiver.Priority.LOW);
            prioritiesToSearch.add(IEnergyReceiver.Priority.NORMAL); // Свои
        } else {
            prioritiesToSearch.add(IEnergyReceiver.Priority.LOW); // Свои
        }

        for (IEnergyReceiver.Priority p : prioritiesToSearch) {
            if (gathered >= amountNeeded) break;

            for (BatteryInfo bat : allBatteries.get(p)) {
                if (gathered >= amountNeeded) break;

                // Батарея должна уметь отдавать (BOTH или OUTPUT)
                if (bat.canOutput() && bat.provider.canExtract()) {
                    long available = Math.min(bat.provider.getEnergyStored(), bat.provider.getProvideSpeed());

                    // Если эта батарея уже отдала что-то в этом тике, учитываем это
                    long alreadyPromised = providerPool.getOrDefault(bat.provider, 0L);
                    available -= alreadyPromised;

                    if (available > 0) {
                        long toTake = Math.min(available, amountNeeded - gathered);
                        providerPool.merge(bat.provider, toTake, Long::sum);
                        gathered += toTake;
                    }
                }
            }
        }
        return gathered;
    }

    // --- СТАНДАРТНЫЕ МЕТОДЫ (Без изменений логики, только копипаст для целостности) ---

    private long distributeProportionally(long amount, Set<IEnergyReceiver> consumers,
                                          Map<IEnergyReceiver, Long> consumerDemand,
                                          Map<IEnergyProvider, Long> providers) {
        if (amount <= 0 || consumers.isEmpty() || providers.isEmpty()) return 0;

        long totalGroupDemand = 0;
        for (IEnergyReceiver consumer : consumers) totalGroupDemand += consumerDemand.getOrDefault(consumer, 0L);
        if (totalGroupDemand <= 0) return 0;

        long totalEnergyGiven = 0;

        // [ВАЖНО] Запоминаем изначальное количество энергии для расчета долей.
        // Если мы будем уменьшать amount в цикле и от него считать процент,
        // последние в списке получат копейки.
        long initialPoolForCalculation = amount;

        // Создаем копию списка, чтобы избежать ConcurrentModificationException (на всякий случай)
        List<IEnergyReceiver> sortedConsumers = new ArrayList<>(consumers);
        // Опционально: можно сортировать по BlockPos, чтобы порядок был детерминированным (убирает мигание цифр)
        // sortedConsumers.sort(Comparator.comparing(c -> ((BlockEntity)c).getBlockPos()));

        for (IEnergyReceiver consumer : sortedConsumers) {
            if (amount <= 0) break; // Энергия кончилась физически

            long demand = consumerDemand.getOrDefault(consumer, 0L);
            if (demand <= 0) continue;

            // Считаем долю от ИСХОДНОГО пула (или спроса, если пул огромен)
            double share = (double) demand / totalGroupDemand;

            // Расчитываем порцию.
            // Math.ceil, чтобы не терять единицы энергии на округлении вниз при делении
            long energyForThis = (long) Math.ceil(initialPoolForCalculation * share);

            // Обрезаем, чтобы не дать больше чем просит потребитель
            energyForThis = Math.min(energyForThis, demand);
            // Обрезаем, чтобы не дать больше чем реально осталось в пуле (amount)
            energyForThis = Math.min(energyForThis, amount);

            if (energyForThis > 0) {
                long accepted = consumer.receiveEnergy(energyForThis, false);
                if (accepted > 0) {
                    extractFromProviders(accepted, providers);
                    totalEnergyGiven += accepted;
                    amount -= accepted; // Уменьшаем реальный остаток
                    consumerDemand.put(consumer, demand - accepted);
                }
            }
        }
        return totalEnergyGiven;
    }

    private void extractFromProviders(long amount, Map<IEnergyProvider, Long> providers) {
        // Та же реализация, что и раньше
        if (amount <= 0 || providers.isEmpty()) return;
        long totalCapacity = providers.values().stream().mapToLong(Long::longValue).sum();
        if (totalCapacity <= 0) return;

        long remaining = amount;
        List<Map.Entry<IEnergyProvider, Long>> providerList = new ArrayList<>(providers.entrySet());

        // Пропорциональное изъятие
        for (Map.Entry<IEnergyProvider, Long> entry : providerList) {
            if (remaining <= 0) break;
            IEnergyProvider provider = entry.getKey();
            long capacity = entry.getValue();
            if (capacity <= 0) continue;

            double share = (double) capacity / totalCapacity;
            long toExtract = (long) (amount * share);
            toExtract = Math.min(remaining, Math.min(capacity, toExtract));

            if (toExtract > 0) {
                long extracted = provider.extractEnergy(toExtract, false);
                remaining -= extracted;
                entry.setValue(capacity - extracted);
            }
        }

        // Добор остатков (ошибки округления)
        if (remaining > 0) {
            providerList.sort(Map.Entry.<IEnergyProvider, Long>comparingByValue().reversed());
            for (Map.Entry<IEnergyProvider, Long> entry : providerList) {
                if (remaining <= 0) break;
                long capacity = entry.getValue();
                if (capacity > 0) {
                    long toExtract = Math.min(remaining, capacity);
                    long extracted = entry.getKey().extractEnergy(toExtract, false);
                    remaining -= extracted;
                    entry.setValue(capacity - extracted);
                }
            }
        }
    }

    /**
     * Балансировка внутри одной группы приоритета (например, две LOW батареи выравниваются друг с другом)
     */
    private void balanceSamePriorityGroup(List<BatteryInfo> batteries) {
        List<BatteryInfo> bothModeBatteries = new ArrayList<>();
        for(BatteryInfo b : batteries) if(b.mode == 0) bothModeBatteries.add(b); // Только BOTH

        if (bothModeBatteries.size() < 2) return;

        long totalEnergy = 0;
        long totalCapacity = 0;
        for (BatteryInfo b : bothModeBatteries) {
            totalEnergy += b.provider.getEnergyStored();
            totalCapacity += b.provider.getMaxEnergyStored();
        }
        if (totalCapacity == 0) return;

        double avgRatio = (double) totalEnergy / totalCapacity;
        long transferBuffer = 100; // Минимальная разница для начала балансировки

        // Простой алгоритм: богатые дают, бедные берут
        for (BatteryInfo giver : bothModeBatteries) {
            long current = giver.provider.getEnergyStored();
            long target = (long) (giver.provider.getMaxEnergyStored() * avgRatio);

            if (current > target + transferBuffer && giver.provider.canExtract()) {
                long toGive = Math.min(current - target, giver.provider.getProvideSpeed());

                for (BatteryInfo taker : bothModeBatteries) {
                    if (toGive <= 0) break;
                    if (taker == giver) continue;

                    long tCurrent = taker.provider.getEnergyStored();
                    long tTarget = (long) (taker.provider.getMaxEnergyStored() * avgRatio);

                    if (tCurrent < tTarget - transferBuffer && taker.receiver.canReceive()) {
                        long canTake = Math.min(tTarget - tCurrent, taker.receiver.getReceiveSpeed());
                        long transfer = Math.min(toGive, canTake);

                        if (transfer > 0) {
                            long extracted = giver.provider.extractEnergy(transfer, false);
                            long accepted = taker.receiver.receiveEnergy(extracted, false);
                            // Возврат непринятого (редкий кейс, но для надежности)
                            if (accepted < extracted) {
                                giver.provider.setEnergyStored(giver.provider.getEnergyStored() + (extracted - accepted));
                            }
                            toGive -= accepted;
                        }
                    }
                }
            }
        }
    }

    // Helper Class
    private static class BatteryInfo {
        final BlockPos pos;
        final IEnergyReceiver receiver;
        final IEnergyProvider provider;
        final int mode;

        BatteryInfo(BlockPos pos, IEnergyReceiver r, IEnergyProvider p, int mode) {
            this.pos = pos;
            this.receiver = r;
            this.provider = p;
            this.mode = mode;
        }
        boolean canInput() { return mode == 0 || mode == 1; }
        boolean canOutput() { return mode == 0 || mode == 2; }
    }

    // --- Методы управления сетью (addNode, removeNode, merge, etc) ---
    // Оставь их без изменений из предыдущей версии (v6.3 или той, что я кидал ранее)
    // Главное изменение выше - в методе tick().

    public void addNode(EnergyNode node) {
        if (nodes.add(node)) {
            node.setNetwork(this);
        }
    }

    public void removeNode(EnergyNode node) {
        if (!nodes.remove(node)) return;
        node.setNetwork(null);

        // Если после удаления узла в сети остался всего 1 блок (или 0)
        if (nodes.size() < 2) {
            // ВАЖНО: Делаем копию списка, чтобы не поймать ConcurrentModificationException
            List<EnergyNode> orphanedNodes = new ArrayList<>(nodes);
            nodes.clear();
            manager.removeNetwork(this);

            // ИСПРАВЛЕНИЕ: Заставляем менеджер заново "осознать" брошенные блоки!
            for (EnergyNode orphaned : orphanedNodes) {
                orphaned.setNetwork(null);
                manager.reAddNode(orphaned.getPos(), this);
            }
        } else {
            verifyConnectivity();
        }
    }

    private void verifyConnectivity() {

        ServerLevel level = manager.getLevel();

        if (nodes.isEmpty()) return;
        Set<EnergyNode> allReachableNodes = new HashSet<>();
        Queue<EnergyNode> queue = new LinkedList<>();
        EnergyNode startNode = nodes.iterator().next();
        queue.add(startNode);
        allReachableNodes.add(startNode);

        while (!queue.isEmpty()) {
            EnergyNode current = queue.poll();
            for (Direction dir : Direction.values()) {
                EnergyNode neighbor = manager.getNode(current.getPos().relative(dir));
                if (neighbor != null && nodes.contains(neighbor) && allReachableNodes.add(neighbor)) {
                    queue.add(neighbor);
                }
            }

            // ИСПРАВЛЕНО ДЛЯ МНОЖЕСТВА ПОДКЛЮЧЕНИЙ
            if (level != null && level.isLoaded(current.getPos())) {
                BlockEntity be = level.getBlockEntity(current.getPos());
                if (be instanceof ConnectorBlockEntity connector) {
                    for (BlockPos linkedPos : connector.getConnections()) {
                        if (linkedPos != null) {
                            EnergyNode linkedNeighbor = manager.getNode(linkedPos);
                            if (linkedNeighbor != null && nodes.contains(linkedNeighbor)
                                    && allReachableNodes.add(linkedNeighbor)) {
                                queue.add(linkedNeighbor);
                            }
                        }
                    }
                }
            }
        }

        if (allReachableNodes.size() < nodes.size()) {
            Set<EnergyNode> lostNodes = new HashSet<>(nodes);
            lostNodes.removeAll(allReachableNodes);
            nodes.removeAll(lostNodes);
            for (EnergyNode lostNode : lostNodes) {
                lostNode.setNetwork(null);
                manager.reAddNode(lostNode.getPos(), this);
            }
            if (nodes.size() < 2) {
                for (EnergyNode remainingNode : nodes) {
                    remainingNode.setNetwork(null);
                    manager.reAddNode(remainingNode.getPos(), this);
                }
                nodes.clear();
                manager.removeNetwork(this);
            }
        }
    }

    public void merge(EnergyNetwork other) {
        if (this == other) return;
        if (other.nodes.size() > this.nodes.size()) {
            other.merge(this);
            return;
        }
        for (EnergyNode node : other.nodes) {
            node.setNetwork(this);
            this.nodes.add(node);
        }
        other.nodes.clear();
        manager.removeNetwork(other);
    }

    public UUID getId() { return id; }

    public int getNodeCount() {
        return nodes.size();
    }


}