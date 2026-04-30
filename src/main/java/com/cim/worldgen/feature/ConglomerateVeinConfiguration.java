package com.cim.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

public record ConglomerateVeinConfiguration(
        int minSize,
        int maxSize,
        int minY,
        int maxY,
        float density,
        float depletionChance
) implements FeatureConfiguration {

    public static final Codec<ConglomerateVeinConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("min_size").forGetter(ConglomerateVeinConfiguration::minSize),
            Codec.INT.fieldOf("max_size").forGetter(ConglomerateVeinConfiguration::maxSize),
            Codec.INT.fieldOf("min_y").forGetter(ConglomerateVeinConfiguration::minY),
            Codec.INT.fieldOf("max_y").forGetter(ConglomerateVeinConfiguration::maxY),
            Codec.FLOAT.fieldOf("density").forGetter(ConglomerateVeinConfiguration::density),
            Codec.FLOAT.fieldOf("depletion_chance").forGetter(ConglomerateVeinConfiguration::depletionChance)
    ).apply(instance, ConglomerateVeinConfiguration::new));
}