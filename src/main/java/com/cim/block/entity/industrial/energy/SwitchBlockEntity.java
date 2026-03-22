package com.cim.block.entity.industrial.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import com.cim.api.energy.EnergyNetworkManager;
import com.cim.api.energy.IEnergyConnector;
import com.cim.block.basic.industrial.energy.SwitchBlock;
import com.cim.block.entity.ModBlockEntities;
import com.cim.capability.ModCapabilities;

import javax.annotation.Nullable;

public class SwitchBlockEntity extends BlockEntity implements IEnergyConnector {

    // Capability всегда "живая", но доступ к ней регулируется через getCapability
    private final LazyOptional<IEnergyConnector> hbmConnector = LazyOptional.of(() -> this);

    public SwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SWITCH_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SwitchBlockEntity entity) {
        if (level.isClientSide) return;

        // Если рубильник включен, он ОБЯЗАН быть в сети
        if (state.getValue(SwitchBlock.POWERED)) {
            ServerLevel serverLevel = (ServerLevel) level;
            EnergyNetworkManager manager = EnergyNetworkManager.get(serverLevel);

            if (!manager.hasNode(pos)) {
                manager.addNode(pos);
            }
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ModCapabilities.ENERGY_CONNECTOR) {
            // [ВАЖНО] Проверяем валидность здесь. Если isValidSide вернет false (например, выключен),
            // мы вернем super (empty). Это заставит EnergyNetworkManager считать узел невалидным.
            if (isValidSide(side)) {
                return hbmConnector.cast();
            }
        }
        return super.getCapability(cap, side);
    }

    private boolean isValidSide(@Nullable Direction side) {
        // [ИСПРАВЛЕНО] Добавлена проверка POWERED.
        // Теперь, если рубильник выключен, он не отдает Capability.
        // Это синхронизирует логику EnergyNetworkManager с состоянием блока.
        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof SwitchBlock)) return false;

        if (!state.getValue(SwitchBlock.POWERED)) return false; // <--- ВОТ ЭТОГО НЕ ХВАТАЛО

        if (side == null) return true;
        Direction facing = state.getValue(SwitchBlock.FACING);
        return side == facing || side == facing.getOpposite();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        hbmConnector.invalidate();
    }

    @Override
    public boolean canConnectEnergy(Direction side) {
        // Используем ту же логику проверки
        return isValidSide(side);
    }

    @Override
    public void setLevel(Level pLevel) {
        super.setLevel(pLevel);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
        hbmConnector.invalidate();
    }

}