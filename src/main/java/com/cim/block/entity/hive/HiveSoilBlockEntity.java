package com.cim.block.entity.hive;


import com.cim.api.hive.HiveNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.cim.api.hive.HiveNetworkMember;
import com.cim.block.entity.ModBlockEntities;

import java.util.UUID;

public class HiveSoilBlockEntity extends BlockEntity implements HiveNetworkMember {
    private UUID networkId;

    public HiveSoilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HIVE_SOIL.get(), pos, state);
    }

    @Override
    public UUID getNetworkId() { return networkId; }

    @Override
    public void setNetworkId(UUID id) {
        this.networkId = id;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.networkId != null) tag.putUUID("NetworkId", this.networkId);
    }
    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.networkId != null) {
            // Регистрируем почву в менеджере при загрузке чанка
            // false — потому что почва не является гнездом для червей
            HiveNetworkManager.get(this.level).addNode(this.networkId, this.worldPosition, false);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("NetworkId")) this.networkId = tag.getUUID("NetworkId");
    }
}
