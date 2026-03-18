package com.cim.datagen.stats;

import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import com.cim.block.basic.ModBlocks;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

// И этот класс для предметов
import net.minecraft.world.item.Item;

public class ModBlockLootTableProvider extends BlockLootSubProvider {
    // Список блоков, которые мы обработали вручную (исключения)
    private final Set<Block> exceptions = new HashSet<>();

    public ModBlockLootTableProvider() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        // --- ИСКЛЮЧЕНИЯ (Сюда вписывай то, что НЕ просто dropSelf) ---

        // Пример руды с Fortune (Тип 1)
        // registerOre1(ModBlocks.ALUMINUM_ORE, ModItems.ALUMINUM_RAW);

        // Пример блока, который дропает не себя (например, глина)
        // registerCustomDrop(ModBlocks.MY_CLAY, Items.CLAY_BALL);

        // --- ДЕФОЛТ (Для всех остальных) ---
        for (RegistryObject<Block> entry : ModBlocks.BLOCKS.getEntries()) {
            Block block = entry.get();
            // Добавляем проверку, чтобы цикл пропускал наш BEAM_COLLISION
            if (!exceptions.contains(block) && block != ModBlocks.BEAM_COLLISION.get()) {
                this.dropSelf(block);
            }
        }
    }

    // Вспомогательный метод для руд
    private void registerOre1(RegistryObject<Block> block, RegistryObject<net.minecraft.world.item.Item> item) {
        this.add(block.get(), createOreDrop(block.get(), item.get()));
        exceptions.add(block.get());
    }

    // Вспомогательный метод для кастомного дропа
    private void registerCustomDrop(RegistryObject<Block> block, RegistryObject<Item> drop) {
        // Используем встроенный метод createSingleItemTable, передавая .get()
        this.add(block.get(), createSingleItemTable(drop.get()));
        exceptions.add(block.get());
    }


    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream()
                .map(RegistryObject::get)
                // Исключаем наш технический блок из проверки на наличие лута
                .filter(block -> block != ModBlocks.BEAM_COLLISION.get())
                .collect(Collectors.toList());
    }
}