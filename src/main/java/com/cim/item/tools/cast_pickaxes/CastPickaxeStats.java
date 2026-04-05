package com.cim.item.tools.cast_pickaxes;

import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import java.util.function.Predicate;

public class CastPickaxeStats {
    private final Tier tier;
    private final int chargeTicks;
    private final float maxDamage;
    private final float reach;
    private final float maxHardnessMultiplier;

    // Жилковый майнер (0 = отключен)
    private final int veinMinerLimit;
    private final float veinMinerDurabilityCost;
    private final Predicate<BlockState> veinMinerPredicate;

    public CastPickaxeStats(Tier tier, int chargeTicks, float maxDamage, float reach,
                            float maxHardnessMultiplier, int veinMinerLimit,
                            float veinMinerDurabilityCost, Predicate<BlockState> veinMinerPredicate) {
        this.tier = tier;
        this.chargeTicks = chargeTicks;
        this.maxDamage = maxDamage;
        this.reach = reach;
        this.maxHardnessMultiplier = maxHardnessMultiplier;
        this.veinMinerLimit = veinMinerLimit;
        this.veinMinerDurabilityCost = veinMinerDurabilityCost;
        this.veinMinerPredicate = veinMinerPredicate;
    }

    // Пресеты для удобства
    public static CastPickaxeStats iron() {
        return new CastPickaxeStats(
                net.minecraft.world.item.Tiers.IRON,
                40, // 2 секунды зарядки
                12.0f,
                5.0f,
                120.0f, // ~4 hardness для железа
                0, 0, s -> false
        );
    }

    public static CastPickaxeStats steel() {
        return new CastPickaxeStats(
                net.minecraft.world.item.Tiers.DIAMOND, // Алмазный тир
                40,
                15.6f,
                5.0f,
                200.0f,
                4, // 4 доп блока
                0.3f, // 30% прочности за блок
                state -> state.is(Tags.Blocks.ORES) ||
                        state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN) ||
                        state.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS)
        );
    }

    public Tier getTier() { return tier; }
    public int getChargeTicks() { return chargeTicks; }
    public float getMaxDamage() { return maxDamage; }
    public float getReach() { return reach; }
    public float getMaxHardness(float chargePercent) {
        float base = (tier.getSpeed() * maxHardnessMultiplier) / 30.0f;
        return base * chargePercent; // Масштабируем от заряда
    }
    public int getVeinMinerLimit() { return veinMinerLimit; }
    public float getVeinMinerDurabilityCost() { return veinMinerDurabilityCost; }
    public boolean canVeinMine(BlockState state) { return veinMinerPredicate.test(state); }
}