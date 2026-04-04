package com.cim.api.metallurgy.system;

import com.cim.event.HotItemHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

public class ItemHeatColorRegistry {

    // Типы градиентов нагрева
    public enum HeatGradient {
        ORANGE_TO_WHITE(255, 100, 0),      // Ярко-оранжевый → белый (стандарт для металлов)
        RED_TO_WHITE(255, 50, 50),         // Ярко-красный → белый (высокотемпературные металлы)
        YELLOW_TO_WHITE(255, 220, 50),     // Ярко-жёлтый → белый (золото, медь)
        BLUE_TO_WHITE(100, 150, 255);      // Голубой → белый (магические/редкие металлы)

        public final int hotR, hotG, hotB;

        HeatGradient(int hotR, int hotG, int hotB) {
            this.hotR = hotR;
            this.hotG = hotG;
            this.hotB = hotB;
        }
    }

    private static final Map<Item, HeatGradient> itemGradients = new HashMap<>();
    private static final HeatGradient DEFAULT_GRADIENT = HeatGradient.ORANGE_TO_WHITE;

    /**
     * Регистрирует предмет с указанным градиентом нагрева
     */
    public static void register(Item item, HeatGradient gradient) {
        itemGradients.put(item, gradient);
    }

    /**
     * Регистрирует блок (через его предмет-форму) с указанным градиентом
     */
    public static void register(Block block, HeatGradient gradient) {
        Item item = block.asItem();
        if (item != null && item != Items.AIR) {
            register(item, gradient);
        }
    }

    /**
     * Регистрирует предметы из RegistryObject
     */
    public static void register(RegistryObject<Item> itemObj, HeatGradient gradient) {
        if (itemObj.isPresent()) {
            register(itemObj.get(), gradient);
        }
    }

    /**
     * Регистрирует блоки из RegistryObject
     */
    public static void registerBlock(RegistryObject<Block> blockObj, HeatGradient gradient) {
        if (blockObj.isPresent()) {
            register(blockObj.get(), gradient);
        }
    }

    /**
     * Регистрирует несколько предметов с одним градиентом
     */
    public static void registerAll(HeatGradient gradient, Item... items) {
        for (Item item : items) {
            register(item, gradient);
        }
    }

    /**
     * Регистрирует несколько блоков с одним градиентом
     */
    public static void registerAllBlocks(HeatGradient gradient, Block... blocks) {
        for (Block block : blocks) {
            register(block, gradient);
        }
    }

    /**
     * Регистрирует смешанный список (Item и Block) с одним градиентом
     * Используй Object... для смешанных типов
     */
    public static void registerMixed(HeatGradient gradient, Object... itemsAndBlocks) {
        for (Object obj : itemsAndBlocks) {
            if (obj instanceof Item item) {
                register(item, gradient);
            } else if (obj instanceof Block block) {
                register(block, gradient);
            } else if (obj instanceof RegistryObject<?> regObj) {
                if (regObj.get() instanceof Item item) {
                    register(item, gradient);
                } else if (regObj.get() instanceof Block block) {
                    register(block, gradient);
                }
            }
        }
    }

    /**
     * Регистрирует несколько предметов из RegistryObject
     */
    @SafeVarargs
    public static void registerAll(HeatGradient gradient, RegistryObject<Item>... items) {
        for (RegistryObject<Item> item : items) {
            register(item, gradient);
        }
    }

    /**
     * Регистрирует несколько блоков из RegistryObject
     */
    @SafeVarargs
    public static void registerAllBlocks(HeatGradient gradient, RegistryObject<Block>... blocks) {
        for (RegistryObject<Block> block : blocks) {
            registerBlock(block, gradient);
        }
    }

    /**
     * Получает градиент для предмета
     */
    public static HeatGradient getGradient(ItemStack stack) {
        return itemGradients.getOrDefault(stack.getItem(), DEFAULT_GRADIENT);
    }

    /**
     * Вычисляет цвет нагрева для предмета
     * @return цвет в формате ARGB или -1 если предмет не горячий
     */
    public static int getHeatColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return -1;
        if (!HotItemHandler.isHot(stack)) return -1;

        float ratio = HotItemHandler.getHeatRatio(stack);

        // Если остыл меньше чем на 5% - показываем оригинальную текстуру
        if (ratio < 0.05f) return -1;

        HeatGradient gradient = getGradient(stack);

        // Интерполяция от горячего цвета к белому (255, 255, 255)
        int r = (int) (gradient.hotR + (255 - gradient.hotR) * (1 - ratio));
        int g = (int) (gradient.hotG + (255 - gradient.hotG) * (1 - ratio));
        int b = (int) (gradient.hotB + (255 - gradient.hotB) * (1 - ratio));

        // Убеждаемся что значения в пределах 0-255
        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Специальный цвет для шлака (с учётом цвета металла)
     */
    public static int getSlagHeatColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return -1;
        if (!stack.hasTag()) return 0xFF555555;

        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        int metalColor = tag.contains("Color") ? tag.getInt("Color") : 0x888888;
        int mr = (metalColor >> 16) & 0xFF;
        int mg = (metalColor >> 8) & 0xFF;
        int mb = metalColor & 0xFF;

        // Если горячий - яркий цвет + оранжевый оттенок от температуры
        if (HotItemHandler.isHot(stack)) {
            float ratio = HotItemHandler.getHeatRatio(stack);

            // Остывший: темный фон + чуть цвета металла
            int coldR = 0x33 + (mr / 4);
            int coldG = 0x33 + (mg / 4);
            int coldB = 0x33 + (mb / 4);

            // Горячий: яркий цвет металла + оранжевый оттенок от температуры
            int hotR = Math.min(255, mr + (int)(60 * ratio));
            int hotG = Math.min(255, mg + (int)(30 * ratio));
            int hotB = Math.min(255, mb + (int)(10 * ratio));

            int finalR = (int) (coldR + (hotR - coldR) * ratio);
            int finalG = (int) (coldG + (hotG - coldG) * ratio);
            int finalB = (int) (coldB + (hotB - coldB) * ratio);

            return (0xFF << 24) | (finalR << 16) | (finalG << 8) | finalB;
        } else {
            // Остывший шлак - темно-серый с оттенком металла
            int dr = 0x33 + (mr / 4);
            int dg = 0x33 + (mg / 4);
            int db = 0x33 + (mb / 4);
            return (0xFF << 24) | (dr << 16) | (dg << 8) | db;
        }
    }
}