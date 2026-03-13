package com.cim.block.entity.deco;

import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BeamCollisionBlockEntity extends BlockEntity {
    private boolean isMaster = false;

    // Данные для "Мастера" (рендерера)
    private Vec3 startPos = null;
    private Vec3 endPos = null;

    // Данные для обычных блоков коллизии
    private BlockPos masterPos = null;

    public BeamCollisionBlockEntity(BlockPos pPos, BlockState pBlockState) {
        // Обязательно замени на свой BEAM_COLLISION_BE из ModBlockEntities!
        super(ModBlockEntities.BEAM_COLLISION_BE.get(), pPos, pBlockState);
    }

    public void setMasterData(Vec3 start, Vec3 end) {
        this.isMaster = true;
        this.startPos = start;
        this.endPos = end;
        this.setChanged();
        // Синхронизируем с клиентом для рендера
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void setSlaveData(BlockPos masterPos) {
        this.isMaster = false;
        this.masterPos = masterPos;
        this.setChanged();
    }

    public boolean isMaster() { return isMaster; }
    public Vec3 getStartPos() { return startPos; }
    public Vec3 getEndPos() { return endPos; }
    public BlockPos getMasterPos() { return masterPos; }

    // --- СОХРАНЕНИЕ NBT ---

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        super.saveAdditional(pTag);
        pTag.putBoolean("IsMaster", isMaster);

        if (isMaster && startPos != null && endPos != null) {
            pTag.putDouble("StartX", startPos.x);
            pTag.putDouble("StartY", startPos.y);
            pTag.putDouble("StartZ", startPos.z);
            pTag.putDouble("EndX", endPos.x);
            pTag.putDouble("EndY", endPos.y);
            pTag.putDouble("EndZ", endPos.z);
        } else if (!isMaster && masterPos != null) {
            pTag.put("MasterPos", NbtUtils.writeBlockPos(masterPos));
        }
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.isMaster = pTag.getBoolean("IsMaster");

        if (isMaster) {
            if (pTag.contains("StartX")) {
                this.startPos = new Vec3(pTag.getDouble("StartX"), pTag.getDouble("StartY"), pTag.getDouble("StartZ"));
                this.endPos = new Vec3(pTag.getDouble("EndX"), pTag.getDouble("EndY"), pTag.getDouble("EndZ"));
            }
        } else {
            if (pTag.contains("MasterPos")) {
                this.masterPos = NbtUtils.readBlockPos(pTag.getCompound("MasterPos"));
            }
        }
    }

    // --- СИНХРОНИЗАЦИЯ КЛИЕНТА (ОЧЕНЬ ВАЖНО ДЛЯ РЕНДЕРА) ---

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- КОРОБКА РЕНДЕРА ---

    @Override
    public AABB getRenderBoundingBox() {
        if (isMaster && startPos != null && endPos != null) {
            // Теперь коробка точно охватывает пространство между центрами блоков!
            return new AABB(startPos, endPos).inflate(1.0);
        }
        return super.getRenderBoundingBox();
    }
}