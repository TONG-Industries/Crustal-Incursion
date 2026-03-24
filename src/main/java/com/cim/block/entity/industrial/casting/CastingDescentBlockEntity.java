package com.cim.block.entity.industrial.casting;

import com.cim.api.metal.Metal;
import com.cim.block.entity.ModBlockEntities;
import com.cim.multiblock.industrial.SmelterBlockEntity;
import com.cim.multiblock.system.MultiblockPartEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CastingDescentBlockEntity extends BlockEntity {
    private static final int TRANSFER_RATE = 10; // мб за тик
    private int transferCooldown = 0;

    public CastingDescentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_DESCENT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingDescentBlockEntity be) {
        if (be.transferCooldown > 0) {
            be.transferCooldown--;
            return;
        }

        // Ищем плавильню (теперь и через части мультиблока)
        SmelterBlockEntity smelter = be.findSmelter(level, pos);
        if (smelter == null) return;

        // Ищем котел снизу под собой
        CastingPotBlockEntity pot = be.findPotBelow(level, pos);
        if (pot == null) return;

        // Получаем самый нижний металл (первый добавленный - FIFO)
        Metal metalToTransfer = smelter.getBottomMetal();
        if (metalToTransfer == null) return;

        // Проверяем, может ли котёл принять этот конкретный металл
        if (!pot.canAcceptMetal(metalToTransfer)) return;

        // Смотрим сколько места осталось в котле
        int spaceAvailable = pot.getRemainingCapacity();
        if (spaceAvailable <= 0) return;

        // Переносим только то, что поместится!
        int toTransfer = Math.min(TRANSFER_RATE, spaceAvailable);
        int extracted = smelter.extractMetal(metalToTransfer, toTransfer);

        if (extracted > 0) {
            pot.addMetal(metalToTransfer, extracted);
            be.transferCooldown = 2; // Небольшая задержка между переносами
        }
    }

    private SmelterBlockEntity findSmelter(Level level, BlockPos pos) {
        // Ищем в 4 сторонах
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighborPos);

            // Прямое подключение к печи
            if (be instanceof SmelterBlockEntity smelter) {
                return smelter;
            }

            // Подключение через часть мультиблока (боковая часть печи)
            if (be instanceof MultiblockPartEntity part) {
                BlockPos controllerPos = part.getControllerPos();
                if (controllerPos != null) {
                    BlockEntity controller = level.getBlockEntity(controllerPos);
                    if (controller instanceof SmelterBlockEntity smelter) {
                        return smelter;
                    }
                }
            }
        }
        return null;
    }

    private CastingPotBlockEntity findPotBelow(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos.below());
        if (be instanceof CastingPotBlockEntity pot) {
            return pot;
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Cooldown", transferCooldown);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        transferCooldown = tag.getInt("Cooldown");
    }
}