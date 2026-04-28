package com.cim.entity.mobs.depth_worm;

import com.cim.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
    private static final EntityDataAccessor<Integer> KILLS = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RAW_KILLS = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<String> BOUND_NEST_ID = SynchedEntityData.defineId(DepthWormEntity.class, EntityDataSerializers.STRING);
    private int meleeCooldown = 0;
    public int ignoreFallDamageTicks = 0;
    public BlockPos nestPos;
    private BlockPos homePos;
    private Runnable onDeathCallback = null;

    public void setOnDeathCallback(Runnable callback) {
        this.onDeathCallback = callback;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (onDeathCallback != null) {
            onDeathCallback.run();
        }
    }

    public void setHomePos(BlockPos pos) {
        this.homePos = pos;
    }

    public BlockPos getHomePos() {
        return this.homePos;
    }

    public void bindToNest(BlockPos nestPos) {
        if (nestPos != null) {
            this.entityData.set(BOUND_NEST_ID, nestPos.asLong() + "");
            this.nestPos = nestPos;
        }
    }

    public BlockPos getBoundNestPos() {
        String id = this.entityData.get(BOUND_NEST_ID);
        if (id == null || id.isEmpty()) return null;
        try {
            return BlockPos.of(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void clearNestBinding() {
        this.entityData.set(BOUND_NEST_ID, "");
        this.nestPos = null;
    }

    public boolean isBoundToNest(BlockPos pos) {
        BlockPos bound = getBoundNestPos();
        return bound != null && bound.equals(pos);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSq) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kills", this.getKills());
        tag.putInt("RawKills", this.getRawKills());
        if (homePos != null) {
            tag.putLong("HomePos", homePos.asLong());
        }
        BlockPos boundNest = getBoundNestPos();
        if (boundNest != null) {
            tag.putLong("BoundNest", boundNest.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(KILLS, tag.getInt("Kills"));
        this.entityData.set(RAW_KILLS, tag.getInt("RawKills"));
        if (tag.contains("HomePos")) {
            homePos = BlockPos.of(tag.getLong("HomePos"));
        } else {
            homePos = null;
        }
        if (tag.contains("BoundNest")) {
            BlockPos bound = BlockPos.of(tag.getLong("BoundNest"));
            bindToNest(bound);
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
                .add(Attributes.ATTACK_DAMAGE, 2.5D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_ATTACKING, false);
        this.entityData.define(IS_FLYING, false);
        this.entityData.define(IS_ANGRY, false);
        this.entityData.define(KILLS, 0);
        this.entityData.define(RAW_KILLS, 0);
        this.entityData.define(BOUND_NEST_ID, "");
    }

    public void setAttacking(boolean attacking) {
        this.entityData.set(IS_ATTACKING, attacking);
    }

    public boolean isAttacking() {
        return this.entityData.get(IS_ATTACKING);
    }

    public void setFlying(boolean flying) {
        this.entityData.set(IS_FLYING, flying);
    }

    public boolean isFlying() {
        return this.entityData.get(IS_FLYING);
    }

    public void addKill() {
        this.entityData.set(KILLS, this.getKills() + 1);
    }

    // === RAW KILLS (чистые убийства, не сбрасываются в улье) ===
    public int getRawKills() {
        return this.entityData.get(RAW_KILLS);
    }

    public void setRawKills(int kills) {
        this.entityData.set(RAW_KILLS, kills);
    }

    public void addRawKill() {
        this.entityData.set(RAW_KILLS, getRawKills() + 1);
    }

    protected void checkBrutalTransformation() {
        if (getRawKills() >= 5) {
            transformToBrutal();
        }
    }

    protected void transformToBrutal() {
        if (this.level().isClientSide) return;

        Level level = this.level();
        Vec3 pos = this.position();

        // ⭐ Эффект трансформации (отсылка к SCP Parasites)
        if (level instanceof ServerLevel serverLevel) {
            // Мелкий взрыв частиц — дым, кровь, осколки хитина
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    pos.x, pos.y + 0.3, pos.z,
                    2,  // count — мало, чтобы был мелкий взрыв
                    0.15, 0.15, 0.15,  // spread
                    0.02  // speed
            );
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    pos.x, pos.y + 0.2, pos.z,
                    8,
                    0.2, 0.1, 0.2,
                    0.01
            );
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                    pos.x, pos.y + 0.4, pos.z,
                    4,
                    0.1, 0.1, 0.1,
                    0.05
            );
            // Тёмные частицы — "паразитарная" субстанция
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.ASH,
                    pos.x, pos.y + 0.5, pos.z,
                    12,
                    0.25, 0.2, 0.25,
                    0.03
            );

            // Локальный толчок (не взрыв, просто отталкивание)
            float knockbackRadius = 2.5F;
            float knockbackStrength = 0.4F;
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(knockbackRadius))) {
                if (entity == this) continue;
                Vec3 diff = entity.position().subtract(pos);
                double dist = diff.length();
                if (dist < 0.01) continue;
                Vec3 knock = diff.normalize().scale(knockbackStrength * (1.0 - dist / knockbackRadius));
                entity.setDeltaMovement(entity.getDeltaMovement().add(knock.x, 0.15, knock.z));
                entity.hurtMarked = true;
            }
        }

        // Спавн брутального червя
        DepthWormBrutalEntity brutal = new DepthWormBrutalEntity(ModEntities.DEPTH_WORM_BRUTAL.get(), level);
        brutal.copyPosition(this);
        brutal.setYRot(this.getYRot());
        brutal.yHeadRot = this.yHeadRot;
        brutal.yBodyRot = this.yBodyRot;
        brutal.setHealth(this.getHealth());
        brutal.setTarget(this.getTarget());

        BlockPos boundNest = this.getBoundNestPos();
        if (boundNest != null) brutal.bindToNest(boundNest);
        if (this.homePos != null) brutal.setHomePos(this.homePos);

        brutal.ignoreFallDamageTicks = this.ignoreFallDamageTicks;

        if (this.onDeathCallback != null) {
            brutal.setOnDeathCallback(this.onDeathCallback);
        }

        level.addFreshEntity(brutal);
        this.discard();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (this.meleeCooldown > 0) return false;
        this.meleeCooldown = 20;
        return super.doHurtTarget(target);
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (this.onGround()) {
            if (this.isFlying()) {
                this.setFlying(false);
                this.setAttacking(false);
            }
            List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(0.5D),
                    e -> e == this.getTarget() && e.isAlive());

            for (LivingEntity target : targets) {
                if (target.hurt(this.damageSources().mobAttack(this), 10.0F)) {
                    this.setFlying(false);
                    this.setAttacking(false);
                    this.setDeltaMovement(this.getDeltaMovement().multiply(-0.3, 0.2, -0.3));
                    break;
                }
            }
        }

        if (this.ignoreFallDamageTicks > 0) this.ignoreFallDamageTicks--;
        if (!level().isClientSide) {
            this.entityData.set(IS_ANGRY, this.hurtTime > 0);
        }

        if (!level().isClientSide && nestPos == null) {
            nestPos = getBoundNestPos();
        }
        if (this.meleeCooldown > 0) this.meleeCooldown--;

        // ⭐ Эволюция в брутального
        if (!level().isClientSide) {
            checkBrutalTransformation();
        }
    }

    @Override
    public void push(net.minecraft.world.entity.Entity entity) {
        super.push(entity);
        if (this.isFlying() && entity instanceof LivingEntity target && target == this.getTarget()) {
            target.hurt(this.damageSources().mobAttack(this), 8.0F);
            this.setFlying(false);
            this.setAttacking(false);
        }
    }

    @Override
    public boolean isPushable() {
        boolean isReturning = this.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrappedGoal -> wrappedGoal.getGoal() instanceof ReturnToHiveGoal && wrappedGoal.isRunning());
        return super.isPushable() && !isReturning;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new DepthWormJumpGoal(this, 1.5D, 5.0F, 10.0F));
        this.goalSelector.addGoal(1, new ReturnToHiveGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

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

    public void addKillPoints(Entity victim) {
        int points = 1;
        if (victim instanceof Player) {
            points = 30;
        } else if (victim instanceof net.minecraft.world.entity.monster.Enemy) {
            if (victim instanceof LivingEntity le && le.getMaxHealth() >= 50.0F) {
                points = 10;
            } else {
                points = 3;
            }
        }
        this.entityData.set(KILLS, this.getKills() + points);
    }

    public int getKills() {
        return this.entityData.get(KILLS);
    }

    @Override
    public void awardKillScore(Entity killed, int score, DamageSource damageSource) {
        super.awardKillScore(killed, score, damageSource);
        this.addKillPoints(killed);
        this.addRawKill(); // ⭐ +1 чистое убийство
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 2, this::predicate));
    }

    private PlayState predicate(software.bernie.geckolib.core.animation.AnimationState<DepthWormEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("death"));
        }
        if (this.isAttacking()) {
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("prepare"));
        }
        if (state.isMoving()) {
            return state.setAndContinue(RawAnimation.begin().thenLoop("slide"));
        }
        return state.setAndContinue(RawAnimation.begin().thenLoop("slide"));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}