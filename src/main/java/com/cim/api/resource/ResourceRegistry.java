package com.cim.api.resource;

import com.cim.block.basic.ModBlocks;
import com.cim.item.ModItems;
import com.cim.main.CrustalIncursionMod;
import com.cim.main.ModCreativeTabs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Универсальный регистр ресурсов (металлы, полимеры, кристаллы и т.д.).
 * Регистрирует основную единицу (слиток/гранула/кристалл),
 * опционально мелкую единицу (самородок/кусочек) и блок.
 * Автоматически добавляет в CIM_RECOURSES_TAB и генерирует рецепты крафта.
 */
public class ResourceRegistry {

    // Все зарегистрированные ресурсы
    private static final List<ResourceEntry> RESOURCES = new ArrayList<>();

    // Флаг инициализации
    private static boolean initialized = false;

    /**
     * Тип ресурса (влияет на названия и поведение)
     */
    public enum ResourceType {
        METAL("ingot", "nugget", "block"),
        POLYMER("granule", "piece", "block"),
        CRYSTAL("crystal", "shard", "block"),
        GEM("gem", "fragment", "block"),
        MINERAL("lump", "dust", "block");

        public final String mainUnit;
        public final String smallUnit;
        public final String blockName;

        ResourceType(String mainUnit, String smallUnit, String blockName) {
            this.mainUnit = mainUnit;
            this.smallUnit = smallUnit;
            this.blockName = blockName;
        }
    }

    /**
     * Конфигурация ресурса
     */
    public static class ResourceConfig {
        private final String name;
        private final ResourceType type;
        private final boolean hasSmallUnit;
        private final boolean hasBlock;
        private final BlockBehaviour.Properties blockProperties;

        public ResourceConfig(String name, ResourceType type) {
            this(name, type, false, false);
        }

        public ResourceConfig(String name, ResourceType type, boolean hasSmallUnit, boolean hasBlock) {
            this.name = name;
            this.type = type;
            this.hasSmallUnit = hasSmallUnit;
            this.hasBlock = hasBlock;
            this.blockProperties = BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 5.0f)
                    .requiresCorrectToolForDrops();
        }

