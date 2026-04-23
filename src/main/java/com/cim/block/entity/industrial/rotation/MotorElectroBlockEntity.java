package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.Rotational;
import com.cim.api.rotation.ShaftDiameter;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.cim.block.basic.industrial.rotation.MotorElectroBlock;

public class MotorElectroBlockEntity extends BlockEntity implements Rotational {

    private final long speedConstant = 2;
    private final long torqueConstant = 100;
    private boolean reversed = false;

    private long currentSpeed = 0;
    private long lastSyncedSpeed = 0;

    @Override
    public long getInertiaContribution() { return 50; }

    @Override
    public long getFrictionContribution() { return 5; }

    @Override
    public long getMaxTorqueTolerance() { return getMaxTorque(); }

    public MotorElectroBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOTOR_ELECTRO_BE.get(), pos, state);
    }

    @Override
    public long getGeneratedSpeed() {
        return reversed ? -speedConstant : speedConstant;
    }

    @Override
    public long getSpeed() {
        return currentSpeed;
    }

    @Override
    public long getVisualSpeed() {
        BlockState state = getBlockState();
        if (!state.hasProperty(MotorElectroBlock.FACING)) {
            return 0;
        }
        Direction facing = getBlockState().getValue(MotorElectroBlock.FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.currentSpeed;
        }
        return this.currentSpeed;
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (neighbor instanceof ShaftBlockEntity shaftBE) {
            if (shaftBE.getBlockState().getBlock() instanceof ShaftBlock shaftBlock) {
                return shaftBlock.getDiameter() == ShaftDiameter.LIGHT;
            }
        }
        return true;
    }

    public long getCurrentVisualSpeed() {
        return this.currentSpeed;
    }

    @Override
    public void setSpeed(long speed) {
        if (this.currentSpeed != speed) {
            this.currentSpeed = speed;
            super.setChanged(); // ИСПОЛЬЗУЕМ ВАНИЛЬНЫЙ МЕТОД

            if (shouldSyncSpeed()) {
                this.lastSyncedSpeed = this.currentSpeed;
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }
            }
        }
    }

    private boolean shouldSyncSpeed() {
        if (this.currentSpeed == 0 && this.lastSyncedSpeed != 0) return true;
        if (this.currentSpeed != 0 && this.lastSyncedSpeed == 0) return true;

        long diff = Math.abs(this.currentSpeed - this.lastSyncedSpeed);
        long threshold = Math.max(2, Math.abs(this.lastSyncedSpeed) / 20);

        return diff >= threshold;
    }

    public void toggleDirection() {
        this.reversed = !this.reversed;
        super.setChanged(); // ИСПОЛЬЗУЕМ ВАНИЛЬНЫЙ МЕТОД
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Reversed", this.reversed);
        tag.putLong("LastSyncedSpeed", this.lastSyncedSpeed);
        tag.putLong("CurrentSpeed", this.currentSpeed);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        this.reversed = tag.getBoolean("Reversed");
        this.lastSyncedSpeed = tag.getLong("LastSyncedSpeed");
        this.currentSpeed = tag.getLong("CurrentSpeed");
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag() {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
        net.minecraft.nbt.CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    @Override
    public long getTorque() { return torqueConstant; }

    @Override
    public boolean isSource() { return true; }

    @Override
    public Direction[] getPropagationDirections() {
        return new Direction[]{ getBlockState().getValue(MotorElectroBlock.FACING) };
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            com.cim.api.rotation.KineticNetwork net = com.cim.api.rotation.KineticNetworkManager.get((net.minecraft.server.level.ServerLevel) level)
                    .getNetworkFor(worldPosition);

            if (net != null) {
                this.currentSpeed = net.getSpeed();
                this.lastSyncedSpeed = this.currentSpeed;
                // НИКАКОГО level.sendBlockUpdated здесь нет!
                net.requestRecalculation();
            }
        }
    }

    @Override
    public java.util.List<BlockPos> getPotentialConnections(net.minecraft.world.level.Level level, BlockPos myPos) {
        Direction facing = getBlockState().getValue(MotorElectroBlock.FACING);
        // Мотор отдает энергию только в ту сторону, куда смотрит его вал
        return java.util.List.of(myPos.relative(facing));
    }

    // --- ЗАЩИТА ВАНИЛЬНОЙ КАМЕРЫ ---
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.2D);
    }

    @Override
    public long getMaxSpeed() { return 256; }

    @Override
    public long getMaxTorque() { return 1024; }
}