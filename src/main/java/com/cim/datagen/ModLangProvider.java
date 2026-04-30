package com.cim.datagen;

import com.cim.main.ResourceRegistry;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
import com.cim.main.CrustalIncursionMod;
import com.cim.block.basic.ModBlocks;
import com.cim.item.ModItems;

public class ModLangProvider extends LanguageProvider {

    protected final String locale;

    public ModLangProvider(PackOutput output, String locale) {
        super(output, CrustalIncursionMod.MOD_ID, locale);
        this.locale = locale;

        // !!! ВАЖНО: Инициализируем ResourceRegistry !!!
        ResourceRegistry.init();
    }

    @Override
    protected void addTranslations() {
        // Сначала автоматические переводы для ресурсов
        ResourceDatagenHelper.generateTranslations(this, locale);

        // Затем ручные переводы
        if (locale.equals("ru_ru")) {
            addRussian();
        } else if (locale.equals("uk_ua")){
            addUkrainian();
        } else {
            addEnglish();
        }
    }

    private void addEnglish() {
//транслейт бай марко_поло  , плиз донт аск ми ебаут проблемс
        // Вкладки креатива (SNM_WEAPONS_TAB)
        add("itemGroup.cim.cim_build_tab", "Building Blocks");
        add("itemGroup.cim.cim_tech_tab", "Technology");
        add("itemGroup.cim.cim_weapons_tab", " Arsenal");
        add("itemGroup.cim.cim_recourses_tab", "Recourses");
        add("itemGroup.cim.cim_nature_tab", "Nature");
// Fluid Identifier
        add("item.cim.fluid_identifier", "Fluid Identifier");
        add("fluid.cim.none", "None");
        add("tooltip.cim.fluid.unknown", "Unknown Fluid");
        add("tooltip.cim.fluid.invalid", "Invalid Fluid");

// Fluids
        add("fluid_type.cim.hydrogen_peroxide", "Hydrogen Peroxide");
        add("fluid_type.cim.sulfuric_acid", "Sulfuric Acid");
        add("fluid_type.cim.natural_gas", "Natural Gas");
        add("fluid_type.cim.steam", "Steam");


        add("tooltip.cim.detminer.desc", "Breaks blocks in a natural blast pattern");
        add("tooltip.cim.detminer.hardness", "Only affects blocks with hardness < 30");
        add("tooltip.cim.detminer.conglomerate", "Has a chance to extract resources from conglomerate");

        // Conglomerate drops
        add("item.cim.conglomerate_chunk", "Conglomerate Chunk");
        add("item.cim.hard_rock", "Hard Rock");

        // Metals
        add("metal.cim.gold", "Gold");
        add("metal.cim.iron", "Iron");
        add("metal.cim.copper", "Copper");
        add("metal.cim.netherite", "Netherite");
        add("metal.cim.steel", "Steel");
        add("metal.cim.aluminum", "Aluminum");
        add("metal.cim.bronze", "Bronze");
        add("metal.cim.tin", "Tin");
        add("metal.cim.zinc", "Zinc");

        add("jei.category.cim.smelting", "Smelting");
        add("jei.category.cim.casting", "Casting");
        add("jei.category.cim.alloying", "Alloying");
        add("jei.category.cim.millstone", "Millstone");
        add(ModItems.LIQUID_METAL.get(), "Liquid metal");

        //Секвойя
        add(ModBlocks.SEQUOIA_BARK.get(), "Sequoia bark");
        add(ModBlocks.SEQUOIA_HEARTWOOD.get(), "Sequoia heartwood");
        add(ModBlocks.SEQUOIA_PLANKS.get(), "Sequoia planks");
        add(ModBlocks.SEQUOIA_ROOTS.get(), "Sequoia roots");
        add(ModBlocks.SEQUOIA_ROOTS_MOSSY.get(), "Mossy sequoia roots");

        add(ModBlocks.SEQUOIA_BARK_DARK.get(), "Dark sequoia bark");
        add(ModBlocks.SEQUOIA_BARK_MOSSY.get(), "Sequoia bark with moss");
        add(ModBlocks.SEQUOIA_BARK_LIGHT.get(), "Light sequoia bark");
        add(ModBlocks.SEQUOIA_DOOR.get(), "Sequoia door");
        add(ModBlocks.SEQUOIA_BIOME_MOSS.get(), "Dark moss");
        add(ModBlocks.SEQUOIA_LEAVES.get(), "Sequoia leaves");

        //Электроника
        add(ModItems.ENERGY_CELL_BASIC.get(), "Energy cell");
        add(ModItems.CREATIVE_BATTERY.get(), "Creative battery");
        add(ModItems.BATTERY.get(), "Battery");
        add(ModItems.BATTERY_ADVANCED.get(), "Advanced battery");
        add(ModItems.BATTERY_LITHIUM.get(), "Lithium battery");
        add(ModItems.BATTERY_TRIXITE.get(), "Trixite Battery");
        add(ModBlocks.MACHINE_BATTERY.get(), "Module energy storage");
        add(ModBlocks.CONVERTER_BLOCK.get(), "Energy converter");
        add(ModBlocks.WIRE_COATED.get(), "Copper wire");
        add(ModBlocks.SWITCH.get(), "Switch");
        add(ModBlocks.TURRET_LIGHT_PLACER.get(), "Light landing turret 'Nagual' ");

        // Нэкроз
        add(ModBlocks.DEPTH_WORM_NEST.get(), "Depth worm nest");
        add(ModBlocks.HIVE_SOIL.get(), "Depth worm hive flesh");



        //Блоки
        add(ModBlocks.CRATE.get(), "Crate");
        add(ModBlocks.CRATE_AMMO.get(), "Ammo crate");
        add(ModBlocks.CONCRETE.get(), "Concrete");
        add(ModBlocks.CONCRETE_RED.get(), "Red concrete");
        add(ModBlocks.CONCRETE_BLUE.get(), "Blue concrete");
        add(ModBlocks.CONCRETE_GREEN.get(), "Green concrete");
        add(ModBlocks.CONCRETE_HAZARD_NEW.get(), "New hazard concrete");
        add(ModBlocks.CONCRETE_HAZARD_OLD.get(), "Old hazard concrete");
        add(ModBlocks.NECROSIS_TEST.get(), "Necrosis test block");
        add(ModBlocks.NECROSIS_TEST2.get(), "Necrosis test block 2");
        add(ModBlocks.NECROSIS_TEST3.get(), "Necrosis test block 3");
        add(ModBlocks.NECROSIS_TEST4.get(), "Necrosis test block 4");
        add(ModBlocks.NECROSIS_PORTAL.get(), "Necrosis portal");
        add(ModBlocks.WASTE_LOG.get(), "Waste log");
        add(ModBlocks.CONCRETE_STAIRS.get(), "Concrete stairs");
        add(ModBlocks.CONCRETE_SLAB.get(), "Concrete slab");
        add(ModBlocks.CONCRETE_RED_STAIRS.get(), "Red concrete stairs");
        add(ModBlocks.CONCRETE_RED_SLAB.get(), "Red concrete slab");
        add(ModBlocks.CONCRETE_BLUE_STAIRS.get(), "Blue concrete stairs");
        add(ModBlocks.CONCRETE_BLUE_SLAB.get(), "Blue concrete slab");
        add(ModBlocks.CONCRETE_GREEN_STAIRS.get(), "Green concrete stairs");
        add(ModBlocks.CONCRETE_GREEN_SLAB.get(), "Green concrete slab");
        add(ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get(), "New hazard concrete stairs");
        add(ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get(), "New hazard concrete slab");
        add(ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get(), "Old hazard concrete stairs");
        add(ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get(), "Old hazard concrete slab");
        add(ModBlocks.SEQUOIA_TRAPDOOR.get(), "Sequoia trapdoor");

        // Валы
//        add(ModBlocks.DRILL_HEAD.get(), "Drill head");
//        add(ModBlocks.MOTOR_ELECTRO.get(), "Electric motor");
//        add(ModBlocks.WIND_GEN_FLUGER.get(), "Wind generator vane");
//        add(ModBlocks.SHAFT_IRON.get(), "Iron shaft");
//        add(ModBlocks.SHAFT_WOODEN.get(), "Wooden shaft");
//        add(ModBlocks.GEAR_PORT.get(), "Gear port");
//        add(ModBlocks.STOPPER.get(), "Stopper");
//        add(ModBlocks.ADDER.get(), "Adder");
//        add(ModBlocks.TACHOMETER.get(), "Tachometer");
//        add(ModBlocks.ROTATION_METER.get(), "Rotation meter");
//        add(ModBlocks.RCONVERTER.get(), "RtoE converter");
//        add(ModBlocks.SHAFT_PLACER.get(), "Shaft placer");
//        add(ModBlocks.MINING_PORT.get(), "Mining port");

        //Другие предметы
        add(ModBlocks.DET_MINER.get(), "Mining charge");
        add(ModItems.DEPTH_WORM_SPAWN_EGG.get(), "Depth worm spawn egg");
        add(ModItems.SCREWDRIVER.get(), "Screwdriver");
        add(ModItems.CROWBAR.get(), "Crowbar");
        add(ModItems.RANGE_DETONATOR.get(), "Long-Range detonator");
        add(ModItems.MULTI_DETONATOR.get(), "Multi-Detonator");
        add(ModItems.DETONATOR.get(), "Detonator");
        add(ModItems.MACHINEGUN.get(), "'A.P. 17'");
        add(ModItems.TURRET_CHIP.get(), "Turret chip");
        add(ModItems.TURRET_LIGHT_PORTATIVE_PLACER.get(), "Portable light turret");
        add(ModItems.AMMO_TURRET.get(), "20mm Turret ammo");
        add(ModItems.AMMO_TURRET_PIERCING.get(), "20mm Armor-Piercing turret ammo");
        add(ModItems.AMMO_TURRET_HOLLOW.get(), "20mm Hollow-Point turret ammo");
        add(ModItems.AMMO_TURRET_FIRE.get(), "20mm Incendiary turret ammo");
        add(ModItems.AMMO_TURRET_RADIO.get(), "20mm Turret ammo with radio-exploder");
        add(ModItems.GRENADE.get(), "Grenade");
        add(ModItems.GRENADEHE.get(), "High explosive grenade");
        add(ModItems.GRENADEFIRE.get(), "Incendiary grenade");
        add(ModItems.GRENADESLIME.get(), "Slime grenade");
        add(ModItems.GRENADESMART.get(), "Smart grenade");
        add(ModItems.GRENADE_IF.get(), "Impact grenade");
        add(ModItems.GRENADE_IF_HE.get(), "HE Impact grenade");
        add(ModItems.GRENADE_IF_SLIME.get(), "Slime impact grenade");
        add(ModItems.GRENADE_IF_FIRE.get(), "Incendiary impact grenade");
        add(ModItems.GRENADE_NUC.get(), "Nuclear grenade");


        // Энтити
        add("entity.cim.turret_light", "Light turret");
        add("entity.cim.turret_light_linked", "Linked light turret");
        add("entity.cim.turret_bullet", "Turret bullet");
        add("entity.cim.depth_worm", "Depth worm");
        add("entity.cim.grenade_projectile", "Grenade");
        add("entity.cim.grenadehe_projectile", "HE Grenade");
        add("entity.cim.grenadefire_projectile", "Incendiary grenade");
        add("entity.cim.grenadesmart_projectile", "Smart grenade");
        add("entity.cim.grenadeslime_projectile", "Slime grenade");
        add("entity.cim.grenade_if_projectile", "Impact grenade");
        add("entity.cim.grenade_if_fire_projectile", "Incendiary Impact grenade");
        add("entity.cim.grenade_if_slime_projectile", "Slime impact grenade");
        add("entity.cim.grenade_if_he_projectile", "HE Impact grenade");
        add("entity.cim.grenade_nuc_projectile", "Nuclear grenade");

        add(ModItems.GRENADIER_GOGGLES.get(), "Grenadier Goggles");

    }


