package com.cim.item.conglomerates;

import com.cim.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ConglomerateItem extends Item {
    public static final String TAG_COMPOSITION = "Composition";
    public static final String TAG_OU = "OreUnits";
    public static final String TAG_ANALYZED = "Analyzed";
    public static final String TAG_VEIN_TYPE = "VeinType"; // Для отладки

    // Тестовые типы жил с весами (чем больше вес, тем чаще)
    private static final Map<String, Map<String, Integer>> VEIN_TEMPLATES = Map.of(
            "iron_copper", Map.of("iron", 35, "copper", 25, "slag", 40),
            "lead_zinc", Map.of("lead", 30, "zinc", 30, "silver", 5, "slag", 35),
            "tin_antimony", Map.of("tin", 40, "antimony", 20, "slag", 40),
            "gold_quartz", Map.of("gold", 15, "silver", 10, "quartz", 35, "slag", 40),
            "rare_earth", Map.of("neodymium", 20, "lanthanum", 15, "thorium", 5, "slag", 60)
    );

    public ConglomerateItem(Properties properties) {
        super(properties);
    }

    /**
     * Создаёт кусок с составом из конкретной жилы
     */
    public static ItemStack createFromVein(Map<String, Integer> composition, int ou, String veinTypeName) {
        ItemStack stack = new ItemStack(ModItems.CONGLOMERATE_CHUNK.get());
        CompoundTag tag = new CompoundTag();

        CompoundTag compTag = new CompoundTag();
        composition.forEach(compTag::putInt);
        tag.put(TAG_COMPOSITION, compTag);

        tag.putInt(TAG_OU, ou);
        tag.putBoolean(TAG_ANALYZED, false);
        tag.putString(TAG_VEIN_TYPE, veinTypeName);

        stack.setTag(tag);
        return stack;
    }

    /**
     * ТЕСТОВЫЙ метод: создаёт кусок со случайным составом
     */
    public static ItemStack createRandomForTest() {
        Random rand = new Random();
        String[] types = VEIN_TEMPLATES.keySet().toArray(new String[0]);
        String selectedType = types[rand.nextInt(types.length)];

        // Добавляем вариацию ±10% к каждому компоненту
        Map<String, Integer> baseComp = VEIN_TEMPLATES.get(selectedType);
        Map<String, Integer> variedComp = new HashMap<>();

        int total = 0;
        for (Map.Entry<String, Integer> entry : baseComp.entrySet()) {
            if (entry.getKey().equals("slag")) continue; // Шлак оставляем на конец

            int variation = (int)(entry.getValue() * 0.2 * (rand.nextDouble() - 0.5)); // ±10%
            int value = Math.max(5, entry.getValue() + variation);
            variedComp.put(entry.getKey(), value);
            total += value;
        }

        // Шлак = остаток до 100
        variedComp.put("slag", Math.max(10, 100 - total));

        return createFromVein(variedComp, 100, selectedType);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int ou = getOU(stack);
        tooltip.add(Component.literal("§7Ore Units: §f" + ou + "/81"));

        // ТЕСТ: всегда показываем состав
        String veinType = stack.hasTag() ? stack.getTag().getString(TAG_VEIN_TYPE) : "unknown";
        tooltip.add(Component.literal("§8[ТЕСТ] Тип жилы: §7" + veinType));

        Map<String, Integer> comp = getComposition(stack);
        if (!comp.isEmpty()) {
            tooltip.add(Component.literal("§8Состав:"));
            comp.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> {
                        String color = getMetalColor(e.getKey());
                        String bar = makeBar(e.getValue());
                        tooltip.add(Component.literal(String.format("  %s%s §7%d%% %s",
                                color, e.getKey(), e.getValue(), bar)));
                    });
        }
    }

    private String makeBar(int percent) {
        int filled = percent / 10;
        StringBuilder bar = new StringBuilder("§8[§a");
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "|" : "§7|");
        }
        bar.append("§8]");
        return bar.toString();
    }

    private String getMetalColor(String metal) {
        return switch(metal.toLowerCase()) {
            case "iron" -> "§c";
            case "copper" -> "§6";
            case "gold" -> "§e";
            case "silver" -> "§f";
            case "lead" -> "§8";
            case "zinc" -> "§a";
            case "tin" -> "§b";
            case "antimony" -> "§3";
            case "neodymium" -> "§5";
            case "lanthanum" -> "§d";
            case "thorium" -> "§2";
            case "uranium" -> "§a";
            case "quartz" -> "§7";
            case "slag" -> "§8";
            default -> "§7";
        };
    }

    public static Map<String, Integer> getComposition(ItemStack stack) {
        if (!stack.hasTag()) return Map.of();
        CompoundTag compTag = stack.getTag().getCompound(TAG_COMPOSITION);
        Map<String, Integer> result = new HashMap<>();
        compTag.getAllKeys().forEach(key -> result.put(key, compTag.getInt(key)));
        return result;
    }

    public static int getOU(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(TAG_OU) : 0;
    }


}