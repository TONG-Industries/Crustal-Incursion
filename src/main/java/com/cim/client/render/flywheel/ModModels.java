package com.cim.client.render.flywheel;

import com.cim.block.basic.ModBlocks;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public class ModModels {
    // Храним связи: Блок Вала -> Его PartialModel
    public static final Map<Block, PartialModel> SHAFT_MODELS = new HashMap<>();

    // Оставляем твои старые модели мотора
    public static final PartialModel MOTOR_BASE = PartialModel.of(new ResourceLocation("cim", "block/electro_motor"));
    public static final PartialModel HALF_SHAFT = PartialModel.of(new ResourceLocation("cim", "block/half_shaft"));

    public static void init() {
        // Динамически регистрируем модели для всех валов в игре
        for (RegistryObject<Block> blockObj : ModBlocks.BLOCKS.getEntries()) {
            if (blockObj.get() instanceof ShaftBlock) {
                ResourceLocation id = blockObj.getId();
                // Создаем PartialModel, ссылаясь на JSON, который сгенерирует DataGen
                SHAFT_MODELS.put(blockObj.get(), PartialModel.of(new ResourceLocation(id.getNamespace(), "block/" + id.getPath())));
            }
        }
    }
}
