package com.cim.worldgen.biome.terrablender;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.ParameterUtils;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.VanillaParameterOverlayBuilder;
import com.cim.worldgen.biome.ModBiomes; // Импорт твоего ключа биома

import java.util.function.Consumer;

public class ModOverworldRegion extends Region {

    // Вес региона. Чем больше число, тем чаще этот регион будет "перехватывать" генерацию у ванилы
    public ModOverworldRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        VanillaParameterOverlayBuilder builder = new VanillaParameterOverlayBuilder();

        // Задаем климат для Рощи Секвой: Расширяем рамки для гигантов!
        new ParameterUtils.ParameterPointListBuilder()
                // --- ИЗМЕНЕНИЕ 1: ТЕМПЕРАТУРА ---
                // Расширяем от ICY (холод в небесах) до NEUTRAL (на земле)
                .temperature(ParameterUtils.Temperature.span(ParameterUtils.Temperature.ICY, ParameterUtils.Temperature.NEUTRAL))

                .humidity(ParameterUtils.Humidity.span(ParameterUtils.Humidity.WET, ParameterUtils.Humidity.HUMID))
                .continentalness(ParameterUtils.Continentalness.span(ParameterUtils.Continentalness.INLAND, ParameterUtils.Continentalness.FAR_INLAND))
                .erosion(ParameterUtils.Erosion.span(ParameterUtils.Erosion.EROSION_0, ParameterUtils.Erosion.EROSION_1))

                // --- ИЗМЕНЕНИЕ 2: ГЛУБИНА ---
                // Делаем биом сплошным от бедрока (UNDERGROUND) до небес (SURFACE)
                .depth(Climate.Parameter.span(-1.5F, 0.25F))
                .weirdness(ParameterUtils.Weirdness.span(ParameterUtils.Weirdness.MID_SLICE_NORMAL_ASCENDING, ParameterUtils.Weirdness.HIGH_SLICE_NORMAL_DESCENDING))
                .build().forEach(point -> builder.add(point, ModBiomes.SEQUOIA_GROVE));

        // Применяем наши правила
        builder.build().forEach(mapper);
    }
}
