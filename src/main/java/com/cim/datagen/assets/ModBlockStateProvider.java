package com.cim.datagen.assets;

import com.cim.main.ResourceRegistry;
import com.cim.block.basic.necrosis.hive.HiveRootsBlock;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.client.model.generators.VariantBlockStateBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.cim.main.CrustalIncursionMod;
import com.cim.block.basic.ModBlocks;
import net.minecraft.world.level.block.SlabBlock;

public class ModBlockStateProvider extends BlockStateProvider {

    private final ExistingFileHelper existingFileHelper;

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, CrustalIncursionMod.MOD_ID, exFileHelper);
        this.existingFileHelper = exFileHelper;

        // !!! ВАЖНО: Инициализируем ResourceRegistry перед использованием !!!
        ResourceRegistry.init();
    }

    @Override
    protected void registerStatesAndModels() {
        // Генерация блокстейтов для ресурсных блоков (СНАЧАЛА!)
        for (ResourceRegistry.ResourceEntry resource : ResourceRegistry.getResources()) {
            if (resource.block != null) {
                // Используем имя блока напрямую из типа ресурса
                String blockName = resource.name + "_" + resource.type.blockName;
                ResourceLocation texture = modLoc("block/" + blockName);

                ModelFile model = models().cubeAll(blockName, texture);
                simpleBlock(resource.block.get(), model);
                simpleBlockItem(resource.block.get(), model);
            }
        }
        //СТАТИЧНИЫЕ БЛОКИ

        cubeAllWithItem(ModBlocks.SEQUOIA_BARK);
        cubeAllWithItem(ModBlocks.SEQUOIA_HEARTWOOD);

        cubeAllWithItem(ModBlocks.MORY_BLOCK);
        cubeAllWithItem(ModBlocks.ANTON_CHIGUR);

        cubeAllWithItem(ModBlocks.SEQUOIA_ROOTS);
        cubeAllWithItem(ModBlocks.SEQUOIA_ROOTS_MOSSY);
        cubeAllWithItem(ModBlocks.SEQUOIA_PLANKS);
        cubeAllWithItem(ModBlocks.SEQUOIA_BARK_DARK);
        cubeAllWithItem(ModBlocks.SEQUOIA_BARK_LIGHT);
        cubeAllWithItem(ModBlocks.SEQUOIA_BARK_MOSSY);
        cubeAllWithItem(ModBlocks.SEQUOIA_BIOME_MOSS);
        cubeAllWithItem(ModBlocks.CONGLOMERATE);
        cubeAllWithItem(ModBlocks.DEPLETED_CONGLOMERATE);
        cubeAllWithItem(ModBlocks.DEPTH_WORM_NEST);
        cubeAllWithItem(ModBlocks.HIVE_SOIL);
        cubeAllWithItem(ModBlocks.HIVE_SOIL_DEAD);
        cubeAllWithItem(ModBlocks.DEPTH_WORM_NEST_DEAD);
        cubeAllWithItem(ModBlocks.SWITCH);
        cubeAllWithItem(ModBlocks.CONVERTER_BLOCK);
//        cubeAllWithItem(ModBlocks.GEAR_PORT);
        cubeAllWithItem(ModBlocks.CONCRETE_TILE);
        cubeAllWithItem(ModBlocks.CONCRETE);
        cubeAllWithItem(ModBlocks.CONCRETE_RED);
        cubeAllWithItem(ModBlocks.CONCRETE_BLUE);
        cubeAllWithItem(ModBlocks.CONCRETE_GREEN);
        cubeAllWithItem(ModBlocks.CONCRETE_HAZARD_NEW);
        cubeAllWithItem(ModBlocks.CONCRETE_HAZARD_OLD);
        cubeAllWithItem(ModBlocks.FIREBRICK_BLOCK);
        cubeAllWithItem(ModBlocks.REINFORCEDBRICK_BLOCK);
        cubeAllWithItem(ModBlocks.NECROSIS_TEST);
        cubeAllWithItem(ModBlocks.NECROSIS_TEST2);
        cubeAllWithItem(ModBlocks.NECROSIS_TEST3);
        cubeAllWithItem(ModBlocks.NECROSIS_TEST4);
        cubeAllWithItem(ModBlocks.NECROSIS_PORTAL);
        cubeAllWithItem(ModBlocks.MINERAL_BLOCK2);
        cubeAllWithItem(ModBlocks.MINERAL_TILE);
        cubeAllWithItem(ModBlocks.MINERAL1);
        cubeAllWithItem(ModBlocks.MINERAL_BLOCK1);
        cubeAllWithItem(ModBlocks.DOLOMITE);
        cubeAllWithItem(ModBlocks.TILE_LIGHT);
        cubeAllWithItem(ModBlocks.LIMESTONE);
        cubeAllWithItem(ModBlocks.MINERAL3);
        cubeAllWithItem(ModBlocks.MINERAL2);
        cubeAllWithItem(ModBlocks.BAUXITE);
        cubeAllWithItem(ModBlocks.DOLOMITE_TILE);
        cubeAllWithItem(ModBlocks.DECO_STEEL);
        cubeAllWithItem(ModBlocks.DECO_STEEL_DARK);
        cubeAllWithItem(ModBlocks.DECO_STEEL_SMOG);
        cubeAllWithItem(ModBlocks.DECO_LEAD);
        cubeAllWithItem(ModBlocks.CONCRETE_NET);
        cubeAllWithItem(ModBlocks.DIRT_ROUGH);
        cubeAllWithItem(ModBlocks.BASALT_ROUGH);

        cubeAllWithItem(ModBlocks.CRATE);
        cubeAllWithItem(ModBlocks.CRATE_AMMO);
        cubeAllWithItem(ModBlocks.CONCRETE_REINFORCED);
        cubeAllWithItem(ModBlocks.CONCRETE_REINFORCED_HEAVY);
        cubeAllWithItem(ModBlocks.CONCRETE_STRIPPED);
        cubeAllWithItem(ModBlocks.CONCRETE_TILE_ALT);
        cubeAllWithItem(ModBlocks.CONCRETE_TILE_ALT_BLUE);
        hiveRootsBlock(ModBlocks.HIVE_ROOTS);

        glassBlockWithItem(ModBlocks.CONCRETE_ARMED_GLASS, "concrete_armed_glass", "translucent");


        //СТАТИЧНИЫЕ БЛОКИ У КОТОРЫХ РАЗНОЕ ДНО/ВЕРХ, ПРИМЕР:
       columnBlockWithItem(ModBlocks.WASTE_LOG,
         modLoc("block/waste_log_side"),
         modLoc("block/waste_log_top"),
         modLoc("block/waste_log_top"));


        //СТАТИЧНИЫЕ ПРОЗРАЧНЫЕ БЛОКИ, ПРИМЕР:
        // cutoutBlockWithItem(ModBlocks.REINFORCED_GLASS);
        simpleBlock(ModBlocks.MULTIBLOCK_PART.get(),
                models().withExistingParent(ModBlocks.MULTIBLOCK_PART.getId().getPath(), "minecraft:block/air"));


        //С ПОВОРОТОМ К ИГРОКУ
        horizontalBlockWithItem(ModBlocks.SMALL_SMELTER,
                modLoc("block/smelter_small_side"),
                modLoc("block/smelter_small_back"),
                modLoc("block/smelter_small_side"),
                modLoc("block/smelter_small_top"),
                modLoc("block/smelter_small_bottom")
        );

        horizontalBlockWithItem(ModBlocks.DET_MINER,
                modLoc("block/det_miner_side"),
                modLoc("block/det_miner_front"),
                modLoc("block/det_miner_front"),
                modLoc("block/det_miner_top"),
                modLoc("block/det_miner_top")
        );
        columnBlockWithItem(ModBlocks.DECO_BEAM,
                modLoc("block/deco_beam_side"),
                modLoc("block/deco_beam_top"),
                modLoc("block/deco_beam_top")
        );

        fluidPipeBlock(ModBlocks.BRONZE_FLUID_PIPE, "bronze");
        fluidPipeBlock(ModBlocks.STEEL_FLUID_PIPE, "steel");
        fluidPipeBlock(ModBlocks.LEAD_FLUID_PIPE, "lead");
        fluidPipeBlock(ModBlocks.TUNGSTEN_FLUID_PIPE, "tungsten");
        fluidPipeBlock(ModBlocks.PIPE_SPOTS, "pipe_spots");


        //ПОВОРОТ ДЛЯ 3Д МОДЕЛИ, ПРИМЕР:
        // customModelBlockWithItem(ModBlocks.TURRET_BASE);



        doorBlockWithRenderType((net.minecraft.world.level.block.DoorBlock) ModBlocks.SEQUOIA_DOOR.get(),
                modLoc("block/sequoia_door_bottom"), modLoc("block/sequoia_door_top"), "cutout");

        trapdoorBlockWithRenderType((net.minecraft.world.level.block.TrapDoorBlock) ModBlocks.SEQUOIA_TRAPDOOR.get(),
                modLoc("block/sequoia_trapdoor"), true, "cutout");

        //генерация моделей для валов
        generateAllShafts();
        //генерация моделей для шестеренок
        generateGearBlockModels();
        generatePulleyBlockModels();


        stairsAndSlabs(ModBlocks.CONCRETE_TILE_ALT.get(), ModBlocks.CONCRETE_TILE_ALT_STAIRS.get(), ModBlocks.CONCRETE_TILE_ALT_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_TILE_ALT_BLUE.get(), ModBlocks.CONCRETE_TILE_ALT_BLUE_STAIRS.get(), ModBlocks.CONCRETE_TILE_ALT_BLUE_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_REINFORCED.get(), ModBlocks.CONCRETE_REINFORCED_STAIRS.get(), ModBlocks.CONCRETE_REINFORCED_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_REINFORCED_HEAVY.get(), ModBlocks.CONCRETE_REINFORCED_HEAVY_STAIRS.get(), ModBlocks.CONCRETE_REINFORCED_HEAVY_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_STRIPPED.get(), ModBlocks.CONCRETE_STRIPPED_STAIRS.get(), ModBlocks.CONCRETE_STRIPPED_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_TILE.get(), ModBlocks.CONCRETE_TILE_STAIRS.get(), ModBlocks.CONCRETE_TILE_SLAB.get());
        stairsAndSlabs(ModBlocks.FIREBRICK_BLOCK.get(), ModBlocks.FIREBRICK_STAIRS.get(), ModBlocks.FIREBRICK_SLAB.get());
        stairsAndSlabs(ModBlocks.SEQUOIA_PLANKS.get(), ModBlocks.SEQUOIA_STAIRS.get(), ModBlocks.SEQUOIA_SLAB.get());
        stairsAndSlabs(ModBlocks.REINFORCEDBRICK_BLOCK.get(), ModBlocks.REINFORCEDBRICK_STAIRS.get(), ModBlocks.REINFORCEDBRICK_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE.get(), ModBlocks.CONCRETE_STAIRS.get(), ModBlocks.CONCRETE_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_RED.get(), ModBlocks.CONCRETE_RED_STAIRS.get(), ModBlocks.CONCRETE_RED_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_BLUE.get(), ModBlocks.CONCRETE_BLUE_STAIRS.get(), ModBlocks.CONCRETE_BLUE_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_GREEN.get(), ModBlocks.CONCRETE_GREEN_STAIRS.get(), ModBlocks.CONCRETE_GREEN_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_HAZARD_NEW.get(), ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get(), ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_HAZARD_OLD.get(), ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get(), ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get());

    }

    public void generatePulleyBlockModels() {
        for (RegistryObject<Item> itemObj : com.cim.item.ModItems.ITEMS.getEntries()) {
            if (itemObj.get() instanceof com.cim.item.rotation.PulleyItem pulley) {
                String name = itemObj.getId().getPath();

                ResourceLocation objModel = modLoc("models/block/" + name + ".obj");
                ResourceLocation texture = modLoc("block/" + name);

                // Создаем пустышку JSON, чтобы Flywheel мог на неё сослаться
                models().getBuilder(name)
                        .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                        .modelLocation(objModel)
                        .flipV(true)
                        .end()
                        .texture("pulley_texture", texture);
            }
        }
    }

    public void hiveRootsBlock(RegistryObject<Block> block) {
        getVariantBuilder(block.get()).forAllStates(state -> {
            boolean up = state.getValue(HiveRootsBlock.UP);
            boolean down = state.getValue(HiveRootsBlock.DOWN);
            boolean hanging = state.getValue(HiveRootsBlock.HANGING);

            // Определяем модель ТОЛЬКО на основе конфигурации, без возраста
            String modelName = block.getId().getPath();

            if (hanging) {
                // Висячие корни
                if (!down) {
                    // Конец висящей цепочки (нижний конец)
                    modelName += "_hanging_end";
                } else if (!up) {
                    // Начало висящей цепочки (верхний конец, у опоры)
                    modelName += "_hanging_top";
                } else {
                    // Середина
                    modelName += "_hanging_middle";
                }
            } else {
                // Растущие вверх корни
                if (!up) {
                    // Верхушка (конец)
                    modelName += "_top";
                } else if (!down) {
                    // Низ (у опоры)
                    modelName += "_bottom";
                } else {
                    // Середина
                    modelName += "_middle";
                }
            }

            return ConfiguredModel.builder()
                    .modelFile(models()
                            .cross(modelName, modLoc("block/" + modelName))
                            .renderType("cutout"))
                    .build();
        });

        // Для инвентаря используем нижнюю часть (у опоры)
        simpleBlockItem(block.get(), models()
                .cross(block.getId().getPath() + "_bottom", modLoc("block/" + block.getId().getPath() + "_bottom"))
                .renderType("cutout"));
    }

    // Метод генерации сложных труб (Блокстейт + Модели) с ДИНАМИЧЕСКИМИ ТЕКСТУРАМИ
    public void fluidPipeBlock(RegistryObject<Block> block, String texturePrefix) {
        String name = block.getId().getPath(); // например "copper_fluid_pipe"

        // Путь к нашей картинке, например "cim:block/bronze_pipe"
        ResourceLocation pipeTexture = modLoc("block/" + texturePrefix + "_pipe");

        // ==========================================
        // 1. ГЕНЕРИРУЕМ JSON-ФАЙЛЫ МОДЕЛЕЙ БЛОКА
        // ==========================================
        ModelFile coreModel = models().getBuilder(name + "_core")
                .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                .modelLocation(modLoc("models/block/base_pipe_core.obj")) // <--- ЕДИНЫЙ БАЗОВЫЙ ФАЙЛ
                .flipV(true)
                .end()
                .texture("pipe_texture", pipeTexture) // <--- ПРОКИДЫВАЕМ ТЕКСТУРУ В MTL
                .texture("particle", pipeTexture);    // <--- ПРОКИДЫВАЕМ ЧАСТИЦЫ ДЛЯ РАЗРУШЕНИЯ БЛОКА

        ModelFile armModel = models().getBuilder(name + "_arm")
                .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                .modelLocation(modLoc("models/block/base_pipe.obj")) // <--- ЕДИНЫЙ БАЗОВЫЙ ФАЙЛ
                .flipV(true)
                .end()
                .texture("pipe_texture", pipeTexture)
                .texture("particle", pipeTexture);

        // ==========================================
        // 2. ГЕНЕРИРУЕМ MULTIPART БЛОКСТЕЙТ (КОД ОСТАЛСЯ ПРЕЖНИМ)
        // ==========================================
        var builder = getMultipartBuilder(block.get());

        // Ядро (всегда рендерится)
        builder.part().modelFile(coreModel).addModel().end();

        // Основные 6 рукавов (если есть подключение)
        builder.part().modelFile(armModel).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.NORTH, true).end();
        builder.part().modelFile(armModel).rotationY(90).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.EAST, true).end();
        builder.part().modelFile(armModel).rotationY(180).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.SOUTH, true).end();
        builder.part().modelFile(armModel).rotationY(270).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.WEST, true).end();
        builder.part().modelFile(armModel).rotationX(270).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.UP, true).end();
        builder.part().modelFile(armModel).rotationX(90).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.DOWN, true).end();

        // NONE (когда труба стоит одна - рисуем все 6 рукавов)
        builder.part().modelFile(armModel).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.NONE, true).end();
        builder.part().modelFile(armModel).rotationY(90).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.NONE, true).end();
        builder.part().modelFile(armModel).rotationY(180).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.NONE, true).end();
        builder.part().modelFile(armModel).rotationY(270).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.NONE, true).end();
        builder.part().modelFile(armModel).rotationX(270).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.NONE, true).end();
        builder.part().modelFile(armModel).rotationX(90).addModel().condition(com.cim.block.basic.fluids.FluidPipeBlock.NONE, true).end();

        // ЗАГЛУШКИ (противоположный рукав, если подключена только с одной стороны)
        builder.part().modelFile(armModel).rotationY(180).addModel()
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.NORTH, true).condition(com.cim.block.basic.fluids.FluidPipeBlock.SOUTH, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.EAST, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.WEST, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.UP, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.DOWN, false).end();

        builder.part().modelFile(armModel).addModel()
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.NORTH, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.SOUTH, true)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.EAST, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.WEST, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.UP, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.DOWN, false).end();

        builder.part().modelFile(armModel).rotationY(270).addModel()
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.NORTH, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.SOUTH, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.EAST, true).condition(com.cim.block.basic.fluids.FluidPipeBlock.WEST, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.UP, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.DOWN, false).end();

        builder.part().modelFile(armModel).rotationY(90).addModel()
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.NORTH, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.SOUTH, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.EAST, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.WEST, true)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.UP, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.DOWN, false).end();

        builder.part().modelFile(armModel).rotationX(90).addModel()
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.NORTH, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.SOUTH, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.EAST, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.WEST, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.UP, true).condition(com.cim.block.basic.fluids.FluidPipeBlock.DOWN, false).end();

        builder.part().modelFile(armModel).rotationX(270).addModel()
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.NORTH, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.SOUTH, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.EAST, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.WEST, false)
                .condition(com.cim.block.basic.fluids.FluidPipeBlock.UP, false).condition(com.cim.block.basic.fluids.FluidPipeBlock.DOWN, true).end();

        // ==========================================
        // 3. ГЕНЕРИРУЕМ МОДЕЛЬ ПРЕДМЕТА (ДЛЯ ИНВЕНТАРЯ)
        // ==========================================
        itemModels().getBuilder(name)
                .parent(new net.minecraftforge.client.model.generators.ModelFile.UncheckedModelFile(modLoc("item/pipe_template")))
                .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                .modelLocation(modLoc("models/item/base_pipe_inventory.obj")) // <--- ЕДИНЫЙ БАЗОВЫЙ ФАЙЛ
                .flipV(true)
                .end()
                .texture("pipe_texture", pipeTexture) // <--- ПРОКИДЫВАЕМ ТЕКСТУРУ ДЛЯ ПРЕДМЕТА В ИНВЕНТАРЕ
                .texture("particle", pipeTexture);
    }

    public void generateAllShafts() {
        for (RegistryObject<Block> blockObj : com.cim.block.basic.ModBlocks.BLOCKS.getEntries()) {
            if (blockObj.get() instanceof com.cim.block.basic.industrial.rotation.ShaftBlock shaft) {
                String name = blockObj.getId().getPath();
                ResourceLocation objModel = modLoc("models/block/shaft_" + shaft.getDiameter().name + ".obj");
                ResourceLocation texture = modLoc("block/" + name);

                // 1. ГЕНЕРАЦИЯ МОДЕЛИ БЛОКА
                ModelFile blockModel = models().getBuilder(name)
                        .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                        .modelLocation(objModel)
                        .flipV(true)
                        .end()
                        .texture("shaft_texture", texture)
                        .texture("particle", texture);

                // Генерация блокстейта (с FACING)
                directionalBlock(blockObj.get(), blockModel);

                // 2. ГЕНЕРАЦИЯ МОДЕЛИ ПРЕДМЕТА (ДЛЯ ИНВЕНТАРЯ)
                itemModels().getBuilder(name)
                        // Ссылка на JSON-шаблон с настройками scale, rotation, translation для GUI и рук
                        .parent(new net.minecraftforge.client.model.generators.ModelFile.UncheckedModelFile(modLoc("item/shaft_template")))
                        .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                        // Можешь использовать ту же модель, что и у блока, или сделать отдельную (например, покороче)
                        .modelLocation(objModel)
                        .flipV(true)
                        .end()
                        .texture("shaft_texture", texture)
                        .texture("particle", texture);
            }
        }
    }

    public void generateGearBlockModels() {
        for (RegistryObject<Item> itemObj : com.cim.item.ModItems.ITEMS.getEntries()) {
            if (itemObj.get() instanceof com.cim.item.rotation.GearItem gear) {
                String name = itemObj.getId().getPath();
                int size = gear.getGearSize();

                ResourceLocation objModel = modLoc("models/block/gear" + size + ".obj");
                ResourceLocation texture = modLoc("block/" + name);

                // Генерируем "блочную" модель (без блокстейта), просто чтобы файл существовал
                models().getBuilder(name) // создаст assets/cim/models/block/gear1_steel.json
                        .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                        .modelLocation(objModel)
                        .flipV(true)
                        .end()
                        .texture("gear_texture", texture);
            }
        }
    }

    // Метод для прозрачных блоков (стекло, решётки и т.д.)
