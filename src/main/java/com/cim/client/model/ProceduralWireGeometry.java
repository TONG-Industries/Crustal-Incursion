package com.cim.client.model;

// Модель провода, которая генерируется процедурно в зависимости от состояния блока.
// Использует IUnbakedGeometry для создания BakedModel на основе BlockState.


import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import com.cim.main.CrustalIncursionMod;

import java.util.function.Function;

public class ProceduralWireGeometry implements IUnbakedGeometry<ProceduralWireGeometry> {
    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
        TextureAtlasSprite sprite = spriteGetter.apply(new Material(
            TextureAtlas.LOCATION_BLOCKS,
                new ResourceLocation(CrustalIncursionMod.MOD_ID, "block/wire_coated")
        ));
        return new ProceduralWireBakedModel(sprite);
    }
}