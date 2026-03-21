package com.cim.api.fluids;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

import java.util.function.Consumer;

public class BaseFluidType extends FluidType {
    private final ResourceLocation stillTexture;
    private final ResourceLocation flowingTexture;
    private final int tintColor;
    private final int acidity;
    private final int radiation;

    public BaseFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture, int tintColor, int acidity, int radiation) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.tintColor = tintColor;
        this.acidity = acidity;
        this.radiation = radiation;
    }



    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return stillTexture;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flowingTexture;
            }

            @Override
            public int getTintColor() {
                return tintColor;
            }
        });
    }

    public int getAcidity() { return acidity; }
    public int getRadiation() { return radiation; }
}