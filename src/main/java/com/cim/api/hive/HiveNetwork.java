package com.cim.api.hive;


import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.cim.block.entity.hive.DepthWormNestBlockEntity;

import java.util.*;

public class HiveNetwork {
    public final UUID id;
    public final Set<BlockPos> members = new HashSet<>();
    public final Map<BlockPos, Integer> wormCounts = new HashMap<>(); // только для гнёзд

    public HiveNetwork(UUID id) { this.id = id; }

    public void addMember(BlockPos pos, boolean isNest) {
        members.add(pos);
        if (isNest) wormCounts.put(pos, 0);
    }
    public void removeMember(BlockPos pos) {
        members.remove(pos);
        wormCounts.remove(pos);
    }

    public int getTotalWorms() {
        return wormCounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean addWorm(Level level, CompoundTag wormTag, BlockPos sourcePos) {
        // 1. Проверяем, есть ли вообще гнезда в этой сети
        if (wormCounts.isEmpty()) {
            System.out.println("[HiveNetwork] ОШИБКА: В сети " + id + " нет зарегистрированных гнезд!");
            return false;
        }

        BlockPos bestNest = null;
        int minCount = Integer.MAX_VALUE;

        // 2. Ищем самое свободное гнездо (лимит 3 червя)
        for (BlockPos nestPos : wormCounts.keySet()) {
            BlockEntity be = level.getBlockEntity(nestPos);
            if (be instanceof DepthWormNestBlockEntity nest) {
                int currentCount = wormCounts.getOrDefault(nestPos, 0);
                if (currentCount < 3 && currentCount < minCount) {
                    minCount = currentCount;
                    bestNest = nestPos;
                }
            }
        }

        // 3. Если нашли место — закидываем червя
        if (bestNest != null) {
            DepthWormNestBlockEntity nestBE = (DepthWormNestBlockEntity) level.getBlockEntity(bestNest);
            nestBE.addWormTag(wormTag); // Метод в твоем NestBE для хранения NBT
            wormCounts.put(bestNest, wormCounts.get(bestNest) + 1);
            System.out.println("[HiveNetwork] УСПЕХ: Червь направлен в гнездо " + bestNest);
            return true;
        }

        System.out.println("[HiveNetwork] ВАРНИНГ: Все гнезда в сети " + id + " переполнены!");
        return false;
    }


    public void updateWormCount(BlockPos nestPos, int delta) {
        wormCounts.merge(nestPos, delta, Integer::sum);
    }

    
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        ListTag membersList = new ListTag();
        for (BlockPos p : members) {
            CompoundTag pTag = NbtUtils.writeBlockPos(p);
            pTag.putBoolean("IsNest", wormCounts.containsKey(p));
            if (wormCounts.containsKey(p)) {
                pTag.putInt("WormCount", wormCounts.get(p));
            }
            membersList.add(pTag);
        }
        tag.put("Members", membersList);
        return tag;
    }

    public static HiveNetwork fromNBT(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        HiveNetwork net = new HiveNetwork(id);
        ListTag membersList = tag.getList("Members", 10);
        for (int i = 0; i < membersList.size(); i++) {
            CompoundTag pTag = membersList.getCompound(i);
            BlockPos pos = NbtUtils.readBlockPos(pTag);
            boolean isNest = pTag.getBoolean("IsNest");
            net.members.add(pos);
            if (isNest) {
                net.wormCounts.put(pos, pTag.getInt("WormCount"));
            }
        }
        return net;
    }
}