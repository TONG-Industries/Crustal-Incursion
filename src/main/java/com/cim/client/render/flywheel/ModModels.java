package com.cim.client.render.flywheel;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class ModModels {
    // Теперь мы используем String ключи (например "gear1_steel" или "shaft_iron")
    public static final Map<String, PartialModel> GEAR_MODELS = new HashMap<>();
    public static final Map<String, PartialModel> SHAFT_MODELS = new HashMap<>();

    public static final PartialModel MOTOR_BASE = PartialModel.of(new ResourceLocation("cim", "block/electro_motor"));
    public static final PartialModel HALF_SHAFT = PartialModel.of(new ResourceLocation("cim", "block/half_shaft"));
    public static final PartialModel BEARING_INNER_RING = PartialModel.of(new ResourceLocation("cim", "block/bearing_shaft"));
    public static final PartialModel BEARING = PartialModel.of(new ResourceLocation("cim", "block/bearing"));
    public static final Map<String, PartialModel> PULLEY_MODELS = new HashMap<>();
    public static final PartialModel BELT_SEGMENT = PartialModel.of(new ResourceLocation("cim", "block/belt_segment"));

    // Статический блок вызывается самым первым, как только Java видит этот класс!
    // Flywheel 100% получит эти модели вовремя.
    static {
        String[] materials = {"iron", "duralumin", "steel", "titanium", "tungsten_carbide"};

        // 1. Загружаем все 15 шестерней
        int[] gearSizes = {1, 2, 3};
        for (int size : gearSizes) {
            for (String mat : materials) {
                String name = "gear" + size + "_" + mat; // Получится "gear1_steel"
                GEAR_MODELS.put(name, PartialModel.of(new ResourceLocation("cim", "block/" + name)));
            }
        }

        // 2. Загружаем все валы
        // ВНИМАНИЕ: Проверь, как именно у тебя называются ID валов в регистрации.
        // Если они называются "shaft_light_iron", оставь так. Если "iron_shaft" - поменяй логику склейки строки.
        String[] diameters = {"light", "medium", "heavy"};
        for (String dia : diameters) {
            for (String mat : materials) {
                String name = "shaft_" + dia + "_" + mat; // Проверь правильность названия!
                SHAFT_MODELS.put(name, PartialModel.of(new ResourceLocation("cim", "block/" + name)));
            }
        }

        PULLEY_MODELS.put("pulley", PartialModel.of(new ResourceLocation("cim", "block/pulley")));
    }

    // Оставляем пустым. Этот метод просто служит "спусковым крючком",
    // чтобы заставить игру прочитать класс ModModels при запуске.
    public static void init() {}
}