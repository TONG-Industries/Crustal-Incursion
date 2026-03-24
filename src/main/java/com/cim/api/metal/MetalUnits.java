package com.cim.api.metal;

public final class MetalUnits {
    // Константы конвертации
    public static final int MB_PER_BLOCK = 1000;
    public static final int MB_PER_INGOT = 111;  // 9 слитков ≈ 1000mb (999)
    public static final int MB_PER_NUGGET = 12;  // 9 самородков ≈ 1 слиток (108)

    private MetalUnits() {}

    /**
     * Распределяет mb на блоки/слитки/самородки с автоматическим переносом 9→1
     */
    public static MetalStack convert(int totalMb) {
        if (totalMb <= 0) {
            return new MetalStack(0, 0, 0, 0, 0);
        }

        int blocks = totalMb / MB_PER_BLOCK;
        int rem = totalMb % MB_PER_BLOCK;

        int ingots = rem / MB_PER_INGOT;
        rem = rem % MB_PER_INGOT;

        int nuggets = rem / MB_PER_NUGGET;
        int leftover = rem % MB_PER_NUGGET;

        // Автоматический перенос: 9 самородков → 1 слиток
        ingots += nuggets / 9;
        nuggets = nuggets % 9;

        // Автоматический перенос: 9 слитков → 1 блок
        blocks += ingots / 9;
        ingots = ingots % 9;

        return new MetalStack(blocks, ingots, nuggets, leftover, totalMb);
    }

    /**
     * Конвертирует блоки/слитки/самородки обратно в mb
     */
    public static int toMb(int blocks, int ingots, int nuggets) {
        return blocks * MB_PER_BLOCK + ingots * MB_PER_INGOT + nuggets * MB_PER_NUGGET;
    }

    /**
     * Форматирует для отображения с автоматическим переносом
     */
    public static String format(int totalMb) {
        MetalStack stack = convert(totalMb);
        StringBuilder sb = new StringBuilder();

        if (stack.blocks > 0) sb.append(stack.blocks).append("б ");
        if (stack.ingots > 0) sb.append(stack.ingots).append("сл ");
        if (stack.nuggets > 0) sb.append(stack.nuggets).append("см");

        String result = sb.toString().trim();
        return result.isEmpty() ? totalMb + "мб" : result;
    }

    /**
     * Форматирует полностью с названиями (блоков/слитков/самородков)
     */
    public static String formatFull(int totalMb) {
        MetalStack stack = convert(totalMb);
        StringBuilder sb = new StringBuilder();

        boolean hasContent = false;

        if (stack.blocks > 0) {
            sb.append(stack.blocks).append(" блоков ");
            hasContent = true;
        }
        if (stack.ingots > 0) {
            sb.append(stack.ingots).append(" слитков ");
            hasContent = true;
        }
        if (stack.nuggets > 0) {
            sb.append(stack.nuggets).append(" самородков");
            hasContent = true;
        }

        if (!hasContent) {
            sb.append("<1 самородка");
        }

        return sb.toString().trim();
    }

    public record MetalStack(int blocks, int ingots, int nuggets, int leftover, int totalMb) {
        public boolean isEmpty() { return totalMb == 0; }

        public int getDisplayTotalIngots() {
            return blocks * 9 + ingots + (nuggets >= 4 ? 1 : 0);
        }

        /**
         * Возвращает строку с переносами для отладки
         */
        public String toDebugString() {
            return String.format("Blocks: %d, Ingots: %d, Nuggets: %d, Leftover: %d mb, Total: %d mb",
                    blocks, ingots, nuggets, leftover, totalMb);
        }
    }
}