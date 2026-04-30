package com.cim.api.rotation;

import com.cim.block.basic.industrial.rotation.ShaftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData; // НОВЫЙ ИМПОРТ

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.cim.main.CrustalIncursionMod.LOGGER;

// 1. ИЗМЕНЕНИЕ: Теперь мы наследуем SavedData
public class KineticNetworkManager extends SavedData {

    private static final String DATA_NAME = "cim_kinetic_networks"; // Имя файла в папке data/

    // Твоя мапа остается! Мы просто будем её сохранять в файл и восстанавливать из него.
    private final Map<BlockPos, KineticNetwork> blockToNetwork = new HashMap<>();
    private final ServerLevel level;

    public KineticNetworkManager(ServerLevel level) {
        this.level = level;
    }

    // 2. ИЗМЕНЕНИЕ: Удаляем WeakHashMap. Теперь DataStorage сам управляет памятью.
    public static KineticNetworkManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                tag -> load(level, tag),
                () -> new KineticNetworkManager(level),
                DATA_NAME
        );
    }

    // 3. НОВОЕ: Метод сохранения (вызывается Майнкрафтом при автосейве)
    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag networksList = new ListTag();

        // Получаем все УНИКАЛЬНЫЕ сети из твоей мапы
        Set<KineticNetwork> uniqueNetworks = new HashSet<>(blockToNetwork.values());
        for (KineticNetwork net : uniqueNetworks) {
            networksList.add(net.serializeNBT()); // Вызовем метод сети для превращения в NBT
        }

        nbt.put("Networks", networksList);
        return nbt;
    }

    // 3. НОВОЕ: Метод загрузки (вызывается при запуске сервера)
    public static KineticNetworkManager load(ServerLevel level, CompoundTag nbt) {
        KineticNetworkManager manager = new KineticNetworkManager(level);
        ListTag networksList = nbt.getList("Networks", Tag.TAG_COMPOUND);

        for (int i = 0; i < networksList.size(); i++) {
            CompoundTag netTag = networksList.getCompound(i);
            KineticNetwork net = KineticNetwork.deserializeNBT(netTag); // Восстанавливаем сеть из NBT

            // Восстанавливаем твою мапу blockToNetwork
            for (BlockPos pos : net.getMembers()) {
                manager.blockToNetwork.put(pos, net);
            }
        }
        return manager;
    }

    // --- ТВОИ МЕТОДЫ (С добавлением this.setDirty()) ---

    public void updateNetworkAfterPlace(BlockPos pos) {
        LOGGER.info("[Kinetic] Block placed at {}", pos.toShortString());

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Rotational node)) {
            LOGGER.warn("[Kinetic] Block at {} does not support rotation!", pos.toShortString());
            return;
        }

        Set<KineticNetwork> neighborNetworks = new HashSet<>();

        for (BlockPos neighborPos : node.getPotentialConnections(level, pos)) {
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);

            if (neighborBE instanceof Rotational neighborNode) {
                // 1. Взаимная проверка: сосед тоже должен считать нас потенциальной парой
                if (neighborNode.getPotentialConnections(level, neighborPos).contains(pos)) {
                    // 2. Физическая проверка: диаметры валов подходят?
                    if (node.canConnectMechanically(pos, neighborPos, neighborNode) &&
                            neighborNode.canConnectMechanically(neighborPos, pos, node)) {

                        KineticNetwork net = blockToNetwork.get(neighborPos);
                        if (net == null) {
                            net = createNewNetworkFrom(neighborPos, null);
                        }
                        if (net != null) neighborNetworks.add(net);
                    }
                }
            }
        }

        if (neighborNetworks.size() > 1) {
            long firstActiveSpeed = 0;
            boolean conflict = false;

            for (KineticNetwork net : neighborNetworks) {
                // БЕРЕМ ЦЕЛЕВУЮ СКОРОСТЬ (так как текущая может быть 0 до первого тика)
                long netSpeed = net.getTargetSpeed();

                // Если генераторов нет, смотрим на текущую скорость по инерции
                if (netSpeed == 0) {
                    netSpeed = net.getSpeed();
                }

                if (netSpeed != 0) {
                    if (firstActiveSpeed == 0) {
                        firstActiveSpeed = netSpeed;
                    } else if (firstActiveSpeed != netSpeed) {
                        conflict = true;
                        break;
                    }
                }
            }

            if (conflict) {
                LOGGER.info("[Kinetic] Rotational conflict at {}! Breaking shaft.", pos.toShortString());
                level.destroyBlock(pos, true);
                return;
            }
        }

        if (neighborNetworks.isEmpty()) {
            KineticNetwork newNet = new KineticNetwork();
            registerBlockToNetwork(pos, newNet);
            newNet.recalculate(level);
        } else if (neighborNetworks.size() == 1) {
            KineticNetwork existingNet = neighborNetworks.iterator().next();
            registerBlockToNetwork(pos, existingNet);

            boolean valid = recalculateNetworkSigns(existingNet);

            // ПРОВЕРКА КОНФЛИКТА
            if (!valid || existingNet.checkConflict(level)) {
                LOGGER.info("[Kinetic] Direct motor placement conflict at {}!", pos.toShortString());

                // 1. Откатываем добавление мотора в эту сеть
                updateNetworkAfterRemove(pos);

                // 2. Ищем вал, через который произошло подключение
                BlockPos blockToBreak = pos; // По умолчанию ломаем сам поставленный блок
                for (Direction dir : node.getPropagationDirections()) {
                    BlockPos neighborPos = pos.relative(dir);
                    if (existingNet.getMembers().contains(neighborPos)) {
                        BlockEntity beNeighbor = level.getBlockEntity(neighborPos);
                        // Если сосед - это НЕ другой источник энергии, значит это вал! Ломаем его.
                        if (beNeighbor instanceof Rotational rot && !rot.isSource()) {
                            blockToBreak = neighborPos;
                            break;
                        }
                    }
                }

                // 3. Ломаем "слабое звено" (true = с выпадением предмета!)
                level.destroyBlock(blockToBreak, true);

                // 4. Если мы сломали соседний вал, наш свежий мотор остался целым в мире.
                // Даем ему собственную независимую сеть!
                if (!blockToBreak.equals(pos)) {
                    KineticNetwork newNet = new KineticNetwork();
                    registerBlockToNetwork(pos, newNet);
                    newNet.recalculate(level);
                }

                this.setDirty();
                return;
            }

            existingNet.recalculate(level);
        } else {
            if (!mergeNetworks(neighborNetworks, pos)) {
                LOGGER.info("[Kinetic] Conflict detected while merging networks at {}!", pos.toShortString());
                updateNetworkAfterRemove(pos);
                level.destroyBlock(pos, true);
                this.setDirty();
                return;
            }
        }

        // 4. ИЗМЕНЕНИЕ: Сообщаем Майнкрафту, что данные изменились и их надо сохранить!
        this.setDirty();
    }

    public void updateNetworkAfterRemove(BlockPos pos) {
        KineticNetwork oldNet = blockToNetwork.remove(pos);
        if (oldNet == null) return;

        LOGGER.info("[Kinetic] Block removed at {}. Breaking network {}", pos.toShortString(), oldNet.getId().toString().substring(0, 8));

        Set<BlockPos> membersToRebuild = new HashSet<>(oldNet.getMembers());
        membersToRebuild.remove(pos);

        for (BlockPos memberPos : membersToRebuild) {
            blockToNetwork.remove(memberPos);
        }

        LOGGER.info("[Kinetic] Network {} dissolved. Rebuilding {} blocks...", oldNet.getId().toString().substring(0, 8), membersToRebuild.size());

        for (BlockPos startPos : membersToRebuild) {
            if (!blockToNetwork.containsKey(startPos)) {
                createNewNetworkFrom(startPos, pos); // ИЗМЕНЕНИЕ: передаем pos как игнорируемый блок
            }
        }

        // 4. ИЗМЕНЕНИЕ: Сообщаем о сохранении
        this.setDirty();
    }

    private KineticNetwork createNewNetworkFrom(BlockPos start, BlockPos ignorePos) {
        KineticNetwork newNet = new KineticNetwork();

        if (level.getBlockEntity(start) instanceof Rotational startNode) {
            float scale = startNode.getNetworkScale();
            long baseSpeed = scale != 0 ? (long)(startNode.getSpeed() / scale) : startNode.getSpeed();
            newNet.setCurrentSpeed(baseSpeed);
        }

        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            if (blockToNetwork.containsKey(current)) continue;

            if (level.getBlockEntity(current) instanceof Rotational node) {
                registerBlockToNetwork(current, newNet);

                // ИСПОЛЬЗУЕМ НОВЫЙ ПОИСК
                for (BlockPos neighborPos : node.getPotentialConnections(level, current)) {
                    if (visited.contains(neighborPos)) continue;
                    // ИЗМЕНЕНИЕ: Игнорируем блок, который сейчас ломается!
                    if (neighborPos.equals(ignorePos)) continue;

                    BlockEntity neighborBE = level.getBlockEntity(neighborPos);
                    if (neighborBE instanceof Rotational neighborNode) {
                        if (neighborNode.getPotentialConnections(level, neighborPos).contains(current) &&
                                node.canConnectMechanically(current, neighborPos, neighborNode) &&
                                neighborNode.canConnectMechanically(neighborPos, current, node)) {

                            queue.add(neighborPos);
                        }
                    }
                }
            }
        }

        // ВАЖНО: Вызываем метод с множителями, который мы писали в прошлый раз!
        recalculateNetworkSigns(newNet);
        newNet.recalculate(level);

        LOGGER.info("[Kinetic] New sub-network {} created: members={}, generators={}, speed={}, targetSpeed={}",
                newNet.getId().toString().substring(0, 8),
                newNet.getMembers().size(),
                newNet.getGenerators().size(),
                newNet.getSpeed(),
                newNet.getTargetSpeed());
        return newNet;
    }

    private boolean recalculateNetworkSigns(KineticNetwork net) {
        if (net.getMembers().isEmpty()) return true;

        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        Map<BlockPos, Float> scales = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos root = null;
        if (!net.getGenerators().isEmpty()) {
            root = net.getGenerators().iterator().next();
        } else {
            // Ищем первый блок, который уже имеет масштаб (чтобы не перевернуть сеть по инерции)
            for (BlockPos p : net.getMembers()) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof Rotational rot && Math.abs(rot.getNetworkScale()) > 0.1f) {
                    root = p;
                    break;
                }
            }
            if (root == null) root = net.getMembers().iterator().next();
        }

        queue.add(root);
        
        float rootScale = 1.0f;
        if (level.getBlockEntity(root) instanceof Rotational rootNode) {
            if (Math.abs(rootNode.getNetworkScale()) > 0.1f) {
                rootScale = Math.signum(rootNode.getNetworkScale());
            }
            rootNode.setNetworkScale(rootScale);
        }
        scales.put(root, rootScale);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            BlockEntity currentBE = level.getBlockEntity(current);
            if (currentBE instanceof Rotational node) {
                float currentScale = scales.getOrDefault(current, 1.0f);
                node.setNetworkScale(currentScale); // Сохраняем множитель в блок

                // ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД ПОИСКА
                for (BlockPos neighborPos : node.getPotentialConnections(level, current)) {
                    if (net.getMembers().contains(neighborPos) && !visited.contains(neighborPos)) {
                        BlockEntity neighborBE = level.getBlockEntity(neighborPos);

                        if (neighborBE instanceof Rotational neighborNode) {
                            // Спрашиваем сам блок, с каким коэффициентом он передает вращение
                            float ratio = node.calculateTransmissionRatio(current, neighborPos, neighborNode);
                            float nextScale = currentScale * ratio;

                            if (!scales.containsKey(neighborPos)) {
                                scales.put(neighborPos, nextScale);
                                queue.add(neighborPos);
                            } else {
                                // ПРОВЕРКА КОНФЛИКТА
                                float existingScale = scales.get(neighborPos);
                                if (Math.abs(existingScale - nextScale) > 0.01f) {
                                    return false; // КОНФЛИКТ НАПРАВЛЕНИЯ ИЛИ ПЕРЕДАТОЧНОГО ЧИСЛА
                                }
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean mergeNetworks(Set<KineticNetwork> networks, BlockPos connectorPos) {
        KineticNetwork mainNet = networks.iterator().next();
        networks.remove(mainNet);

        for (KineticNetwork otherNet : networks) {
            for (BlockPos memberPos : otherNet.getMembers()) {
                blockToNetwork.put(memberPos, mainNet);
                mainNet.addMember(memberPos);
            }

            for (BlockPos genPos : otherNet.getGenerators()) {
                mainNet.addGenerator(genPos);
            }
        }

        registerBlockToNetwork(connectorPos, mainNet);
        boolean valid = recalculateNetworkSigns(mainNet);
        if (!valid || mainNet.checkConflict(level)) {
            return false;
        }
        mainNet.recalculate(level);
        return true;
    }

    private void registerBlockToNetwork(BlockPos pos, KineticNetwork net) {
        blockToNetwork.put(pos, net);
        net.addMember(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Rotational rot && rot.isSource()) {
            net.addGenerator(pos);
        }
    }

    public void tickAllNetworks() {
        Set<KineticNetwork> uniqueNetworks = new HashSet<>(blockToNetwork.values());
        boolean anyChanged = false;

        for (KineticNetwork net : uniqueNetworks) {
            // Пусть метод tick возвращает true, если скорость изменилась
            if (net.tick(this.level)) {
                anyChanged = true;
            }
        }

        // Если хоть одна сеть в мире изменила скорость — помечаем для сохранения
        if (anyChanged) {
            this.setDirty();
        }
    }

    public KineticNetwork getNetworkFor(BlockPos pos) {
        return blockToNetwork.get(pos);
    }
}