    private void addRussian() {
        //Секвойя
        add("item.cim.cast_pickaxe_iron", "Литая железная кирка");
        add("item.cim.cast_pickaxe_steel", "Литая стальная кирка");

        add("item.cim.cast_pickaxe.desc.charge", "§7Зажмите ПКМ для мощного удара");
        add("item.cim.cast_pickaxe.desc.mining_power", "§6Мощность: %s");
        add("item.cim.cast_pickaxe.desc.vein_miner_info", "Жилковый майнер: %s");

        add(ModItems.GRENADIER_GOGGLES.get(), "Очки гренадёра");
        add("item.cim.grenadier_goggles.desc.explosion_resist", "Защита от взрывов: +%s%%");
        // Металлы
        add("metal.cim.gold", "Золото");
        add("metal.cim.iron", "Железо");
        add("metal.cim.copper", "Медь");
        add("metal.cim.netherite", "Незерит");
        add("metal.cim.steel", "Сталь");
        add("metal.cim.aluminum", "Алюминий");
        add("metal.cim.bronze", "Бронза");
        add("metal.cim.tin", "Олово");
        add("metal.cim.zinc", "Цинк");

        add("item.cim.hot_ingot.tooltip", "§6§lРАСКАЛЁННЫЙ! §r§7(%s%%)");
        add(ModBlocks.SEQUOIA_BARK.get(), "Кора секвойи");
        add(ModBlocks.SEQUOIA_HEARTWOOD.get(), "Бревно секвойи");
        add(ModBlocks.SEQUOIA_PLANKS.get(), "Доски из секвойи");
        add(ModBlocks.SEQUOIA_ROOTS.get(), "Корни секвойи");
        add(ModBlocks.SEQUOIA_ROOTS_MOSSY.get(), "Корни секвойи с мхом");

        add(ModBlocks.SEQUOIA_BARK_DARK.get(), "Тёмная кора секвойи");
        add(ModBlocks.SEQUOIA_BARK_MOSSY.get(), "Кора секвойи с мхом");
        add(ModBlocks.SEQUOIA_BARK_LIGHT.get(), "Светлая кора секвойи");
        add(ModBlocks.SEQUOIA_DOOR.get(), "Дверь из секвойи");
        add(ModBlocks.SEQUOIA_BIOME_MOSS.get(), "Тёмный мох");
        add(ModBlocks.SEQUOIA_LEAVES.get(), "Листья секвойи");
        add(ModBlocks.SEQUOIA_TRAPDOOR.get(), "Люк из секвойи");

        //Электроника
        add(ModItems.ENERGY_CELL_BASIC.get(), "Энергетическая ячейка");
        add(ModItems.CREATIVE_BATTERY.get(), "Бесконечный аккумулятор");
        add(ModItems.BATTERY.get(), "Батарея");
        add(ModItems.BATTERY_ADVANCED.get(), "Улучшенный аккумулятор");
        add(ModItems.BATTERY_LITHIUM.get(), "Литий-ионный аккумулятор");
        add(ModItems.BATTERY_TRIXITE.get(), "Продвинутый аккумулятор");
        add(ModBlocks.MACHINE_BATTERY.get(), "Модульное энергохранилище");
        add(ModBlocks.CONVERTER_BLOCK.get(), "Энергетический конвертер");
        add(ModBlocks.WIRE_COATED.get(), "Провод из красной меди");
        add(ModBlocks.SWITCH.get(), "Рубильник");

        add(ModBlocks.TURRET_LIGHT_PLACER.get(), "Лёгкая десантная турель 'Нагваль'");

        // Нэкроз
        add(ModBlocks.DEPTH_WORM_NEST.get(), "Ядро улья глубинного червя");
        add(ModBlocks.HIVE_SOIL.get(), "Плоть улья глубинного червя");

        //Блоки
        add(ModBlocks.CRATE.get(), "Ящик");
        add(ModBlocks.CRATE_AMMO.get(), "Ящик с патронами");
        add(ModBlocks.CONCRETE.get(), "Бетон");
        add(ModBlocks.CONCRETE_RED.get(), "Красный бетон");
        add(ModBlocks.CONCRETE_BLUE.get(), "Синий бетон");
        add(ModBlocks.CONCRETE_GREEN.get(), "Зелёный бетон");
        add(ModBlocks.CONCRETE_HAZARD_NEW.get(), "Бетон 'в полоску'");
        add(ModBlocks.CONCRETE_HAZARD_OLD.get(), "Изношенный бетон 'в полоску'");
        add(ModBlocks.NECROSIS_TEST.get(), "Тестовый блок Некроза");
        add(ModBlocks.NECROSIS_TEST2.get(), "Тестовый блок Некроза 2");
        add(ModBlocks.NECROSIS_TEST3.get(), "Тестовый блок Некроза 3");
        add(ModBlocks.NECROSIS_TEST4.get(), "Тестовый блок Некроза 4");
        add(ModBlocks.NECROSIS_PORTAL.get(), "Портал Некроза");
        add(ModBlocks.WASTE_LOG.get(),"Обугленное бревно");
        add(ModBlocks.CONCRETE_STAIRS.get(), "Бетонные ступени");
        add(ModBlocks.CONCRETE_SLAB.get(), "Бетонная плита");
        add(ModBlocks.CONCRETE_RED_STAIRS.get(), "Лестница из красного бетона");
        add(ModBlocks.CONCRETE_RED_SLAB.get(), "Плита из красного бетона");
        add(ModBlocks.CONCRETE_BLUE_STAIRS.get(), "Лестница из синего бетона");
        add(ModBlocks.CONCRETE_BLUE_SLAB.get(), "Плита из синего бетона");
        add(ModBlocks.CONCRETE_GREEN_STAIRS.get(), "Ступени из зелёного бетона");
        add(ModBlocks.CONCRETE_GREEN_SLAB.get(), "Плита из зелёного бетона");
        add(ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get(), "Ступени из бетона 'в полоску'");
        add(ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get(), "Плита из бетона 'в полоску'");
        add(ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get(), "Ступени из изношенного бетона 'в полоску'");
        add(ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get(), "Плита из изношенного бетона 'в полоску'");


        // Валы
//        add(ModBlocks.DRILL_HEAD.get(), "Головка бура");
//        add(ModBlocks.MOTOR_ELECTRO.get(), "Электромотор");
//        add(ModBlocks.SHAFT_IRON.get(), "Железный вал");
//        add(ModBlocks.SHAFT_WOODEN.get(), "Деревянный вал");
//        add(ModBlocks.GEAR_PORT.get(), "Трансмиттер вращения");
//        add(ModBlocks.STOPPER.get(), "Стопор");
//        add(ModBlocks.ADDER.get(), "Сумматор");
//        add(ModBlocks.TACHOMETER.get(), "Тахометр");
//        add(ModBlocks.ROTATION_METER.get(), "Измеритель вращения");
//        add(ModBlocks.RCONVERTER.get(), "Преобразователь вращения в энергию");
//        add(ModBlocks.SHAFT_PLACER.get(), "Установщик валов");
//        add(ModBlocks.MINING_PORT.get(), "Сборочный порт");
        add("jei.category.cim.smelting", "Плавка");
        add("jei.category.cim.casting", "Отлив");
        add("jei.category.cim.alloying", "Сплав");
        add("jei.category.cim.millstone", "Жернова");
        add(ModItems.LIQUID_METAL.get(), "Жидкий металл");
        //Другие предметы
        add(ModBlocks.DET_MINER.get(), "Шахтёрский заряд");
        add(ModItems.RANGE_DETONATOR.get(), "Детонатор дальнего действия");
        add(ModItems.DEPTH_WORM_SPAWN_EGG.get(), "Яйцо призыва глубинного червя");
        add(ModItems.SCREWDRIVER.get(), "Отвёртка");
        add(ModItems.CROWBAR.get(), "Монтировка");
        add(ModItems.MULTI_DETONATOR.get(), "Мульти-детонатор");
        add(ModItems.DETONATOR.get(), "Детонатор");
        add(ModItems.MACHINEGUN.get(), "'А.П. 17'");
        add(ModItems.TURRET_CHIP.get(), "Чип турели");
        add(ModItems.TURRET_LIGHT_PORTATIVE_PLACER.get(), "Портативная лёгкая десантная турель 'Нагваль'");
//        add(ModItems.WIND_GEN_FLUGER.get(), "Ветряной генератор вращения");
        add(ModItems.AMMO_TURRET_PIERCING.get(), "20мм турельный боеприпас");
        add(ModItems.AMMO_TURRET_HOLLOW.get(), "20мм экспансивный турельный боеприпас");
        add(ModItems.AMMO_TURRET_FIRE.get(), "20мм зажигательный турельный боеприпас");
        add(ModItems.AMMO_TURRET_RADIO.get(), "20мм турельный боеприпас с радио-взрывателем");
        add(ModItems.GRENADE.get(), "Граната");
        add(ModItems.GRENADEHE.get(), "Фугасная граната");
        add(ModItems.GRENADEFIRE.get(), "Зажигательная граната");
        add(ModItems.GRENADESLIME.get(), "Граната в слизи");
        add(ModItems.GRENADESMART.get(), "УМная граната");
        add(ModItems.GRENADE_IF.get(), "Ударная граната");
        add(ModItems.GRENADE_IF_HE.get(), "Фугасная ударная граната");
        add(ModItems.GRENADE_IF_SLIME.get(), "Ударная граната в слизи");
        add(ModItems.GRENADE_IF_FIRE.get(), "Зажигательная ударная граната");
        add(ModItems.GRENADE_NUC.get(), "Водородная граната");

        // Вкладка креатива
        add("itemGroup.cim.cim_build_tab", "Строительные блоки");
        add("itemGroup.cim.cim_tech_tab", "Технологии");
        add("itemGroup.cim.cim_weapons_tab", "Арсенал");
        add("itemGroup.cim.cim_recourses_tab", "Ресурсы");
        add("itemGroup.cim.cim_nature_tab", "Природа");


// Fluid Identifier
        add("item.cim.fluid_identifier", "Жидкостный индефикатор");
        add("fluid.cim.none", "Ничего");
        add("tooltip.cim.fluid.unknown", "Неизвестная жидкость");
        add("tooltip.cim.fluid.invalid", "Недействительная жидкость");

// Fluids
        add("fluid_type.cim.hydrogen_peroxide", "Пероксид водорода");
        add("fluid_type.cim.sulfuric_acid", "Серная кислота");
        add("fluid_type.cim.natural_gas", "Природный газ");
        add("fluid_type.cim.steam", "Пар");
        // Энтити
        add("entity.cim.turret_light", "Лёгкая турель");
        add("entity.cim.turret_light_linked", "Связанная лёгкая турель");
        add("entity.cim.turret_bullet", "Пуля турели");
        add("entity.cim.depth_worm", "Глубинный червь");
        add("entity.cim.grenade_projectile", "Граната");
        add("entity.cim.grenadehe_projectile", "Фугасная граната");
        add("entity.cim.grenadefire_projectile", "Зажигательная граната");
        add("entity.cim.grenadesmart_projectile", "Умная граната");
        add("entity.cim.grenadeslime_projectile", "Желатиновая граната");
        add("entity.cim.grenade_if_projectile", "Ударная граната");
        add("entity.cim.grenade_if_fire_projectile", "Зажигательная ударная граната");
        add("entity.cim.grenade_if_slime_projectile", "Желатиновая ударная граната");
        add("entity.cim.grenade_if_he_projectile", "Фугасная ударная граната");
        add("entity.cim.grenade_nuc_projectile", "Ядерная граната");
    }
    private void addUkrainian() {
        // Вкладки креативу
        add("itemGroup.cim.cim_build_tab", "Будівельні блоки");
        add("itemGroup.cim.cim_tech_tab", "Технології");
        add("itemGroup.cim.cim_weapons_tab", "Арсенал");
        add("itemGroup.cim.cim_tools_tab", "Інструменти");
        add("itemGroup.cim.cim_nature_tab", "Природа");
// Fluid Identifier
        add("item.cim.fluid_identifier", "Ідентифікатор рідин");
        add("fluid.cim.none", "Нічого");
        add("tooltip.cim.fluid.unknown", "Невідома рідина");
        add("tooltip.cim.fluid.invalid", "Недійсна рідина");

// Fluids
        add("fluid_type.cim.hydrogen_peroxide", "Перекис водню");
        add("fluid_type.cim.sulfuric_acid", "Сірчана кислота");
        add("fluid_type.cim.natural_gas", "Природний газ");
        add("fluid_type.cim.steam", "Пара");


        // Секвойя
        add(ModBlocks.SEQUOIA_BARK.get(), "Кора секвої");
        add(ModBlocks.SEQUOIA_HEARTWOOD.get(), "Колода секвої");
        add(ModBlocks.SEQUOIA_PLANKS.get(), "Дошки з секвої");
        add(ModBlocks.SEQUOIA_ROOTS.get(), "Коріння секвої");
        add(ModBlocks.SEQUOIA_ROOTS_MOSSY.get(), "Коріння секвої з мохом");
        add(ModBlocks.SEQUOIA_BARK_DARK.get(), "Темна кора секвої");
        add(ModBlocks.SEQUOIA_BARK_MOSSY.get(), "Кора секвої з мохом");
        add(ModBlocks.SEQUOIA_BARK_LIGHT.get(), "Світла кора секвої");
        add(ModBlocks.SEQUOIA_DOOR.get(), "Двері з секвої");
        add(ModBlocks.SEQUOIA_TRAPDOOR.get(), "Люк з секвої");
        add(ModBlocks.SEQUOIA_BIOME_MOSS.get(), "Темний мох");
        add(ModBlocks.SEQUOIA_LEAVES.get(), "Листя секвої");

        // Електроніка
        add(ModItems.ENERGY_CELL_BASIC.get(), "Базова енергетична комірка");
        add(ModItems.CREATIVE_BATTERY.get(), "Батарея творчого режиму");
        add(ModItems.BATTERY.get(), "Батарея");
        add(ModItems.BATTERY_ADVANCED.get(), "Покращена батарея");
        add(ModItems.BATTERY_LITHIUM.get(), "Літієва батарея");
        add(ModItems.BATTERY_TRIXITE.get(), "Тріксітова батарея");

        add(ModBlocks.MACHINE_BATTERY.get(), "Машинна батарея");
        add(ModBlocks.CONVERTER_BLOCK.get(), "Конвертер");
        add(ModBlocks.WIRE_COATED.get(), "Ізольований дріт");
        add(ModBlocks.SWITCH.get(), "Вимикач");
        add(ModBlocks.TURRET_LIGHT_PLACER.get(), "Легка турель");

        // Некроз
        add(ModBlocks.DEPTH_WORM_NEST.get(), "Гніздо глибинного черв'яка");
        add(ModBlocks.HIVE_SOIL.get(), "Ґрунт вулика");

        // Блоки
        add(ModBlocks.CRATE.get(), "Ящик");
        add(ModBlocks.CRATE_AMMO.get(), "Ящик з набоями");
        add(ModBlocks.CONCRETE.get(), "Бетон");
        add(ModBlocks.CONCRETE_RED.get(), "Червоний бетон");
        add(ModBlocks.CONCRETE_BLUE.get(), "Синій бетон");
        add(ModBlocks.CONCRETE_GREEN.get(), "Зелений бетон");
        add(ModBlocks.CONCRETE_HAZARD_NEW.get(), "Новий небезпечний бетон");
        add(ModBlocks.CONCRETE_HAZARD_OLD.get(), "Старий небезпечний бетон");
        add(ModBlocks.NECROSIS_TEST.get(), "Тестовий блок некрозу");
        add(ModBlocks.NECROSIS_TEST2.get(), "Тестовий блок некрозу 2");
        add(ModBlocks.NECROSIS_TEST3.get(), "Тестовий блок некрозу 3");
        add(ModBlocks.NECROSIS_TEST4.get(), "Тестовий блок некрозу 4");
        add(ModBlocks.NECROSIS_PORTAL.get(), "Портал некрозу");
        add(ModBlocks.WASTE_LOG.get(), "Заражена колода");
        add(ModBlocks.CONCRETE_STAIRS.get(), "Бетонні сходи");
        add(ModBlocks.CONCRETE_SLAB.get(), "Бетонна плита");
        add(ModBlocks.CONCRETE_RED_STAIRS.get(), "Сходи з червоного бетону");
        add(ModBlocks.CONCRETE_RED_SLAB.get(), "Плита з червоного бетону");
        add(ModBlocks.CONCRETE_BLUE_STAIRS.get(), "Сходи з синього бетону");
        add(ModBlocks.CONCRETE_BLUE_SLAB.get(), "Плита з синього бетону");
        add(ModBlocks.CONCRETE_GREEN_STAIRS.get(), "Сходи з зеленого бетону");
        add(ModBlocks.CONCRETE_GREEN_SLAB.get(), "Плита з зеленого бетону");
        add(ModBlocks.CONCRETE_HAZARD_NEW_STAIRS.get(), "Сходи з нового небезпечного бетону");
        add(ModBlocks.CONCRETE_HAZARD_NEW_SLAB.get(), "Плита з нового небезпечного бетону");
        add(ModBlocks.CONCRETE_HAZARD_OLD_STAIRS.get(), "Сходи зі старого небезпечного бетону");
        add(ModBlocks.CONCRETE_HAZARD_OLD_SLAB.get(), "Плита зі старого небезпечного бетону");

        // Метали
        add("metal.cim.gold", "Золото");
        add("metal.cim.iron", "Залізо");
        add("metal.cim.copper", "Мідь");
        add("metal.cim.netherite", "Незерит");
        add("metal.cim.steel", "Сталь");
        add("metal.cim.aluminum", "Алюміній");

        // Вали
//        add(ModBlocks.DRILL_HEAD.get(), "Бурова головка");
//        add(ModBlocks.MOTOR_ELECTRO.get(), "Електромотор");
//        add(ModItems.WIND_GEN_FLUGER.get(), "Флюгер вітрогенератора");
        add(ModItems.AMMO_TURRET_PIERCING.get(), "Бронебійні набої для турелі");
        add(ModItems.AMMO_TURRET_HOLLOW.get(), "Експансивні набої для турелі");
        add(ModItems.AMMO_TURRET_FIRE.get(), "Запальні набої для турелі");
        add(ModItems.AMMO_TURRET_RADIO.get(), "Радіоактивні набої для турелі");
        add(ModItems.GRENADE.get(), "Граната");
        add(ModItems.GRENADEHE.get(), "Фугасна граната");
        add(ModItems.GRENADEFIRE.get(), "Запальна граната");
        add(ModItems.GRENADESLIME.get(), "Слизова граната");
        add(ModItems.GRENADESMART.get(), "Розумна граната");
        add(ModItems.GRENADE_IF.get(), "Ударна граната");
        add(ModItems.GRENADE_IF_HE.get(), "Фугасна ударна граната");
        add(ModItems.GRENADE_IF_SLIME.get(), "Слизова ударна граната");
        add(ModItems.GRENADE_IF_FIRE.get(), "Запальна ударна граната");
        add(ModItems.GRENADE_NUC.get(), "Ядерна граната");
//        add(ModBlocks.SHAFT_IRON.get(), "Залізний вал");
//        add(ModBlocks.SHAFT_WOODEN.get(), "Дерев'яний вал");
//        add(ModBlocks.GEAR_PORT.get(), "Редукторний порт");
//        add(ModBlocks.STOPPER.get(), "Стопор");
//        add(ModBlocks.ADDER.get(), "Суматор");
//        add(ModBlocks.TACHOMETER.get(), "Тахометр");
//        add(ModBlocks.ROTATION_METER.get(), "Датчик обертання");
//        add(ModBlocks.RCONVERTER.get(), "Обертальний перетворювач");
//        add(ModBlocks.SHAFT_PLACER.get(), "Розміщувач валів");
//        add(ModBlocks.MINING_PORT.get(), "Шахтарський порт");
        // Сутності
        add("entity.cim.turret_light", "Легка турель");
        add("entity.cim.turret_light_linked", "Зв'язана легка турель");
        add("entity.cim.turret_bullet", "Куля турелі");
        add("entity.cim.depth_worm", "Глибинний черв'як");
        add("entity.cim.grenade_projectile", "Граната");
        add("entity.cim.grenadehe_projectile", "Фугасна граната");
        add("entity.cim.grenadefire_projectile", "Запальна граната");
        add("entity.cim.grenadesmart_projectile", "Розумна граната");
        add("entity.cim.grenadeslime_projectile", "Слизова граната");
        add("entity.cim.grenade_if_projectile", "Ударна граната");
        add("entity.cim.grenade_if_fire_projectile", "Запальна ударна граната");
        add("entity.cim.grenade_if_slime_projectile", "Слизова ударна граната");
        add("entity.cim.grenade_if_he_projectile", "Фугасна ударна граната");
        add("entity.cim.grenade_nuc_projectile", "Ядерна граната");
    }
}