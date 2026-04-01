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

    // В РУКАХ: 10 секунд базовое
    public static final int BASE_COOLING_TIME_HANDS = 200;
    // В КОТЛЕ: 3.3 секунды (~10x быстрее чем было!)
    public static final int BASE_COOLING_TIME_POT = 20; // Было 67, стало 20 (1 секунда!)

    public static final int ROOM_TEMP = 20;

    private static final Map<UUID, Integer> damageCooldown = new HashMap<>();
    private static final int DAMAGE_COOLDOWN_TICKS = 10;

    /**
     * Устанавливает горячесть предмета
     * @param meltingPoint Температура плавления металла
     * @param isInPot true = в 10 раз быстрее охлаждение!
     */
    public static void setHot(ItemStack stack, int meltingPoint, boolean isInPot) {
        int baseTime = isInPot ? BASE_COOLING_TIME_POT : BASE_COOLING_TIME_HANDS;

        stack.getOrCreateTag().putInt("HotTime", baseTime);
        stack.getOrCreateTag().putInt("HotTimeMax", baseTime);
        stack.getOrCreateTag().putInt("MeltingPoint", meltingPoint);
        stack.getOrCreateTag().putBoolean("CooledInPot", isInPot);
    }

    /**
     * Получает текущую температуру (от MeltingPoint до 20°C)
     */
    public static int getTemperature(ItemStack stack) {
        if (!isHot(stack)) return ROOM_TEMP;

        float hotTime = stack.getTag().getFloat("HotTime");
        int maxTime = stack.getTag().getInt("HotTimeMax");
        int meltingPoint = stack.getTag().getInt("MeltingPoint");
        if (maxTime <= 0) maxTime = BASE_COOLING_TIME_HANDS;
        if (meltingPoint <= 0) meltingPoint = 1000;

        float ratio = hotTime / (float) maxTime;
        return ROOM_TEMP + (int) (ratio * (meltingPoint - ROOM_TEMP));
    }

    public static float getHeatRatio(ItemStack stack) {
        if (!isHot(stack)) return 0f;

        float hotTime = stack.getTag().getFloat("HotTime");
        int maxTime = stack.getTag().getInt("HotTimeMax");
        if (maxTime <= 0) maxTime = BASE_COOLING_TIME_HANDS;

        return Math.min(1f, hotTime / (float) maxTime);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        Player player = event.player;
        UUID playerId = player.getUUID();

        boolean hasHotItem = false;
        float maxHeatRatio = 0f;
        int maxTemp = ROOM_TEMP;
        boolean inventoryChanged = false;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (isHot(stack)) {
                float hotTime = stack.getTag().getFloat("HotTime");
                int meltingPoint = stack.getTag().getInt("MeltingPoint");
                if (meltingPoint <= 0) meltingPoint = 1000;

                // Для шлака берем температуру из его NBT если есть
                if (stack.getItem() instanceof SlagItem) {
                    int slagMeltingPoint = SlagItem.getMetalMeltingPointTemp(stack);
                    if (slagMeltingPoint > 0) meltingPoint = slagMeltingPoint;
                }

                if (hotTime > 0) {
                    // Динамическое охлаждение: быстрее при высокой температуре
                    float currentTempRatio = hotTime / stack.getTag().getInt("HotTimeMax");
                    float coolingMultiplier = 0.3f + (1.7f * currentTempRatio);

                    // Базовое охлаждение: -0.1 за тик
                    float coolingRate = 0.1f * coolingMultiplier;

                    float newHotTime = Math.max(0, hotTime - coolingRate);

                    if (Math.abs(newHotTime - hotTime) >= 0.05f || newHotTime <= 0) {
                        if (newHotTime <= 0) {
                            clearHotTags(stack);
                        } else {
                            stack.getOrCreateTag().putFloat("HotTime", newHotTime);
                        }
                        inventoryChanged = true;
                    }

                    hasHotItem = true;
                    float ratio = newHotTime / stack.getTag().getInt("HotTimeMax");
                    maxHeatRatio = Math.max(maxHeatRatio, ratio);
                    int currentTemp = ROOM_TEMP + (int) (ratio * (meltingPoint - ROOM_TEMP));
                    maxTemp = Math.max(maxTemp, currentTemp);
                } else {
                    clearHotTags(stack);
                    inventoryChanged = true;
                }
            }
        }

        // Урон и поджог
        if (hasHotItem && maxHeatRatio > 0.1f) {
            int fireSeconds = (int) (maxHeatRatio * 4);
            float damageAmount = (maxTemp / 1000f) * maxHeatRatio * 2;

            if (fireSeconds > 0) player.setSecondsOnFire(fireSeconds);

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

        if (inventoryChanged && player.level().getGameTime() % 20 == 0) {
            player.getInventory().setChanged();
        }
    }

    private static void clearHotTags(ItemStack stack) {
        stack.removeTagKey("HotTime");
        stack.removeTagKey("HotTimeMax");
        stack.removeTagKey("MeltingPoint");
        stack.removeTagKey("CooledInPot");
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // Только если горячий (не шлак сам по себе!)
        if (!isHot(stack)) return;

        // Для шлака - проверяем что он реально горячий, и берем его температуру
        int meltingPoint = stack.getTag().getInt("MeltingPoint");
        if (stack.getItem() instanceof SlagItem) {
            int slagTemp = SlagItem.getMetalMeltingPointTemp(stack);
            if (slagTemp > 0) meltingPoint = slagTemp;
        }
        if (meltingPoint <= 0) meltingPoint = 1000;

        float hotTime = stack.getTag().getFloat("HotTime");
        int maxTime = stack.getTag().getInt("HotTimeMax");
        if (maxTime <= 0) maxTime = BASE_COOLING_TIME_HANDS;

        if (hotTime <= 0) return;

        float ratio = hotTime / (float) maxTime;
        int temperature = ROOM_TEMP + (int) (ratio * (meltingPoint - ROOM_TEMP));
        int percent = (int) (ratio * 100);
        boolean cooledInPot = stack.getTag().getBoolean("CooledInPot");

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

        String source = cooledInPot ? " §7[быстрое охлаждение]" : "";

        event.getToolTip().add(Component.literal("")
                .append(Component.literal("▓▓▓ ").withStyle(color))
                .append(Component.literal(intensity).withStyle(color, ChatFormatting.BOLD))
                .append(Component.literal(" ▓▓▓" + source).withStyle(color)));

        event.getToolTip().add(Component.literal(String.format("  §c%d°C §7(%d%%)", temperature, percent)));

        if (ratio > 0.7f) {
            event.getToolTip().add(Component.literal("  §c§oОпасно! Обожжёт руки!")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    public static boolean isHot(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("HotTime")
                && stack.getTag().getFloat("HotTime") > 0;
    }
}