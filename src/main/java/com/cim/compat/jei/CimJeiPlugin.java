package com.cim.compat.jei;

import com.cim.block.entity.industrial.MillstoneBlockEntity;
import com.cim.block.entity.industrial.MillstoneBlockEntity.GrindRecipe;
import com.cim.main.CrustalIncursionMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JeiPlugin
public class CimJeiPlugin implements IModPlugin {

    public static final ResourceLocation UID = new ResourceLocation(CrustalIncursionMod.MOD_ID, "jei_plugin");
    public static final RecipeType<GrindRecipeWrapper> MILLSTONE_TYPE =
            RecipeType.create(CrustalIncursionMod.MOD_ID, "millstone", GrindRecipeWrapper.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new MillstoneCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<GrindRecipeWrapper> recipes = new ArrayList<>();
        for (Map.Entry<net.minecraft.world.item.Item, GrindRecipe> entry : MillstoneBlockEntity.RECIPES.entrySet()) {
            recipes.add(new GrindRecipeWrapper(entry.getKey(), entry.getValue()));
        }
        registration.addRecipes(MILLSTONE_TYPE, recipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(com.cim.block.basic.ModBlocks.JERNOVA.get()), MILLSTONE_TYPE);
    }

    // === Категория с фоном крафтинг-стола ===
    public static class MillstoneCategory implements IRecipeCategory<GrindRecipeWrapper> {

        private final IDrawable background;
        private final IDrawable icon;
        private final Component title;

        public MillstoneCategory(IGuiHelper guiHelper) {
            // ВАНИЛЬНЫЙ ФОН КРАФТИНГ-ТАБЛИЦЫ (116x54)
            this.background = guiHelper.createDrawable(
                    new ResourceLocation("jei", "textures/gui/single_recipe_background.png"),
                    0, 0, 116, 54);
            this.icon = guiHelper.createDrawableIngredient(
                    VanillaTypes.ITEM_STACK,
                    new ItemStack(com.cim.block.basic.ModBlocks.JERNOVA.get()));
            this.title = Component.translatable("block.cim.jernova");
        }

        @Override
        public RecipeType<GrindRecipeWrapper> getRecipeType() {
            return MILLSTONE_TYPE;
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
        public void setRecipe(IRecipeLayoutBuilder builder, GrindRecipeWrapper recipe, IFocusGroup focuses) {
            // Вход: слот слева (координаты как в стандартном крафте)
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 19)
                    .addItemStack(new ItemStack(recipe.input()));

            // Выход: слот справа
            builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 19)
                    .addItemStack(new ItemStack(recipe.recipe.output(), recipe.recipe.outputCount()));
        }
    }

    // === Обертка для рецепта ===
    public record GrindRecipeWrapper(net.minecraft.world.item.Item input, GrindRecipe recipe) {}
}