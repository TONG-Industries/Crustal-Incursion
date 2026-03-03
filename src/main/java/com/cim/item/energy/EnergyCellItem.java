package com.cim.item.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.cim.client.gecko.item.energy.EnergyCellItemRenderer;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class EnergyCellItem extends Item implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private final long capacity;
    private final long chargingSpeed;
    private final long unchargingSpeed;

    // NBT ключ для хранения энергии в предмете
    public static final String TAG_ENERGY = "CellEnergy";

    public EnergyCellItem(Properties properties, long capacity, long chargingSpeed, long unchargingSpeed) {
        super(properties);
        this.capacity = capacity;
        this.chargingSpeed = chargingSpeed;
        this.unchargingSpeed = unchargingSpeed;
    }

    // ========== Параметры ячейки ==========

    public long getCellCapacity(ItemStack stack) { return capacity; }
    public long getCellChargingSpeed(ItemStack stack) { return chargingSpeed; }
    public long getCellUnchargingSpeed(ItemStack stack) { return unchargingSpeed; }
    public boolean isValidCell(ItemStack stack) { return true; }

    // ========== Энергия в NBT ==========

    /**
     * Получить количество энергии, хранящейся в ячейке.
     */
    public static long getStoredEnergy(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) return 0;
        return stack.getTag().getLong(TAG_ENERGY);
    }

    /**
     * Установить количество энергии в ячейке.
     */
    public static void setStoredEnergy(ItemStack stack, long energy) {
        if (stack.isEmpty()) return;
        stack.getOrCreateTag().putLong(TAG_ENERGY, Math.max(0, energy));
    }

    /**
     * Получить максимальную ёмкость ячейки из ItemStack.
     * (Удобный статический метод)
     */
    public static long getMaxEnergy(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EnergyCellItem cell)) return 0;
        return cell.getCellCapacity(stack);
    }

    /**
     * Заполнить ячейку энергией. Возвращает сколько РЕАЛЬНО приняла.
     */
    public static long fill(ItemStack stack, long amount) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EnergyCellItem)) return 0;
        long max = getMaxEnergy(stack);
        long current = getStoredEnergy(stack);
        long space = max - current;
        long toFill = Math.min(amount, space);
        if (toFill > 0) {
            setStoredEnergy(stack, current + toFill);
        }
        return toFill;
    }

    /**
     * Извлечь энергию из ячейки. Возвращает сколько РЕАЛЬНО отдала.
     */
    public static long drain(ItemStack stack, long amount) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EnergyCellItem)) return 0;
        long current = getStoredEnergy(stack);
        long toDrain = Math.min(amount, current);
        if (toDrain > 0) {
            setStoredEnergy(stack, current - toDrain);
        }
        return toDrain;
    }

    // ========== Полоска заряда (как у лука/кирки) ==========

    @Override
    public boolean isBarVisible(ItemStack stack) {
        // Показываем полоску если в ячейке есть хоть немного энергии
        return getStoredEnergy(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long max = getMaxEnergy(stack);
        if (max <= 0) return 0;
        long current = getStoredEnergy(stack);
        return (int) Math.round(13.0 * current / max); // 13 = макс ширина полоски
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Зелёный → Жёлтый → Красный в зависимости от заполненности
        long max = getMaxEnergy(stack);
        if (max <= 0) return 0xFF0000;
        float ratio = (float) getStoredEnergy(stack) / max;
        int r = (int) (255 * (1f - ratio));
        int g = (int) (255 * ratio);
        return (r << 16) | (g << 8);
    }

    // ========== GeckoLib ==========

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private EnergyCellItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new EnergyCellItemRenderer();
                return renderer;
            }
        });
    }

    // ========== Тултип ==========

    @Override
    public void appendHoverText(ItemStack pStack, @Nullable Level pLevel, List<Component> pTooltip, TooltipFlag pFlag) {
        super.appendHoverText(pStack, pLevel, pTooltip, pFlag);

        long stored = getStoredEnergy(pStack);
        long max = getCellCapacity(pStack);

        // Энергия в ячейке
        if (stored > 0) {
            pTooltip.add(Component.literal("§eEnergy: " + formatNumber(stored) + " / " + formatNumber(max) + " HE"));
        } else {
            pTooltip.add(Component.literal("§7Energy: Empty"));
        }

        pTooltip.add(Component.literal("Capacity: " + formatNumber(max) + " HE")
                .withStyle(ChatFormatting.GOLD));
        pTooltip.add(Component.literal("Charge Speed: " + formatNumber(getCellChargingSpeed(pStack)) + " HE/t")
                .withStyle(ChatFormatting.GREEN));
        pTooltip.add(Component.literal("Discharge Speed: " + formatNumber(getCellUnchargingSpeed(pStack)) + " HE/t")
                .withStyle(ChatFormatting.RED));
    }

    private static String formatNumber(long value) {
        if (value >= 1_000_000_000L) return String.format("%.2fG", value / 1_000_000_000.0);
        if (value >= 1_000_000L) return String.format("%.2fM", value / 1_000_000.0);
        if (value >= 1_000L) return String.format("%.2fK", value / 1_000.0);
        return String.valueOf(value);
    }
}