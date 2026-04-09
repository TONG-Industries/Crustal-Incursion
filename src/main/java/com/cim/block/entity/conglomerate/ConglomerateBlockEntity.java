package com.cim.block.entity.conglomerate;

import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

public class ConglomerateBlockEntity extends BlockEntity {
    private UUID veinId;
    private float localDepletion = 0.0f; // 0.0 - 1.0 для визуала
    private int blockOu = 1000; // Буфер конкретного блока (10 успешных ударов)

    public ConglomerateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONGLOMERATE.get(), pos, state);
    }
    public int getBlockOu() {
        return blockOu;
    }

    public void consumeOu(int amount) {
        this.blockOu = Math.max(0, this.blockOu - amount);
        setChanged();
    }
    public void setVeinId(UUID id) {
        this.veinId = id;
        setChanged();
    }

    public UUID getVeinId() {
        return veinId;
    }

    public void setLocalDepletion(float ratio) {
        this.localDepletion = Math.min(1.0f, Math.max(0.0f, ratio));
        setChanged();
        // Синхронизация для клиента (если будешь менять текстуру через blockstates)
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private boolean depleted = false;

    public void markDepleted() {
        this.depleted = true;
        setChanged();
    }

    public boolean isDepleted() {
        return depleted;
    }

    // Обновить saveAdditional и load:
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (veinId != null) tag.putUUID("VeinId", veinId);
        tag.putFloat("Depletion", localDepletion);
        tag.putBoolean("Depleted", depleted);
        tag.putInt("BlockOu", blockOu); // Сохраняем OU блока
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("VeinId")) veinId = tag.getUUID("VeinId");
        localDepletion = tag.getFloat("Depletion");
        depleted = tag.getBoolean("Depleted");
        if (tag.contains("BlockOu")) {
            blockOu = tag.getInt("BlockOu"); // Загружаем OU
        } else {
            blockOu = 1000; // Для старых блоков, где тега еще нет
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }
}