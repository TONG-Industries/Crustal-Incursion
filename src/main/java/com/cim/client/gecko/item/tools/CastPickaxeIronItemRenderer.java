package com.cim.client.gecko.item.tools;


import com.cim.item.tools.CastPickaxeIronItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class CastPickaxeIronItemRenderer extends GeoItemRenderer<CastPickaxeIronItem> {
    public CastPickaxeIronItemRenderer() {
        super(new CastPickaxeIronItemModel());
    }
}