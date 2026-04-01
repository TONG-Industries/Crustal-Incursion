package com.cim.event;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "cim", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HotItemHandler {

    // === КОНФИГУРАЦИЯ ВРЕМЕНИ ОХЛАЖДЕНИЯ ===

    // В руках: базовое время 300 тиков (15 сек)
    public static final int BASE_COOLING_TIME_HANDS = 150;
    // В котле: базовое время 160 тиков (8 сек)
    public static final int BASE_COOLING_TIME_POT = 80;

    // Коэффициент квадратичного охлаждения
    public static final float QUADRATIC_FACTOR = 4.0f;

    // Минимальное время между повреждениями от горячих предметов
    private static final int DAMAGE_COOLDOWN_TICKS = 10;

    public static final int ROOM_TEMP = 20;

    // === ПОРОГИ ТЕМПЕРАТУР ДЛЯ СТАТУСОВ (°C) ===
    // Теперь привязаны к АБСОЛЮТНЫМ градусам, не к процентам!
    public static final int TEMP_EXTREME = 800;    // Выше 800°C
    public static final int TEMP_HOT = 400;       // 400-800°C
    public static final int TEMP_WARM = 100;      // 100-400°C
    // Ниже 100°C - ОСТЫВАЕТ

    private static final Map<UUID, Integer> damageCooldown = new HashMap<>();

    /**
     * Устанавливает горячесть предмета
     * @param meltingPoint Температура плавления металла (влияет на цвет в тултипе)
     * @param isInPot true = охлаждение в котле (быстрее)
     */
    public static void setHot(ItemStack stack, int meltingPoint, boolean isInPot) {
        int baseTime = isInPot ? BASE_COOLING_TIME_POT : BASE_COOLING_TIME_HANDS;

        stack.getOrCreateTag().putFloat("HotTime", baseTime);
        stack.getOrCreateTag().putInt("HotTimeMax", baseTime);
        stack.getOrCreateTag().putInt("MeltingPoint", meltingPoint);
        stack.getOrCreateTag().putBoolean("CooledInPot", isInPot);
    }

    /**
     * Получает текущую температуру предмета (от комнатной до температуры плавления)
     */
    public static int getTemperature(ItemStack stack) {
        if (!isHot(stack)) return ROOM_TEMP;

        float heatRatio = getHeatRatio(stack);
        int meltingPoint = getMeltingPoint(stack);

        if (meltingPoint <= 0) meltingPoint = 1000;

        // Температура = комнатная + (процент нагрева * разница)
        return ROOM_TEMP + (int) (heatRatio * (meltingPoint - ROOM_TEMP));
    }

    /**
     * Получает процент нагрева (1.0 = горячий, 0.0 = остыл)
     */
    public static float getHeatRatio(ItemStack stack) {
        if (!isHot(stack)) return 0f;

        float hotTime = getHotTime(stack);
        int maxTime = getHotTimeMax(stack);
        if (maxTime <= 0) maxTime = BASE_COOLING_TIME_HANDS;

        return Math.max(0f, Math.min(1f, hotTime / (float) maxTime));
    }

    /**
     * Получает процент остывания (0.0 = горячий, 1.0 = остыл) - для удобства отображения
     */
    public static float getCoolingRatio(ItemStack stack) {
        return 1.0f - getHeatRatio(stack);
    }

    // === ГЕТТЕРЫ ДЛЯ NBT ===

    public static float getHotTime(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        // Поддерживаем и float и int для совместимости
        if (stack.getTag().contains("HotTime", 99)) { // 99 = любое число
            return stack.getTag().getFloat("HotTime");
        }
        return 0;
    }

    public static int getHotTimeMax(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return stack.getTag().getInt("HotTimeMax");
    }

    public static int getMeltingPoint(ItemStack stack) {
        if (!stack.hasTag()) return 1000;
        return stack.getTag().getInt("MeltingPoint");
    }

    public static boolean wasCooledInPot(ItemStack stack) {
        if (!stack.hasTag()) return false;
        return stack.getTag().getBoolean("CooledInPot");
    }

    /**
     * Определяет статус нагрева по АБСОЛЮТНОЙ температуре (°C)
     */
    public static HeatStatus getHeatStatus(int temperature) {
        if (temperature >= TEMP_EXTREME) return HeatStatus.EXTREME;
        if (temperature >= TEMP_HOT) return HeatStatus.HOT;
        if (temperature >= TEMP_WARM) return HeatStatus.WARM;
        return HeatStatus.COOLING;
    }

    public enum HeatStatus {
        EXTREME(ChatFormatting.DARK_RED, "РАСКАЛЁННЫЙ", " §c§o Бросай и беги!"),
        HOT(ChatFormatting.RED, "ПЕРЕГРЕТЫЙ", ""),
        WARM(ChatFormatting.GOLD, "ГОРЯЧИЙ", ""),
        COOLING(ChatFormatting.YELLOW, "НАГРЕТЫЙ", "");

        public final ChatFormatting color;
        public final String label;
        public final String warning;

        HeatStatus(ChatFormatting color, String label, String warning) {
            this.color = color;
            this.label = label;
            this.warning = warning;
        }
    }

    // === ОБРАБОТКА ТИКОВ ===

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;
        UUID playerId = player.getUUID();

        boolean hasHotItem = false;
        float maxHeatRatio = 0f;
        int maxTemp = ROOM_TEMP;
        boolean inventoryChanged = false;

        // Обрабатываем весь инвентарь
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (!isHot(stack)) continue;

            float hotTime = getHotTime(stack);
            int maxTime = getHotTimeMax(stack);
            int meltingPoint = getMeltingPoint(stack);

            if (maxTime <= 0) maxTime = BASE_COOLING_TIME_HANDS;
            if (meltingPoint <= 0) meltingPoint = 1000;

            if (hotTime > 0) {
                // === КВАДРАТИЧНОЕ ОХЛАЖДЕНИЕ ===
                float heatRatio = hotTime / (float) maxTime;

                float baseRate = (float) maxTime / 10000f;
                if (baseRate < 0.05f) baseRate = 0.05f;

                float quadraticMultiplier = 1.0f + (QUADRATIC_FACTOR * heatRatio * heatRatio);

                float coolingRate = baseRate * quadraticMultiplier;

                if (coolingRate < 0.02f) coolingRate = 0.02f;

                float newHotTime = Math.max(0, hotTime - coolingRate);

                // Обновляем NBT
                if (newHotTime <= 0.5f) {
                    clearHotTags(stack);
                    newHotTime = 0;
                } else {
                    stack.getOrCreateTag().putFloat("HotTime", newHotTime);
                }

                hasHotItem = true;
                maxHeatRatio = Math.max(maxHeatRatio, heatRatio);
                int currentTemp = getTemperature(stack);
                maxTemp = Math.max(maxTemp, currentTemp);

                inventoryChanged = true;
            } else {
                clearHotTags(stack);
                inventoryChanged = true;
            }
        }

        // Урон и поджог от горячих предметов
        if (hasHotItem && maxHeatRatio > 0.15f) {
            int fireSeconds = (int) (maxHeatRatio * 5);
            float damageAmount = (maxTemp / 1200f) * maxHeatRatio * 1.5f;

            if (fireSeconds > 0) {
                player.setSecondsOnFire(fireSeconds);
            }

            int currentCooldown = damageCooldown.getOrDefault(playerId, 0);
            if (currentCooldown <= 0 && damageAmount >= 0.5f) {
                player.hurt(player.damageSources().onFire(), damageAmount);
                damageCooldown.put(playerId, DAMAGE_COOLDOWN_TICKS);
            } else {
                damageCooldown.put(playerId, Math.max(0, currentCooldown - 1));
            }
        } else {
            damageCooldown.remove(playerId);
        }

        // Синхронизация инвентаря раз в секунду
        if (inventoryChanged && player.level().getGameTime() % 20 == 0) {
            player.getInventory().setChanged();
        }
    }

    /**
     * Очищает все теги горячести
     */
    public static void clearHotTags(ItemStack stack) {
        if (!stack.hasTag()) return;

        stack.removeTagKey("HotTime");
        stack.removeTagKey("HotTimeMax");
        stack.removeTagKey("MeltingPoint");
        stack.removeTagKey("CooledInPot");
    }

    /**
     * Проверяет, горячий ли предмет
     */
    public static boolean isHot(ItemStack stack) {
        if (!stack.hasTag()) return false;

        // Проверяем наличие тега и что время > 0
        if (!stack.getTag().contains("HotTime")) return false;

        float hotTime = stack.getTag().getFloat("HotTime");
        return hotTime > 0.5f; // Небольшой порог для избежания мигания
    }

    // === ТУЛТИПЫ ===

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // Показываем инфу о нагреве ТОЛЬКО если предмет реально горячий
        if (!isHot(stack)) return;

        // НЕ показываем тултип горячести для шлака - пусть SlagItem сам всё показывает
        if (stack.getItem() instanceof SlagItem) {
            return;
        }

        int meltingPoint = getMeltingPoint(stack);
        if (meltingPoint <= 0) meltingPoint = 1000;

        int temperature = getTemperature(stack);
        float heatRatio = getHeatRatio(stack);
        int percent = (int) (heatRatio * 100);
        boolean cooledInPot = wasCooledInPot(stack);

        // === ПРИВЯЗКА СТАТУСА К ГРАДУСАМ, НЕ К ПРОЦЕНТАМ! ===
        HeatStatus status = getHeatStatus(temperature);
        String source = cooledInPot ? " §8[Охл.]" : "";

        // Главная строка с интенсивностью
        event.getToolTip().add(Component.literal("")
                .append(Component.literal("||").withStyle(status.color))
                .append(Component.literal(status.label).withStyle(status.color, ChatFormatting.BOLD))
                .append(Component.literal("||").withStyle(status.color))
                .append(Component.literal(source)));

        // Температура и процент
        event.getToolTip().add(Component.literal(String.format("  §c%d°C §7/ §c%d°C §7(%d%%)",
                temperature, meltingPoint, percent)));

        // Предупреждение если очень горячо
        if (!status.warning.isEmpty()) {
            event.getToolTip().add(Component.literal(status.warning));
        }
    }
}