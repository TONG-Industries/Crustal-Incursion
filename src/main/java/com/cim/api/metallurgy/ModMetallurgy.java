package com.cim.api.metallurgy;

import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.api.metallurgy.system.recipe.AlloyRecipe;
import com.cim.api.metallurgy.system.recipe.AlloySlot;
import com.cim.main.ResourceRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class ModMetallurgy {

    public static void init() {

        // === ЗОЛОТО ===
        // Потребление: 0.5 градуса/тик, плавится при 1064°C
        Metal gold = registerMetalWithItems("gold", 0xffac2a, 1064, 0.5f,
                Items.GOLD_INGOT, Items.GOLD_NUGGET, Blocks.GOLD_BLOCK);

        // Кастомные рецепты: указываем ТОЧНЫЙ выход в единицах и ВРЕМЯ
        // Меч = 9 единиц (1 слиток), 80 тиков (4 сек), потребление 0.8/тик
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_SWORD, gold, 9, 1064, 0.8f, 80);
        // Шлем = 18 единиц (2 слитка)
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_HELMET, gold, 18, 1064, 0.6f, 100);
        // Нагрудник = 45 единиц (5 слитков)
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_CHESTPLATE, gold, 45, 1064, 0.7f, 120);
        // Поножи = 36 единиц (4 слитка)
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_LEGGINGS, gold, 36, 1064, 0.6f, 110);
        // Ботинки = 18 единиц (2 слитка)
        MetallurgyRegistry.addSmeltRecipe(Items.GOLDEN_BOOTS, gold, 18, 1064, 0.5f, 80);

        // === ЖЕЛЕЗО ===
        // Потребление: 0.8 градуса/тик, плавится при 958°C
        Metal iron = registerMetalWithItems("iron", 0xba826c, 958, 0.8f,
                Items.IRON_INGOT, Items.IRON_NUGGET, Blocks.IRON_BLOCK);

        // Руды и сырые куски плавятся дольше чем готовые слитки
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_ORE, iron, 9, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.DEEPSLATE_IRON_ORE, iron, 9, 958, 1.0f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.RAW_IRON, iron, 9, 958, 0.9f, 80);
        // Меч = 9 единиц
        MetallurgyRegistry.addSmeltRecipe(Items.IRON_SWORD, iron, 9, 958, 0.8f, 60);

        // === МЕДЬ ===
        // Потребление: 0.6 градуса/тик, плавится при 1085°C
        Metal copper = registerMetalWithItems("copper", 0xc15a36, 1085, 0.6f,
                Items.COPPER_INGOT, null, Blocks.COPPER_BLOCK);

        MetallurgyRegistry.addSmeltRecipe(Items.COPPER_ORE, copper, 9, 1085, 0.8f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.DEEPSLATE_COPPER_ORE, copper, 9, 1085, 0.8f, 100);
        MetallurgyRegistry.addSmeltRecipe(Items.RAW_COPPER, copper, 9, 1085, 0.7f, 80);

        // === НЕЗЕРИТ ===
        // Потребление: 2.0 градуса/тик, плавится при 1200°C (очень требовательный!)
        Metal netherite = registerMetalWithItems("netherite", 0x4a2940, 1200, 2.0f,
                Items.NETHERITE_INGOT, null, Blocks.NETHERITE_BLOCK);


        // === КАСТОМ МЕТАЛЛЫ ===


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
                300   // 15 секунд плавки
        );
        MetallurgyRegistry.addAlloyRecipe(steelAlloy);


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

        // ГЕНЕРАЦИЯ СТАНДАРТНЫХ РЕЦЕПТОВ
        // Автоматически создаёт рецепты для слитков (3с), самородков (1с), блоков (9с)
        // с потреблением из металла
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