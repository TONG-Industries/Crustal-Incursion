package com.cim.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;
import com.cim.main.CrustalIncursionMod;
import com.cim.worldgen.biome.ModBiomes;
import com.cim.worldgen.ModConfiguredFeatures;
import com.cim.worldgen.ModPlacedFeatures;

// ВАЖНО: Убедись, что путь импорта совпадает с тем, где лежит твой класс ModBiomes!
// import razchexlitiel.cim.worldgen.biome.ModBiomes;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModWorldGenProvider extends DatapackBuiltinEntriesProvider {

    // Сюда мы "складываем" всё, что касается генерации мира
    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, ModConfiguredFeatures::bootstrap) // Добавили сборку чертежей
            .add(Registries.PLACED_FEATURE, ModPlacedFeatures::bootstrap)
            .add(Registries.BIOME, ModBiomes::bootstrap); // Подключаем генерацию нашего биома Рощи
    // Чуть позже мы добавим сюда ConfiguredFeatures и PlacedFeatures для нашего гигантского дерева!

    public ModWorldGenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        // Передаем наш набор (BUILDER) и ID мода в родительский класс
        super(output, registries, BUILDER, Set.of(CrustalIncursionMod.MOD_ID));
    }
}