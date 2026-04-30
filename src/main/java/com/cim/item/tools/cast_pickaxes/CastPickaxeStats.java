package com.cim.item.tools.cast_pickaxes;

import net.minecraft.world.item.Tier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CastPickaxeStats {
    private final Tier tier;
    private final int chargeTicks;
    private final float maxDamage;
    private final float reach;
    private final float maxHardnessMultiplier;
    private final int veinMinerLimit;
    private final float veinMinerDurabilityCost;
    private final Predicate<BlockState> veinMinerPredicate;
    private final int veinMinerRange;
    private final List<PerkTooltip> perks;

    public static class PerkTooltip {
        public final String translationKey;
        public final Object[] args;
        public final int color;

        public PerkTooltip(String translationKey, Object[] args, int color) {
            this.translationKey = translationKey;
            this.args = args;
            this.color = color;
        }

        public PerkTooltip(String translationKey, int color) {
            this(translationKey, new Object[0], color);
        }
    }

    public CastPickaxeStats(Tier tier, int chargeTicks, float maxDamage, float reach,
                            float maxHardnessMultiplier, int veinMinerLimit,
                            float veinMinerDurabilityCost, Predicate<BlockState> veinMinerPredicate,
                            int veinMinerRange, List<PerkTooltip> perks) {
        this.tier = tier;
        this.chargeTicks = chargeTicks;
        this.maxDamage = maxDamage;
        this.reach = reach;
        this.maxHardnessMultiplier = maxHardnessMultiplier;
        this.veinMinerLimit = veinMinerLimit;
        this.veinMinerDurabilityCost = veinMinerDurabilityCost;
        this.veinMinerPredicate = veinMinerPredicate;
        this.veinMinerRange = veinMinerRange;
        this.perks = perks;
    }

    public static CastPickaxeStats iron() {
        List<PerkTooltip> perks = new ArrayList<>();

        return new CastPickaxeStats(
                net.minecraft.world.item.Tiers.IRON,
                40,
                12.0f,
                5.0f,
                120.0f,
                0, 0, s -> false, 0, perks
        );
    }

    public static CastPickaxeStats steel() {
        List<PerkTooltip> perks = new ArrayList<>();
        Predicate<BlockState> veinMinerPredicate = state -> {
            return !state.is(net.minecraft.world.level.block.Blocks.STONE) &&
                    !state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE) &&
                    !state.is(net.minecraft.world.level.block.Blocks.DIRT) &&
                    !state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) &&
                    !state.is(net.minecraft.world.level.block.Blocks.NETHERRACK) &&
                    !state.is(net.minecraft.world.level.block.Blocks.END_STONE) &&
                    !state.is(net.minecraft.world.level.block.Blocks.BASALT) &&
                    !state.is(net.minecraft.world.level.block.Blocks.BLACKSTONE) &&
                    !state.is(net.minecraft.world.level.block.Blocks.TUFF) &&
                    !state.is(net.minecraft.world.level.block.Blocks.CALCITE) &&
                    !state.is(net.minecraft.world.level.block.Blocks.DRIPSTONE_BLOCK) &&
                    !state.is(net.minecraft.world.level.block.Blocks.SMOOTH_BASALT) &&
                    !state.is(net.minecraft.world.level.block.Blocks.COBBLED_DEEPSLATE) &&
                    !state.is(net.minecraft.world.level.block.Blocks.DEEPSLATE) &&
                    !state.is(net.minecraft.tags.BlockTags.STONE_ORE_REPLACEABLES);
        };

        return new CastPickaxeStats(
                net.minecraft.world.item.Tiers.DIAMOND,
                50,
                15.6f,
                5.0f,
                200.0f,
                4,
                0.3f,
                veinMinerPredicate,
                2,
                perks
        );
    }

    // Геттеры
    public Tier getTier() { return tier; }
    public int getChargeTicks() { return chargeTicks; }
    public float getMaxDamage() { return maxDamage; }
    public float getReach() { return reach; }

    public float getMaxHardness(float chargePercent) {
        float maxHardnessAtFullCharge = (tier.getSpeed() * maxHardnessMultiplier) / 30.0f;
        return maxHardnessAtFullCharge * chargePercent;
    }

    public int getMiningSecondsEquivalent() {
        return (int)(maxHardnessMultiplier / 20f);
    }

    public int getVeinMinerLimit() { return veinMinerLimit; }
    public float getVeinMinerDurabilityCost() { return veinMinerDurabilityCost; }
    public boolean canVeinMine(BlockState state) { return veinMinerPredicate.test(state); }
    public int getVeinMinerRange() { return veinMinerRange; }
    public List<PerkTooltip> getPerks() { return perks; }

    public int getConglomerateTierLevel() {
        if (tier == net.minecraft.world.item.Tiers.IRON) return 0;
        if (tier == net.minecraft.world.item.Tiers.DIAMOND) return 1;
        if (tier == net.minecraft.world.item.Tiers.NETHERITE) return 2;
        return 0;
    }
}