// Рендерит все 6 сторон одной текстурой, использует renderType "cutout" или "translucent"
    public void glassBlockWithItem(RegistryObject<Block> block, String textureName, String renderType) {
        String name = block.getId().getPath();
        ResourceLocation texture = modLoc("block/" + textureName);

        ModelFile model = models().cubeAll(name, texture)
                .renderType(renderType);

        simpleBlock(block.get(), model);
        simpleBlockItem(block.get(), model);
    }


    private void resourceBlockWithItem(RegistryObject<Block> blockObject) {
        String registrationName = blockObject.getId().getPath();
        ResourceLocation textureLocation = modLoc("textures/block/" + registrationName + ".png");

        if (!existingFileHelper.exists(textureLocation, net.minecraft.server.packs.PackType.CLIENT_RESOURCES)) {
            CrustalIncursionMod.LOGGER.warn("Texture not found for block {}: {}. Skipping.", registrationName, textureLocation);
            return;
        }

        simpleBlock(blockObject.get(), models().cubeAll(registrationName, modLoc("block/" + registrationName)));
        simpleBlockItem(blockObject.get(), models().getExistingFile(blockTexture(blockObject.get())));
    }

    private void oreWithItem(RegistryObject<Block> blockObject) {
        String registrationName = blockObject.getId().getPath();
        String baseName = registrationName.replace("_ore", "");
        String textureName = "ore_" + baseName;

        simpleBlock(blockObject.get(), models().cubeAll(registrationName, modLoc("block/" + textureName)));
        simpleBlockItem(blockObject.get(), models().getExistingFile(blockTexture(blockObject.get())));
    }

    private void blockWithItem(RegistryObject<Block> blockObject) {
        simpleBlock(blockObject.get());
        simpleBlockItem(blockObject.get(), models().getExistingFile(blockTexture(blockObject.get())));
    }

    private void columnBlockWithItem(RegistryObject<Block> blockObject, ResourceLocation side, ResourceLocation top, ResourceLocation bottom) {
        simpleBlock(blockObject.get(), models().cubeBottomTop(blockObject.getId().getPath(), side, bottom, top));
        simpleBlockItem(blockObject.get(), models().getExistingFile(blockTexture(blockObject.get())));
    }

    private <T extends Block> void customObjBlock(RegistryObject<T> blockObject) {
        horizontalBlock(blockObject.get(), models().getExistingFile(modLoc("block/" + blockObject.getId().getPath())));
    }

    private void orientableBlockWithItem(RegistryObject<Block> blockObject, ResourceLocation side, ResourceLocation front, ResourceLocation top) {
        var model = models().orientable(blockObject.getId().getPath(), side, front, top).texture("particle", front);
        horizontalBlock(blockObject.get(), model);
        simpleBlockItem(blockObject.get(), model);
    }
    public void fullOrientableBlockWithItem(RegistryObject<Block> block, ResourceLocation side, ResourceLocation front, ResourceLocation back, ResourceLocation top, ResourceLocation bottom) {
        String name = block.getId().getPath();

        ModelFile model = models().withExistingParent(name, "minecraft:block/cube")
                .texture("up", top)
                .texture("down", bottom)
                .texture("east", side)
                .texture("west", side)
                .texture("north", front)
                .texture("south", back)
                .texture("particle", side);

        directionalBlock(block.get(), model);

        simpleBlockItem(block.get(), model);
    }


    private void stairsAndSlabs(Block fullBlock, StairBlock stairs, SlabBlock slab) {
        ResourceLocation texture = blockTexture(fullBlock);
        stairsBlock(stairs, texture);
        slabBlock(slab, texture, texture);

        ResourceLocation stairsId = ForgeRegistries.BLOCKS.getKey(stairs);
        ResourceLocation slabId = ForgeRegistries.BLOCKS.getKey(slab);
        if (stairsId != null) {
            simpleBlockItem(stairs, models().getExistingFile(modLoc("block/" + stairsId.getPath())));
        }
        if (slabId != null) {
            simpleBlockItem(slab, models().getExistingFile(modLoc("block/" + slabId.getPath())));
        }
    }


    private void registerSnowLayerBlock(RegistryObject<Block> block, String baseName) {
        ResourceLocation texture = blockTexture(block.get());
        VariantBlockStateBuilder builder = getVariantBuilder(block.get());
        for (int i = 1; i <= 8; i++) {
            ModelFile model;
            if (i == 8) {
                model = models().withExistingParent(baseName + "_height16", mcLoc("block/cube_all")).texture("all", texture).texture("particle", texture);
            } else {
                String parentName = "block/snow_height" + (i * 2);
                model = models().withExistingParent(baseName + "_height" + (i * 2), mcLoc(parentName)).texture("texture", texture).texture("particle", texture);
            }
            builder.partialState().with(SnowLayerBlock.LAYERS, i).modelForState().modelFile(model).addModel();
        }
        simpleBlockItem(block.get(), models().withExistingParent(baseName + "_inventory", mcLoc("block/snow_height2")).texture("texture", texture).texture("particle", texture));
    }


    // 1. Улучшенный метод для горизонтально-поворачиваемых блоков (печи, батареи)
    public void horizontalBlockWithItem(RegistryObject<Block> block, ResourceLocation side, ResourceLocation front, ResourceLocation back, ResourceLocation top, ResourceLocation bottom) {
        String name = block.getId().getPath();

        // Используем "minecraft:block/cube" - это база, она есть всегда
        ModelFile model = models().withExistingParent(name, "minecraft:block/cube")
                .texture("up", top)
                .texture("down", bottom)
                .texture("east", side)
                .texture("west", side)
                .texture("north", front) // Лицо
                .texture("south", back)  // Зад
                .texture("particle", side);

        // Важно: horizontalBlock сам покрутит эту модель,
        // сопоставляя текстуры north/south с направлением FACING
        horizontalBlock(block.get(), model);
        simpleBlockItem(block.get(), model);
    }

    // 2. Метод для блоков, которые могут вращаться во всех 3 плоскостях (по 6 сторонам)
    // Подойдет, если батарея может висеть на потолке или стене
    public void directionalBlockWithItem(RegistryObject<Block> block, ModelFile model) {
        directionalBlock(block.get(), model);
        simpleBlockItem(block.get(), model);
    }

    // 3. Быстрый метод для "простых" кубов с предметом
    // Использует одну текстуру для всех сторон
    public void cubeAllWithItem(RegistryObject<Block> block) {
        simpleBlockWithItem(block.get(), cubeAll(block.get()));
    }

    // 4. Метод для прозрачных блоков (стекло, решетки) с поддержкой Cutout
    public void cutoutBlockWithItem(RegistryObject<Block> block) {
        ModelFile model = models().cubeAll(block.getId().getPath(), blockTexture(block.get())).renderType("cutout");
        simpleBlockWithItem(block.get(), model);
    }

    // Измени с private на public
    public void simpleBlockWithItem(Block block, ModelFile model) {
        simpleBlock(block, model);
        simpleBlockItem(block, model);
    }

    // Для FullOBlock (вращение во все 6 сторон: Up, Down, North, South, East, West)
    public void fullBlockWithItem(RegistryObject<Block> block, ResourceLocation side, ResourceLocation front, ResourceLocation top) {
        // Создаем модель с "лицом" (front)
        ModelFile model = models().orientable(block.getId().getPath(), side, front, top);

        // directionalBlock сам привяжет модель к свойству FACING и пропишет углы X и Y в JSON
        directionalBlock(block.get(), model);
        simpleBlockItem(block.get(), model);
    }

    // Для SideOBlock (только горизонтальное вращение)
    public void sideBlockWithItem(RegistryObject<Block> block, ResourceLocation side, ResourceLocation front, ResourceLocation top) {
        ModelFile model = models().orientable(block.getId().getPath(), side, front, top);

        // horizontalBlock привяжет модель к HORIZONTAL_FACING (только повороты по Y)
        horizontalBlock(block.get(), model);
        simpleBlockItem(block.get(), model);
    }

    private void doorBlockWithItem(RegistryObject<? extends DoorBlock> door, ResourceLocation bottom, ResourceLocation top) {
        doorBlock(door.get(), bottom, top);
        ModelFile itemModel = models().withExistingParent(
                door.getId().getPath(),           // имя модели предмета
                modLoc("block/" + door.getId().getPath()) // родитель — модель блока
        );
        simpleBlockItem(door.get(), itemModel);
    }

    private void trapdoorBlockWithItem(RegistryObject<? extends TrapDoorBlock> trapdoor, ResourceLocation texture, boolean orientable) {
        trapdoorBlock(trapdoor.get(), texture, orientable);
        ModelFile itemModel = models().withExistingParent(
                trapdoor.getId().getPath(),
                modLoc("block/" + trapdoor.getId().getPath())
        );
        simpleBlockItem(trapdoor.get(), itemModel);
    }

}