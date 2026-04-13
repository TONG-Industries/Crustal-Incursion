package com.cim.datagen.stats;

import com.cim.item.ModItems;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
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

        registerOreWithMultipleDrops(ModBlocks.BAUXITE, ModItems.BAUXITE_CHUNK);
        registerOreWithMultipleDrops(ModBlocks.DOLOMITE, ModItems.DOLOMITE_CHUNK);
        registerOreWithMultipleDrops(ModBlocks.LIMESTONE, ModItems.LIMESTONE_CHUNK);



        // --- ДЕФОЛТ ДЛЯ ВСЕХ ОСТАЛЬНЫХ БЛОКОВ ---
        for (RegistryObject<Block> entry : ModBlocks.BLOCKS.getEntries()) {
            Block block = entry.get();
            if (exceptions.contains(block)) continue;
            if (block == ModBlocks.BEAM_COLLISION.get() || block == ModBlocks.MULTIBLOCK_PART.get() || block == ModBlocks.PIPE_SPOTS.get()) continue;

            this.dropSelf(block);
        }
    }

    // Новый вспомогательный метод для дропа 1-3 штук с удачей
    private void registerOreWithMultipleDrops(RegistryObject<Block> block, RegistryObject<Item> item) {
        this.add(block.get(), createSilkTouchDispatchTable(block.get(),
                this.applyExplosionDecay(block.get(),
                        LootItem.lootTableItem(item.get())
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 3.0F))) // Дроп 1-3
                                .apply(ApplyBonusCount.addOreBonusCount(Enchantments.BLOCK_FORTUNE)) // Поддержка Удачи
                )));
        exceptions.add(block.get());
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
                .filter(block -> block != ModBlocks.BEAM_COLLISION.get() && block != ModBlocks.MULTIBLOCK_PART.get())
                .collect(Collectors.toList());
    }
}