// DepthWormBrutalEntity.java
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
    private int postAttackAnimTimer = 0;
    private int meleeCooldown = 0;

    public DepthWormBrutalEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 45.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
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
        // ⭐ ИСПРАВЛЕНО: followingTargetEvenIfNotSeen = false — как у обычного червя,
        // чтобы брутал мог переключаться между прыжком и ближним боем
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4D, false));
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
        if (this.meleeCooldown > 0) return false;
        this.meleeCooldown = 20; // ⭐ 1 секунда, было 40
        this.triggerPostAttackAnim();
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

        // ⭐ Кулдаун рукопашной
        if (this.meleeCooldown > 0) this.meleeCooldown--;

        // ⭐ Таймер пост-атаки (единственный таймер для анимации)
        if (this.postAttackAnimTimer > 0) {
            if (--this.postAttackAnimTimer == 0) {
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

        // ⭐ ИСПРАВЛЕНО: Привязываемся к ЦЕНТРУ хитбокса цели, не к ногам
        // target.position() — ноги, getBoundingBox().getCenter() — центр
        Vec3 targetCenter = target.getBoundingBox().getCenter();

        // ⭐ ИСПРАВЛЕНО: Сдвигаем на ~5 пикселей (0.3125 блока) назад (в сторону хвоста червя)
        // Червь "впивается" в центр тела цели, но смещён назад от направления движения цели
        Vec3 targetVel = target.getDeltaMovement();
        Vec3 velDir = targetVel.lengthSqr() > 0.001
                ? targetVel.normalize()
                : target.getLookAngle();

        // offsetForward теперь ОТРИЦАТЕЛЬНЫЙ — червь сзади центра (в сторону хвоста)
        double offsetForward = -0.3125; // ~5 пикселей назад
        Vec3 attachPos = targetCenter.add(velDir.scale(offsetForward));

        // Плавное прилипание
        Vec3 wormPos = this.position();
        double lerp = 0.5; // Мягче, чтобы не дергаться
        double newX = wormPos.x + (attachPos.x - wormPos.x) * lerp;
        double newY = wormPos.y + (attachPos.y - wormPos.y) * lerp;
        double newZ = wormPos.z + (attachPos.z - wormPos.z) * lerp;

        this.setPos(newX, newY, newZ);
        this.setDeltaMovement(targetVel);
        Vec3 lookDir = targetCenter.subtract(wormPos);
        float yaw = (float) (Math.atan2(lookDir.z, lookDir.x) * (180.0 / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;

        // Урон каждые 10 тиков
        if (this.tickCount % 10 == 0) {
            target.hurt(this.damageSources().mobAttack(this), 2.0F);
        }

        if (target instanceof Player player) {
            player.hurtMarked = true;
        }
    }
    @Override
    protected void checkBrutalTransformation() {
        // Брутальный червь не эволюционирует дальше
    }

    @Override
    protected void transformToBrutal() {
        // Уже брутальный — игнорируем
    }
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return this.ignoreFallDamageTicks <= 0 && super.causeFallDamage(distance, multiplier * 0.5F, source);
    }

    public void triggerPostAttackAnim() {
        this.postAttackAnimTimer = 20;
        this.setAttacking(true);
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

            // ⭐ ИЗМЕНЕНО: attack держится и после атаки (postAttackAnimTimer)
            if (this.isAttacking() || this.postAttackAnimTimer > 0) {
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