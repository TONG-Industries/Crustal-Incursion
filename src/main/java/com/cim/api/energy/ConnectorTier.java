package com.cim.api.energy;

/**
 * @param maxLength Максимальная длина провода в блоках
 * @param maxConnections Сколько проводов можно воткнуть в один этот коннектор
 * @param wireRadius Толщина визуального провода (например, 0.03125f для обычного)
 * @param width Ширина коллизии коннектора в пикселях (x и z)
 * @param height Высота коллизии коннектора в пикселях (y)
 */
public record ConnectorTier(int maxLength, int maxConnections, float wireRadius, double width, double height) {}