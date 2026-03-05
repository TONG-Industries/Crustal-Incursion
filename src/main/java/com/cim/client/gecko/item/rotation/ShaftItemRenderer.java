package com.cim.client.gecko.item.rotation;

import com.cim.item.rotation.ShaftBlockItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class ShaftItemRenderer extends GeoItemRenderer<ShaftBlockItem> {
    public ShaftItemRenderer() {
        super(new ShaftItemModel());
    }
}