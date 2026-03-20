package com.cim.multiblock.system;

import net.minecraft.util.StringRepresentable;

public enum PartRole implements StringRepresentable {
    DEFAULT("default"),
    ENERGY_CONNECTOR("energy_connector"),
    FLUID_CONNECTOR("fluid_connector"),
    ITEM_INPUT("item_input"),
    ITEM_OUTPUT("item_output"),
    UNIVERSAL_CONNECTOR("universal_connector"),
    LADDER("ladder"),
    CONTROLLER("controller");

    private final String name;

    PartRole(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean canReceiveEnergy() {
        return this == ENERGY_CONNECTOR || this == UNIVERSAL_CONNECTOR;
    }

    public boolean canSendEnergy() {
        return this == ENERGY_CONNECTOR || this == UNIVERSAL_CONNECTOR;
    }

    public boolean isConveyorConnectionPoint() {
        return this == UNIVERSAL_CONNECTOR;
    }

    public boolean isLadder() {
        return this == LADDER;
    }

    public boolean isController() {
        return this == CONTROLLER;
    }
}
