package com.cim.api.rotation;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import com.cim.block.basic.industrial.rotation.AdderBlock;
import com.cim.block.basic.industrial.rotation.MotorElectroBlock;
import com.cim.block.entity.industrial.rotation.AdderBlockEntity;
import com.cim.block.entity.industrial.rotation.MotorElectroBlockEntity;
import com.cim.block.entity.industrial.rotation.WindGenFlugerBlockEntity;

import java.util.HashSet;
import java.util.Set;

public class RotationNetworkHelper {
    private static final int MAX_SEARCH_DEPTH = 1024;

    @Nullable
    public static RotationSource findSource(@Nullable BlockEntity start, @Nullable Direction fromDir) {
        if (start == null) return null; // Защита 80 уровня
        return findSourceInternal(start, fromDir, new HashSet<>(), 0);
    }
    @Nullable
    private static RotationSource findSourceInternal(BlockEntity start, @Nullable Direction fromDir,
                                                     Set<BlockPos> visited, int depth) {
        if (start == null) return null;
        if (depth > MAX_SEARCH_DEPTH || visited.contains(start.getBlockPos())) {
            return null;
        }
        visited.add(start.getBlockPos());

        Level level = start.getLevel();
        if (level == null) return null;

        Direction[] nextDirs;
        if (start instanceof RotationalNode node) {
            nextDirs = node.getPropagationDirections(fromDir);
        } else {
            return null;
        }

        long currentTime = level.getGameTime();

        for (Direction dir : nextDirs) {
            BlockPos neighborPos = start.getBlockPos().relative(dir);
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) continue;

            // 1. Проверка стандартных источников (Мотор, Ветряк)
            RotationSource directSource = getDirectSource(neighbor, dir);
            if (directSource != null) {
                return directSource;
            }

            // 2. СПЕЦИАЛЬНАЯ ПРОВЕРКА ДЛЯ СУММАТОРА
            // Если сосед — сумматор, мы можем брать из него данные, ТОЛЬКО если стоим на его ВЫХОДЕ
            if (neighbor instanceof AdderBlockEntity adder) {
                Direction adderFacing = adder.getBlockState().getValue(AdderBlock.FACING);
                Direction adderOutput = adderFacing.getOpposite();

                // dir.getOpposite() — это сторона сумматора, к которой присоединен наш вал
                if (dir.getOpposite() == adderOutput) {
                    // Если вал стоит на выходе — возвращаем суммированную мощность
                    return new RotationSource(adder.getSpeed(), adder.getTorque());
                } else {
                    // Если вал стоит СБОКУ или СПЕРЕДИ — игнорируем сумматор как источник
                    continue;
                }
            }

            // 3. Дальше твоя обычная логика рекурсии для валов
            if (neighbor instanceof RotationalNode neighborNode) {
                // Оптимизация: верим кешу соседа, если он валиден
                if (neighborNode.isCacheValid(currentTime)) {
                    RotationSource cached = neighborNode.getCachedSource();
                    if (cached != null) return cached;
                }

                // Рекурсивный поиск
                RotationSource found = findSourceInternal(neighbor, dir.getOpposite(), visited, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Nullable
    private static RotationSource getDirectSource(BlockEntity neighbor, Direction dir) {
        if (neighbor instanceof MotorElectroBlockEntity motor) {
            // Строгая проверка: Мотор должен смотреть в сторону вала.
            // dir - направление от Вала к Мотору.
            // dir.getOpposite() - направление от Мотора к Валу.
            // Мотор отдает энергию только ПЕРЕД собой.
            Direction motorFacing = motor.getBlockState().getValue(MotorElectroBlock.FACING);
            if (motorFacing == dir.getOpposite()) {
                return new RotationSource(motor.getSpeed(), motor.getTorque());
            }
        } else if (neighbor instanceof WindGenFlugerBlockEntity windGen) {
            return new RotationSource(windGen.getSpeed(), windGen.getTorque());
        }
        return null;
    }
}