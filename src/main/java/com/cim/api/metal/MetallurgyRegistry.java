package com.cim.api.metal;

import com.cim.api.metal.recipe.SmeltingRecipe;
import com.cim.main.CrustalIncursionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MetallurgyRegistry {
    private static final Map<ResourceLocation, Metal> METALS = new LinkedHashMap<>();
    private static final List<SmeltingRecipe> ALLOY_RECIPES = new ArrayList<>();
    private static final List<SimpleSmeltingRecipe> SMELTING_RECIPES = new ArrayList<>();

    public static Metal IRON;
    public static Metal GOLD;
    public static Metal COPPER;
    public static Metal NETHERITE;
    public static Metal STEEL;
    public static Metal BRONZE;
    public static Metal CAST_IRON;
    public static Metal COAL; // Вспомогательный материал

    public static void init() {
        // Ванильные металлы
        IRON = register("iron", 0xB87333, 958,
                Items.IRON_INGOT, Items.IRON_NUGGET, Blocks.IRON_BLOCK);

        GOLD = register("gold", 0xFFD700, 1064,
                Items.GOLD_INGOT, Items.GOLD_NUGGET, Blocks.GOLD_BLOCK);

        COPPER = register("copper", 0xFF6B35, 1085,
                Items.COPPER_INGOT, null, Blocks.COPPER_BLOCK);

        NETHERITE = register("netherite", 0x383038, 1200,
                Items.NETHERITE_INGOT, null, Blocks.NETHERITE_BLOCK);

        // Сплавы
        STEEL = register("steel", 0x71797E, 1470,
                Items.IRON_INGOT, Items.IRON_NUGGET, null);

        BRONZE = register("bronze", 0xCD7F32, 950,
                Items.BRICK, null, null);

        CAST_IRON = register("cast_iron", 0x434B4D, 1200,
                Items.IRON_BLOCK, null, null);

        // Вспомогательный материал (не плавится, используется в рецептах)
        COAL = register("coal", 0x2D2D2D, 1,
                Items.COAL, null, null);

        generateVanillaRecipes();
        registerAlloys();
    }

    private static Metal register(String name, int color, int meltingPoint,
                                  @Nullable Item ingot, @Nullable Item nugget, @Nullable Block block) {
        ResourceLocation id = new ResourceLocation(CrustalIncursionMod.MOD_ID, name);
        Metal metal = new Metal(id, color, meltingPoint, ingot, nugget, block);
        METALS.put(id, metal);
        return metal;
    }

    private static void generateVanillaRecipes() {
        // Железо
        addSimple(Items.IRON_ORE, IRON, 1, 0, 0, 800, 3, 600);
        addSimple(Items.DEEPSLATE_IRON_ORE, IRON, 1, 0, 0, 800, 3, 600);
        addSimple(Items.RAW_IRON, IRON, 1, 0, 0, 600, 3, 400);
        addSimple(Items.IRON_INGOT, IRON, 0, 1, 0, 1000, 2, 200);

        // Золото
        addSimple(Items.GOLD_ORE, GOLD, 1, 0, 0, 750, 3, 550);
        addSimple(Items.DEEPSLATE_GOLD_ORE, GOLD, 1, 0, 0, 750, 3, 550);
        addSimple(Items.RAW_GOLD, GOLD, 1, 0, 0, 550, 3, 350);

        // Медь
        addSimple(Items.COPPER_ORE, COPPER, 1, 0, 0, 700, 3, 500);
        addSimple(Items.DEEPSLATE_COPPER_ORE, COPPER, 1, 0, 0, 700, 3, 500);
        addSimple(Items.RAW_COPPER, COPPER, 1, 0, 0, 500, 3, 350);

        // Незерит
        addSimple(Items.NETHERITE_SCRAP, NETHERITE, 0, 1, 0, 1200, 5, 1000);

        // Утильсырьё
        addSimple(Items.IRON_HELMET, IRON, 0, 2, 0, 900, 4, 800);
        addSimple(Items.IRON_CHESTPLATE, IRON, 0, 5, 0, 900, 4, 1200);
        addSimple(Items.IRON_LEGGINGS, IRON, 0, 4, 0, 900, 4, 1000);
        addSimple(Items.IRON_BOOTS, IRON, 0, 2, 0, 900, 4, 600);
        addSimple(Items.IRON_PICKAXE, IRON, 0, 1, 0, 900, 4, 600);
        addSimple(Items.IRON_SWORD, IRON, 0, 1, 0, 900, 4, 400);
    }

    private static void registerAlloys() {
        // СТАЛЬ: 2 железа + 2 угля = 2 слитка стали
        addAlloy("steel_alloy", 1200, 5, 1500)
                .slot(0, Items.IRON_INGOT, 1)   // Красный - 1 слиток
                .slot(1, Items.COAL, 1)           // Желтый - 1 уголь
                .slot(2, Items.IRON_INGOT, 1)   // Зеленый - 1 слиток
                .slot(3, Items.COAL, 1)           // Синий - 1 уголь
                .output(STEEL, 0, 2, 0)          // 2 слитка стали
                .desc("Сплав железа с углем");

        // БРОНЗА: 2 меди + 1 олово(золото) = 2 слитка бронзы
        addAlloy("bronze_alloy", 900, 3, 800)
                .slot(0, Items.COPPER_INGOT, 1)
                .slot(1, Items.GOLD_INGOT, 1)     // placeholder для олова - 1 слиток
                .slot(2, Items.COPPER_INGOT, 1)
                .slot(3, null, 0)                  // Пусто
                .output(BRONZE, 0, 2, 0)
                .desc("Сплав меди и олова");

        // ЧУГУН: 3 железа + 1 уголь = 4 слитка чугуна
        addAlloy("cast_iron", 1400, 8, 2000)
                .slot(0, Items.IRON_INGOT, 1)
                .slot(1, Items.COAL, 1)           // Обычный уголь
                .slot(2, Items.IRON_INGOT, 1)
                .slot(3, Items.IRON_INGOT, 1)     // Третье железо
                .output(CAST_IRON, 0, 4, 0)
                .desc("Высокоуглеродистое железо");
    }

    public static void addSimple(Item input, Metal output, int outBlocks, int outIngots, int outNuggets,
                                 int minTemp, int heatPerTick, int totalHeat) {
        int mb = MetalUnits.toMb(outBlocks, outIngots, outNuggets);
        SMELTING_RECIPES.add(new SimpleSmeltingRecipe(input, output, mb, minTemp, heatPerTick, totalHeat));
    }

    public static AlloyBuilder addAlloy(String id, int minTemp, int heatPerTick, int totalHeat) {
        return new AlloyBuilder(id, minTemp, heatPerTick, totalHeat);
    }

    public static class AlloyBuilder {
        private final String id;
        private final int minTemp, heatPerTick, totalHeat;
        private final SmeltingRecipe.Slot[] slots = new SmeltingRecipe.Slot[4];
        private Metal output;
        private int outAmount;
        private String desc = "";

        public AlloyBuilder(String id, int minTemp, int heatPerTick, int totalHeat) {
            this.id = id;
            this.minTemp = minTemp;
            this.heatPerTick = heatPerTick;
            this.totalHeat = totalHeat;
            Arrays.fill(slots, SmeltingRecipe.Slot.EMPTY);
        }

        // Новый метод с количеством
        public AlloyBuilder slot(int index, @Nullable Item item, int count) {
            if (index >= 0 && index < 4) {
                if (item == null || item == Items.AIR || count <= 0) {
                    this.slots[index] = SmeltingRecipe.Slot.EMPTY;
                } else {
                    this.slots[index] = new SmeltingRecipe.Slot(item, count);
                }
            }
            return this;
        }

        // Старый метод для совместимости (всегда count=1)
        public AlloyBuilder slot(int index, @Nullable Item item) {
            return slot(index, item, 1);
        }

        public AlloyBuilder output(Metal metal, int blocks, int ingots, int nuggets) {
            this.output = metal;
            this.outAmount = MetalUnits.toMb(blocks, ingots, nuggets);
            return this;
        }

        public AlloyBuilder desc(String description) {
            this.desc = description;
            return this;
        }

        public void build() {
            ALLOY_RECIPES.add(new SmeltingRecipe(id, slots, output, outAmount, minTemp, heatPerTick, totalHeat, desc));
        }
    }

    public static Optional<Metal> get(ResourceLocation id) {
        return Optional.ofNullable(METALS.get(id));
    }

    public static Collection<Metal> getAllMetals() {
        return Collections.unmodifiableCollection(METALS.values());
    }

    public static SmeltingRecipe findAlloyRecipe(ItemStack[] inputs) {
        for (SmeltingRecipe recipe : ALLOY_RECIPES) {
            if (recipe.matches(inputs)) return recipe;
        }
        return null;
    }

    public static SimpleSmeltingRecipe findSimpleRecipe(Item input) {
        for (SimpleSmeltingRecipe recipe : SMELTING_RECIPES) {
            if (recipe.input() == input) return recipe;
        }
        return null;
    }

    public static List<SmeltingRecipe> getAlloyRecipes() {
        return Collections.unmodifiableList(ALLOY_RECIPES);
    }

    public static List<SimpleSmeltingRecipe> getSimpleRecipes() {
        return Collections.unmodifiableList(SMELTING_RECIPES);
    }

    public record SimpleSmeltingRecipe(Item input, Metal output, int outputMb,
                                       int minTemp, int heatPerTick, int totalHeat) {}
}