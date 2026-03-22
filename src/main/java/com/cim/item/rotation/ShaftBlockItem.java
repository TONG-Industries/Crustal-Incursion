package com.cim.item.rotation;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.basic.industrial.rotation.ShaftType;

import com.cim.client.gecko.item.rotation.ShaftItemRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class ShaftBlockItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ShaftBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public ShaftType getShaftType() {
        // Предполагаем, что блок всегда является экземпляром ShaftBlock
        return ((ShaftBlock) getBlock()).getShaftType();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private ShaftItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new ShaftItemRenderer();
                return renderer;
            }
        });
    }
}