        public ResourceConfig(String name, ResourceType type, boolean hasSmallUnit, boolean hasBlock,
                              BlockBehaviour.Properties blockProperties) {
            this.name = name;
            this.type = type;
            this.hasSmallUnit = hasSmallUnit;
            this.hasBlock = hasBlock;
            this.blockProperties = blockProperties;
        }
    }

    /**
     * Запись о зарегистрированном ресурсе
     */
    public static class ResourceEntry {
        public final String name;
        public final ResourceType type;
        public final RegistryObject<Item> mainUnit;
        public final RegistryObject<Item> smallUnit;
        public final RegistryObject<Block> block;
        public final RegistryObject<Item> blockItem;

        public ResourceEntry(String name, ResourceType type,
                             RegistryObject<Item> mainUnit,
                             RegistryObject<Item> smallUnit,
                             RegistryObject<Block> block,
                             RegistryObject<Item> blockItem) {
            this.name = name;
            this.type = type;
            this.mainUnit = mainUnit;
            this.smallUnit = smallUnit;
            this.block = block;
            this.blockItem = blockItem;
        }

        public boolean hasSmallUnit() {
            return smallUnit != null;
        }

        public boolean hasBlock() {
            return block != null;
        }

        public String getMainUnitId() {
            return mainUnit.getId().getPath();
        }

        public String getSmallUnitId() {
            return smallUnit != null ? smallUnit.getId().getPath() : null;
        }

        public String getBlockId() {
            return block != null ? block.getId().getPath() : null;
        }
    }

    /**
     * Регистрирует ресурс с полной конфигурацией
     */
    public static ResourceEntry register(ResourceConfig config) {
        String name = config.name;
        ResourceType type = config.type;

        // 1. Основная единица (всегда)
        String mainUnitName = name + "_" + type.mainUnit;
        RegistryObject<Item> mainUnit = ModItems.ITEMS.register(mainUnitName,
                () -> new Item(new Item.Properties()));

        // 2. Мелкая единица (если нужно)
        RegistryObject<Item> smallUnit = null;
        if (config.hasSmallUnit) {
            String smallUnitName = name + "_" + type.smallUnit;
            smallUnit = ModItems.ITEMS.register(smallUnitName,
                    () -> new Item(new Item.Properties()));
        }

        // 3. Блок и его предмет (если нужно)
        RegistryObject<Block> block = null;
        RegistryObject<Item> blockItem = null;

        if (config.hasBlock) {
            String blockName = name + "_" + type.blockName;
            block = ModBlocks.BLOCKS.register(blockName,
                    () -> new Block(config.blockProperties));

            final RegistryObject<Block> blockRef = block;
            blockItem = ModItems.ITEMS.register(blockName,
                    () -> new BlockItem(blockRef.get(), new Item.Properties()));
        }

        ResourceEntry entry = new ResourceEntry(name, type, mainUnit, smallUnit, block, blockItem);
        RESOURCES.add(entry);

        CrustalIncursionMod.LOGGER.info("Registered resource: {} (type={}, main={}, small={}, block={})",
                name, type.name(), true, config.hasSmallUnit, config.hasBlock);

        return entry;
    }

    // ============ Удобные методы ============

    public static ResourceEntry registerMainOnly(String name, ResourceType type) {
        return register(new ResourceConfig(name, type, false, false));
    }

    public static ResourceEntry registerWithSmall(String name, ResourceType type) {
        return register(new ResourceConfig(name, type, true, false));
    }

    public static ResourceEntry registerWithBlock(String name, ResourceType type) {
        return register(new ResourceConfig(name, type, false, true));
    }

    public static ResourceEntry registerFull(String name, ResourceType type) {
        return register(new ResourceConfig(name, type, true, true));
    }

    public static ResourceEntry registerFull(String name, ResourceType type,
                                             BlockBehaviour.Properties blockProps) {
        return register(new ResourceConfig(name, type, true, true, blockProps));
    }

    // ============ Креативная вкладка ============

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() != ModCreativeTabs.CIM_RECOURSES_TAB.get()) {
            return;
        }

        // Основные единицы
        for (ResourceEntry resource : RESOURCES) {
            event.accept(resource.mainUnit.get());
        }

        // Мелкие единицы
        for (ResourceEntry resource : RESOURCES) {
            if (resource.smallUnit != null) {
                event.accept(resource.smallUnit.get());
            }
        }

        // Блоки
        for (ResourceEntry resource : RESOURCES) {
            if (resource.blockItem != null) {
                event.accept(resource.blockItem.get());
            }
        }
    }

    public static List<ResourceEntry> getResources() {
        return new ArrayList<>(RESOURCES);
    }

    /**
     * Инициализация - вызывать ДО регистрации блоков/предметов в конструкторе мода!
     */
    public static void init() {
        if (initialized) {
            return;
        }

        RESOURCES.clear();

        // ============ МЕТАЛЛЫ ============

        registerFull("steel", ResourceType.METAL,
                BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                        .strength(6.0f, 7.0f)
                        .requiresCorrectToolForDrops());

        registerFull("aluminum", ResourceType.METAL,
                BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                        .strength(3.0f, 5.0f)
                        .requiresCorrectToolForDrops());

//        // ============ ПОЛИМЕРЫ ============
//
//        registerFull("epoxy", ResourceType.POLYMER,
//                BlockBehaviour.Properties.copy(Blocks.STONE)
//                        .strength(2.0f, 3.0f)
//                        .sound(SoundType.STONE));
//
//        registerWithBlock("polyethylene", ResourceType.POLYMER);
//
//        // ============ КРИСТАЛЛЫ ============
//
//        registerFull("quartzite", ResourceType.CRYSTAL,
//                BlockBehaviour.Properties.copy(Blocks.AMETHYST_BLOCK)
//                        .strength(4.0f, 6.0f)
//                        .requiresCorrectToolForDrops());

        initialized = true;
        CrustalIncursionMod.LOGGER.info("ResourceRegistry initialized with {} resources", RESOURCES.size());
    }
}