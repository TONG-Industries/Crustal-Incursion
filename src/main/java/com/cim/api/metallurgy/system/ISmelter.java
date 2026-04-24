package com.cim.api.metallurgy.system;

import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.List;

public interface ISmelter {
    /**
     * Извлечь металл из резервуара.
     * @param metal тип металла
     * @param maxUnits максимальное количество единиц для извлечения
     * @return сколько реально извлечено
     */
    int extractMetal(Metal metal, int maxUnits);

    /**
     * Получить металл для литья с учётом приоритета.
     * @param preferredMetals список предпочтительных металлов (металлы, уже находящиеся в котлах)
     * @return металл, который можно отлить, или null
     */
    Metal getMetalForCasting(List<Metal> preferredMetals);

    /**
     * Проверить, есть ли в резервуаре хотя бы один металл.
     */
    boolean hasMetal();

    /**
     * Общее количество единиц металла в резервуаре.
     */
    int getTotalMetalAmount();

    /**
     * Вместимость резервуара в единицах.
     */
    int getSmelterCapacity();

    /**
     * Получить блок-энтайти (для доступа к позиции, миру и т.д.)
     */
    BlockEntity asBlockEntity();
}