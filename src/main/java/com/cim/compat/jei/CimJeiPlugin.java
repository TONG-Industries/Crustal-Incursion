package com.cim.compat.jei;

import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.api.metallurgy.system.recipe.AlloyRecipe;
import com.cim.api.metallurgy.system.recipe.AlloySlot;
import com.cim.api.metallurgy.system.recipe.MoldRecipe;
import com.cim.api.metallurgy.system.recipe.MoldRecipeRegistry;
import com.cim.block.basic.ModBlocks;
import com.cim.event.SlagItem;
import com.cim.item.ModItems;
import com.cim.main.CrustalIncursionMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JeiPlugin
public class CimJeiPlugin implements IModPlugin {

    public static final ResourceLocation UID = new ResourceLocation(CrustalIncursionMod.MOD_ID, "jei_plugin");

    public static final RecipeType<SmeltingWrapper> SMELTING_TYPE =
            RecipeType.create(CrustalIncursionMod.MOD_ID, "smelting", SmeltingWrapper.class);
    public static final RecipeType<CastingWrapper> CASTING_TYPE =
            RecipeType.create(CrustalIncursionMod.MOD_ID, "casting", CastingWrapper.class);
    public static final RecipeType<AlloyingWrapper> ALLOYING_TYPE =
            RecipeType.create(CrustalIncursionMod.MOD_ID, "alloying", AlloyingWrapper.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new SmeltingCategory(guiHelper));
        registration.addRecipeCategories(new CastingCategory(guiHelper));
        registration.addRecipeCategories(new AlloyingCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // === ПЛАВКА (предмет → жидкий металл) ===
        List<SmeltingWrapper> smeltingRecipes = new ArrayList<>();

        // Обычные рецепты
        for (var recipe : MetallurgyRegistry.getAllSmeltRecipes()) {
            smeltingRecipes.add(new SmeltingWrapper(
                    new ItemStack(recipe.input()),
                    recipe.output(),
                    recipe.outputUnits(),
                    recipe.minTemp(),
                    recipe.heatConsumption(),
                    recipe.smeltTimeTicks()
            ));
        }

        // Шлаковые рецепты (переплавка)
        for (Metal metal : MetallurgyRegistry.getAllMetals()) {
            ItemStack slag = SlagItem.createSlag(metal, MetalUnits2.UNITS_PER_INGOT);
            smeltingRecipes.add(new SmeltingWrapper(
                    slag,
                    metal,
                    MetalUnits2.UNITS_PER_INGOT,
                    metal.getMeltingPoint(),
                    metal.getHeatConsumptionPerTick(),
                    metal.calculateSmeltTimeForUnits(MetalUnits2.UNITS_PER_INGOT)
            ));
        }

        registration.addRecipes(SMELTING_TYPE, smeltingRecipes);

        // === ЛИТЬЁ (жидкий металл + форма → предмет) ===
        List<CastingWrapper> castingRecipes = new ArrayList<>();
        for (MoldRecipe mold : MoldRecipeRegistry.getAllRecipes()) {
            for (Metal metal : MetallurgyRegistry.getAllMetals()) {
                ItemStack output = mold.createOutput(metal);
                if (!output.isEmpty()) {
                    castingRecipes.add(new CastingWrapper(mold, metal, output, mold.getRequiredUnits()));
                }
            }
        }
        registration.addRecipes(CASTING_TYPE, castingRecipes);

        // === СПЛАВЫ ===
        List<AlloyingWrapper> alloyingRecipes = new ArrayList<>();
        for (AlloyRecipe recipe : MetallurgyRegistry.getAllAlloyRecipes()) {
            alloyingRecipes.add(new AlloyingWrapper(recipe));
        }
        registration.addRecipes(ALLOYING_TYPE, alloyingRecipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SMELTER.get()), SMELTING_TYPE, ALLOYING_TYPE, CASTING_TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SMALL_SMELTER.get()), SMELTING_TYPE);
    }

    // ==================== ОБЁРТКИ ====================

    public record SmeltingWrapper(ItemStack input, Metal metal, int outputUnits,
                                  int temp, float heatConsumption, int timeTicks) {}

    public record CastingWrapper(MoldRecipe mold, Metal metal, ItemStack output, int requiredUnits) {}

    public record AlloyingWrapper(AlloyRecipe recipe) {}

    // ==================== УТИЛИТЫ ====================

    private static ItemStack createLiquidMetalStack(Metal metal, int amount) {
        ItemStack stack = new ItemStack(ModItems.LIQUID_METAL.get());
        stack.getOrCreateTag().putString("MetalId", metal.getId().toString());
        stack.getTag().putInt("Amount", amount);
        stack.getTag().putInt("MetalColor", metal.getColor());
        return stack;
    }

    // ==================== КАТЕГОРИЯ: ПЛАВКА ====================

    public static class SmeltingCategory implements IRecipeCategory<SmeltingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;
        private final List<ItemStack> machines;

        public SmeltingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/jei/jei_cast_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.SMELTER.get()));
            this.title = Component.translatable("jei.category.cim.smelting");
            this.machines = Arrays.asList(
                    new ItemStack(ModBlocks.SMELTER.get()),
                    new ItemStack(ModBlocks.SMALL_SMELTER.get())
            );
        }

        @Override
        public RecipeType<SmeltingWrapper> getRecipeType() {
            return SMELTING_TYPE;
        }

        @Override
        public Component getTitle() {
            return title;
        }

        @Override
        public IDrawable getBackground() {
            return background;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SmeltingWrapper recipe, IFocusGroup focuses) {
            // Вход: 2×2 сетка, предмет в первом слоте
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 13)
                    .addItemStack(recipe.input());

            // Остальные 3 входных слота пустые (для фона)
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 13);
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 31);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 31);

            // Выход: жидкий металл в первом слоте правой сетки
            ItemStack liquidMetal = createLiquidMetalStack(recipe.metal(), recipe.outputUnits());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 13)
                    .addItemStack(liquidMetal);

            // Остальные 3 выходных слота пустые
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 13);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 31);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 31);
        }

        @Override
        public void draw(SmeltingWrapper recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                         double mouseX, double mouseY) {
            // Декоративная плавильня в центре
            long sec = System.currentTimeMillis() / 1000;
            ItemStack machine = machines.get((int) (sec % machines.size()));
            guiGraphics.renderItem(machine, 52, 13);
            guiGraphics.renderItemDecorations(Minecraft.getInstance().font, machine, 52, 13);

            // Текст: температура и время
            var font = Minecraft.getInstance().font;
            guiGraphics.drawString(font, recipe.temp() + "°C", 42, 41, 0xFF555555, false);
            guiGraphics.drawString(font, String.format("%.1fs", recipe.timeTicks() / 20f), 42, 51, 0xFF555555, false);
        }
    }

    // ==================== КАТЕГОРИЯ: ЛИТЬЁ ====================

    public static class CastingCategory implements IRecipeCategory<CastingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public CastingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/jei/jei_cast_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModItems.MOLD_INGOT.get()));
            this.title = Component.translatable("jei.category.cim.casting");
        }

        @Override
        public RecipeType<CastingWrapper> getRecipeType() {
            return CASTING_TYPE;
        }

        @Override
        public Component getTitle() {
            return title;
        }

        @Override
        public IDrawable getBackground() {
            return background;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CastingWrapper recipe, IFocusGroup focuses) {
            // Вход: жидкий металл в первом слоте левой сетки
            ItemStack liquidMetal = createLiquidMetalStack(recipe.metal(), recipe.requiredUnits());
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 13)
                    .addItemStack(liquidMetal);

            builder.addSlot(RecipeIngredientRole.INPUT, 23, 13);
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 31);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 31);

            // Центр: форма (реальный ингредиент!)
            builder.addSlot(RecipeIngredientRole.INPUT, 52, 13)
                    .addItemStack(new ItemStack(recipe.mold().getMoldItem()));

            // Выход: готовый предмет в первом слоте правой сетки
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 13)
                    .addItemStack(recipe.output());

            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 13);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 31);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 31);
        }
    }

    // ==================== КАТЕГОРИЯ: СПЛАВЫ ====================

    public static class AlloyingCategory implements IRecipeCategory<AlloyingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public AlloyingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/jei/jei_alloy_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(Items.BLAST_FURNACE)); // или иконка сплава
            this.title = Component.translatable("jei.category.cim.alloying");
        }

        @Override
        public RecipeType<AlloyingWrapper> getRecipeType() {
            return ALLOYING_TYPE;
        }

        @Override
        public Component getTitle() {
            return title;
        }

        @Override
        public IDrawable getBackground() {
            return background;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, AlloyingWrapper wrapper, IFocusGroup focuses) {
            AlloyRecipe recipe = wrapper.recipe();
            AlloySlot[] slots = recipe.getSlots();

            // Вход: 1×4 слота
            int[] xs = {5, 23, 41, 59};
            for (int i = 0; i < 4; i++) {
                if (slots[i].item() != null && slots[i].count() > 0) {
                    ItemStack stack = new ItemStack(slots[i].item(), slots[i].count());
                    builder.addSlot(RecipeIngredientRole.INPUT, xs[i], 22)
                            .addItemStack(stack);
                } else {
                    builder.addSlot(RecipeIngredientRole.INPUT, xs[i], 22);
                }
            }

            // Выход: жидкий металл сплава
            ItemStack liquidMetal = createLiquidMetalStack(recipe.getOutputMetal(), recipe.getOutputUnits());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 22)
                    .addItemStack(liquidMetal);
        }

        @Override
        public void draw(AlloyingWrapper wrapper, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                         double mouseX, double mouseY) {
            AlloyRecipe recipe = wrapper.recipe();
            var font = Minecraft.getInstance().font;

            // Температура и время в столбик
            guiGraphics.drawString(font, recipe.getOutputMetal().getMeltingPoint() + "°C", 77, 22, 0xFF555555, false);
            guiGraphics.drawString(font, String.format("%.1fs", recipe.getSmeltTimeTicks() / 20f), 77, 32, 0xFF555555, false);
        }
    }
}