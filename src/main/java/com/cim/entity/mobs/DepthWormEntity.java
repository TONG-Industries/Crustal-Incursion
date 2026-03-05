package com.cim.entity.mobs;


import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.cim.block.entity.hive.DepthWormNestBlockEntity;
import com.cim.goal.DepthWormJumpGoal;
import com.cim.goal.ReturnToHiveGoal;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class DepthWormEntity extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> IS_ATTACKING = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FLYING = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.BOOLEAN);

    public int ignoreFallDamageTicks = 0;
    public BlockPos nestPos;
    private BlockPos homePos;

    public void setHomePos(BlockPos pos) {
        this.homePos = pos;
    }

    public BlockPos getHomePos() {
        return this.homePos;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (homePos != null) {
            tag.putLong("HomePos", homePos.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomePos")) {
            homePos = BlockPos.of(tag.getLong("HomePos"));
        } else {
            homePos = null;
        }
    }

    public DepthWormEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    private static final EntityDataAccessor<Boolean> IS_ANGRY =
            SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.BOOLEAN);



    public boolean isAngry() {
        return this.entityData.get(IS_ANGRY);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_ATTACKING, false);
        this.entityData.define(IS_FLYING, false);
        this.entityData.define(IS_ANGRY, false);
    }

    public void setAttacking(boolean attacking) {
        this.entityData.set(IS_ATTACKING, attacking);
    }
    public boolean isAttacking() {
        return this.entityData.get(IS_ATTACKING);
    }

    public void setFlying(boolean flying) { this.entityData.set(IS_FLYING, flying); }
    public boolean isFlying() { return this.entityData.get(IS_FLYING); }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.onGround()) {
            if (this.isFlying()) {
                this.setFlying(false);
                this.setAttacking(false);
            }
            // Увеличиваем хитбокс сканирования, чтобы "толпа" не мешала попадать
            List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(0.5D),
                    e -> e == this.getTarget() && e.isAlive());

            for (LivingEntity target : targets) {
                // Наносим урон напрямую, игнорируя push
                if (target.hurt(this.damageSources().mobAttack(this), 10.0F)) {
                    this.setFlying(false);
                    this.setAttacking(false);
                    // Отлетаем чуть-чуть назад после успешного удара, чтобы дать место другим червям
                    this.setDeltaMovement(this.getDeltaMovement().multiply(-0.3, 0.2, -0.3));
                    break;
                }
            }
        }


        if (this.ignoreFallDamageTicks > 0) this.ignoreFallDamageTicks--;
        if (!level().isClientSide) {
            this.entityData.set(IS_ANGRY, this.hurtTime > 0);
        }
        // Поиск гнезда на сервере
        if (!level().isClientSide) {
            if (this.nestPos != null) {
                if (!(level().getBlockEntity(this.nestPos) instanceof DepthWormNestBlockEntity nest) || nest.isFull()) {
                    this.nestPos = null;
                }
            }

            if (this.nestPos == null && level().getGameTime() % 100 == 0) {
                Iterable<BlockPos> ps = BlockPos.betweenClosed(blockPosition().offset(-16, -8, -16), blockPosition().offset(16, 8, 16));
                for (BlockPos p : ps) {
                    if (level().getBlockEntity(p) instanceof DepthWormNestBlockEntity nest && !nest.isFull()) {
                        this.nestPos = p.immutable();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void push(net.minecraft.world.entity.Entity entity) {
        super.push(entity);
        // Дополнительная проверка на урон при столкновении
        if (this.isFlying() && entity instanceof LivingEntity target && target == this.getTarget()) {
            target.hurt(this.damageSources().mobAttack(this), 8.0F);
            this.setFlying(false);
            this.setAttacking(false);
        }
    }


    @Override
    public boolean isPushable() {
        // Проверяем, запущена ли сейчас цель возвращения домой
        boolean isReturning = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof ReturnToHiveGoal && wrappedGoal.isRunning());

        return super.isPushable() && !isReturning;
    }



    @Override
    protected void registerGoals() {
        // Приоритет 0: Прыжок (боевой)
        this.goalSelector.addGoal(0, new DepthWormJumpGoal(this, 1.5D, 5.0F, 10.0F));

        // Приоритет 1: Универсальный вход в улей (через почву или само гнездо)
        this.goalSelector.addGoal(1, new ReturnToHiveGoal(this));

        // Приоритет 2: Обычная атака
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));

        // Остальные цели
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // Цели выбора мишени
        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                (target) -> target.isAlive() && target.deathTime <= 0 && !(target instanceof DepthWormEntity)));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this).setAlertOthers());
    }


    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return this.ignoreFallDamageTicks <= 0 && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof DepthWormEntity) && super.canAttack(target);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(software.bernie.geckolib.core.animation.AnimationState<DepthWormEntity> state) {
        // 1. Приоритет смерти
        if (this.isDeadOrDying()) {
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("death"));
        }

        // 2. ПРИОРИТЕТ АТАКИ (Важно: используем setAndContinue, чтобы не перезапускать анимацию каждый тик)
        if (this.isAttacking()) {
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("prepare"));
        }

        // 3. Движение
        if (state.isMoving()) {
            return state.setAndContinue(RawAnimation.begin().thenLoop("slide"));
        }

        // 4. Покой
        return state.setAndContinue(RawAnimation.begin().thenLoop("slide"));
    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }
}