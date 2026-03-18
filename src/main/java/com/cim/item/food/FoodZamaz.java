package com.cim.item.food;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.TickTask;

import java.util.ArrayList;
import java.util.List;

public class FoodZamaz extends Item {

    private final List<EffectEntry> afterEffects;
    private final List<EffectEntry> mainEffects;

    public FoodZamaz(Builder builder) {
        super(new Item.Properties().food(builder.toFoodProperties()));
        this.afterEffects = new ArrayList<>(builder.afterEffects);
        this.mainEffects = new ArrayList<>(builder.effects);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide && !afterEffects.isEmpty()) {
            // Берём максимальную длительность из mainEffects напрямую
            int maxDuration = 0;
            for (EffectEntry e : mainEffects) {
                if (e.duration > maxDuration) {
                    maxDuration = e.duration;
                }
            }

            int delay = maxDuration;
            ServerLevel serverLevel = (ServerLevel) level;
            serverLevel.getServer().tell(new TickTask(delay, () -> {
                if (entity.isAlive()) {
                    for (EffectEntry e : afterEffects) {
                        entity.addEffect(new MobEffectInstance(e.effect, e.duration, e.amplifier));
                    }
                }
            }));
        }

        return result;
    }

    public static class Builder {
        private int nutrition = 4;
        private float saturation = 1.0F;
        private boolean alwaysEat = false;
        private boolean isMeat = false;
        private int eatDuration = 32;
        final List<EffectEntry> effects = new ArrayList<>();
        final List<EffectEntry> afterEffects = new ArrayList<>();

        public Builder nutrition(int value) { this.nutrition = value; return this; }
        public Builder saturation(float value) { this.saturation = value; return this; }
        public Builder alwaysEat() { this.alwaysEat = true; return this; }
        public Builder meat() { this.isMeat = true; return this; }
        public Builder eatDuration(int ticks) { this.eatDuration = ticks; return this; }

        public Builder effect(MobEffect effect, int seconds, int amplifier, float chance) {
            this.effects.add(new EffectEntry(effect, seconds * 20, amplifier, chance));
            return this;
        }

        public Builder effect(MobEffect effect, int seconds, int amplifier) {
            return effect(effect, seconds, amplifier, 1.0F);
        }

        public Builder afterEffect(MobEffect effect, int seconds, int amplifier) {
            this.afterEffects.add(new EffectEntry(effect, seconds * 20, amplifier, 1.0F));
            return this;
        }

        FoodProperties toFoodProperties() {
            FoodProperties.Builder food = new FoodProperties.Builder()
                    .nutrition(nutrition)
                    .saturationMod(saturation);

            if (alwaysEat) food.alwaysEat();
            if (isMeat) food.meat();
            if (eatDuration == 16) food.fast();

            for (EffectEntry e : effects) {
                food.effect(() -> new MobEffectInstance(e.effect, e.duration, e.amplifier), e.chance);
            }

            return food.build();
        }
    }

    private record EffectEntry(MobEffect effect, int duration, int amplifier, float chance) {}
}