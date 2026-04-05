package com.cim.client.gecko.item.tools;

import com.cim.item.tools.cast_pickaxes.CastPickaxeItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class CastPickaxeItemRenderer extends GeoItemRenderer<CastPickaxeItem> {
    public CastPickaxeItemRenderer() {
        super(new CastPickaxeItemModel());
    }
}