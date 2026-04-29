package com.cim.api.metallurgy;

import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.api.metallurgy.system.recipe.AlloyRecipe;
import com.cim.api.metallurgy.system.recipe.AlloySlot;
import com.cim.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.cim.item.ModItems;
import com.cim.main.ResourceRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class ModMetallurgy {

    public static void init() {

        // === ЗОЛОТО ===
        Metal gold = registerMetalWithItems("gold", 0xffac2a, 1064, 0.5f,
                Items.GOLD_INGOT, Items.GOLD_NUGGET, Blocks.GOLD_BLOCK);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLD_ORE, gold, 9, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.DEEPSLATE_GOLD_ORE, gold, 9, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.RAW_GOLD, gold, 9, 958, 0.9f, 80);
        MetallurgyRegistry.addSmeltRecipe(Items.RAW_GOLD_BLOCK, gold, 81, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_PICKAXE, gold, 27, 1064, 0.7f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_AXE, gold, 27, 1064, 0.7f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_SHOVEL, gold, 9, 1064, 0.5f, 50);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_HOE, gold, 18, 1064, 0.6f, 70);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_SWORD, gold, 9, 1064, 0.8f, 80);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_HELMET, gold, 18, 1064, 0.6f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_CHESTPLATE, gold, 45, 1064, 0.7f, 120);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_LEGGINGS, gold, 36, 1064, 0.6f, 110);
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_BOOTS, gold, 18, 1064, 0.5f, 80);

        // === ЖЕЛЕЗО ===
        Metal iron = registerMetalWithItems("iron", 0xba826c, 958, 0.8f,
                Items.IRON_INGOT, Items.IRON_NUGGET, Blocks.IRON_BLOCK);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_ORE, iron, 9, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.DEEPSLATE_IRON_ORE, iron, 9, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.RAW_IRON, iron, 9, 958, 0.9f, 80);
        MetallurgyRegistry.addSmeltRecipe(Items.RAW_IRON_BLOCK, iron, 81, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_SWORD, iron, 9, 958, 0.8f, 60);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_PICKAXE, iron, 27, 958, 0.8f, 120);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_AXE, iron, 27, 958, 0.8f, 120);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_SHOVEL, iron, 9, 958, 0.6f, 60);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_HOE, iron, 18, 958, 0.7f, 80);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_HELMET, iron, 45, 958, 0.8f, 150);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_CHESTPLATE, iron, 72, 958, 0.9f, 180);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_LEGGINGS, iron, 63, 958, 0.9f, 160);
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_BOOTS, iron, 36, 958, 0.7f, 100);
        MetallurgyRegistry.addSmeltRecipe(ModItems.CAST_PICKAXE_IRON_BASE.get(), iron, 45, 958, 0.8f, 180);
        // === МЕДЬ ===
        Metal copper = registerMetalWithItems("copper", 0xc15a36, 1085, 0.6f,
                Items.COPPER_INGOT, null, Blocks.COPPER_BLOCK);
        MetallurgyRegistry.addSmeltRecipe(Items.COPPER_ORE, copper, 9, 1085, 0.8f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.DEEPSLATE_COPPER_ORE, copper, 9, 1085, 0.8f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.RAW_COPPER, copper, 9, 1085, 0.7f, 80);
        MetallurgyRegistry.addSmeltRecipe(Items.RAW_COPPER_BLOCK, copper, 81, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.LIGHTNING_ROD, copper, 27, 958, 1.0f, 50);

        // === НЕЗЕРИТ ===
        // Потребление: 2.0 градуса/тик, плавится при 1200°C (очень требовательный!)
        Metal netherite = registerMetalWithItems("netherite", 0x4a2940, 1200, 2.0f,
                Items.NETHERITE_INGOT, null, Blocks.NETHERITE_BLOCK);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_PICKAXE, netherite, 27, 1200, 1.8f, 200);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_AXE, netherite, 27, 1200, 1.8f, 200);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_SHOVEL, netherite, 9, 1200, 1.5f, 120);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_HOE, netherite, 18, 1200, 1.6f, 150);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_HELMET, netherite, 45, 1200, 1.7f, 220);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_CHESTPLATE, netherite, 72, 1200, 2.0f, 260);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_LEGGINGS, netherite, 63, 1200, 1.9f, 240);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_BOOTS, netherite, 36, 1200, 1.6f, 180);
        MetallurgyRegistry.addSmeltRecipe(Items.NETHERITE_SWORD, netherite, 18, 1200, 1.6f, 150);

        // === СТАЛЬ ===
        Metal steel = registerMetalWithItems("steel", 0x202321, 1440, 1.2f,
                ResourceRegistry.getMainUnit("steel"),
                ResourceRegistry.getSmallUnit("steel"),
                ResourceRegistry.getBlock("steel"));
        // Сплав: 2 слитка железа + 2 угля = 18 единиц стали (2 слитка)
        // Время: 300 тиков (15 сек), потребление: 1.5/тик
        AlloyRecipe steelAlloy = new AlloyRecipe(
                new AlloySlot[]{
                        new AlloySlot(Items.IRON_INGOT, 2),
                        new AlloySlot(Items.COAL, 2),
                        new AlloySlot(null, 0),
                        new AlloySlot(null, 0)
                },
                steel,
                18, // Точный выход: 18 единиц = 2 слитка стали
                1.5f, // Потребление 1.5 градуса/тик
                200   // 10 секунд плавки
        );
        MetallurgyRegistry.addAlloyRecipe(steelAlloy);
        MetallurgyRegistry.addSmeltRecipe(ModItems.CAST_PICKAXE_STEEL_BASE.get(), steel, 45, 1440, 1.2f, 180);

        // === АЛЮМИНИЙ ===
        Metal aluminum = registerMetalWithItems("aluminum", 0x8ebcd4, 660, 0.4f,
                ResourceRegistry.getMainUnit("aluminum"),
                ResourceRegistry.getSmallUnit("aluminum"),
                ResourceRegistry.getBlock("aluminum"));

        // === БРОНЗА ===
        Metal bronze = registerMetalWithItems("bronze", 0xcb9a3e, 930, 0.6f,
                ResourceRegistry.getMainUnit("bronze"),
                ResourceRegistry.getSmallUnit("bronze"),
                ResourceRegistry.getBlock("bronze"));

        // === ЦИНК ===
        Metal zinc = registerMetalWithItems("zinc", 0x968e8f, 419, 0.6f,
                ResourceRegistry.getMainUnit("zinc"),
                ResourceRegistry.getSmallUnit("zinc"),
                ResourceRegistry.getBlock("zinc"));

        // === ОЛОВО ===
        Metal tin = registerMetalWithItems("tin", 0x47675b, 232, 0.2f,
                ResourceRegistry.getMainUnit("tin"),
                ResourceRegistry.getSmallUnit("tin"),
                ResourceRegistry.getBlock("tin"));


        // === РЕГИСТРАЦИЯ ФОРМ ЛИТЬЯ ===
        MoldRecipeRegistry.register(ModItems.MOLD_INGOT.get(), MetalUnits2.UNITS_PER_INGOT, metal ->
                metal.hasIngot() ? new ItemStack(metal.getIngot()) : ItemStack.EMPTY
        );

        MoldRecipeRegistry.register(ModItems.MOLD_NUGGET.get(), MetalUnits2.UNITS_PER_NUGGET, metal ->
                metal.hasNugget() ? new ItemStack(metal.getNugget()) : ItemStack.EMPTY
        );

        MoldRecipeRegistry.register(ModItems.MOLD_BLOCK.get(), MetalUnits2.UNITS_PER_BLOCK, metal ->
                metal.hasBlock() ? new ItemStack(metal.getBlock()) : ItemStack.EMPTY
        );


        MoldRecipeRegistry.register(ModItems.MOLD_PICKAXE.get(), 45, metal -> {
            String path = metal.getId().getPath();
            if (path.equals("iron")) {
                return new ItemStack(ModItems.CAST_PICKAXE_IRON_BASE.get());
            }
            if (path.equals("steel")) {
                return new ItemStack(ModItems.CAST_PICKAXE_STEEL_BASE.get());
            }
            return ItemStack.EMPTY;
        });



        MetallurgyRegistry.generateStandardRecipes();
    }


    /**
     * Универсальный метод для регистрации металла с привязкой предметов.
     * @param name идентификатор
     * @param color цвет
     * @param meltingPoint температура плавления в градусах
     * @param heatConsumptionPerTick потребление температуры за тик (градусы/тик)
     * @param ingot слиток (может быть null)
     * @param nugget самородок (может быть null)
     * @param block блок (может быть null)
     */
    private static Metal registerMetalWithItems(String name, int color, int meltingPoint,
                                                float heatConsumptionPerTick,
                                                Item ingot, Item nugget, Block block) {
        Metal metal = MetallurgyRegistry.registerMetal(name, color, meltingPoint,
                MetalUnits2.UNITS_PER_INGOT,
                nugget != null ? MetalUnits2.UNITS_PER_NUGGET : 0,
                block != null ? MetalUnits2.UNITS_PER_BLOCK : 0,
                heatConsumptionPerTick);
        if (ingot != null) metal.setIngot(ingot);
        if (nugget != null) metal.setNugget(nugget);
        if (block != null) metal.setBlock(block);
        return metal;
    }
}