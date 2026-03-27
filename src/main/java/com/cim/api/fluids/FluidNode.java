package com.cim.api.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public class FluidNode {
    private final BlockPos pos;
    private FluidNetwork network;

    public FluidNode(BlockPos pos) {
        this.pos = pos;
    }

    public BlockPos getPos() {
        return pos;
    }

    public FluidNetwork getNetwork() {
        return network;
    }

    public void setNetwork(FluidNetwork network) {
        this.network = network;
    }

    // Проверка валидности (как в энергосети)
    public boolean isValid(ServerLevel level) {
        // Если чанк не загружен, сохраняем узел в памяти
        if (!level.isLoaded(pos)) return true;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;

        // Валиден, если это труба или блок с инвентарем для жидкостей
        return be instanceof com.cim.block.entity.fluids.FluidPipeBlockEntity ||
                be.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FluidNode that = (FluidNode) o;
        return pos.equals(that.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}
