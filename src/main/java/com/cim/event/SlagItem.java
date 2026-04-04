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
    public static final String TAG_HEAT_CONSUMPTION = "HeatConsumption";
    public static final String TAG_MELTING_POINT_TEMP = "MeltingPointTemp"; // Температура плавления металла

    public static final int BASE_COOLING_TIME = 100;

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

            MetalUnits2.MetalStack units = MetalUnits2.convertFromUnits(amount);

            Optional<Metal> metalOpt = MetallurgyRegistry.get(metalId);
            String metalName = metalOpt.map(m -> Component.translatable(m.getTranslationKey()).getString())
                    .orElse(metalId.getPath());

            // === ИНФО О ПЕРЕПЛАВКЕ ===
            tooltip.add(Component.literal("§8Можно переплавить в плавильне").withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.literal("§7Металл: §f" + metalName));

            StringBuilder content = new StringBuilder("§7Содержит: §f");
            if (units.blocks() > 0) content.append(units.blocks()).append(" блоков ");
            if (units.ingots() > 0) content.append(units.ingots()).append(" слитков ");
            if (units.nuggets() > 0) content.append(units.nuggets()).append(" самородков");
            tooltip.add(Component.literal(content.toString().trim()));
            tooltip.add(Component.literal(String.format("§7Температура плавки: §f%d°C", meltingPoint)));
            tooltip.add(Component.literal(String.format("§7Потребление тепла: §f%.1f§7/тик", heatConsumption)));
            int smeltTimeTicks = calculateSmeltTime(stack);
            float smeltTimeSeconds = smeltTimeTicks / 20f;
            tooltip.add(Component.literal(String.format("§7Время переплавки: §f%.1fс §8(макс 30с)", smeltTimeSeconds)));

            // === ИНФО О ТЕМПЕРАТУРЕ И НАГРЕВЕ ===
            if (HotItemHandler.isHot(stack)) {
                addHeatTooltip(stack, tooltip, meltingPoint);
            }
        } else {
            tooltip.add(Component.literal("§8Пустой шлак").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Добавляет тултип с информацией о нагреве шлака
     * ПРИВЯЗКА К ГРАДУСАМ, не к процентам!
     */
    private void addHeatTooltip(ItemStack stack, List<Component> tooltip, int metalMeltingPoint) {
        int temperature = HotItemHandler.getTemperature(stack);
        float heatRatio = HotItemHandler.getHeatRatio(stack);
        int percent = (int) (heatRatio * 100);
        boolean cooledInPot = HotItemHandler.wasCooledInPot(stack);

        // === ПРИВЯЗКА СТАТУСА К АБСОЛЮТНЫМ ГРАДУСАМ ===
        HotItemHandler.HeatStatus status = HotItemHandler.getHeatStatus(temperature);
        String source = cooledInPot ? " §8[быстрое охл.]" : "";

        // Разделитель
        tooltip.add(Component.literal(""));

        // Главная строка с интенсивностью
        tooltip.add(Component.literal("")
                .append(Component.literal("||").withStyle(status.color))
                .append(Component.literal(status.label).withStyle(status.color, ChatFormatting.BOLD))
                .append(Component.literal("||").withStyle(status.color))
                .append(Component.literal(source)));

        // Температура: текущая / максимальная (температура плавления металла)
        tooltip.add(Component.literal(String.format("  §c%d°C §7/ §c%d°C §7(%d%%)",
                temperature, metalMeltingPoint, percent)));

        // Предупреждение если очень горячо
        if (!status.warning.isEmpty()) {
            tooltip.add(Component.literal(status.warning));
        }
    }

    public static int calculateSmeltTime(ItemStack stack) {
        if (!stack.hasTag()) return 100;

        CompoundTag tag = stack.getTag();
        int amount = tag.getInt(TAG_AMOUNT);

        ResourceLocation metalId = new ResourceLocation(tag.getString(TAG_METAL_ID));
        Optional<Metal> metalOpt = MetallurgyRegistry.get(metalId);

        if (metalOpt.isPresent()) {
            return metalOpt.get().calculateSmeltTimeForUnits(amount);
        }

        float ingots = amount / 9f;
        return Math.min((int) (ingots * 60), 600);
    }

    public static float getHeatConsumption(ItemStack stack) {
        if (!stack.hasTag()) return 0.5f;

        CompoundTag tag = stack.getTag();
        if (tag.contains(TAG_HEAT_CONSUMPTION)) {
            return tag.getFloat(TAG_HEAT_CONSUMPTION);
        }

        ResourceLocation metalId = new ResourceLocation(tag.getString(TAG_METAL_ID));
        return MetallurgyRegistry.get(metalId)
                .map(Metal::getHeatConsumptionPerTick)
                .orElse(0.5f);
    }

    public static ItemStack createSlag(Metal metal, int amount) {
        ItemStack stack = new ItemStack(ModItems.SLAG.get());
        CompoundTag tag = stack.getOrCreateTag();

        tag.putString(TAG_METAL_ID, metal.getId().toString());
        tag.putInt(TAG_AMOUNT, amount);
        tag.putInt(TAG_MELTING_POINT, metal.getMeltingPoint());
        tag.putInt(TAG_COLOR, metal.getColor());
        tag.putFloat(TAG_HEAT_CONSUMPTION, metal.getHeatConsumptionPerTick());
        tag.putInt(TAG_MELTING_POINT_TEMP, metal.getMeltingPoint()); // Сохраняем температуру плавления!

        // Горячесть через HotItemHandler
        HotItemHandler.setHot(stack, metal.getMeltingPoint(), false);

        return stack;
    }

    public static ItemStack createSlagFromNBT(CompoundTag tag) {
        ItemStack stack = new ItemStack(ModItems.SLAG.get());
        stack.setTag(tag.copy());
        return stack;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        // === ФИКС ДРОЖАНИЯ: проверяем через HotItemHandler ===
        // Если слот не менялся и оба предмета - шлак, проверяем изменение горячести
        if (!slotChanged && oldStack.getItem() == newStack.getItem() && oldStack.getItem() instanceof SlagItem) {
            // Если изменился только HotTime - не проигрывать анимацию
            boolean oldHot = HotItemHandler.isHot(oldStack);
            boolean newHot = HotItemHandler.isHot(newStack);

            // Если состояние горячести не изменилось кардинально (оба горячие или оба остывшие)
            // и предмет тот же - не трясти
            if (oldHot == newHot) {
                return false;
            }
        }
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    @Nullable
    public static Metal getMetal(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(TAG_METAL_ID)) return null;
        ResourceLocation id = new ResourceLocation(stack.getTag().getString(TAG_METAL_ID));
        return MetallurgyRegistry.get(id).orElse(null);
    }

    public static int getAmount(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return stack.getTag().getInt(TAG_AMOUNT);
    }

    public static int getMeltingPoint(ItemStack stack) {
        if (!stack.hasTag()) return 1000;
        return stack.getTag().getInt(TAG_MELTING_POINT);
    }

    public static int getColor(ItemStack stack) {
        if (!stack.hasTag()) return 0x888888;
        return stack.getTag().getInt(TAG_COLOR);
    }

    /**
     * Получает температуру плавления металла (для горячести)
     */
    public static int getMetalMeltingPointTemp(ItemStack stack) {
        if (!stack.hasTag()) return 1000;
        // Сначала пробуем новый тег, иначе берем из старого
        if (stack.getTag().contains(TAG_MELTING_POINT_TEMP)) {
            return stack.getTag().getInt(TAG_MELTING_POINT_TEMP);
        }
        return stack.getTag().getInt(TAG_MELTING_POINT);
    }
}