package com.cim.item.tools.cast_pickaxes;



import com.cim.block.basic.conglomerate.ConglomerateBlock;
import com.cim.client.gecko.item.tools.CastPickaxeItemRenderer;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

public class CastPickaxeItem extends PickaxeItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final CastPickaxeStats stats;

    private static final String TAG_ANIM_STATE = "CastAnimState";
    private static final int ANIM_STATE_CHARGING = 1;
    private static final int ANIM_STATE_HOLDING = 2;
    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("c6a7b6f2-4b2c-11ee-be56-0242ac120002");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("c6a7b9c0-4b2c-11ee-be56-0242ac120002");
    private static final int COOLDOWN_TICKS = 20;

    // Анимации с динамической скоростью
    private static final RawAnimation CHARGING_ANIM = RawAnimation.begin()
            .thenPlay("charging");
    private static final RawAnimation HOLDING_ANIM = RawAnimation.begin()
            .thenPlay("holding");
    private static final RawAnimation HIT_ANIM = RawAnimation.begin()
            .thenPlay("hit");

    public CastPickaxeItem(CastPickaxeStats stats, Properties properties) {
        super(stats.getTier(), 1, -2.8f, properties.stacksTo(1));
        this.stats = stats;
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public CastPickaxeStats getStats() { return stats; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.CONTINUE)
                .triggerableAnim("charging", CHARGING_ANIM)
                .triggerableAnim("holding", HOLDING_ANIM)
                .triggerableAnim("hit", HIT_ANIM)
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /**
     * Рассчитывает скорость анимации на основе времени зарядки кирки.
     * Для кирки с 60 тиков зарядки, анимация должна играться в 1.5x (60/40)
     */
    public float getAnimationSpeed() {
        return 40.0f / stats.getChargeTicks();
    }

    private boolean canUse(Player player) {
        return player.getOffhandItem().isEmpty() && !player.getCooldowns().isOnCooldown(this);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (!canUse(player)) {
            if (!player.level().isClientSide && player.getCooldowns().isOnCooldown(this)) {
                player.displayClientMessage(Component.literal("§cПерезарядка..."), true);
            } else if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.cim.cast_pickaxe.warning.twohanded")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        player.startUsingItem(context.getHand());

        if (!player.level().isClientSide) {
            ItemStack stack = player.getItemInHand(context.getHand());
            long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) player.level());

            // Запускаем анимацию с рассчитанной скоростью
            float speed = getAnimationSpeed();
            triggerAnim(player, instanceId, "controller", "charging");

            // Отправляем пакет с скоростью анимации клиенту
            // Примечание: для точной синхронизации скорости нужен кастомный пакет
            // или использовать DataTickets в контроллере
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!canUse(player)) {
            if (!level.isClientSide && player.getCooldowns().isOnCooldown(this)) {
                player.displayClientMessage(Component.literal("§cПерезарядка..."), true);
            }
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        player.startUsingItem(hand);

        if (!level.isClientSide) {
            ItemStack stack = player.getItemInHand(hand);
            long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) level);
            triggerAnim(player, instanceId, "controller", "charging");
        }

        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        super.onUseTick(level, entity, stack, remainingUseDuration);

        if (level.isClientSide || !(entity instanceof Player player)) return;

        int chargeTime = 72000 - remainingUseDuration;
        if (chargeTime == stats.getChargeTicks()) {
            long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) level);
            triggerAnim(player, instanceId, "controller", "holding");
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int chargeTime = 72000 - timeLeft;
        InteractionHand hand = player.getUsedItemHand();
        float chargePercent = Math.min(1.0f, chargeTime / (float) stats.getChargeTicks());

        if (!level.isClientSide) {
            long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) level);
            triggerAnim(player, instanceId, "controller", "hit");
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        if (chargePercent < 0.05f) {
            player.swing(hand, true);
            if (!level.isClientSide) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_WEAK,
                        SoundSource.PLAYERS, 0.5f, 1.2f);
            }
            return;
        }

        int attackResult = performAttack(stack, level, player, chargePercent, hand);
        player.swing(hand, true);

        if (!level.isClientSide) {
            if (attackResult == 1) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.PLAYERS, 0.8f, 1.0f);
            } else if (attackResult == 0) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_WEAK,
                        SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        }
    }

    private int performAttack(ItemStack stack, Level level, Player player, float chargePercent, InteractionHand hand) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getViewVector(1.0f);
        float reach = stats.getReach();
        Vec3 reachVec = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);

        LivingEntity target = findEntityOnPath(player, level, eyePos, reachVec);
        if (target != null && player.distanceToSqr(target) <= reach * reach) {
            boolean hit = performHeavyAttack(stack, level, player, target, chargePercent);
            return hit ? 1 : 0;
        } else {
            BlockHitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(
                    eyePos, reachVec,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    player
            ));
            if (blockHit.getType() != HitResult.Type.MISS) {
                boolean hit = performHeavyStrike(stack, level, player, blockHit.getBlockPos(),
                        blockHit.getDirection(), chargePercent, hand);
                return hit ? 2 : 0;
            }
        }
        return 0;
    }

    private LivingEntity findEntityOnPath(Player player, Level level, Vec3 start, Vec3 end) {
        AABB searchBox = new AABB(start, end).inflate(1.0);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && e.isPickable());

        LivingEntity closest = null;
        double closestDist = stats.getReach() * stats.getReach();

        for (LivingEntity entity : entities) {
            Optional<Vec3> hit = entity.getBoundingBox().clip(start, end);
            if (hit.isPresent()) {
                double dist = start.distanceToSqr(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                }
            }
        }
        return closest;
    }

    private boolean performHeavyStrike(ItemStack stack, Level level, Player player, BlockPos pos,
                                       Direction face, float chargePercent, InteractionHand hand) {
        if (level.isClientSide) return false;

        BlockState state = level.getBlockState(pos);
        boolean fullCharge = chargePercent >= 1.0f;

        // СНАЧАЛА проверяем ConglomerateBlock
        if (state.getBlock() instanceof ConglomerateBlock conglomerate) {
            if (!fullCharge) {
                // Эффекты как при ударе по недобываемому блоку (Пункт 2)
                spawnCritParticles(level, pos.getCenter());
                level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 1.0f, 0.5f);
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.5f, 1.2f);

                // Немного тратим прочность, чтобы игрок не спамил просто так
                stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
                return true; // Строго возвращаем true, чтобы удар засчитался как завершенный, но БЕЗ добычи
            }

            // Если заряд 100% - полноценная добыча
            ConglomerateBlock.CastPickaxeTier tier = (this.stats == CastPickaxeStats.iron())
                    ? ConglomerateBlock.CastPickaxeTier.IRON
                    : ConglomerateBlock.CastPickaxeTier.STEEL;

            conglomerate.mineWithCastPickaxe((ServerLevel)level, pos, player, tier);

            // Эффекты успешного мощного удара
            stack.hurtAndBreak(5, player, (p) -> p.broadcastBreakEvent(hand));
            spawnCritParticles(level, pos.getCenter());
            playPickaxeHitSound(level, pos, 1.0f); // Сочный звук попадания
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.8f);
            player.causeFoodExhaustion(0.2f);

            return true;
        }

        // Дальше идет твоя старая логика для обычных блоков (hardness и т.д.)
        float hardness = state.getDestroySpeed(level, pos);
        boolean canHarvest = isCorrectToolForDrops(stack, state);

        if (hardness < 0) return true; // Бедрок и т.п.

        float maxHardness = stats.getMaxHardness(chargePercent);

        if (fullCharge && stats.getVeinMinerLimit() > 0 && stats.canVeinMine(state)) {
            return performVeinMiner(stack, level, player, pos, state, face, hand, chargePercent);
        }

        if (canHarvest && hardness <= maxHardness) {
            level.destroyBlock(pos, true, player);
            int damage = fullCharge ? 2 : 1;
            stack.hurtAndBreak(damage, player, (p) -> p.broadcastBreakEvent(hand));
            if (fullCharge) player.causeFoodExhaustion(0.2f);
            playPickaxeHitSound(level, pos, chargePercent);
            return true;
        } else {
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
            spawnCritParticles(level, pos.getCenter());

            if (fullCharge) {
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.8f);
            } else {
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.5f, 1.2f);
            }
            return true;
        }
    }

    private void spawnCritParticles(Level level, Vec3 pos) {
        if (level.isClientSide) return;
        ((ServerLevel)level).sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z, 15, 0.3, 0.3, 0.3, 0.1);
    }

    private void playPickaxeHitSound(Level level, BlockPos pos, float chargePercent) {
        if (level.isClientSide) return;
        float volume = 0.4f + chargePercent * 0.8f;
        float pitch = 1.2f - chargePercent * 0.4f;
        level.playSound(null, pos,
                com.cim.sound.ModSounds.PICKAXE_HIT.get(),
                SoundSource.PLAYERS, volume, pitch);
    }

    private boolean performVeinMiner(ItemStack stack, Level level, Player player, BlockPos center,
                                     BlockState centerState, Direction face, InteractionHand hand, float chargePercent) {
        List<BlockPos> toBreak = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(center);
        visited.add(center);
        Block targetBlock = centerState.getBlock();
        int range = stats.getVeinMinerRange();

        while (!queue.isEmpty() && toBreak.size() < stats.getVeinMinerLimit()) {
            BlockPos current = queue.poll();

            // Поиск в зоне range (например, range=2 -> от -2 до 2 по всем осям)
            for (int x = -range; x <= range; x++) {
                for (int y = -range; y <= range; y++) {
                    for (int z = -range; z <= range; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        BlockPos neighbor = current.offset(x, y, z);
                        if (visited.contains(neighbor)) continue;
                        visited.add(neighbor);

                        BlockState neighborState = level.getBlockState(neighbor);
                        if (neighborState.is(targetBlock) && stats.canVeinMine(neighborState)) {
                            toBreak.add(neighbor);
                            queue.add(neighbor);
                            if (toBreak.size() >= stats.getVeinMinerLimit()) break;
                        }
                    }
                    if (toBreak.size() >= stats.getVeinMinerLimit()) break;
                }
                if (toBreak.size() >= stats.getVeinMinerLimit()) break;
            }
        }

        // Собираем дропы
        List<net.minecraft.world.item.ItemStack> allDrops = new ArrayList<>();

        allDrops.addAll(Block.getDrops(centerState, (ServerLevel)level, center, null, player, stack));
        level.destroyBlock(center, false);

        for (BlockPos pos : toBreak) {
            BlockState state = level.getBlockState(pos);
            allDrops.addAll(Block.getDrops(state, (ServerLevel)level, pos, null, player, stack));
            level.destroyBlock(pos, false);
        }

        // Спавним дропы в центре
        for (net.minecraft.world.item.ItemStack drop : allDrops) {
            ItemEntity entity = new ItemEntity(level,
                    center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, drop);
            entity.setDeltaMovement(0, 0.1, 0);
            level.addFreshEntity(entity);
        }

        // Урон кирке
        int extraBlocks = toBreak.size();
        float durabilityCost = 2.0f + (extraBlocks * stats.getVeinMinerDurabilityCost());
        int totalDamage = (int)Math.ceil(durabilityCost);
        stack.hurtAndBreak(totalDamage, player, (p) -> p.broadcastBreakEvent(hand));
        player.causeFoodExhaustion(0.2f);

        playPickaxeHitSound(level, center, chargePercent);
        spawnCritParticles(level, center.getCenter());

        for (BlockPos pos : toBreak) {
            spawnCritParticles(level, pos.getCenter());
        }
        return true;
    }

    private boolean performHeavyAttack(ItemStack stack, Level level, Player player,
                                       LivingEntity target, float chargePercent) {
        if (level.isClientSide) return false;

        float damage = stats.getMaxDamage() * chargePercent;
        if (damage < 1.0f) damage = 1.0f;

        if (target.hurt(player.damageSources().playerAttack(player), damage)) {
            float knockback = 1.2f * chargePercent;
            if (knockback > 0) {
                target.knockback(knockback,
                        player.getX() - target.getX(),
                        player.getZ() - target.getZ());
            }

            stack.hurtAndBreak(2, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));

            int particleCount = (int)(20 * chargePercent);
            if (particleCount > 0) {
                ((ServerLevel)level).sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        particleCount, 0.3, 0.4, 0.3, 0.2);
            }

            float volume = 0.2F + (chargePercent * 0.6F);
            float pitch = 1.6F - (chargePercent * 0.4F);
            level.playSound(null, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    com.cim.sound.ModSounds.BULLET_IMPACT.get(), SoundSource.PLAYERS, volume, pitch);

            return true;
        }
        return false;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, Player player) {
        if (player.isUsingItem() || player.getCooldowns().isOnCooldown(this)) {
            return true;
        }
        return super.onBlockStartBreak(itemstack, pos, player);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && player.getCooldowns().isOnCooldown(this)) {
            return false;
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();

            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    ATTACK_DAMAGE_UUID, "Tool modifier",
                    stats.getTier().getAttackDamageBonus() + 1.0f,
                    AttributeModifier.Operation.ADDITION
            ));

            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                    ATTACK_SPEED_UUID, "Tool modifier",
                    -2.8f,
                    AttributeModifier.Operation.ADDITION
            ));

            return builder.build();
        }
        return super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // Добавляем перки из конфигурации
        for (CastPickaxeStats.PerkTooltip perk : stats.getPerks()) {
            net.minecraft.network.chat.MutableComponent text;
            if (perk.args.length > 0) {
                text = Component.translatable(perk.translationKey, perk.args);
            } else {
                text = Component.translatable(perk.translationKey);
            }
            tooltip.add(text.withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(perk.color)));
        }
        tooltip.add(Component.translatable("item.cim.cast_pickaxe.desc.charge").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cim.cast_pickaxe.desc.mining_power",
                stats.getMiningSecondsEquivalent()).withStyle(ChatFormatting.GOLD));

        if (stats.getVeinMinerLimit() > 0) {
            tooltip.add(Component.translatable("item.cim.cast_pickaxe.desc.vein_miner_info",
                            stats.getVeinMinerLimit(),
                            (int)(stats.getVeinMinerDurabilityCost() * 100))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private CastPickaxeItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new CastPickaxeItemRenderer();
                return renderer;
            }
        });
    }
}