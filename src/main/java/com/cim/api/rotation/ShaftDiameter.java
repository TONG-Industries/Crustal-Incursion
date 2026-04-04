package com.cim.api.rotation;

public enum ShaftDiameter {
    LIGHT(4, "light", 0.5f, 1.5f, 0.3f),
    MEDIUM(8, "medium", 1.0f, 1.0f, 1.0f),
    HEAVY(12, "heavy", 2.5f, 0.6f, 3.0f);

    public final int pixels;
    public final String name;
    public final float torqueMod;
    public final float speedMod;
    public final float inertiaMod;

    ShaftDiameter(int pixels, String name, float torqueMod, float speedMod, float inertiaMod) {
        this.pixels = pixels;
        this.name = name;
        this.torqueMod = torqueMod;
        this.speedMod = speedMod;
        this.inertiaMod = inertiaMod;
    }
}