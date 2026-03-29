package com.cim.block.entity.fluids;

import com.cim.api.fluids.FluidNetworkManager;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.registries.ForgeRegistries;

public class FluidPipeBlockEntity extends BlockEntity {

    public static final ModelProperty<Fluid> FLUID_PROP = new ModelProperty<>();
    private Fluid filterFluid = Fluids.EMPTY;
    private boolean hasFlowed = false;

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_PIPE_BE.get(), pos, state);
    }

    // ==========================================
    // ЛОГИКА ЖИДКОСТНОЙ СЕТИ (БЕЗ ТИКОВ!)
    // ==========================================
    @Override
    public void onLoad() {
        super.onLoad();
        // Добавляем трубу в сеть только один раз при прогрузке чанка
        if (this.level != null && !this.level.isClientSide) {
            FluidNetworkManager manager = FluidNetworkManager.get((ServerLevel) this.level);
            if (!manager.hasNode(this.getBlockPos())) {
                manager.addNode(this.getBlockPos());
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Удаляем трубу из сети при разрушении блока или выгрузке чанка
        if (this.level != null && !this.level.isClientSide) {
            FluidNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
    }

    // ==========================================
    // ЛОГИКА ФИЛЬТРА И РЕНДЕРА
    // ==========================================
    public void setFilterFluid(Fluid fluid) {
        this.filterFluid = fluid;
        this.setChanged();
        this.requestModelDataUpdate();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public Fluid getFilterFluid() {
        return filterFluid;
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(FLUID_PROP, this.filterFluid)
                .build();
    }

    public boolean hasFlowed() {
        return hasFlowed;
    }

    public void setHasFlowed(boolean hasFlowed) {
        if (this.hasFlowed != hasFlowed) {
            this.hasFlowed = hasFlowed;
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                // Отправляем пакет клиенту, чтобы он начал рисовать пузырьки
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
            }
        }
    }

    // ==========================================
    // СОХРАНЕНИЕ И ПАКЕТЫ (NBT)
    // ==========================================
    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        net.minecraft.resources.ResourceLocation fluidKey = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(filterFluid);
        tag.putString("FilterFluid", fluidKey == null ? "minecraft:empty" : fluidKey.toString());
        tag.putBoolean("HasFlowed", this.hasFlowed); // Сохраняем флаг
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        net.minecraft.resources.ResourceLocation fluidKey = new net.minecraft.resources.ResourceLocation(tag.getString("FilterFluid"));
        this.filterFluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(fluidKey);
        if (this.filterFluid == null) this.filterFluid = net.minecraft.world.level.material.Fluids.EMPTY;
        this.hasFlowed = tag.getBoolean("HasFlowed"); // Загружаем флаг
        this.requestModelDataUpdate();
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
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        // Принудительно заставляем клиент перерисовать точки при получении пакета
        if (this.level != null && this.level.isClientSide) {
            this.requestModelDataUpdate();
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
        }
    }
}