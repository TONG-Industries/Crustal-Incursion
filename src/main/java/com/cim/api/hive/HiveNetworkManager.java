package com.cim.api.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import com.cim.block.entity.hive.DepthWormNestBlockEntity;

import java.util.*;

public class HiveNetworkManager {
    private final Map<UUID, Set<BlockPos>> networkNodes = new HashMap<>();
    private final Map<UUID, HiveNetwork> networks = new HashMap<>();
    private final Map<BlockPos, UUID> posToNetwork = new HashMap<>();

    public void tick(Level level) {
        if (networks.isEmpty()) return;

        List<HiveNetwork> safeCopy = new ArrayList<>(networks.values());

        for (HiveNetwork network : safeCopy) {
            if (network.isDead() || network.isAbandoned()) {
                networks.remove(network.id);
                System.out.println("[HiveManager] Removed network " + network.id +
                        (network.isAbandoned() ? " (abandoned, no nests)" : " (dead)"));
                continue;
            }

            if (!network.hasAnyLoadedChunk(level)) continue;
            network.update(level);
        }
    }

    public static HiveNetworkManager get(Level level) {
        return level.getCapability(HiveNetworkManagerProvider.HIVE_NETWORK_MANAGER).orElse(null);
    }

    public HiveNetwork getNetwork(UUID id) {
        if (id == null) return null;
        return networks.computeIfAbsent(id, HiveNetwork::new);
    }

    public void addNode(UUID networkId, BlockPos pos, boolean isNest) {
        HiveNetwork network = getNetwork(networkId);
        network.addMember(pos, isNest);
        posToNetwork.put(pos, networkId);
        networkNodes.computeIfAbsent(networkId, k -> new HashSet<>()).add(pos);
    }

    public void mergeNetworks(UUID mainId, UUID secondId, Level level) {
        if (mainId.equals(secondId)) return;

        HiveNetwork mainNet = getNetwork(mainId);
        HiveNetwork secondNet = networks.get(secondId);
        if (secondNet == null) return;

        System.out.println("[Hive] Merging networks! " + secondId + " absorbed into " + mainId);

        mainNet.killsPool = Math.min(50, mainNet.killsPool + secondNet.killsPool);

        for (BlockPos pos : new HashSet<>(secondNet.members)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HiveNetworkMember member) {
                member.setNetworkId(mainId);
            }

            posToNetwork.put(pos, mainId);

            if (secondNet.wormCounts.containsKey(pos)) {
                mainNet.wormCounts.put(pos, secondNet.wormCounts.get(pos));
                mainNet.nestWormData.put(pos, new ArrayList<>(secondNet.getNestWormData(pos)));
            }
            mainNet.members.add(pos);
        }

        networks.remove(secondId);
        networkNodes.remove(secondId);
    }

    public void removeNode(UUID networkId, BlockPos pos, Level level) {
        HiveNetwork network = networks.get(networkId);
        if (network != null) {
            network.removeMember(pos);
            if (network.members.isEmpty()) {
                networks.remove(networkId);
            }
        }
        posToNetwork.remove(pos);
        Set<BlockPos> nodes = networkNodes.get(networkId);
        if (nodes != null) nodes.remove(pos);
    }

    public BlockPos findNearestNode(UUID networkId, Vec3 targetPos, Level level) {
        HiveNetwork network = getNetwork(networkId);
        if (network == null || network.members.isEmpty()) return null;

        BlockPos targetBlockPos = BlockPos.containing(targetPos);
        BlockPos closest = null;
        double minDistance = Double.MAX_VALUE;

        for (BlockPos pos : network.members) {
            double dist = pos.distSqr(targetBlockPos);
            if (dist < minDistance) {
                minDistance = dist;
                closest = pos;
            }
        }
        return closest;
    }

    public boolean addWormToNetwork(UUID networkId, CompoundTag wormData, BlockPos entryPos, Level level) {
        HiveNetwork network = getNetwork(networkId);
        if (network == null) return false;

        for (BlockPos pos : network.members) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DepthWormNestBlockEntity nest) {
                if (!nest.isFull()) {
                    nest.addWormTag(wormData);
                    network.updateWormCount(pos, 1);
                    network.addWormDataToNest(pos, wormData);
                    System.out.println("[Hive] Worm bound to nest at " + pos + ". Total in nest: " +
                            network.getNestWormData(pos).size());
                    return true;
                }
            }
        }
        return false;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag networksList = new ListTag();
        for (HiveNetwork net : networks.values()) {
            networksList.add(net.toNBT());
        }
        tag.put("Networks", networksList);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        networks.clear();
        posToNetwork.clear();
        networkNodes.clear();

        ListTag networksList = tag.getList("Networks", 10);
        for (int i = 0; i < networksList.size(); i++) {
            HiveNetwork net = HiveNetwork.fromNBT(networksList.getCompound(i));
            networks.put(net.id, net);

            for (BlockPos pos : net.members) {
                posToNetwork.put(pos, net.id);
                networkNodes.computeIfAbsent(net.id, k -> new HashSet<>()).add(pos);
            }
        }
    }
    public void updateWormCount(UUID networkId, BlockPos pos, int delta) {
        HiveNetwork network = getNetwork(networkId);
        if (network != null) {
            network.updateWormCount(pos, delta);
        }
    }
    public static final Capability<HiveNetworkManager> HIVE_NETWORK_MANAGER =
            CapabilityManager.get(new CapabilityToken<>(){});
}