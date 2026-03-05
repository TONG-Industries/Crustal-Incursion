package com.cim.client.gecko.item.rotation;


import com.cim.item.rotation.MotorElectroBlockItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class MotorElectroItemRenderer extends GeoItemRenderer<MotorElectroBlockItem> {
    public MotorElectroItemRenderer() {
        super(new MotorElectroItemModel());
    }
}