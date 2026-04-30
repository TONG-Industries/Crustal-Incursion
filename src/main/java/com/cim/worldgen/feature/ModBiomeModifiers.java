package com.cim.worldgen.feature;

import com.cim.main.CrustalIncursionMod;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ForgeBiomeModifiers;
import net.minecraftforge.registries.ForgeRegistries;

public class ModBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ADD_CONGLOMERATE_VEIN =
            ResourceKey.create(ForgeRegistries.Keys.BIOME_MODIFIERS,
                    new ResourceLocation(CrustalIncursionMod.MOD_ID, "add_conglomerate_vein"));

    public static void bootstrap(BootstapContext<BiomeModifier> context) {
        var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        var biomes = context.lookup(Registries.BIOME);

        // === КОНГЛОМЕРАТЫ ===
        for (OreVeinRegistry.ConglomerateEntry entry : OreVeinRegistry.CONGLOMERATES) {
            context.register(entry.biomeModifierKey,
                    new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                            biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                            HolderSet.direct(placedFeatures.getOrThrow(entry.placedKey)),
                            GenerationStep.Decoration.UNDERGROUND_ORES
                    ));
        }

        // === АВТО-ДОБАВЛЕНИЕ РУД В БИОМЫ ===
        for (OreVeinRegistry.OreEntry ore : OreVeinRegistry.ORES) {
            context.register(ore.biomeModifierKey,
                    new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                            biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                            HolderSet.direct(placedFeatures.getOrThrow(ore.placedKey)),
                            GenerationStep.Decoration.UNDERGROUND_ORES
                    ));
        }

        // === СПЕЦ-ЖИЛЫ в биомы ===
        for (OreVeinRegistry.SpecialOreEntry ore : OreVeinRegistry.SPECIAL_ORES) {
            context.register(ore.biomeModifierKey,
                    new ForgeBiomeModifiers.AddFeaturesBiomeModifier(
                            biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                            HolderSet.direct(placedFeatures.getOrThrow(ore.placedKey)),
                            GenerationStep.Decoration.UNDERGROUND_ORES
                    ));
        }
    }
}