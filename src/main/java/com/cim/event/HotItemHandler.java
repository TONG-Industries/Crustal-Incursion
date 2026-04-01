package com.cim.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
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

    // Базовое время остывания (10 секунд = 200 тиков)
    public static final int BASE_COOLING_TIME = 200;
    // Максимальная температура (для отображения в градусах)
    public static final int MAX_TEMPERATURE = 1000; // °C

    // Кулдаун для урона, чтобы не спамить
    private static final Map<UUID, Integer> damageCooldown = new HashMap<>();
    private static final int DAMAGE_COOLDOWN_TICKS = 10; // Урон раз в 0.5 секунды

    /**
     * Получает "температуру" предмета в градусах Цельсия
     * 100% hotTime = MAX_TEMPERATURE (1000°C)
     * 0% hotTime = 20°C (комнатная)
     */
    public static int getTemperature(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains("HotTime")) return 20;

        int hotTime = stack.getTag().getInt("HotTime");
        int maxTime = stack.getTag().getInt("HotTimeMax");
        if (maxTime <= 0) maxTime = BASE_COOLING_TIME;

        float ratio = hotTime / (float) maxTime;
        return 20 + (int) (ratio * (MAX_TEMPERATURE - 20));
    }

    /**
     * Получает процент горячести (0.0 - 1.0)
     */
    public static float getHeatRatio(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains("HotTime")) return 0f;

        int hotTime = stack.getTag().getInt("HotTime");
        int maxTime = stack.getTag().getInt("HotTimeMax");
        if (maxTime <= 0) maxTime = BASE_COOLING_TIME;

        return Math.min(1f, hotTime / (float) maxTime);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;
        UUID playerId = player.getUUID();

        boolean hasHotItem = false;
        float maxHeatRatio = 0f; // Максимальная горячесть среди всех предметов
        boolean inventoryChanged = false;

        // === ПОСТОЯННОЕ ОХЛАЖДЕНИЕ ===
        // Каждый тик уменьшаем HotTime на 0.1 (10 тиков = -1)
        // Это плавное охлаждение без дёрганья!
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (stack.hasTag() && stack.getTag().contains("HotTime")) {
                float hotTime = stack.getTag().getFloat("HotTime");

                if (hotTime > 0) {
                    // Плавное уменьшение: -0.1 за тик = -2 за секунду
                    float newHotTime = Math.max(0, hotTime - 0.1f);

                    // Обновляем только если значение изменилось достаточно сильно
                    // или если предмет остыл полностью
                    if (Math.abs(newHotTime - hotTime) >= 0.05f || newHotTime <= 0) {
                        if (newHotTime <= 0) {
                            // Полностью остыл - чистим теги
                            stack.removeTagKey("HotTime");
                            stack.removeTagKey("HotTimeMax");
                        } else {
                            stack.getOrCreateTag().putFloat("HotTime", newHotTime);
                        }
                        inventoryChanged = true;
                    }

                    hasHotItem = true;
                    maxHeatRatio = Math.max(maxHeatRatio, getHeatRatio(stack));
                } else {
                    // Уже остыл, но теги остались
                    stack.removeTagKey("HotTime");
                    stack.removeTagKey("HotTimeMax");
                    inventoryChanged = true;
                }
            }
        }

        // === УРОН И ПОДЖОГ ОТ ГОРЯЧИХ ПРЕДМЕТОВ ===
        if (hasHotItem && maxHeatRatio > 0.1f) { // Начинает действовать после 10%

            // Чем горячее, тем больше урон и дольше поджог
            // 10% = 1 урон, 100% = 10 урона
            int fireSeconds = (int) (maxHeatRatio * 4); // 0.4 - 4 секунды поджога
            float damageAmount = maxHeatRatio * 2; // 0.2 - 2 урона

            // Поджог всегда, если горячо
            if (fireSeconds > 0) {
                player.setSecondsOnFire(fireSeconds);
            }

            // Урон с кулдауном
            int currentCooldown = damageCooldown.getOrDefault(playerId, 0);
            if (currentCooldown <= 0 && damageAmount >= 0.5f) {
                // Наносим урон
                player.hurt(player.damageSources().onFire(), damageAmount);
                damageCooldown.put(playerId, DAMAGE_COOLDOWN_TICKS);
            } else {
                damageCooldown.put(playerId, Math.max(0, currentCooldown - 1));
            }
        } else {
            damageCooldown.remove(playerId);
        }

        // === СИНХРОНИЗАЦИЯ ИНВЕНТАРЯ ===
        // Только при реальных изменениях и не чаще раза в секунду
        if (inventoryChanged && player.level().getGameTime() % 20 == 0) {
            player.getInventory().setChanged();
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.hasTag() || !stack.getTag().contains("HotTime")) return;

        float hotTime = stack.getTag().getFloat("HotTime");
        int maxTime = stack.getTag().getInt("HotTimeMax");
        if (maxTime <= 0) maxTime = BASE_COOLING_TIME;

        if (hotTime <= 0) return;

        float ratio = hotTime / (float) maxTime;
        int temperature = getTemperature(stack);
        int percent = (int) (ratio * 100);

        // Формируем градиент цветов от белого к красному
        ChatFormatting color;
        String intensity;

        if (ratio > 0.8f) {
            color = ChatFormatting.RED;
            intensity = "РАСКАЛЁННЫЙ";
        } else if (ratio > 0.5f) {
            color = ChatFormatting.GOLD;
            intensity = "ГОРЯЧИЙ";
        } else if (ratio > 0.2f) {
            color = ChatFormatting.YELLOW;
            intensity = "ТЁПЛЫЙ";
        } else {
            color = ChatFormatting.GRAY;
            intensity = "ОСТЫВАЕТ";
        }

        // Главная строка: температура и процент
        event.getToolTip().add(Component.literal("")
                .append(Component.literal("▓▓▓ ").withStyle(color))
                .append(Component.literal(intensity).withStyle(color, ChatFormatting.BOLD))
                .append(Component.literal(" ▓▓▓").withStyle(color)));

        // Детали: градусы и процент
        event.getToolTip().add(Component.literal(String.format("  %d°C (%d%%)", temperature, percent))
                .withStyle(ChatFormatting.GRAY));

        // Предупреждение если очень горячо
        if (ratio > 0.7f) {
            event.getToolTip().add(Component.literal("  §c§oОпасно! Обожжёт руки!")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    /**
     * Устанавливает горячесть предмета (для использования из других классов)
     * @param stack Предмет
     * @param seconds Время горячести в секундах
     */
    public static void setHot(ItemStack stack, int seconds) {
        int ticks = seconds * 20;
        stack.getOrCreateTag().putFloat("HotTime", ticks);
        stack.getOrCreateTag().putInt("HotTimeMax", ticks);
    }

    /**
     * Проверяет, является ли предмет горячим
     */
    public static boolean isHot(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains("HotTime")) return false;
        return stack.getTag().getFloat("HotTime") > 0;
    }
}