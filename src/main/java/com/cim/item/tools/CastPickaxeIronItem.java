package com.cim.item.tools;

import com.cim.client.gecko.item.tools.CastPickaxeIronItemRenderer;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class CastPickaxeIronItem extends PickaxeItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("c6a7b6f2-4b2c-11ee-be56-0242ac120002");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("c6a7b9c0-4b2c-11ee-be56-0242ac120002");

    private static final int CHARGE_TICKS = 40; // 2 секунды для полной готовности
    private static final float HEAVY_REACH = 5.0f; // Дальность мощного удара

    public CastPickaxeIronItem(Properties properties) {
        super(Tiers.IRON, 1, -2.8f, properties.stacksTo(1));
    }

    // Проверка двуручности
    private boolean canUse(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && player.getOffhandItem().isEmpty();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (!canUse(player, context.getHand())) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.cim.cast_pickaxe_iron.warning.twohanded")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }

        // Начинаем зарядку независимо от того, есть ли у блока use
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME; // Блокируем use блока
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!canUse(player, hand)) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.cim.cast_pickaxe_iron.warning.twohanded")
                                .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    // Огромная длительность, чтобы игрок мог держать сколько угодно
    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; // Как у лука - 1 час реального времени
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int chargeTime = 72000 - timeLeft;
        InteractionHand hand = player.getUsedItemHand();

        // Только полный заряд (с небольшой погрешностью в 2 тика)
        if (chargeTime < CHARGE_TICKS - 2) {
            performSwing(level, player, hand, false);
            return;
        }

        // Raycast в момент отпускания - определяем что под прицелом ПРЯМО СЕЙЧАС
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getViewVector(1.0f);
        Vec3 reachPos = eyePos.add(lookVec.x * HEAVY_REACH, lookVec.y * HEAVY_REACH, lookVec.z * HEAVY_REACH);

        HitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
                eyePos, reachPos,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        ));

        boolean success = false;

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            success = performHeavyBlockStrike(stack, level, player, blockHit.getBlockPos(), blockHit.getDirection());
        } else {
            // Проверяем сущности
            HitResult entityHit = pickEntity(player, HEAVY_REACH);
            if (entityHit != null && entityHit.getType() == HitResult.Type.ENTITY) {
                EntityHitResult eHit = (EntityHitResult) entityHit;
                if (eHit.getEntity() instanceof LivingEntity target) {
                    success = performHeavyEntityStrike(stack, level, player, target);
                }
            }
        }

        // Если не попали ни во что - взмах
        if (!success) {
            performSwing(level, player, hand, false);
        } else {
            performSwing(level, player, hand, true);
        }
    }

    private boolean performHeavyBlockStrike(ItemStack stack, Level level, Player player, BlockPos pos, Direction face) {
        if (level.isClientSide) return false;

        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);

        // Проверка дистанции
        if (player.distanceToSqr(pos.getCenter()) > (HEAVY_REACH * HEAVY_REACH)) return false;

        // Эффект удара всегда проигрывается (даже по бедроку)
        spawnHeavyStrikeEffects(level, pos, face, state, true);

        // Проверяем, можем ли сломать (hardness >= 0 и не бедрок/барьер)
        boolean canBreak = hardness >= 0 && isCorrectToolForDrops(stack, state);

        if (canBreak) {
            // Эквивалент 6 секунд добычи: урон = speed * 120 / 30
            float maxHardness = (Tiers.IRON.getSpeed() * 120) / 30.0f; // 24.0f

            if (hardness <= maxHardness) {
                level.destroyBlock(pos, true, player);
                stack.hurtAndBreak(2, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
                player.causeFoodExhaustion(0.2f); // Усталость
                return true;
            }
        }

        // Не сломали, но ударили - урон инструменту за "попытку"
        stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));
        return true; // Считаем успехом, т.к. удар состоялся
    }

    private boolean performHeavyEntityStrike(ItemStack stack, Level level, Player player, LivingEntity target) {
        if (level.isClientSide) return false;

        // Урон: база 4 + бонус 8 = 12 (6 сердец)
        float damage = 12.0f;

        if (target.hurt(player.damageSources().playerAttack(player), damage)) {
            // Оглушение и отброс
            target.knockback(1.2f,
                    player.getX() - target.getX(),
                    player.getZ() - target.getZ());

            stack.hurtAndBreak(2, player, (p) -> p.broadcastBreakEvent(player.getUsedItemHand()));

            // Эффекты
            level.playSound(null, target.blockPosition(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.9f);
            ((ServerLevel)level).sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    15, 0.3, 0.4, 0.3, 0.1);

            return true;
        }
        return false;
    }

    private void performSwing(Level level, Player player, InteractionHand hand, boolean strong) {
        // Принудительная анимация взмаха
        player.swing(hand, true);

        if (!level.isClientSide) {
            if (strong) {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8f, 1.0f);
            } else {
                level.playSound(null, player.blockPosition(),
                        SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.6f, 1.2f);
            }
        }
    }

    private void spawnHeavyStrikeEffects(Level level, BlockPos pos, Direction face, BlockState state, boolean strong) {
        if (level.isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 hitVec = pos.getCenter().add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5
        );

        // Частицы блока
        serverLevel.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                hitVec.x, hitVec.y, hitVec.z,
                strong ? 30 : 10, 0.3, 0.3, 0.3, 0.15
        );

        // Искры
        serverLevel.sendParticles(
                ParticleTypes.CRIT,
                hitVec.x, hitVec.y, hitVec.z,
                strong ? 20 : 5, 0.2, 0.2, 0.2, 0.5
        );

        // Звук
        level.playSound(null, pos,
                strong ? SoundEvents.ANVIL_LAND : SoundEvents.STONE_HIT,
                SoundSource.BLOCKS,
                strong ? 0.5f : 0.3f,
                strong ? 0.6f : 1.2f
        );
    }

    // Ручной entity raycast (стандартный pick пропускает сущности)
    @Nullable
    private HitResult pickEntity(Player player, double reach) {
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getViewVector(1.0f).scale(reach);
        Vec3 endPos = eyePos.add(lookVec);

        // Проверяем сущности на пути
        var entities = player.level().getEntities(
                player,
                player.getBoundingBox().expandTowards(lookVec).inflate(1.0),
                e -> e.isAlive() && e.isPickable() && e != player
        );

        HitResult closest = null;
        double closestDist = reach * reach;

        for (var entity : entities) {
            var hit = entity.getBoundingBox().clip(eyePos, endPos);
            if (hit.isPresent()) {
                double dist = eyePos.distanceToSqr(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = new EntityHitResult(entity, hit.get());
                }
            }
        }

        return closest;
    }

    // Блокировка обычной добычи во время зарядки
    @Override
    public boolean onBlockStartBreak(ItemStack itemstack, BlockPos pos, Player player) {
        // Если игрок использует предмет (заряжает), отменяем добычу
        if (player.isUsingItem()) {
            return true; // true = отменить добычу
        }
        return super.onBlockStartBreak(itemstack, pos, player);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND) {
            ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();

            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    ATTACK_DAMAGE_UUID,
                    "Tool modifier",
                    Tiers.IRON.getAttackDamageBonus() + 1.0f,
                    AttributeModifier.Operation.ADDITION
            ));

            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                    ATTACK_SPEED_UUID,
                    "Tool modifier",
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
        tooltip.add(Component.translatable("item.cim.cast_pickaxe_iron.desc.hold").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("item.cim.cast_pickaxe_iron.desc.twohanded")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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