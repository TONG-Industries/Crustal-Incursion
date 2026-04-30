package com.cim.datagen.assets;

import com.cim.block.basic.ModBlocks;
import com.cim.datagen.ResourceDatagenHelper;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;
import com.cim.main.CrustalIncursionMod;
import com.cim.item.ModItems;
import com.cim.main.ResourceRegistry;

import java.util.LinkedHashMap;

public class ModItemModelProvider extends ItemModelProvider {

    private static LinkedHashMap<ResourceKey<TrimMaterial>, Float> trimMaterials = new LinkedHashMap<>();
    static {
        trimMaterials.put(TrimMaterials.QUARTZ, 0.1F);
        trimMaterials.put(TrimMaterials.IRON, 0.2F);
        trimMaterials.put(TrimMaterials.NETHERITE, 0.3F);
        trimMaterials.put(TrimMaterials.REDSTONE, 0.4F);
        trimMaterials.put(TrimMaterials.COPPER, 0.5F);
        trimMaterials.put(TrimMaterials.GOLD, 0.6F);
        trimMaterials.put(TrimMaterials.EMERALD, 0.7F);
        trimMaterials.put(TrimMaterials.DIAMOND, 0.8F);
        trimMaterials.put(TrimMaterials.LAPIS, 0.9F);
        trimMaterials.put(TrimMaterials.AMETHYST, 1.0F);
    }

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, CrustalIncursionMod.MOD_ID, existingFileHelper);

        // !!! ВАЖНО: Инициализируем ResourceRegistry перед использованием !!!
        ResourceRegistry.init();
    }

    @Override
    protected void registerModels() {
        // Автогенерация моделей для ресурсов (СНАЧАЛА!)
        ResourceDatagenHelper.generateItemModels(this);
        simpleItem(ModItems.SCREWDRIVER);
        simpleItem(ModItems.CROWBAR);
        simpleItem(ModItems.CAST_PICKAXE_STEEL_BASE);
        simpleItem(ModItems.CAST_PICKAXE_IRON_BASE);
        simpleItem(ModItems.WOODEN_HANDLE);
        simpleItem(ModItems.MOLD_PICKAXE);
        simpleItem(ModItems.MOLD_EMPTY);
        simpleItem(ModItems.ROPE);
        simpleItem(ModItems.LIQUID_METAL);
        simpleItem(ModItems.RANGE_DETONATOR);
        simpleItem(ModItems.DEPTH_WORM_SPAWN_EGG);
        simpleItem(ModItems.DEPTH_WORM_BRUTAL_SPAWN_EGG);
        simpleItem(ModItems.DETONATOR);
        simpleItem(ModItems.MULTI_DETONATOR);
        simpleItem(ModItems.HARD_ROCK);
        simpleItem(ModItems.CONGLOMERATE_CHUNK);
        simpleItem(ModItems.CREATIVE_BATTERY);
        simpleItem(ModItems.BATTERY);
        simpleItem(ModItems.POKER);
        simpleItem(ModItems.BATTERY_ADVANCED);
        simpleItem(ModItems.BATTERY_LITHIUM);
        simpleItem(ModItems.BATTERY_TRIXITE);
        simpleItem(ModItems.DOLOMITE_SMES);
        simpleItem(ModItems.FIRE_SMES);
        simpleItem(ModItems.WIRE_COIL);
        simpleItem(ModItems.LIMESTONE_CHUNK);
        simpleItem(ModItems.LIMESTONE_POWDER);
        simpleItem(ModItems.BEAM_PLACER);
        simpleItem(ModItems.BAUXITE_CHUNK);
        simpleItem(ModItems.BAUXITE_POWDER);
        simpleItem(ModItems.DOLOMITE_CHUNK);
        simpleItem(ModItems.DOLOMITE_POWDER);
        simpleItem(ModItems.PROTECTOR_LEAD);
        simpleItem(ModItems.PROTECTOR_STEEL);
        simpleItem(ModItems.PROTECTOR_TUNGSTEN);
        simpleItem(ModItems.SLAG);
        simpleItem(ModItems.TURRET_CHIP);
        simpleItem(ModItems.TURRET_LIGHT_PORTATIVE_PLACER);

        simpleItem(ModItems.GRENADE);
        simpleItem(ModItems.GRENADESMART);
        simpleItem(ModItems.GRENADESLIME);
        simpleItem(ModItems.GRENADEHE);
        simpleItem(ModItems.GRENADEFIRE);

        simpleItem(ModItems.MOLD_INGOT);
        simpleItem(ModItems.BELT);

        simpleItem(ModItems.GRENADE_NUC);
        simpleItem(ModItems.GRENADE_IF_HE);
        simpleItem(ModItems.GRENADE_IF_FIRE);
        simpleItem(ModItems.GRENADE_IF_SLIME);
        simpleItem(ModItems.GRENADE_IF);
        simpleItem(ModItems.MORY_FOOD);
        simpleItem(ModItems.COFFEE);
        simpleItem(ModItems.MORY_LAH);
        simpleItem(ModItems.MOLD_BLOCK);
        simpleItem(ModItems.MOLD_NUGGET);
        simpleItem(ModItems.FIREBRICK);
        simpleItem(ModItems.REINFORCEDBRICK);
        simpleItem(ModItems.INFINITE_FLUID_BARREL);
        simpleBlockItem(ModBlocks.CONNECTOR);
        simpleBlockItem(ModBlocks.MEDIUM_CONNECTOR);
        simpleBlockItem(ModBlocks.LARGE_CONNECTOR);
        simpleItem(ModItems.FUEL_ASH);
        complexBlockItem(ModBlocks.FLUID_BARREL);
        complexBlockItem(ModBlocks.BEARING_BLOCK);

        generateAllGears();
        generateAllPulleys();
        // Пример регистрации блоков как предметов (если это обычный куб)
        // complexBlockItem(ModBlocks.NECROTIC_ORE);

        // Для дверей (плоские иконки как в ванилле)
        // doorItem(ModBlocks.METAL_DOOR);
    }

    public void generateAllPulleys() {
        for (RegistryObject<Item> itemObj : com.cim.item.ModItems.ITEMS.getEntries()) {
            if (itemObj.get() instanceof com.cim.item.rotation.PulleyItem pulley) {
                String name = itemObj.getId().getPath(); // должно быть "pulley"

                ResourceLocation objModel = modLoc("models/block/" + name + ".obj");
                ResourceLocation texture = modLoc("block/" + name); // берет текстуру из assets/cim/textures/block/pulley.png

                getBuilder(name)
                        .parent(new net.minecraftforge.client.model.generators.ModelFile.UncheckedModelFile(modLoc("item/pulley_template")))
                        .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                        .modelLocation(objModel)
                        .flipV(true)
                        .end()
                        // ВАЖНО: Имя "pulley_texture" должно быть в pulley.mtl!
                        .texture("pulley_texture", texture)
                        .texture("particle", texture);
            }
        }
    }

    public void generateAllGears() {
        for (RegistryObject<Item> itemObj : com.cim.item.ModItems.ITEMS.getEntries()) {
            // Проверяем, что это именно шестерня
            if (itemObj.get() instanceof com.cim.item.rotation.GearItem gear) {
                String name = itemObj.getId().getPath(); // например, gear1_steel
                int size = gear.getGearSize();

                // Путь к OBJ модели зависит только от РАЗМЕРА
                ResourceLocation objModel = modLoc("models/block/gear" + size + ".obj");
                // Путь к текстуре совпадает с именем регистрации
                ResourceLocation texture = modLoc("block/" + name);

                // ГЕНЕРАЦИЯ МОДЕЛИ ПРЕДМЕТА
                getBuilder(name)
                        .parent(new net.minecraftforge.client.model.generators.ModelFile.UncheckedModelFile(modLoc("item/gear_template")))
                        .customLoader(net.minecraftforge.client.model.generators.loaders.ObjModelBuilder::begin)
                        .modelLocation(objModel)
                        .flipV(true)
                        .end()
                        .texture("gear_texture", texture)
                        .texture("particle", texture);
            }
        }
    }

    private ItemModelBuilder simpleItem(RegistryObject<Item> item) {
        return withExistingParent(item.getId().getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                new ResourceLocation(CrustalIncursionMod.MOD_ID, "item/" + item.getId().getPath()));
    }

    private ItemModelBuilder simpleBlockItem(RegistryObject<Block> block) {
        return withExistingParent(block.getId().getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                new ResourceLocation(CrustalIncursionMod.MOD_ID, "item/" + block.getId().getPath()));
    }

    // Если предмет должен выглядеть как 3D блок (например, руда)
    private ItemModelBuilder complexBlockItem(RegistryObject<Block> block) {
        return withExistingParent(block.getId().getPath(),
                new ResourceLocation(CrustalIncursionMod.MOD_ID, "block/" + block.getId().getPath()));
    }

    private ItemModelBuilder doorItem(RegistryObject<Block> block) {
        return withExistingParent(block.getId().getPath(),
                new ResourceLocation("item/generated")).texture("layer0",
                new ResourceLocation(CrustalIncursionMod.MOD_ID, "item/" + block.getId().getPath()));
    }
}