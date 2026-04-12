package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.Rotational;
import com.cim.api.rotation.ShaftDiameter;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

public class ShaftBlockEntity extends BlockEntity implements Rotational {

    private long speed = 0;
    private long lastSyncedSpeed = 0;
    private int networkSign = 1;

    private ItemStack attachedGear = ItemStack.EMPTY;

    public ShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAFT_BE.get(), pos, state);
    }

    public boolean hasGear() { return !attachedGear.isEmpty(); }
    public ItemStack getAttachedGear() { return attachedGear; }

    public void setAttachedGear(ItemStack gear) {
        this.attachedGear = gear;
        this.setChanged();

        if (level != null && !level.isClientSide) {
            // Флаг 2! Тихо обновляем данные на клиенте
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public Direction[] getPropagationDirections() {
        BlockState state = getBlockState();
        if (!state.hasProperty(ShaftBlock.FACING)) return new Direction[0];
        Direction facing = state.getValue(ShaftBlock.FACING);
        if (hasGear()) return Direction.values();
        return new Direction[]{facing, facing.getOpposite()};
    }

    @Override
    public boolean canConnectMechanically(Direction direction, Rotational neighbor) {
        ShaftDiameter thisDiameter = ((ShaftBlock)this.getBlockState().getBlock()).getDiameter();
        Direction thisFacing = getBlockState().getValue(ShaftBlock.FACING);
        boolean isEndToEnd = (direction.getAxis() == thisFacing.getAxis());

        if (neighbor instanceof ShaftBlockEntity otherShaft) {
            Direction otherFacing = otherShaft.getBlockState().getValue(ShaftBlock.FACING);
            ShaftDiameter otherDiameter = ((ShaftBlock)otherShaft.getBlockState().getBlock()).getDiameter();

            if (isEndToEnd) {
                return thisDiameter == otherDiameter && otherFacing.getAxis() == thisFacing.getAxis();
            } else {
                if (thisFacing.getAxis() == otherFacing.getAxis() && this.hasGear() && otherShaft.hasGear()) {
                    return true;
                }
                return false;
            }
        }
        if (neighbor instanceof BearingBlockEntity bearing) {
            return bearing.hasShaft() && bearing.getShaftDiameter() == thisDiameter;
        }
        if (neighbor instanceof MotorElectroBlockEntity) {
            return thisDiameter == ShaftDiameter.LIGHT;
        }
        return true;
    }

    @Override
    public void setNetworkSign(int sign) { this.networkSign = sign; }

    @Override
    public int getNetworkSign() { return this.networkSign; }

    @Override
    public void setSpeed(long speed) {
        long actualSpeed = speed * this.networkSign;
        if (this.speed != actualSpeed) {
            this.speed = actualSpeed;
            setChanged();
            if (shouldSyncSpeed()) {
                this.lastSyncedSpeed = this.speed;
                if (level != null && !level.isClientSide) {
                    // Флаг 2!
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
                }
            }
        }
    }

    private boolean shouldSyncSpeed() {
        if (this.speed == 0 && this.lastSyncedSpeed != 0) return true;
        if (this.speed != 0 && this.lastSyncedSpeed == 0) return true;
        long diff = Math.abs(this.speed - this.lastSyncedSpeed);
        long threshold = Math.max(2, Math.abs(this.lastSyncedSpeed) / 20);
        return diff >= threshold;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", this.speed);
        tag.putLong("LastSyncedSpeed", this.lastSyncedSpeed);
        tag.putInt("NetworkSign", this.networkSign);
        if (!attachedGear.isEmpty()) {
            tag.put("AttachedGear", attachedGear.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.speed = tag.getLong("Speed");
        this.lastSyncedSpeed = tag.getLong("LastSyncedSpeed");
        this.networkSign = tag.contains("NetworkSign") ? tag.getInt("NetworkSign") : 1;
        if (tag.contains("AttachedGear")) {
            this.attachedGear = ItemStack.of(tag.getCompound("AttachedGear"));
        } else {
            this.attachedGear = ItemStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putLong("Speed", this.speed);
        if (!attachedGear.isEmpty()) {
            tag.put("AttachedGear", attachedGear.save(new CompoundTag()));
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            var net = com.cim.api.rotation.KineticNetworkManager.get((net.minecraft.server.level.ServerLevel) level)
                    .getNetworkFor(worldPosition);
            if (net != null) {
                this.speed = net.getSpeed();
                this.lastSyncedSpeed = this.speed;
                net.requestRecalculation();
            }
        }
    }

    @Override
    public long getVisualSpeed() {
        BlockState state = getBlockState();
        if (!state.hasProperty(ShaftBlock.FACING)) return this.speed;

        Direction facing = state.getValue(ShaftBlock.FACING);
        // Инвертируем визуальную скорость для позитивных осей (правило правой руки)
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.2D);
    }

    @Override
    public long getSpeed() { return speed; }
    @Override
    public long getTorque() { return 0; }
    @Override
    public long getMaxSpeed() { return 256; }
    @Override
    public long getMaxTorque() { return 1024; }
    @Override
    public long getInertiaContribution() { return 5; }
    @Override
    public long getFrictionContribution() { return 1; }
    @Override
    public long getMaxTorqueTolerance() { return 1000; }
}