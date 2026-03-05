package com.cim.client.gecko.item.rotation;


import com.cim.item.rotation.DrillHeadItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class DrillHeadItemRenderer extends GeoItemRenderer<DrillHeadItem> {
    public DrillHeadItemRenderer() {
        super(new DrillHeadItemModel());
    }
}