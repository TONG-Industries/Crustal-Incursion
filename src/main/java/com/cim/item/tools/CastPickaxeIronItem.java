package com.cim.item.tools;

import com.cim.client.gecko.item.tools.CastPickaxeIronItemRenderer;
import com.cim.sound.ModSounds;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

public class CastPickaxeIronItem extends PickaxeItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("c6a7b6f2-4b2c-11ee-be56-0242ac120002");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("c6a7b9c0-4b2c-11ee-be56-0242ac120002");

    public static final int CHARGE_TICKS = 40;
    public static final int COOLDOWN_TICKS = 20;
    public static final float HEAVY_REACH = 5.0f;
    public static final float MAX_DAMAGE = 12.0f;

    private static final RawAnimation CHARGING_ANIM = RawAnimation.begin().thenPlay("charging");
    private static final RawAnimation HOLDING_ANIM = RawAnimation.begin().thenPlay("holding");
    private static final RawAnimation HIT_ANIM = RawAnimation.begin().thenPlay("hit");

    public CastPickaxeIronItem(Properties properties) {
        super(Tiers.IRON, 1, -2.8f, properties.stacksTo(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

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
                        Component.translatable("item.cim.cast_pickaxe_iron.warning.twohanded")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        player.startUsingItem(context.getHand());

        // Запускаем анимацию зарядки на сервере (синхронизируется с клиентом)
        if (!player.level().isClientSide) {
            ItemStack stack = player.getItemInHand(context.getHand());
            long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) player.level());
            triggerAnim(player, instanceId, "controller", "charging");
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
        return UseAnim.NONE; // Отключаем стандартную анимацию лука
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        super.onUseTick(level, entity, stack, remainingUseDuration);

        if (level.isClientSide || !(entity instanceof Player player)) return;

        int chargeTime = 72000 - remainingUseDuration;

        // Когда достигли полного заряда — переключаем на holding
        if (chargeTime == CHARGE_TICKS) {
            long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) level);
            triggerAnim(player, instanceId, "controller", "holding");
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int chargeTime = 72000 - timeLeft;
        InteractionHand hand = player.getUsedItemHand();

        // Расчет процента заряда (0.0 - 1.0)
        float chargePercent = Math.min(1.0f, chargeTime / (float) CHARGE_TICKS);

        // Запускаем анимацию удара
        if (!level.isClientSide) {
            long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) level);
            triggerAnim(player, instanceId, "controller", "hit");
        }

        // Слишком короткое нажатие — просто взмах
        if (chargePercent < 0.1f) {
            player.swing(hand, true);
            return;
        }

        // Кулдаун только при полном заряде (100%)
        if (chargePercent >= 1.0f) {
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }

        // Выполняем удар с учетом процента заряда
        boolean success = performAttack(stack, level, player, chargePercent);

        player.swing(hand, true);
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(),
                    success ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                    SoundSource.PLAYERS, 0.8f, success ? 1.0f : 1.2f);
        }
    }

    /**
     * Выполняет атаку с учетом процента заряда
     */
    private boolean performAttack(ItemStack stack, Level level, Player player, float chargePercent) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 reachVec = eyePos.add(lookVec.x * HEAVY_REACH, lookVec.y * HEAVY_REACH, lookVec.z * HEAVY_REACH);

        // 1. Сначала проверяем сущности (приоритет)
        LivingEntity target = findEntityOnPath(player, level, eyePos, reachVec);
        if (target != null && player.distanceToSqr(target) <= HEAVY_REACH * HEAVY_REACH) {
            return performHeavyAttack(stack, level, player, target, chargePercent);
        } else {
            // 2. Затем блоки
            BlockHitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(
                    eyePos, reachVec,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    player
            ));

            if (blockHit.getType() != HitResult.Type.MISS) {
                return performHeavyStrike(stack, level, player, blockHit.getBlockPos(), blockHit.getDirection(), chargePercent);
            }
        }
        return false;
    }

    private LivingEntity findEntityOnPath(Player player, Level level, Vec3 start, Vec3 end) {
        AABB searchBox = new AABB(start, end).inflate(1.0);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && e.isPickable());

        LivingEntity closest = null;
        double closestDist = HEAVY_REACH * HEAVY_REACH;

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

    private boolean performHeavyStrike(ItemStack stack, Level level, Player player, BlockPos pos, Direction face, float chargePercent) {
        if (level.isClientSide) return false;

        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);

        // Эффекты удара (частицы, звуки) зависят от силы заряда
        boolean strong = chargePercent >= 1.0f;
        spawnHeavyEffects(level, pos, face, state, strong);

        if (hardness < 0) return true;

        // При полном заряде — мгновенная добыча
        if (chargePercent >= 1.0f) {
            boolean canHarvest = isCorrectToolForDrops(stack, state);
            float maxHardness = (Tiers.IRON.getSpeed() * 120) / 30.0f;

            if (canHarvest && hardness <= maxHardness) {
                level.destroyBlock(pos, true, player);
                stack.hurtAndBreak(2, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
                player.causeFoodExhaustion(0.2f);
                return true;
            } else {
                stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
                return true;
            }
        } else {
            float efficiencyMultiplier = 1.0f + chargePercent * 2.0f; // от 1x до 3x скорость
            if (hardness > 0) {
                stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
            }
            return true;
        }
    }

    private boolean performHeavyAttack(ItemStack stack, Level level, Player player, LivingEntity target, float chargePercent) {
        if (level.isClientSide) return false;

        float damage = MAX_DAMAGE * chargePercent;
        if (damage < 1.0f) damage = 1.0f;

        if (target.hurt(player.damageSources().playerAttack(player), damage)) {
            float knockback = 1.2f * chargePercent;
            if (knockback > 0) {
                target.knockback(knockback,
                        player.getX() - target.getX(),
                        player.getZ() - target.getZ());
            }

            stack.hurtAndBreak(2, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));

            // Частицы
            int particleCount = (int)(20 * chargePercent);
            if (particleCount > 0) {
                ((ServerLevel)level).sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        particleCount, 0.3, 0.4, 0.3, 0.2);
            }

            // --- ПРОИГРЫВАНИЕ ЗВУКА (добавлено) ---
            if (chargePercent >= 1.0f) { // только при полностью заряженном ударе
                Random random = new Random();
                float pitch = 0.7F + chargePercent * 0.5F + random.nextFloat() * 0.1F;
                level.playSound(null, target.blockPosition(), ModSounds.BULLET_IMPACT.get(), SoundSource.PLAYERS, 0.5F, pitch);
            }


            return true;
        }
        return false;
    }

    private void spawnHeavyEffects(Level level, BlockPos pos, Direction face, BlockState state, boolean strong) {
        if (level.isClientSide) return;

        Vec3 hitVec = pos.getCenter().add(
                face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5
        );

        ((ServerLevel)level).sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                hitVec.x, hitVec.y, hitVec.z, strong ? 30 : 10, 0.3, 0.3, 0.3, 0.15
        );

        ((ServerLevel)level).sendParticles(ParticleTypes.CRIT,
                hitVec.x, hitVec.y, hitVec.z, strong ? 15 : 5, 0.2, 0.2, 0.2, 0.5);

        level.playSound(null, pos,
                strong ? SoundEvents.ANVIL_LAND : SoundEvents.STONE_HIT,
                SoundSource.BLOCKS, 0.5f, strong ? 0.6f : 1.2f);
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
                    Tiers.IRON.getAttackDamageBonus() + 1.0f,
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
        tooltip.add(Component.translatable("item.cim.cast_pickaxe_iron.desc.charge").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.cim.cast_pickaxe_iron.desc.power").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cim.cast_pickaxe_iron.desc.priority").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("item.cim.cast_pickaxe_iron.desc.twohanded")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private CastPickaxeIronItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new CastPickaxeIronItemRenderer();
                return renderer;
            }
        });
    }
}