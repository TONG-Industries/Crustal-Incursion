package com.cim.client.render.flywheel;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class ModModels {
    // Укажи здесь путь к твоей JSON-модели вала
    // "cim" - это твой modid, "block/shaft" - путь к файлу assets/cim/models/block/shaft.json
    public static final PartialModel SHAFT_MODEL = PartialModel.of(new ResourceLocation("cim", "block/shaft"));
    public static final PartialModel MOTOR_BASE = PartialModel.of(new ResourceLocation("cim", "block/electro_motor"));
    public static final PartialModel HALF_SHAFT = PartialModel.of(new ResourceLocation("cim", "block/half_shaft"));
    public static void init() {
        // Этот пустой метод нужен просто для того, чтобы класс загрузился
        // и статичные переменные инициализировались в нужный момент
    }
}
