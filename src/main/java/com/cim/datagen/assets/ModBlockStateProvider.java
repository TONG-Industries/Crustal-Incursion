package com.cim.datagen.assets;

import com.cim.block.basic.necrosis.hive.HiveRootsBlock;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
    }

    @Override
    protected void registerStatesAndModels() {

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

        cubeAllWithItem(ModBlocks.DEPTH_WORM_NEST);
        cubeAllWithItem(ModBlocks.HIVE_SOIL);
        cubeAllWithItem(ModBlocks.HIVE_SOIL_DEAD);
        cubeAllWithItem(ModBlocks.DEPTH_WORM_NEST_DEAD);
        cubeAllWithItem(ModBlocks.SWITCH);
        cubeAllWithItem(ModBlocks.CONVERTER_BLOCK);
        cubeAllWithItem(ModBlocks.GEAR_PORT);
        cubeAllWithItem(ModBlocks.CONCRETE);
        cubeAllWithItem(ModBlocks.CONCRETE_RED);
        cubeAllWithItem(ModBlocks.CONCRETE_BLUE);
        cubeAllWithItem(ModBlocks.CONCRETE_GREEN);
        cubeAllWithItem(ModBlocks.CONCRETE_HAZARD_NEW);
        cubeAllWithItem(ModBlocks.CONCRETE_HAZARD_OLD);

        cubeAllWithItem(ModBlocks.NECROSIS_TEST);
        cubeAllWithItem(ModBlocks.NECROSIS_TEST2);
        cubeAllWithItem(ModBlocks.NECROSIS_TEST3);
        cubeAllWithItem(ModBlocks.NECROSIS_TEST4);
        cubeAllWithItem(ModBlocks.NECROSIS_PORTAL);

        cubeAllWithItem(ModBlocks.DECO_STEEL);
        cubeAllWithItem(ModBlocks.DECO_STEEL_DARK);
        cubeAllWithItem(ModBlocks.DECO_STEEL_SMOG);
        cubeAllWithItem(ModBlocks.DECO_LEAD);

        cubeAllWithItem(ModBlocks.DIRT_ROUGH);
        cubeAllWithItem(ModBlocks.BASALT_ROUGH);
        cubeAllWithItem(ModBlocks.MORY_BLOCK);

        cubeAllWithItem(ModBlocks.CRATE);
        cubeAllWithItem(ModBlocks.CRATE_AMMO);
        simpleBlockWithItem(ModBlocks.WIRE_COATED.get(), models().getExistingFile(modLoc("block/wire_coated")));

        hiveRootsBlock(ModBlocks.HIVE_ROOTS);

        //СТАТИЧНИЫЕ БЛОКИ У КОТОРЫХ РАЗНОЕ ДНО/ВЕРХ, ПРИМЕР:
       columnBlockWithItem(ModBlocks.WASTE_LOG,
         modLoc("block/waste_log_side"),
         modLoc("block/waste_log_top"),
         modLoc("block/waste_log_top"));


        //СТАТИЧНИЫЕ ПРОЗРАЧНЫЕ БЛОКИ, ПРИМЕР:
        // cutoutBlockWithItem(ModBlocks.REINFORCED_GLASS);


        //С ПОВОРОТОМ К ИГРОКУ
        fullOrientableBlockWithItem(ModBlocks.DET_MINER,
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


        //ПОВОРОТ ДЛЯ 3Д МОДЕЛИ, ПРИМЕР:
        // customModelBlockWithItem(ModBlocks.TURRET_BASE);



        doorBlockWithRenderType((net.minecraft.world.level.block.DoorBlock) ModBlocks.SEQUOIA_DOOR.get(),
                modLoc("block/sequoia_door_bottom"), modLoc("block/sequoia_door_top"), "cutout");

        trapdoorBlockWithRenderType((net.minecraft.world.level.block.TrapDoorBlock) ModBlocks.SEQUOIA_TRAPDOOR.get(),
                modLoc("block/sequoia_trapdoor"), true, "cutout");


        stairsAndSlabs(ModBlocks.CONCRETE.get(), ModBlocks.CONCRETE_STAIRS.get(), ModBlocks.CONCRETE_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_RED.get(), ModBlocks.CONCRETE_RED_STAIRS.get(), ModBlocks.CONCRETE_RED_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_BLUE.get(), ModBlocks.CONCRETE_BLUE_STAIRS.get(), ModBlocks.CONCRETE_BLUE_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_GREEN.get(), ModBlocks.CONCRETE_GREEN_STAIRS.get(), ModBlocks.CONCRETE_GREEN_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_HAZARD_NEW.get(), ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get(), ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get());
        stairsAndSlabs(ModBlocks.CONCRETE_HAZARD_OLD.get(), ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get(), ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get());

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

            // УБРАНО: добавление возраста к имени модели
            // if (age > 0) {
            //     modelName += "_age" + age;
            // }

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
    // Генерирует модель и прописывает вращение в блокстейт
    public void horizontalBlockWithItem(RegistryObject<Block> block, ResourceLocation side, ResourceLocation front, ResourceLocation top) {
        String name = block.getId().getPath();
        ModelFile model = models().orientable(name, side, front, top);

        // Создает 4 варианта вращения (North, South, East, West)
        horizontalBlock(block.get(), model);
        // Создает модель предмета на основе этой же модели
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