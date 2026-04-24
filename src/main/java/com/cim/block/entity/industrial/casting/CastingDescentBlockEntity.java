package com.cim.block.entity.industrial.casting;

import com.cim.api.metallurgy.system.ISmelter;
import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.block.basic.industrial.casting.CastingDescentBlock;
import com.cim.block.basic.industrial.casting.SmallSmelterBlock;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CastingDescentBlockEntity extends BlockEntity {
    // Медленная передача без промежуточного буфера (фикс рассинхрона)
    private static final int TRANSFER_RATE = 3; // 3 единицы за раз (~3 самородка)
    private static final int TRANSFER_COOLDOWN = 0; // Задержка между передачами (8 тиков)
    private static final int MAX_TRANSFER_DISTANCE = 6;

    private int transferCooldown = 0;
    private boolean isPouring = false;
    private Metal pouringMetal = null;
    private int pouringTicks = 0;
    private int continuousPourTicks = 0;

    private int lastKnownDistance = 1;
    private float lastKnownFillLevel = 0.0f;

    public CastingDescentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_DESCENT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingDescentBlockEntity be) {
        Direction facing = state.getValue(CastingDescentBlock.FACING);
        Direction back = facing.getOpposite();
        BlockPos smelterPos = pos.relative(back);


        // Кулдаун для замедления передачи
        if (be.transferCooldown > 0) be.transferCooldown--;

        // Плавное завершение визуализации литья
        if (be.pouringTicks > 0) {
            be.pouringTicks--;
            if (be.pouringTicks <= 0) {
                be.setPouring(false, null);
                be.continuousPourTicks = 0;
            }
        }

        // Передача только когда кулдаун закончился
        if (be.transferCooldown > 0) return;

        ISmelter smelter = be.findSmelter(level, pos);
        if (smelter == null) {
            be.setPouring(false, null);
            return;
        }

        CastingPotBlockEntity mainPot = be.findPotBelow(level, pos);
        if (mainPot == null) {
            be.setPouring(false, null);
            return;
        }

        // ПОЛУЧАЕМ ПРИОРИТЕТНЫЕ МЕТАЛЛЫ ИЗ КОТЛОВ
        List<CastingPotBlockEntity> network = mainPot.findNetwork();
        List<Metal> preferredMetals = new ArrayList<>();

        for (CastingPotBlockEntity pot : network) {
            Metal potMetal = pot.getCurrentMetal();
            if (potMetal != null && !preferredMetals.contains(potMetal)) {
                preferredMetals.add(potMetal);
            }
        }

        int totalNetworkSpace = network.stream().mapToInt(CastingPotBlockEntity::getRemainingCapacity).sum();

        if (totalNetworkSpace <= 0) {
            be.setPouring(false, null);
            return;
        }

        // Выбираем металл с учетом приоритета
        Metal metalToTransfer = smelter.getMetalForCasting(preferredMetals);

        if (metalToTransfer == null) {
            be.setPouring(false, null);
            return;
        }

        boolean canAccept = network.stream().anyMatch(p -> p.canAcceptMetal(metalToTransfer));

        if (!canAccept) {
            be.setPouring(false, null);
            return;
        }

        // ПЕРЕДАЧА МЕТАЛЛА (сразу в котлы, без накопления в спуске!)
        int toTransfer = Math.min(TRANSFER_RATE, totalNetworkSpace);
        int extracted = smelter.extractMetal(metalToTransfer, toTransfer);

        if (extracted > 0) {
            // Сразу передаем в сеть котлов
            int filled = mainPot.fillNetwork(metalToTransfer, extracted);

            // Если передалось меньше чем извлекли (не должно произойти, но на всякий случай)
            if (filled < extracted) {
                // Возвращаем остаток обратно в плавильню (хотя это маловероятно)
                // Или просто теряем (но лучше предотвратить)
            }

            if (filled > 0) {
                be.lastKnownFillLevel = mainPot.getFillLevel();
                be.setPouring(true, metalToTransfer);
                be.transferCooldown = TRANSFER_COOLDOWN; // Задержка до следующей порции
                be.continuousPourTicks++;
            } else {
                be.setPouring(false, null);
            }
        } else {
            be.setPouring(false, null);
        }
    }

    private void setPouring(boolean pouring, Metal metal) {
        if (this.isPouring != pouring || this.pouringMetal != metal) {
            this.isPouring = pouring;
            this.pouringMetal = metal;
            if (pouring) {
                this.pouringTicks = 10;
            } else {
                this.pouringTicks = 0;
            }
            this.setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    private ISmelter findSmelter(Level level, BlockPos pos) {
        BlockState descentState = level.getBlockState(pos);
        Direction facing = descentState.getValue(CastingDescentBlock.FACING);
        Direction back = facing.getOpposite();

        // 1. Приоритет: позиция прямо сзади спуска (фронтовая сторона для мелкой плавильни)
        ISmelter smelter = checkSmelterAt(level, pos.relative(back), facing, true);
        if (smelter != null) return smelter;

        // 2. Остальные горизонтальные стороны — только для большой плавильни/нагревателя
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (dir == back) continue; // уже проверяли
            smelter = checkSmelterAt(level, pos.relative(dir), facing, false);
            if (smelter != null) return smelter;
        }

        return null;
    }

    private ISmelter checkSmelterAt(Level level, BlockPos checkPos, Direction descentFacing, boolean allowSmall) {
        BlockEntity be = level.getBlockEntity(checkPos);
        if (be instanceof ISmelter smelter) {
            // === МЕЛКАЯ ПЛАВИЛЬНЯ ===
            // Только фронтовое подключение: мелкая плавильня должна смотреть на спуск
            if (be instanceof SmallSmelterBlockEntity) {
                if (!allowSmall) return null;
                BlockState smelterState = level.getBlockState(checkPos);
                if (smelterState.hasProperty(SmallSmelterBlock.FACING)) {
                    Direction smelterFacing = smelterState.getValue(SmallSmelterBlock.FACING);
                    if (smelterFacing == descentFacing) {
                        return smelter;
                    }
                }
                return null;
            }

            // === БОЛЬШАЯ ПЛАВИЛЬНЯ / НАГРЕВАТЕЛЬ ===
            // Любая боковая сторона подходит
            return smelter;
        }

        // === МУЛЬТИБЛОК-ЧАСТИ ===
        if (be instanceof com.cim.multiblock.system.MultiblockPartEntity part) {
            BlockPos controllerPos = part.getControllerPos();
            if (controllerPos != null) {
                BlockEntity controller = level.getBlockEntity(controllerPos);
                if (controller instanceof ISmelter smelter) {
                    if (controller instanceof SmallSmelterBlockEntity) {
                        if (!allowSmall) return null;
                        BlockState smelterState = level.getBlockState(controllerPos);
                        if (smelterState.hasProperty(SmallSmelterBlock.FACING)) {
                            Direction smelterFacing = smelterState.getValue(SmallSmelterBlock.FACING);
                            if (smelterFacing == descentFacing) {
                                return smelter;
                            }
                        }
                        return null;
                    }
                    return smelter;
                }
            }
        }

        return null;
    }

    private CastingPotBlockEntity findPotBelow(Level level, BlockPos pos) {
        for (int i = 1; i <= MAX_TRANSFER_DISTANCE; i++) {
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

    public boolean isPouring() { return isPouring; }
    public Metal getPouringMetal() { return pouringMetal; }
    public float getStreamEndY() { return -lastKnownDistance + (0.25f * lastKnownFillLevel); }
    public int getContinuousPourTicks() { return continuousPourTicks; }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(getBlockPos().offset(-1, -6, -1), getBlockPos().offset(2, 1, 2));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Cooldown", transferCooldown);
        tag.putBoolean("IsPouring", isPouring);
        tag.putInt("PouringTicks", pouringTicks);
        tag.putInt("ContinuousPour", continuousPourTicks);
        tag.putInt("LastDistance", lastKnownDistance);
        tag.putFloat("LastFill", lastKnownFillLevel);
        if (pouringMetal != null) tag.putString("PouringMetal", pouringMetal.getId().toString());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        transferCooldown = tag.getInt("Cooldown");
        isPouring = tag.getBoolean("IsPouring");
        pouringTicks = tag.getInt("PouringTicks");
        continuousPourTicks = tag.getInt("ContinuousPour");
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
        if (pouringMetal != null) tag.putString("PouringMetal", pouringMetal.getId().toString());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) load(pkt.getTag());
    }
}