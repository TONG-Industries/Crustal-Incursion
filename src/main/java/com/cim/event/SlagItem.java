package com.cim.event;

import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class SlagItem extends Item {
    public static final String TAG_METAL_ID = "MetalId";
    public static final String TAG_AMOUNT = "Amount";
    public static final String TAG_MELTING_POINT = "MeltingPoint";
    public static final String TAG_COLOR = "Color";
    public static final String TAG_HEAT_CONSUMPTION = "HeatConsumption"; // НОВОЕ: потребление температуры

    // Базовое время остывания шлака как предмета (10 секунд)
    public static final int BASE_COOLING_TIME = 200;

    public SlagItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_METAL_ID)) {
            ResourceLocation metalId = new ResourceLocation(tag.getString(TAG_METAL_ID));
            int amount = tag.getInt(TAG_AMOUNT);
            int meltingPoint = tag.getInt(TAG_MELTING_POINT);
            float heatConsumption = tag.getFloat(TAG_HEAT_CONSUMPTION);

            // Конвертируем единицы в понятные значения
            MetalUnits2.MetalStack units = MetalUnits2.convertFromUnits(amount);

            Optional<Metal> metalOpt = MetallurgyRegistry.get(metalId);
            String metalName = metalOpt.map(m -> Component.translatable(m.getTranslationKey()).getString())
                    .orElse(metalId.getPath());

            tooltip.add(Component.literal("§7Металл: §f" + metalName));

            // Формируем строку содержимого
            StringBuilder content = new StringBuilder("§7Содержит: §f");
            if (units.blocks() > 0) content.append(units.blocks()).append(" блоков ");
            if (units.ingots() > 0) content.append(units.ingots()).append(" слитков ");
            if (units.nuggets() > 0) content.append(units.nuggets()).append(" самородков");

            tooltip.add(Component.literal(content.toString().trim()));

            // Свойства металла
            tooltip.add(Component.literal(String.format("§7Температура плавки: §f%d°C", meltingPoint)));
            tooltip.add(Component.literal(String.format("§7Потребление тепла: §f%.1f§7/тик", heatConsumption)));

            // Время плавки (динамическое, макс 30 сек)
            int smeltTimeTicks = calculateSmeltTime(stack);
            float smeltTimeSeconds = smeltTimeTicks / 20f;
            tooltip.add(Component.literal(String.format("§7Время переплавки: §f%.1fс §8(макс 30с)", smeltTimeSeconds)));

            // Горячий?
            if (tag.contains("HotTime") && tag.getFloat("HotTime") > 0) {
                int hotTime = (int) tag.getFloat("HotTime");
                int maxTime = tag.getInt("HotTimeMax");
                if (maxTime <= 0) maxTime = BASE_COOLING_TIME;

                int temp = 20 + (int) ((hotTime / (float) maxTime) * 980); // 20°C - 1000°C
                tooltip.add(Component.empty());
                tooltip.add(Component.literal(String.format("§6§lГОРЯЧИЙ! §c%d°C", temp))
                        .withStyle(ChatFormatting.RED));
            }

            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§8Можно переплавить в плавильне").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("§8Пустой шлак").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Рассчитывает время плавки шлака в тиках
     * Зависит от количества металла, максимум 600 тиков (30 секунд)
     */
    public static int calculateSmeltTime(ItemStack stack) {
        if (!stack.hasTag()) return 100;

        CompoundTag tag = stack.getTag();
        int amount = tag.getInt(TAG_AMOUNT);

        // Получаем металл для расчёта
        ResourceLocation metalId = new ResourceLocation(tag.getString(TAG_METAL_ID));
        Optional<Metal> metalOpt = MetallurgyRegistry.get(metalId);

        if (metalOpt.isPresent()) {
            return metalOpt.get().calculateSmeltTimeForUnits(amount);
        }

        // Fallback: базовый расчёт если металл не найден
        float ingots = amount / 9f;
        return Math.min((int) (ingots * 60), 600);
    }

    /**
     * Получает потребление температуры шлака (из металла)
     */
    public static float getHeatConsumption(ItemStack stack) {
        if (!stack.hasTag()) return 0.5f;

        CompoundTag tag = stack.getTag();
        if (tag.contains(TAG_HEAT_CONSUMPTION)) {
            return tag.getFloat(TAG_HEAT_CONSUMPTION);
        }

        // Fallback: получаем из металла
        ResourceLocation metalId = new ResourceLocation(tag.getString(TAG_METAL_ID));
        return MetallurgyRegistry.get(metalId)
                .map(Metal::getHeatConsumptionPerTick)
                .orElse(0.5f);
    }

    /**
     * Создаёт стак шлака с указанным металлом и количеством
     * Копирует ВСЕ свойства металла: цвет, температуру, потребление
     */
    public static ItemStack createSlag(Metal metal, int amount) {
        ItemStack stack = new ItemStack(ModItems.SLAG.get());
        CompoundTag tag = stack.getOrCreateTag();

        tag.putString(TAG_METAL_ID, metal.getId().toString());
        tag.putInt(TAG_AMOUNT, amount);
        tag.putInt(TAG_MELTING_POINT, metal.getMeltingPoint());
        tag.putInt(TAG_COLOR, metal.getColor());
        tag.putFloat(TAG_HEAT_CONSUMPTION, metal.getHeatConsumptionPerTick());

        // Добавляем горячесть
        tag.putFloat("HotTime", BASE_COOLING_TIME);
        tag.putInt("HotTimeMax", BASE_COOLING_TIME);

        return stack;
    }

    /**
     * Создаёт стак шлака с сохранением всех характеристик (для NBT)
     */
    public static ItemStack createSlagFromNBT(CompoundTag tag) {
        ItemStack stack = new ItemStack(ModItems.SLAG.get());
        stack.setTag(tag.copy());
        return stack;
    }

    /**
     * Получает металл из шлака
     */
    @Nullable
    public static Metal getMetal(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG_METAL_ID)) return null;
        ResourceLocation id = new ResourceLocation(stack.getTag().getString(TAG_METAL_ID));
        return MetallurgyRegistry.get(id).orElse(null);
    }

    /**
     * Получает количество материала в шлаке
     */
    public static int getAmount(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return stack.getTag().getInt(TAG_AMOUNT);
    }

    /**
     * Получает температуру плавления шлака
     */
    public static int getMeltingPoint(ItemStack stack) {
        if (!stack.hasTag()) return 1000;
        return stack.getTag().getInt(TAG_MELTING_POINT);
    }

    /**
     * Получает цвет металла в шлаке
     */
    public static int getColor(ItemStack stack) {
        if (!stack.hasTag()) return 0x888888;
        return stack.getTag().getInt(TAG_COLOR);
    }
}