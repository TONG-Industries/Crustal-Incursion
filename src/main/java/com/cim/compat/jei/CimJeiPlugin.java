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
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
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
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.LIQUID_METAL.get(),
                (stack, context) -> {
                    if (stack.hasTag() && stack.getTag().contains("MetalId")) {
                        return stack.getTag().getString("MetalId");
                    }
                    return IIngredientSubtypeInterpreter.NONE;
                });
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
        List<SmeltingWrapper> smeltingRecipes = new ArrayList<>();

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

    private static ItemStack createLiquidMetalStack(Metal metal, int amount) {
        ItemStack stack = new ItemStack(ModItems.LIQUID_METAL.get());
        stack.getOrCreateTag().putString("MetalId", metal.getId().toString());
        stack.getTag().putInt("Amount", amount);
        stack.getTag().putInt("MetalColor", metal.getColor());
        return stack;
    }

    public record SmeltingWrapper(ItemStack input, Metal metal, int outputUnits,
                                  int temp, float heatConsumption, int timeTicks) {}

    public record CastingWrapper(MoldRecipe mold, Metal metal, ItemStack output, int requiredUnits) {}

    public record AlloyingWrapper(AlloyRecipe recipe) {}

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

        @Override public RecipeType<SmeltingWrapper> getRecipeType() { return SMELTING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, SmeltingWrapper recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 13).addItemStack(recipe.input());
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 13);
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 31);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 31);

            ItemStack liquidMetal = createLiquidMetalStack(recipe.metal(), recipe.outputUnits());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 13).addItemStack(liquidMetal);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 13);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 31);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 31);
        }

        @Override
        public void draw(SmeltingWrapper recipe, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            long sec = System.currentTimeMillis() / 1000;
            ItemStack machine = machines.get((int) (sec % machines.size()));
            gg.renderItem(machine, 52, 13);
            gg.renderItemDecorations(Minecraft.getInstance().font, machine, 52, 13);

            var font = Minecraft.getInstance().font;
            gg.drawString(font, recipe.temp() + "°C", 42, 41, 0xFF555555, false);
            gg.drawString(font, String.format("%.1fs", recipe.timeTicks() / 20f), 42, 51, 0xFF555555, false);
        }
    }

    public static class CastingCategory implements IRecipeCategory<CastingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public CastingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/jei/jei_cast_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.SMELTER.get()));
            this.title = Component.translatable("jei.category.cim.casting");
        }

        @Override public RecipeType<CastingWrapper> getRecipeType() { return CASTING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, CastingWrapper recipe, IFocusGroup focuses) {
            ItemStack liquidMetal = createLiquidMetalStack(recipe.metal(), recipe.requiredUnits());
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 13).addItemStack(liquidMetal);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 13);
            builder.addSlot(RecipeIngredientRole.INPUT, 5, 31);
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 31);

            builder.addSlot(RecipeIngredientRole.INPUT, 52, 13)
                    .addItemStack(new ItemStack(recipe.mold().getMoldItem()));

            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 13).addItemStack(recipe.output());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 13);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 81, 31);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 31);
        }
    }

    public static class AlloyingCategory implements IRecipeCategory<AlloyingWrapper> {
        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public AlloyingCategory(IGuiHelper guiHelper) {
            this.background = guiHelper.createDrawable(
                    new ResourceLocation(CrustalIncursionMod.MOD_ID, "textures/gui/jei/jei_alloy_gui.png"),
                    0, 0, 120, 60);
            this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    new ItemStack(ModBlocks.SMELTER.get()));
            this.title = Component.translatable("jei.category.cim.alloying");
        }

        @Override public RecipeType<AlloyingWrapper> getRecipeType() { return ALLOYING_TYPE; }
        @Override public Component getTitle() { return title; }
        @Override public IDrawable getBackground() { return background; }
        @Override public IDrawable getIcon() { return icon; }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, AlloyingWrapper wrapper, IFocusGroup focuses) {
            AlloyRecipe recipe = wrapper.recipe();
            AlloySlot[] slots = recipe.getSlots();
            int[] xs = {5, 23, 41, 59};

            for (int i = 0; i < 4; i++) {
                if (slots[i].item() != null && slots[i].count() > 0) {
                    builder.addSlot(RecipeIngredientRole.INPUT, xs[i], 22)
                            .addItemStack(new ItemStack(slots[i].item(), slots[i].count()));
                } else {
                    builder.addSlot(RecipeIngredientRole.INPUT, xs[i], 22);
                }
            }

            ItemStack liquidMetal = createLiquidMetalStack(recipe.getOutputMetal(), recipe.getOutputUnits());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 99, 22).addItemStack(liquidMetal);
        }

        @Override
        public void draw(AlloyingWrapper wrapper, IRecipeSlotsView view, GuiGraphics gg, double mx, double my) {
            AlloyRecipe recipe = wrapper.recipe();
            var font = Minecraft.getInstance().font;
            gg.drawString(font, recipe.getOutputMetal().getMeltingPoint() + "°C", 4, 42, 0xFF555555, false);
            gg.drawString(font, String.format("%.1fs", recipe.getSmeltTimeTicks() / 20f), 4, 52, 0xFF555555, false);
        }
    }
}