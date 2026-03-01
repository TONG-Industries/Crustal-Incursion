package razchexlitiel.cim.client.gecko.item.rotation;

import razchexlitiel.cim.item.rotation.ShaftBlockItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class ShaftItemRenderer extends GeoItemRenderer<ShaftBlockItem> {
    public ShaftItemRenderer() {
        super(new ShaftItemModel());
    }
}