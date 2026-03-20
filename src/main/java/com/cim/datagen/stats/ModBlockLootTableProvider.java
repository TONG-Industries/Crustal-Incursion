package com.cim.datagen.stats;

import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import com.cim.block.basic.ModBlocks;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.world.item.Item;

public class ModBlockLootTableProvider extends BlockLootSubProvider {
    private final Set<Block> exceptions = new HashSet<>();

    public ModBlockLootTableProvider() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        // --- ИСКЛЮЧЕНИЯ (ручные правила) ---
        // (здесь могут быть registerOre1 и registerCustomDrop)

        // --- ДЕФОЛТ ДЛЯ ВСЕХ ОСТАЛЬНЫХ БЛОКОВ ---
        for (RegistryObject<Block> entry : ModBlocks.BLOCKS.getEntries()) {
            Block block = entry.get();

            // Пропускаем блоки, которые не должны иметь стандартного дропа
            if (exceptions.contains(block)) continue;
            if (block == ModBlocks.BEAM_COLLISION.get()) continue;// добавлено

            this.dropSelf(block);
        }
    }

    private void registerOre1(RegistryObject<Block> block, RegistryObject<Item> item) {
        this.add(block.get(), createOreDrop(block.get(), item.get()));
        exceptions.add(block.get());
    }

    private void registerCustomDrop(RegistryObject<Block> block, RegistryObject<Item> drop) {
        this.add(block.get(), createSingleItemTable(drop.get()));
        exceptions.add(block.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .map(RegistryObject::get)
                .filter(block -> block != ModBlocks.BEAM_COLLISION.get())
                .collect(Collectors.toList());
    }
}