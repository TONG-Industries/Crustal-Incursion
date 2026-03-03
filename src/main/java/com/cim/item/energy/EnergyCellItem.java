package com.cim.item.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
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

    public EnergyCellItem(Properties properties, long capacity, long chargingSpeed, long unchargingSpeed) {
        super(properties);
        this.capacity = capacity;
        this.chargingSpeed = chargingSpeed;
        this.unchargingSpeed = unchargingSpeed;
    }

    public long getCellCapacity(ItemStack stack) { return capacity; }
    public long getCellChargingSpeed(ItemStack stack) { return chargingSpeed; }
    public long getCellUnchargingSpeed(ItemStack stack) { return unchargingSpeed; }
    public boolean isValidCell(ItemStack stack) { return true; }

    // ========== GeckoLib ==========

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Нет анимаций
    }

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
        pTooltip.add(Component.literal("Capacity: " + formatNumber(getCellCapacity(pStack)) + " HE")
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