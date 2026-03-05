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

import javax.annotation.Nullable;
import java.util.*;

public class HiveNetworkManager {
    private final Map<UUID, Set<BlockPos>> networkNodes = new HashMap<>();
    private final Map<UUID, HiveNetwork> networks = new HashMap<>();
    private final Map<BlockPos, UUID> posToNetwork = new HashMap<>();

    public boolean hasNests(UUID netId) {
        HiveNetwork net = networks.get(netId);
        return net != null && !net.wormCounts.isEmpty();
    }

    public void addNode(UUID networkId, BlockPos pos) {
        networkNodes.computeIfAbsent(networkId, k -> new HashSet<>()).add(pos.immutable());
    }

    public void mergeNetworks(UUID masterId, UUID targetId, Level level) {
        if (masterId.equals(targetId)) return;

        Set<BlockPos> targetNodes = networkNodes.remove(targetId);
        if (targetNodes != null) {
            for (BlockPos p : targetNodes) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof HiveNetworkMember member) {
                    member.setNetworkId(masterId);
                    this.addNode(masterId, p);
                    be.setChanged();
                }
            }
        }

        HiveNetwork targetNet = networks.remove(targetId);
        if (targetNet != null) {
            networks.computeIfAbsent(masterId, HiveNetwork::new);
        }
    }

    public void removeNode(UUID networkId, BlockPos pos, Level level) {
        Set<BlockPos> nodes = networkNodes.get(networkId);
        if (nodes != null) {
            nodes.remove(pos);
            if (nodes.isEmpty()) {
                networkNodes.remove(networkId);
                networks.remove(networkId);
            } else {
                validateNetwork(networkId, level);
            }
        }
        posToNetwork.remove(pos);
    }

    public void validateNetwork(UUID networkId, Level level) {
        if (level == null) return;
        Set<BlockPos> nodes = networkNodes.get(networkId);
        if (nodes == null) return;

        boolean hasNest = nodes.stream()
                .anyMatch(p -> level.getBlockEntity(p) instanceof DepthWormNestBlockEntity);

        if (!hasNest) {
            for (BlockPos p : nodes) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof HiveNetworkMember member) {
                    member.setNetworkId(null);
                    be.setChanged();
                }
            }
            networkNodes.remove(networkId);
            networks.remove(networkId);
        }
    }

    @Nullable
    public BlockPos findNearestNode(UUID networkId, Vec3 pos, Level level) {
        Set<BlockPos> nodes = networkNodes.get(networkId);
        if (nodes == null) return null;
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (BlockPos nodePos : nodes) {
            double distSq = pos.distanceToSqr(nodePos.getX() + 0.5, nodePos.getY() + 0.5, nodePos.getZ() + 0.5);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = nodePos;
            }
        }
        return best;
    }

    public boolean hasFreeNest(UUID networkId, Level level) {
        Set<BlockPos> nodes = networkNodes.get(networkId);
        if (nodes == null) return false;

        for (BlockPos p : nodes) {
            if (level.getBlockEntity(p) instanceof DepthWormNestBlockEntity nest) {
                if (!nest.isFull()) return true;
            }
        }
        return false;
    }

    public boolean addWormToNetwork(UUID networkId, CompoundTag wormData, BlockPos entryPos, Level level) {
        Set<BlockPos> nodes = networkNodes.get(networkId);
        if (nodes == null) return false;

        for (BlockPos pos : nodes) {
            if (level.getBlockEntity(pos) instanceof DepthWormNestBlockEntity nest) {
                if (!nest.isFull()) {
                    nest.addWormTag(wormData);
                    return true;
                }
            }
        }
        return false;
    }

    public void updateWormCount(UUID netId, BlockPos nestPos, int delta) {
        HiveNetwork net = networks.get(netId);
        if (net != null) net.updateWormCount(nestPos, delta);
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
            for (BlockPos p : net.members) {
                posToNetwork.put(p, net.id);
                this.addNode(net.id, p);
            }
        }
    }

    public static final Capability<HiveNetworkManager> HIVE_NETWORK_MANAGER = CapabilityManager.get(new CapabilityToken<>(){});

    public static HiveNetworkManager get(Level level) {
        return level.getCapability(HIVE_NETWORK_MANAGER).orElse(null);
    }
}