package com.cim.client.gecko.item.energy;

import com.cim.item.energy.EnergyCellItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class EnergyCellItemRenderer extends GeoItemRenderer<EnergyCellItem> {
    public EnergyCellItemRenderer() {
        super(new EnergyCellItemModel());
    }
}
