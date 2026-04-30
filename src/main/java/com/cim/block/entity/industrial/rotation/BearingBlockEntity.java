package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.KineticNetwork;
import com.cim.api.rotation.KineticNetworkManager;
import com.cim.api.rotation.Rotational;
import com.cim.api.rotation.ShaftDiameter;
import com.cim.api.rotation.ShaftMaterial;
import com.cim.block.basic.industrial.rotation.BearingBlock;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BearingBlockEntity extends BlockEntity implements Rotational {

    private long speed = 0;
    private long lastSyncedSpeed = 0;
    private float networkScale = 1.0f;

    private boolean hasShaft = false;
    private ShaftMaterial shaftMaterial = null;
    private ShaftDiameter shaftDiameter = null;

    public BearingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BEARING_BE.get(), pos, state);
    }

    public boolean hasShaft() { return hasShaft; }
    public ShaftMaterial getShaftMaterial() { return shaftMaterial; }
    public ShaftDiameter getShaftDiameter() { return shaftDiameter; }

    public void insertShaft(ShaftMaterial material, ShaftDiameter diameter) {
        this.hasShaft = true;
        this.shaftMaterial = material;
        this.shaftDiameter = diameter;
        setChanged();
        syncToClient(); // ИСПРАВЛЕНИЕ: Мгновенно сообщаем клиенту о новом вале!
    }

    public void removeShaft() {
        this.hasShaft = false;
        this.shaftMaterial = null;
        this.shaftDiameter = null;
        setChanged();
        syncToClient(); // ИСПРАВЛЕНИЕ: Мгновенно сообщаем клиенту, что вал убрали!
    }

    @Override
    public boolean canConnectMechanically(BlockPos myPos, BlockPos neighborPos, Rotational neighbor) {
        if (!this.hasShaft()) return false;

        if (neighbor instanceof ShaftBlockEntity shaftBE) {
            if (shaftBE.getBlockState().getBlock() instanceof ShaftBlock shaftBlock) {
                return shaftBlock.getDiameter() == this.getShaftDiameter();
            }
        }
        if (neighbor instanceof BearingBlockEntity otherBearing) {
            return otherBearing.hasShaft() && otherBearing.getShaftDiameter() == this.getShaftDiameter();
        }
        return true;
    }

    @Override
    public void setSpeed(long speed) {
        // ВАЖНО: Кастуем к long после умножения на float!
        long actualSpeed = (long) (speed * this.networkScale);

        if (this.speed != actualSpeed) {
            this.speed = actualSpeed;
            setChanged();
            if (shouldSyncSpeed()) {
                this.lastSyncedSpeed = this.speed;
                syncToClient();
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

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public Direction[] getPropagationDirections() {
        if (!hasShaft) return new Direction[0];
        Direction facing = getBlockState().getValue(BearingBlock.FACING);
        return new Direction[]{facing, facing.getOpposite()};
    }

    @Override
    public long getInertiaContribution() {
        long baseInertia = 10;
        if (hasShaft && shaftMaterial != null && shaftDiameter != null) {
            baseInertia += (long) (shaftMaterial.baseInertia() * shaftDiameter.inertiaMod);
        }
        return baseInertia;
    }

    @Override
    public long getFrictionContribution() { return 1; }

    @Override
    public long getMaxTorqueTolerance() { return 10000; }
    @Override
    public long getMaxSpeed() { return 1024; }

    @Override
    public long getMaxTorque() {
        if (hasShaft() && getShaftMaterial() != null && getShaftDiameter() != null) {
            return (long) (getShaftMaterial().baseTorque() * getShaftDiameter().torqueMod);
        }
        return 10000;
    }

    @Override
    public long getSpeed() { return speed; }

    @Override
    public long getVisualSpeed() {
        if (!this.hasShaft) return 0;
        BlockState state = getBlockState();
        if (!state.hasProperty(BearingBlock.FACING)) return 0;

        Direction facing = state.getValue(BearingBlock.FACING);
        if (facing == Direction.SOUTH || facing == Direction.EAST || facing == Direction.UP) {
            return -this.speed;
        }
        return this.speed;
    }

    @Override
    public long getTorque() { return 0; }
    @Override
    public boolean isSource() { return false; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", this.speed);
        tag.putLong("LastSyncedSpeed", this.lastSyncedSpeed);
        tag.putBoolean("HasShaft", this.hasShaft);
        tag.putLong("LastSyncedSpeed", this.lastSyncedSpeed);
        tag.putFloat("NetworkScale", this.networkScale); // Сохраняем знак

        if (this.hasShaft && this.shaftMaterial != null && this.shaftDiameter != null) {
            tag.putString("ShaftMaterial", this.shaftMaterial.name());
            tag.putString("ShaftDiameter", this.shaftDiameter.name());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.speed = tag.getLong("Speed");
        this.lastSyncedSpeed = tag.getLong("LastSyncedSpeed");
        this.hasShaft = tag.getBoolean("HasShaft");
        this.lastSyncedSpeed = tag.getLong("LastSyncedSpeed");
        this.networkScale = tag.contains("NetworkScale") ? tag.getInt("NetworkScale") : 1; // Загружаем знак

        if (this.hasShaft) {
            // ИСПРАВЛЕНИЕ: Опускаем регистр в lowercase, чтобы switch находил совпадения
            String matName = tag.getString("ShaftMaterial").toLowerCase();
            String diaName = tag.getString("ShaftDiameter");

            this.shaftMaterial = switch (matName) {
                case "duralumin" -> ShaftMaterial.DURALUMIN;
                case "steel" -> ShaftMaterial.STEEL;
                case "titanium" -> ShaftMaterial.TITANIUM;
                case "tungsten_carbide" -> ShaftMaterial.TUNGSTEN_CARBIDE;
                default -> ShaftMaterial.IRON;
            };

            try {
                this.shaftDiameter = ShaftDiameter.valueOf(diaName);
            } catch (IllegalArgumentException e) {
                this.shaftDiameter = ShaftDiameter.MEDIUM;
            }
        }
    }

    @Override
    public java.util.List<BlockPos> getPotentialConnections(net.minecraft.world.level.Level level, BlockPos myPos) {
        // Если вала нет, подшипник ни с чем не соединяется
        if (!hasShaft) return java.util.Collections.emptyList();

        Direction facing = getBlockState().getValue(BearingBlock.FACING);
        // Передает вращение только вперед и назад по своей оси
        return java.util.List.of(myPos.relative(facing), myPos.relative(facing.getOpposite()));
    }



    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            KineticNetwork net = KineticNetworkManager.get((ServerLevel) level).getNetworkFor(worldPosition);
            if (net != null) {
                this.speed = net.getSpeed();
                this.lastSyncedSpeed = this.speed;
                net.requestRecalculation();
            }
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.2D);
    }

    @Override
    public void setNetworkScale(float scale) { this.networkScale = scale; }

    @Override
    public float getNetworkScale() { return this.networkScale; }
}