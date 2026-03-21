package com.cim.api.fluids;

public enum PipeTier {
    // Название (Макс. Температура, Макс. Кислотность, Макс. Радиация)
    BRONZE(423, 0, 0),        // Бронзовая: слабая, для воды
    STEEL(1773, 30, 0),        // Стальная: держит лаву, но боится сильной кислоты
    LEAD(673, 60, 100),       // Свинцовая: для кислоты и радиоактивных отходов
    TUNGSTEN(3273, 100, 70);  // Вольфрамовая: держит вообще всё

    private final int maxTemperature; //температура в Кельвинах, при которой труба начинает плавиться
    private final int maxAcidity; //кислотность от 0 (нейтральная) до 100 (сильнокислая), при которой труба начинает корродировать
    private final int maxRadiation; //радиация от 0 (безопасная) до 100 (сильно радиоактивная), при которой труба начинает излучать радиацию.

    PipeTier(int maxTemperature, int maxAcidity, int maxRadiation) {
        this.maxTemperature = maxTemperature;
        this.maxAcidity = maxAcidity;
        this.maxRadiation = maxRadiation;
    }

    public int getMaxTemperature() { return maxTemperature; }
    public int getMaxAcidity() { return maxAcidity; }
    public int getMaxRadiation() { return maxRadiation; }
}