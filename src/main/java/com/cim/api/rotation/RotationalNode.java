package com.cim.api.rotation;

import net.minecraft.core.Direction;

import javax.annotation.Nullable;

public interface RotationalNode extends Rotational {
    @Nullable RotationSource getCachedSource();
    void setCachedSource(@Nullable RotationSource source, long gameTime);
    boolean isCacheValid(long currentTime);
    void invalidateCache();
    Direction[] getPropagationDirections(@Nullable Direction fromDir);

    /**
     * Возвращает true, если данный узел может предоставить источник вращения
     * при запросе с указанного направления.
     */
    default boolean canProvideSource(@Nullable Direction fromDir) {
        return false;
    }
}