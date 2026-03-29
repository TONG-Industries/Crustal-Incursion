package com.cim.block.entity.industrial.casting;

import com.cim.api.metal.Metal;
import com.cim.api.metal.MetallurgyRegistry;
import com.cim.block.entity.ModBlockEntities;
import com.cim.multiblock.industrial.SmelterBlockEntity;
import com.cim.multiblock.system.MultiblockPartEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class CastingDescentBlockEntity extends BlockEntity {
    private static final int TRANSFER_RATE = 10;
    private static final int POURING_TICKS = 10;
    private int transferCooldown = 0;

    private boolean isPouring = false;
    private Metal pouringMetal = null;
    private int pouringTicks = 0;

    private int lastKnownDistance = 1;
    private float lastKnownFillLevel = 0.0f;

    public CastingDescentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_DESCENT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingDescentBlockEntity be) {
        // 1. Уменьшаем кулдауны
        if (be.transferCooldown > 0) be.transferCooldown--;

        // Таймер автоматического отключения струи (на случай если что-то пойдет не так)
        if (be.pouringTicks > 0) {
            be.pouringTicks--;
            if (be.pouringTicks <= 0) {
                be.setPouring(false, null);
            }
        }

        // Ограничиваем частоту логики передачи (раз в 2 тика)
        if (be.transferCooldown > 0) return;

        // 2. Ищем источник (Плавильня)
        SmelterBlockEntity smelter = be.findSmelter(level, pos);
        if (smelter == null) {
            be.setPouring(false, null);
            return;
        }

        Metal metalToTransfer = smelter.getBottomMetal();
        if (metalToTransfer == null) {
            be.setPouring(false, null);
            return;
        }

        // 3. Ищем вход в сеть (Котел прямо под спуском)
        CastingPotBlockEntity mainPot = be.findPotBelow(level, pos);
        if (mainPot == null) {
            be.setPouring(false, null);
            return;
        }

        // 4. ЛОГИКА СЕТИ: Получаем все котлы в линии
        List<CastingPotBlockEntity> network = mainPot.findNetwork();

        // Считаем общую свободную емкость всей линии (до 7 блоков)
        int totalNetworkSpace = network.stream().mapToInt(CastingPotBlockEntity::getRemainingCapacity).sum();

        // Проверяем, может ли ХОТЯ БЫ ОДИН котел в этой сети принять наш металл
        boolean canAnyPoolAccept = network.stream().anyMatch(p -> p.canAcceptMetal(metalToTransfer));

        // 5. ПЕРЕДАЧА: Если место есть и металл подходит
        if (canAnyPoolAccept && totalNetworkSpace > 0) {
            int toTransfer = Math.min(TRANSFER_RATE, totalNetworkSpace);
            int extracted = smelter.extractMetal(metalToTransfer, toTransfer);

            if (extracted > 0) {
                // Распределяем металл по всем котлам в сети одновременно
                mainPot.fillNetwork(metalToTransfer, extracted);

                // Обновляем данные для рендера струи (высота зависит от котла прямо под нами)
                be.lastKnownFillLevel = mainPot.getFillLevel();
                be.setPouring(true, metalToTransfer);

                be.pouringTicks = POURING_TICKS;
                be.transferCooldown = 2; // Кулдаун перед следующей порцией
            } else {
                // ИСПРАВЛЕНИЕ: Если в плавильне кончился металл — гасим струю МГНОВЕННО
                be.setPouring(false, null);
            }
        } else {
            // ИСПРАВЛЕНИЕ: Если сеть полна или металлы несовместимы — гасим струю МГНОВЕННО
            be.setPouring(false, null);
        }
    }


    private void setPouring(boolean pouring, Metal metal) {
        if (this.isPouring != pouring || this.pouringMetal != metal) {
            this.isPouring = pouring;
            this.pouringMetal = metal;
            if (!pouring) {
                this.pouringTicks = 0;
            }
            this.setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    private SmelterBlockEntity findSmelter(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighborPos);
            if (be instanceof SmelterBlockEntity smelter) return smelter;
            if (be instanceof MultiblockPartEntity part) {
                BlockPos controllerPos = part.getControllerPos();
                if (controllerPos != null) {
                    BlockEntity controller = level.getBlockEntity(controllerPos);
                    if (controller instanceof SmelterBlockEntity smelter) return smelter;
                }
            }
        }
        return null;
    }

    private CastingPotBlockEntity findPotBelow(Level level, BlockPos pos) {
        for (int i = 1; i <= 6; i++) {
            BlockPos checkPos = pos.below(i);
            BlockEntity be = level.getBlockEntity(checkPos);
            if (be instanceof CastingPotBlockEntity pot) {
                lastKnownDistance = i;
                return pot;
            }
            if (!level.getBlockState(checkPos).isAir()) return null;
        }
        return null;
    }

    // ==================== Геттеры ====================

    public boolean isPouring() { return isPouring; }
    public Metal getPouringMetal() { return pouringMetal; }
    public float getStreamEndY() {
        return -lastKnownDistance + (0.25f * lastKnownFillLevel);
    }

    // ВАЖНО: Расширяем хитбокс для рендера чтобы он включал всю струю вниз
    @Override
    public AABB getRenderBoundingBox() {
        // Струя может идти вниз на 6 блоков + запас
        return new AABB(getBlockPos().offset(-1, -6, -1), getBlockPos().offset(2, 1, 2));
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Cooldown", transferCooldown);
        tag.putBoolean("IsPouring", isPouring);
        tag.putInt("PouringTicks", pouringTicks);
        tag.putInt("LastDistance", lastKnownDistance);
        tag.putFloat("LastFill", lastKnownFillLevel);
        if (pouringMetal != null) {
            tag.putString("PouringMetal", pouringMetal.getId().toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        transferCooldown = tag.getInt("Cooldown");
        isPouring = tag.getBoolean("IsPouring");
        pouringTicks = tag.getInt("PouringTicks");
        lastKnownDistance = tag.getInt("LastDistance");
        if (lastKnownDistance == 0) lastKnownDistance = 1;
        lastKnownFillLevel = tag.getFloat("LastFill");

        if (tag.contains("PouringMetal")) {
            ResourceLocation id = new ResourceLocation(tag.getString("PouringMetal"));
            MetallurgyRegistry.get(id).ifPresent(m -> pouringMetal = m);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("IsPouring", isPouring);
        tag.putInt("PouringTicks", pouringTicks);
        tag.putInt("LastDistance", lastKnownDistance);
        tag.putFloat("LastFill", lastKnownFillLevel);
        if (pouringMetal != null) {
            tag.putString("PouringMetal", pouringMetal.getId().toString());
        }
        return tag;
    }


    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            this.load(pkt.getTag());
            // Важно: на клиенте вызываем обновление рендера
            if (level != null && level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

}