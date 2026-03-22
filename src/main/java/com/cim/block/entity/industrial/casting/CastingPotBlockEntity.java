package com.cim.block.entity.industrial.casting;

import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CastingPotBlockEntity extends BlockEntity {
    private ItemStack mold = ItemStack.EMPTY;
    private static final String TAG_MOLD = "Mold";

    public CastingPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_POT.get(), pos, state);
    }

    public ItemStack getMold() {
        return mold;
    }

    public void setMold(ItemStack stack) {
        this.mold = stack.copy();
        this.setChanged();

        if (level != null && !level.isClientSide) {
            // Отправляем обновление всем клиентам
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasMold() {
        return !mold.isEmpty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Всегда сохраняем, даже пустой, для консистентности
        tag.put(TAG_MOLD, mold.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_MOLD, CompoundTag.TAG_COMPOUND)) {
            mold = ItemStack.of(tag.getCompound(TAG_MOLD));
        } else {
            mold = ItemStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        // Критично: всегда возвращаем тег, даже с пустым стаком!
        // Иначе клиент не получит обновление при удалении предмета
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_MOLD, mold.save(new CompoundTag()));
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        // Принудительно обрабатываем пакет на клиенте
        if (pkt.getTag() != null) {
            CompoundTag tag = pkt.getTag();
            if (tag.contains(TAG_MOLD, CompoundTag.TAG_COMPOUND)) {
                this.mold = ItemStack.of(tag.getCompound(TAG_MOLD));
            } else {
                this.mold = ItemStack.EMPTY;
            }
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        // Явно обрабатываем начальную загрузку данных
        this.load(tag);
    }
}