package razchexlitiel.cim.worldgen.biome;

import net.minecraft.data.worldgen.BiomeDefaultFeatures;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.VegetationPlacements;
import net.minecraft.world.level.levelgen.GenerationStep;
import razchexlitiel.cim.main.CrustalIncursionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Musics;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import razchexlitiel.cim.worldgen.ModPlacedFeatures;

public class ModBiomes {

    // 1. Уникальный ключ для нашей красивой рощи
    public static final ResourceKey<Biome> SEQUOIA_GROVE = ResourceKey.create(Registries.BIOME,
            new ResourceLocation(CrustalIncursionMod.MOD_ID, "sequoia_grove"));

    // 2. Метод Bootstrap для DataGen
    public static void bootstrap(BootstapContext<Biome> context) {
        context.register(SEQUOIA_GROVE, sequoiaGroveBiome(context));
    }

    // 3. Фабрика биома: Настраиваем красоту
    private static Biome sequoiaGroveBiome(BootstapContext<Biome> context) {
        MobSpawnSettings.Builder spawnBuilder = new MobSpawnSettings.Builder();

        // Позже сюда мы добавим спавн нашей гигантской секвойи
        BiomeGenerationSettings.Builder biomeBuilder =
                new BiomeGenerationSettings.Builder(context.lookup(Registries.PLACED_FEATURE), context.lookup(Registries.CONFIGURED_CARVER));

        BiomeDefaultFeatures.addDefaultCarversAndLakes(biomeBuilder);

        // ШАГ 2: Локальные изменения (Аметисты всегда ДО валунов)
        BiomeDefaultFeatures.addDefaultCrystalFormations(biomeBuilder);
        biomeBuilder.addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, MiscOverworldPlacements.FOREST_ROCK);

        // ШАГ 3: Подземные структуры
        BiomeDefaultFeatures.addDefaultMonsterRoom(biomeBuilder);

        // ШАГ 4: Подземелье и руды
        BiomeDefaultFeatures.addDefaultUndergroundVariety(biomeBuilder);
        BiomeDefaultFeatures.addDefaultOres(biomeBuilder);
        BiomeDefaultFeatures.addDefaultSoftDisks(biomeBuilder);

        // ШАГ 5: Растительность (СТРОЖАЙШИЙ ПОРЯДОК!)
        // 5.1 Сначала ВСЕГДА идут деревья

        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.SMALL_SEQUOIA_PLACED_KEY);
        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.MEDIUM_SEQUOIA_PLACED_KEY);

        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, ModPlacedFeatures.GIANT_SEQUOIA_PLACED_KEY);

        // 5.2 Затем трава и папоротники
        BiomeDefaultFeatures.addFerns(biomeBuilder);
        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_GRASS_NORMAL);

        // 5.3 Затем грибы
        BiomeDefaultFeatures.addDefaultMushrooms(biomeBuilder);


        // 5.4 В самом конце кусты (ягоды)
        biomeBuilder.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_BERRY_COMMON);

        return new Biome.BiomeBuilder()
                .hasPrecipitation(true)  // Дождь разрешен
                .temperature(0.7F)       // Комфортная, слегка прохладная температура (как в горах)
                .downfall(0.8F)          // Высокая влажность (идеально для секвой)

                // Визуальные эффекты: Делаем красиво
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .waterColor(4159204)
                        .waterFogColor(329011)
                        .skyColor(0x78A7FF)           // Ясное, чистое голубое небо
                        .fogColor(0xC0D8FF)           // Легкий, свежий утренний туман
                        .grassColorOverride(9219125) // Насыщенный, сочный зеленый цвет травы
                        .foliageColorOverride(9219125)
                        // Добавляем спокойную музыку из тайги для атмосферы
                        .backgroundMusic(Musics.createGameMusic(net.minecraft.sounds.SoundEvents.MUSIC_BIOME_OLD_GROWTH_TAIGA))
                        .build())
                .mobSpawnSettings(spawnBuilder.build())
                .generationSettings(biomeBuilder.build())
                .build();
    }
}
