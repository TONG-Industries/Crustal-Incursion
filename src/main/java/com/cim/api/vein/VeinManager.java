package com.cim.api.vein;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VeinManager extends SavedData {
    private static final String DATA_NAME = "cim_vein_manager";

    private final Map<UUID, VeinMetadata> veinIndex = new HashMap<>();
    private final Map<UUID, VeinData> activeVeins = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Set<UUID>> chunkToVeins = new HashMap<>();

    public static VeinManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                VeinManager::load,
                VeinManager::new,
                DATA_NAME
        );
    }

    public UUID registerVein(Set<BlockPos> blocks, VeinComposition composition, int yLevel) {
        UUID id = UUID.randomUUID();

        int minX = blocks.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int maxX = blocks.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int minZ = blocks.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxZ = blocks.stream().mapToInt(BlockPos::getZ).max().orElse(0);

        ChunkPos minChunk = new ChunkPos(minX >> 4, minZ >> 4);
        ChunkPos maxChunk = new ChunkPos(maxX >> 4, maxZ >> 4);

        for (int cx = minChunk.x; cx <= maxChunk.x; cx++) {
            for (int cz = minChunk.z; cz <= maxChunk.z; cz++) {
                chunkToVeins.computeIfAbsent(new ChunkPos(cx, cz), k -> new HashSet<>()).add(id);
            }
        }

        int maxUnits = blocks.size() * 810;
        VeinMetadata meta = new VeinMetadata(id, composition, maxUnits, minChunk, maxChunk, yLevel);
        veinIndex.put(id, meta);

        VeinData data = new VeinData(id, composition, blocks);
        activeVeins.put(id, data);

        setDirty();
        return id;
    }

    public VeinData getVein(UUID id) {
        VeinData cached = activeVeins.get(id);
        if (cached != null) return cached;
        return loadVeinFromStorage(id);
    }

    public void onChunkLoad(ChunkPos pos) {
        Set<UUID> veinsInChunk = chunkToVeins.get(pos);
        if (veinsInChunk == null) return;

        for (UUID veinId : veinsInChunk) {
            if (!activeVeins.containsKey(veinId)) {
                VeinData data = loadVeinFromStorage(veinId);
                if (data != null) activeVeins.put(veinId, data);
            }
        }
    }

    public void onChunkUnload(ChunkPos pos) {
        Set<UUID> veinsInChunk = chunkToVeins.get(pos);
        if (veinsInChunk == null) return;

        for (UUID veinId : veinsInChunk) {
            VeinMetadata meta = veinIndex.get(veinId);
            if (meta != null && areAllChunksUnloaded(meta)) {
                VeinData data = activeVeins.remove(veinId);
                if (data != null) saveVeinToStorage(data);
            }
        }
    }

    private boolean areAllChunksUnloaded(VeinMetadata meta) {
        return false; // Заглушка
    }

    private VeinData loadVeinFromStorage(UUID id) {
        VeinMetadata meta = veinIndex.get(id);
        if (meta == null) return null;
        // TODO: реальная загрузка из NBT
        return null;
    }

    private void saveVeinToStorage(VeinData data) {
        // TODO: реальное сохранение
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag indexList = new ListTag();
        veinIndex.values().forEach(meta -> indexList.add(meta.serialize()));
        tag.put("VeinIndex", indexList);
        return tag;
    }

    public static VeinManager load(CompoundTag tag) {
        VeinManager manager = new VeinManager();
        ListTag indexList = tag.getList("VeinIndex", 10);
        indexList.forEach(nbt -> {
            VeinMetadata meta = VeinMetadata.deserialize((CompoundTag) nbt);
            manager.veinIndex.put(meta.id, meta);
        });
        return manager;
    }

    public static class VeinMetadata {
        public final UUID id;
        public final VeinComposition composition;
        public final int maxUnits;
        public final ChunkPos minChunk;
        public final ChunkPos maxChunk;
        public final int yLevel;
        public int remainingUnits;

        public VeinMetadata(UUID id, VeinComposition composition, int maxUnits, ChunkPos minChunk, ChunkPos maxChunk, int yLevel) {
            this.id = id;
            this.composition = composition;
            this.maxUnits = maxUnits;
            this.remainingUnits = maxUnits;
            this.minChunk = minChunk;
            this.maxChunk = maxChunk;
            this.yLevel = yLevel;
        }

        public CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.put("Composition", composition.serialize());
            tag.putInt("MaxUnits", maxUnits);
            tag.putInt("Remaining", remainingUnits);
            tag.putInt("MinCX", minChunk.x);
            tag.putInt("MinCZ", minChunk.z);
            tag.putInt("MaxCX", maxChunk.x);
            tag.putInt("MaxCZ", maxChunk.z);
            tag.putInt("YLevel", yLevel);
            return tag;
        }

        public static VeinMetadata deserialize(CompoundTag tag) {
            UUID id = tag.getUUID("Id");
            VeinComposition composition = VeinComposition.deserialize(tag.getCompound("Composition"));
            int maxUnits = tag.getInt("MaxUnits");
            ChunkPos min = new ChunkPos(tag.getInt("MinCX"), tag.getInt("MinCZ"));
            ChunkPos maxChunk = new ChunkPos(tag.getInt("MaxCX"), tag.getInt("MaxCZ"));
            int yLevel = tag.getInt("YLevel");
            VeinMetadata meta = new VeinMetadata(id, composition, maxUnits, min, maxChunk, yLevel);
            meta.remainingUnits = tag.getInt("Remaining");
            return meta;
        }
    }

    public static class VeinData {
        public final UUID id;
        public final VeinComposition composition;
        public final Set<BlockPos> blocks;
        private int remainingUnits;

        public VeinData(UUID id, VeinComposition composition, Set<BlockPos> blocks) {
            this.id = id;
            this.composition = composition;
            this.blocks = blocks;
            this.remainingUnits = blocks.size() * 810;
        }

        public void consumeUnits(int amount) {
            this.remainingUnits = Math.max(0, remainingUnits - amount);
        }

        public int getRemainingUnits() { return remainingUnits; }
        public boolean isDepleted() { return remainingUnits <= 0; }
        public float getDepletionRatio() {
            return 1.0f - ((float)remainingUnits / (blocks.size() * 810f));
        }
        public VeinComposition getComposition() { return composition; }

        public String getTypeName() {
            return composition.getPrimaryMetal();
        }
    }
}