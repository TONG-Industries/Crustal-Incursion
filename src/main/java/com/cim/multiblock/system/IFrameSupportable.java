package com.cim.multiblock.system;

public interface IFrameSupportable {
    void checkForFrame();
    boolean setFrameVisible(boolean visible);
    boolean isFrameVisible();
}