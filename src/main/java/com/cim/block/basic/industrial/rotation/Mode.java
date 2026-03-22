package com.cim.block.basic.industrial.rotation;

import net.minecraft.util.StringRepresentable;

public enum Mode implements StringRepresentable {
    SPEED("speed"),
    TORQUE("torque");

    private final String name;

    Mode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}