package com.cim.api.rotation;

public record ShaftMaterial(
        String name,
        long baseTorque,
        long baseSpeed,
        int maxDeflection,
        long baseInertia,
        double baseFriction
) {
    public static final ShaftMaterial IRON = new ShaftMaterial("iron", 500, 256, 5, 5, 1.0);
    public static final ShaftMaterial DURALUMIN = new ShaftMaterial("duralumin", 1000, 640, 7, 2, 0.4);
    public static final ShaftMaterial STEEL = new ShaftMaterial("steel", 3500, 160, 10, 25, 1.5);
    public static final ShaftMaterial TITANIUM = new ShaftMaterial("titanium", 6000, 800, 14, 12, 0.2);
    public static final ShaftMaterial TUNGSTEN_CARBIDE = new ShaftMaterial("tungsten_carbide", 15000, 1200, 20, 8, 0.05);
}
