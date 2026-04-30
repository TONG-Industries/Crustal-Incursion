package com.cim.worldgen.feature;

import com.cim.main.CrustalIncursionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, CrustalIncursionMod.MOD_ID);

    public static final RegistryObject<Feature<ConglomerateVeinConfiguration>> CONGLOMERATE_VEIN =
            FEATURES.register("conglomerate_vein",
                    () -> new ConglomerateVeinFeature(ConglomerateVeinConfiguration.CODEC));

    public static final RegistryObject<Feature<SpecialVeinConfiguration>> SPECIAL_VEIN =
            FEATURES.register("special_vein",
                    () -> new SpecialVeinFeature(SpecialVeinConfiguration.CODEC));
}