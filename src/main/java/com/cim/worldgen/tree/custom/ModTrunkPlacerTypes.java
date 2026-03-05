package com.cim.worldgen.tree.custom;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import com.cim.main.CrustalIncursionMod;

public class ModTrunkPlacerTypes {

    // 1. ЕДИНСТВЕННЫЙ реестр для всех типов стволов
    public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACER_TYPES =
            DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, CrustalIncursionMod.MOD_ID);

    // 2. Регистрируем гигантскую секвойю
    public static final RegistryObject<TrunkPlacerType<GiantSequoiaTrunkPlacer>> GIANT_SEQUOIA_TRUNK_PLACER =
            TRUNK_PLACER_TYPES.register("giant_sequoia_trunk_placer",
                    () -> new TrunkPlacerType<>(GiantSequoiaTrunkPlacer.CODEC));

    // 3. Регистрируем маленькую секвойю в ЭТОТ ЖЕ реестр
    public static final RegistryObject<TrunkPlacerType<MiniSequoiaTrunkPlacer>> MINI_SEQUOIA_TRUNK =
            TRUNK_PLACER_TYPES.register("mini_sequoia_trunk",
                    () -> new TrunkPlacerType<>(MiniSequoiaTrunkPlacer.CODEC));

    public static final RegistryObject<TrunkPlacerType<MediumSequoiaTrunkPlacer>> MEDIUM_SEQUOIA_TRUNK =
            TRUNK_PLACER_TYPES.register("medium_sequoia_trunk",
                    () -> new TrunkPlacerType<>(MediumSequoiaTrunkPlacer.CODEC));

    // 4. Метод для подключения к главной шине
    public static void register(IEventBus eventBus) {
        TRUNK_PLACER_TYPES.register(eventBus);
    }
}