package com.cim.item.tags;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AmmoRegistry {
    private static final Map<String, AmmoType> AMMO_BY_ID = new HashMap<>();

    // ИЗМЕНЕНИЕ: Теперь храним СПИСОК патронов для калибра
    private static final Map<String, List<AmmoType>> AMMO_LIST_BY_CALIBER = new HashMap<>();

    public static class AmmoType {
        public final String id;
        public final String caliber;
        public final float damage;
        public final float speed;
        public final boolean isPiercing;

        public AmmoType(String id, String caliber, float damage, float speed, boolean isPiercing) {
            this.id = id;
            this.caliber = caliber;
            this.damage = damage;
            this.speed = speed;
            this.isPiercing = isPiercing;
        }
    }

    public static void register(Item item, String caliber, float damage, float speed, boolean isPiercing) {
        String itemId = ForgeRegistries.ITEMS.getKey(item).toString();
        AmmoType type = new AmmoType(itemId, caliber, damage, speed, isPiercing);
        AMMO_BY_ID.put(itemId, type);

        // Добавляем в список по калибру
        AMMO_LIST_BY_CALIBER.computeIfAbsent(caliber, k -> new ArrayList<>()).add(type);
    }

    /**
     * Возвращает случайный патрон указанного калибра.
     * Если калибра нет, возвращает null.
     */
    public static AmmoType getRandomAmmoForCaliber(String caliber, RandomSource random) { // <-- RandomSource
        List<AmmoType> list = AMMO_LIST_BY_CALIBER.get(caliber);
        if (list == null || list.isEmpty()) return null;
        return list.get(random.nextInt(list.size()));
    }

    // ОСТАЛЬНЫЕ МЕТОДЫ (getAmmoTypeById, isValidAmmo и т.д.) ОСТАВЬ КАК ЕСТЬ
    // (Но проверь, чтобы они использовали AMMO_BY_ID, что они и так делают)

    public static AmmoType getAmmoTypeById(String itemId) {
        AmmoType cached = AMMO_BY_ID.get(itemId);
        if (cached != null) return cached;
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item != null) return getAmmoTypeFromItem(item);
        return null;
    }

    public static AmmoType getAmmoTypeFromItem(Item item) {
        if (item instanceof IAmmoItem iAmmo) {
            return new AmmoType(
                    ForgeRegistries.ITEMS.getKey(item).toString(),
                    iAmmo.getCaliber(),
                    iAmmo.getDamage(),
                    iAmmo.getSpeed(),
                    iAmmo.isPiercing()
            );
        }
        return null;
    }

    public static boolean isValidAmmo(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof IAmmoItem) return true;
        String itemId = ForgeRegistries.ITEMS.getKey(item).toString();
        return AMMO_BY_ID.containsKey(itemId);
    }

    public static String getCaliber(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item instanceof IAmmoItem iAmmo) {
            return iAmmo.getCaliber();
        }
        String itemId = ForgeRegistries.ITEMS.getKey(item).toString();
        AmmoType type = AMMO_BY_ID.get(itemId);
        return type != null ? type.caliber : null;
    }
}
