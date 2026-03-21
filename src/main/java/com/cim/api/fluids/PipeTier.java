package com.cim.api.fluids;

public enum PipeTier {
    // Название (Макс. Температура, Макс. Кислотность, Макс. Радиация)
    COPPER(300, 0, 0),        // Медная: слабая, для воды
    STEEL(1500, 2, 0),        // Стальная: держит лаву, но боится сильной кислоты
    LEAD(500, 10, 100),       // Свинцовая: для кислоты и радиоактивных отходов
    TUNGSTEN(3000, 10, 100);  // Вольфрамовая: держит вообще всё

    private final int maxTemperature;
    private final int maxAcidity;
    private final int maxRadiation;

    PipeTier(int maxTemperature, int maxAcidity, int maxRadiation) {
        this.maxTemperature = maxTemperature;
        this.maxAcidity = maxAcidity;
        this.maxRadiation = maxRadiation;
    }

    public int getMaxTemperature() { return maxTemperature; }
    public int getMaxAcidity() { return maxAcidity; }
    public int getMaxRadiation() { return maxRadiation; }
}