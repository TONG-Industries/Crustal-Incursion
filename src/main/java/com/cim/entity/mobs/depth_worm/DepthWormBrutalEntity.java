package com.cim.entity.mobs.depth_worm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class DepthWormBrutalEntity extends DepthWormEntity {

    private static final EntityDataAccessor<Boolean> IS_PREPARING_JUMP =
            SynchedEntityData.defineId(DepthWormBrutalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_IMPALING =
            SynchedEntityData.defineId(DepthWormBrutalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> IMPALED_ENTITY_ID =
            SynchedEntityData.defineId(DepthWormBrutalEntity.class, EntityDataSerializers.INT);

    private LivingEntity impaledTargetCache = null;
    private int attackAnimTimer = 0;

    // ⭐ CHANGED: кулдаун рукопашной (1 сек = 20 тиков)
    private int meleeCooldown = 0;

    public DepthWormBrutalEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 45.0D)
                // ⭐ CHANGED: скорость ходьбы ×2
                .add(Attributes.MOVEMENT_SPEED, 0.44D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                // ⭐ CHANGED: больше агро-радиус — нет «слепоты» в ближнем бою
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_PREPARING_JUMP, false);
        this.entityData.define(IS_IMPALING, false);
        this.entityData.define(IMPALED_ENTITY_ID, -1);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new DepthWormBrutalJumpGoal(this, 1.8D, 6.0F, 24.0F));
        this.goalSelector.addGoal(1, new ReturnToHiveGoal(this));
        // ⭐ CHANGED: followingTargetEvenIfNotSeen = true — не теряет цель за углом
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4D, true));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, true,
                (target) -> target.isAlive()
                        && target.deathTime <= 0
                        && !(target instanceof DepthWormEntity)
                        && !(target instanceof DepthWormBrutalEntity)));
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this).setAlertOthers());
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // ⭐ CHANGED: кулдаун 1 секунда + гарантированная анимация
        if (this.meleeCooldown > 0) return false;

        this.meleeCooldown = 20;
        this.setAttacking(true);
        this.attackAnimTimer = 10; // 0.5 сек анимации удара
        return super.doHurtTarget(target);
    }

    @Override
    public void aiStep() {
        boolean wasFlying = this.isFlying();
        super.aiStep();

        if (this.onGround() && this.isFlying() && !this.isImpaling()) {
            this.setFlying(false);
            this.setPreparingJump(false);
            this.handleLanding();
        }
        
        if (this.level().isClientSide) return;

        // ⭐ CHANGED: таймеры
        if (this.meleeCooldown > 0) this.meleeCooldown--;
        if (this.attackAnimTimer > 0) {
            if (--this.attackAnimTimer == 0) {
                this.setAttacking(false);
            }
        }

        if (this.onGround() && wasFlying && !this.isFlying()) {
            this.handleLanding();
        }

        if (this.isImpaling()) {
            this.updateImpaledTargetPosition();
        }
    }

    private void handleLanding() {
        LivingEntity target = getImpaledTarget();
        if (target != null && target.isAlive()) {
            float fall = this.fallDistance;
            if (fall > 3.0F) {
                target.hurt(this.damageSources().mobAttack(this), fall - 3.0F);
            }
            clearImpaledTarget();
        }
        this.setPreparingJump(false);
    }

    private void updateImpaledTargetPosition() {
        LivingEntity target = getImpaledTarget();
        if (target == null || !target.isAlive()) {
            clearImpaledTarget();
            return;
        }

        Vec3 pos = this.position();
        Vec3 look = this.getLookAngle();
        Vec3 tongue = pos.add(look.x * 1.3, this.getBbHeight() * 0.4, look.z * 1.3);

        target.setPos(tongue.x, tongue.y, tongue.z);
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        target.fallDistance = 0F;

        if (target instanceof Player player) {
            player.hurtMarked = true;
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return this.ignoreFallDamageTicks <= 0 && super.causeFallDamage(distance, multiplier * 0.5F, source);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 2, state -> {
            if (this.isDeadOrDying()) {
                return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("death"));
            }

            if (this.isImpaling() || (this.isFlying() && !this.onGround())) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("jump"));
            }

            if (this.isPreparingJump()) {
                return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("prepare"));
            }

            // ⭐ CHANGED: attack теперь гарантированно проигрывается при setAttacking
            if (this.isAttacking()) {
                return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("attack"));
            }

            if (state.isMoving()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("slide"));
            }

            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));
    }

    public boolean isPreparingJump() {
        return this.entityData.get(IS_PREPARING_JUMP);
    }

    public void setPreparingJump(boolean v) {
        this.entityData.set(IS_PREPARING_JUMP, v);
    }

    public boolean isImpaling() {
        return this.entityData.get(IS_IMPALING);
    }

    public void setImpaling(boolean v) {
        this.entityData.set(IS_IMPALING, v);
    }

    public int getImpaledEntityId() {
        return this.entityData.get(IMPALED_ENTITY_ID);
    }

    public void setImpaledEntityId(int id) {
        this.entityData.set(IMPALED_ENTITY_ID, id);
    }

    public LivingEntity getImpaledTarget() {
        if (this.impaledTargetCache != null && this.impaledTargetCache.isAlive()) {
            return this.impaledTargetCache;
        }
        int id = getImpaledEntityId();
        if (id != -1) {
            var e = this.level().getEntity(id);
            if (e instanceof LivingEntity le) {
                this.impaledTargetCache = le;
                return le;
            }
        }
        return null;
    }

    public void setImpaledTarget(LivingEntity target) {
        this.impaledTargetCache = target;
        setImpaledEntityId(target != null ? target.getId() : -1);
        setImpaling(target != null);
    }

    public void clearImpaledTarget() {
        this.impaledTargetCache = null;
        setImpaledEntityId(-1);
        setImpaling(false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PreparingJump", isPreparingJump());
        tag.putBoolean("Impaling", isImpaling());
        tag.putInt("ImpaledId", getImpaledEntityId());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPreparingJump(tag.getBoolean("PreparingJump"));
        setImpaling(tag.getBoolean("Impaling"));
        setImpaledEntityId(tag.getInt("ImpaledId"));
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (!this.level().isClientSide && this.isImpaling()) {
            clearImpaledTarget();
        }
    }
}