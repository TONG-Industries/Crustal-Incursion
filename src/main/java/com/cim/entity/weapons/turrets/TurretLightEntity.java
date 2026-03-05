package com.cim.entity.weapons.turrets;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import com.cim.entity.weapons.bullets.TurretBulletEntity;
import com.cim.entity.weapons.turrets.logic.TurretLightComputer;
import com.cim.item.tags.AmmoRegistry;
import com.cim.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TurretLightEntity extends Monster implements GeoEntity, RangedAttackMob {

    // GeckoLib
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // Synched data
    private static final EntityDataAccessor<Boolean> SHOOTING = SynchedEntityData.defineId(TurretLightEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DEPLOYED = SynchedEntityData.defineId(TurretLightEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(TurretLightEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DEPLOY_TIMER = SynchedEntityData.defineId(TurretLightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(TurretLightEntity.class, EntityDataSerializers.INT);

    // Balance
    private static final int DEPLOY_DURATION = 80;
    private static final int SHOT_ANIMATION_LENGTH = 14;
    private static final double TARGET_LOCK_DISTANCE = 5.0D;

    // === НОВЫЕ ПОЛЯ ===
    private final TurretLightComputer computer;                 // бортовой компьютер
    private int ammoCount = 20;                                 // начальный боезапас
    private int lifetimeTicks = 3600;                            // 3 минуты = 3*60*20
    private boolean outOfAmmo = false;                           // флаг истощения БК
    private int outOfAmmoTimer = 0;                               // таймер до смерти (3 сек = 60 тиков)

    // Local state
    private int shootAnimTimer = 0;
    private int shotCooldown = 0;
    private int lockSoundCooldown = 0;
    private LivingEntity currentTargetCache = null;
    private int currentTargetPriority = 999;

    public TurretLightEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
        this.computer = new TurretLightComputer(this, TurretLightComputer.Config.STANDARD_20MM); // используем стандартную конфигурацию
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 35.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SHOOTING, false);
        this.entityData.define(DEPLOYED, false);
        this.entityData.define(OWNER_UUID, Optional.empty());
        this.entityData.define(DEPLOY_TIMER, DEPLOY_DURATION);
        this.entityData.define(TARGET_ID, -1);
    }

    @Override
    public int getMaxHeadYRot() { return 360; }

    @Override
    public int getMaxHeadXRot() { return 80; }

    public Vec3 getMuzzlePos() {
        double offsetY = 10.49 / 16.0;
        double offsetZ = 14.86 / 16.0;

        float yRotRad = this.yHeadRot * ((float) Math.PI / 180F);
        float xRotRad = -this.getXRot() * ((float) Math.PI / 180F);

        double yShift = Math.sin(xRotRad) * offsetZ;
        double forwardShift = Math.cos(xRotRad) * offsetZ;

        double x = this.getX() - Math.sin(yRotRad) * forwardShift;
        double y = this.getY() + offsetY + yShift;
        double z = this.getZ() + Math.cos(yRotRad) * forwardShift;

        return new Vec3(x, y, z);
    }

    // -------------------- ВЛАДЕЛЕЦ И СОЮЗНИКИ --------------------

    @Override
    public boolean isAlliedTo(Entity entity) {
        if (super.isAlliedTo(entity)) return true;

        UUID myOwner = this.getOwnerUUID();
        if (myOwner != null) {
            if (entity.getUUID().equals(myOwner)) return true;

            // Другая обычная турель с тем же владельцем
            if (entity instanceof TurretLightEntity otherTurret) {
                UUID theirOwner = otherTurret.getOwnerUUID();
                if (myOwner.equals(theirOwner)) return true;
            }

            // Linked-турель с тем же владельцем
            if (entity instanceof TurretLightLinkedEntity linkedTurret) {
                UUID theirOwner = linkedTurret.getOwnerUUID();
                if (myOwner.equals(theirOwner)) return true;
            }
        }
        return false;
    }

    // -------------------- TICK --------------------

    @Override
    public void tick() {
        super.tick();

        this.yBodyRot = this.getYRot();
        this.yBodyRotO = this.getYRot();

        if (!this.level().isClientSide) {
            // --- Таймер развёртывания ---
            int currentTimer = this.entityData.get(DEPLOY_TIMER);
            if (currentTimer > 0) {
                this.entityData.set(DEPLOY_TIMER, currentTimer - 1);
            }
            if (currentTimer - 1 <= 0 && !this.isDeployed()) {
                this.entityData.set(DEPLOYED, true);
            }

            // --- Кулдауны ---
            if (this.shotCooldown > 0) this.shotCooldown--;
            if (this.lockSoundCooldown > 0) this.lockSoundCooldown--;

            if (this.isShooting()) {
                shootAnimTimer++;
                if (shootAnimTimer >= SHOT_ANIMATION_LENGTH) {
                    this.setShooting(false);
                    shootAnimTimer = 0;
                }
            }

            // --- Логика времени жизни ---
            if (!outOfAmmo) {
                lifetimeTicks--;
                if (lifetimeTicks <= 0) {
                    this.discard();  // время вышло – смерть с дропом
                    return;
                }
            } else {
                // Режим ожидания после истощения БК
                outOfAmmoTimer--;
                if (outOfAmmoTimer <= 0) {
                    this.discard();  // смерть без дропа (флаг outOfAmmo=true)
                    return;
                }
            }

            // --- Получение цели ---
            LivingEntity target = this.getTarget();

            // Обновление трекинга в компьютере
            computer.updateTracking(target);

            // Приоритет текущей цели
            currentTargetPriority = target != null ? computer.calculateTargetPriority(target, getOwnerUUID()) : 999;

            // Звук захвата при смене цели
            if (target != null && target != currentTargetCache && this.isDeployed()) {
                if (this.lockSoundCooldown <= 0) {
                    if (ModSounds.TURRET_LOCK.isPresent()) {
                        this.playSound(ModSounds.TURRET_LOCK.get(), 1.0F, 1.0F);
                    }
                    this.lockSoundCooldown = 40;
                }
                currentTargetCache = target;
            } else if (target == null) {
                currentTargetCache = null;
            }

            // Синхронизация ID цели для клиента (отладка)
            int targetId = target != null ? target.getId() : -1;
            if (this.entityData.get(TARGET_ID) != targetId) {
                this.entityData.set(TARGET_ID, targetId);
            }

            // Наведение
            if (target != null && this.isDeployed() && !outOfAmmo) {
                Vec3 aimPos = computer.getAimTargetPosition(target, computer.config.bulletSpeed, computer.config.bulletGravity);
                if (aimPos != null) {
                    this.getLookControl().setLookAt(aimPos.x, aimPos.y, aimPos.z, 30.0F, 30.0F);
                }
            }

            // --- Переключение целей (каждые 10 тиков) ---
            if (this.tickCount % 10 == 0) {
                // Ближайшая угроза в упор
                LivingEntity closeThreat = computer.findClosestThreat(getOwnerUUID());
                if (closeThreat != null && closeThreat != this.getTarget()) {
                    int newPriority = computer.calculateTargetPriority(closeThreat, getOwnerUUID());
                    if (newPriority < currentTargetPriority) {
                        this.setTarget(closeThreat);
                        currentTargetPriority = newPriority;
                    }
                }

                // Атаковавший владельца (если не держим ближнюю цель)
                boolean canSwitchToFarTarget = true;
                if (target != null && target.isAlive()) {
                    if (this.distanceToSqr(target) < TARGET_LOCK_DISTANCE * TARGET_LOCK_DISTANCE) {
                        canSwitchToFarTarget = false;
                    }
                }
                if (canSwitchToFarTarget) {
                    UUID ownerUUID = this.getOwnerUUID();
                    if (ownerUUID != null) {
                        Player owner = this.level().getPlayerByUUID(ownerUUID);
                        if (owner != null) {
                            LivingEntity ownerAttacker = owner.getLastHurtByMob();
                            if (ownerAttacker != null && ownerAttacker != this.getTarget() && ownerAttacker.isAlive() && !isAlliedTo(ownerAttacker)) {
                                int newPriority = computer.calculateTargetPriority(ownerAttacker, getOwnerUUID());
                                if (newPriority < currentTargetPriority) {
                                    this.setTarget(ownerAttacker);
                                    currentTargetPriority = newPriority;
                                }
                            }
                        }
                    }
                }
            }

        } else {
            // --- КЛИЕНТ: отладочные вычисления (опционально) ---
            int targetId = this.entityData.get(TARGET_ID);
            if (targetId != -1) {
                Entity targetEntity = this.level().getEntity(targetId);
                if (targetEntity instanceof LivingEntity livingTarget && livingTarget.isAlive()) {
                    Vec3 muzzle = getMuzzlePos();
                    computer.calculateBallisticVelocity(livingTarget, muzzle);
                }
            }
        }
    }

    // -------------------- СТРЕЛЬБА --------------------

    @Override
    public void performRangedAttack(LivingEntity target, float pullProgress) {
        if (!this.isDeployed()) return;
        if (this.shotCooldown > 0) return;
        if (outOfAmmo) return;                     // патронов нет – не стреляем

        // Проверка линии огня через компьютер
        Vec3 muzzlePos = getMuzzlePos();
        if (!computer.canShootSafe(target, muzzlePos, getOwnerUUID())) return;

        // Расчёт баллистики
        Vec3 ballisticVelocity = computer.calculateBallisticVelocity(target, muzzlePos);
        if (ballisticVelocity == null) return;

        // Проверка углов (как в оригинале)
        double horizontalDist = Math.sqrt(ballisticVelocity.x * ballisticVelocity.x + ballisticVelocity.z * ballisticVelocity.z);
        float turretCheckYaw = (float) (Math.atan2(ballisticVelocity.z, ballisticVelocity.x) * (180D / Math.PI)) - 90.0F;
        float targetPitch = (float) (Math.atan2(ballisticVelocity.y, horizontalDist) * (180D / Math.PI));
        float currentYaw = this.yHeadRot;
        float currentPitch = -this.getXRot();

        float yawDiff = Math.abs(wrapDegrees(turretCheckYaw - currentYaw));
        float pitchDiff = Math.abs(wrapDegrees(targetPitch - currentPitch));

        if (yawDiff > 10.0F || pitchDiff > 10.0F) return;

        // --- ТРАТА ПАТРОНА ---
        ammoCount--;
        if (ammoCount <= 0) {
            outOfAmmo = true;
            outOfAmmoTimer = 60;      // 3 секунды (20 тиков/сек * 3)
        }

        // Оповещаем компьютер о выстреле (фильтр отдачи)
        computer.onShotFired(target, muzzlePos);

        // Анимация и кулдаун
        this.shotCooldown = SHOT_ANIMATION_LENGTH;
        this.setShooting(true);
        this.shootAnimTimer = 0;

        // Воспроизведение звука
        if (ModSounds.TURRET_FIRE.isPresent()) {
            this.playSound(ModSounds.TURRET_FIRE.get(), 1.0F, 1.0F);
        }

        // Создание пули (сервер)
        if (!this.level().isClientSide) {
            ServerLevel serverLevel = (ServerLevel) this.level();
            TurretBulletEntity bullet = new TurretBulletEntity(serverLevel, this);

            // Определяем тип патрона (случайный из реестра, но можно сохранять тип последнего использованного)
            AmmoRegistry.AmmoType randomAmmo = AmmoRegistry.getRandomAmmoForCaliber("20mm_turret", this.level().random);
            if (randomAmmo != null) {
                bullet.setAmmoType(randomAmmo);
            } else {
                bullet.setAmmoType(new AmmoRegistry.AmmoType("default", "20mm_turret", 6.0f, 3.0f, false));
            }

            bullet.setPos(muzzlePos.x, muzzlePos.y, muzzlePos.z);
            bullet.setDeltaMovement(ballisticVelocity);

            float bulletYaw = (float) (Math.atan2(ballisticVelocity.x, ballisticVelocity.z) * (180D / Math.PI));
            bullet.setYRot(bulletYaw);
            bullet.setXRot(targetPitch);
            bullet.yRotO = bulletYaw;
            bullet.xRotO = targetPitch;

            serverLevel.addFreshEntity(bullet);
        }
    }

    private float wrapDegrees(float degrees) {
        float f = degrees % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    // -------------------- ДРОП ПРИ СМЕРТИ --------------------

    @Override
    public void remove(RemovalReason reason) {
        // Используем геттер level() вместо прямого обращения к полю
        if (!this.level().isClientSide && !outOfAmmo) {
            for (int i = 0; i < 4; i++) {
                // Заменяем level.random на this.level().random (или на this.random)
                AmmoRegistry.AmmoType type = AmmoRegistry.getRandomAmmoForCaliber("20mm_turret", this.level().random);
                if (type != null) {
                    Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(type.id));
                    if (item != null) {
                        this.spawnAtLocation(new ItemStack(item));
                    }
                }
            }
        }
        super.remove(reason);
    }


    // -------------------- GECKOLIB & NBT --------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "deploy_ctrl", 0, event -> {
            if (!this.isDeployed()) return event.setAndContinue(RawAnimation.begin().thenPlay("deploy"));
            return PlayState.STOP;
        }));

        controllers.add(new AnimationController<>(this, "shoot_ctrl", 0, event -> {
            if (this.isShooting()) {
                if (event.getController().getAnimationState() == AnimationController.State.STOPPED) {
                    event.getController().forceAnimationReset();
                }
                return event.setAndContinue(RawAnimation.begin().thenPlay("shot"));
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.getOwnerUUID() != null) tag.putUUID("Owner", this.getOwnerUUID());
        tag.putBoolean("Deployed", this.isDeployed());
        tag.putInt("DeployTimer", this.entityData.get(DEPLOY_TIMER));
        // Новые поля
        tag.putInt("AmmoCount", ammoCount);
        tag.putInt("LifetimeTicks", lifetimeTicks);
        tag.putBoolean("OutOfAmmo", outOfAmmo);
        tag.putInt("OutOfAmmoTimer", outOfAmmoTimer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) this.entityData.set(OWNER_UUID, Optional.of(tag.getUUID("Owner")));
        if (tag.contains("Deployed")) this.entityData.set(DEPLOYED, tag.getBoolean("Deployed"));
        if (tag.contains("DeployTimer")) this.entityData.set(DEPLOY_TIMER, tag.getInt("DeployTimer"));
        // Чтение новых полей
        if (tag.contains("AmmoCount")) ammoCount = tag.getInt("AmmoCount");
        if (tag.contains("LifetimeTicks")) lifetimeTicks = tag.getInt("LifetimeTicks");
        if (tag.contains("OutOfAmmo")) outOfAmmo = tag.getBoolean("OutOfAmmo");
        if (tag.contains("OutOfAmmoTimer")) outOfAmmoTimer = tag.getInt("OutOfAmmoTimer");
    }

    @Override
    protected void registerGoals() {
        // Атака (стрельба)
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.Goal() {
            private final TurretLightEntity turret = TurretLightEntity.this;

            @Override
            public boolean canUse() {
                LivingEntity target = this.turret.getTarget();
                return this.turret.isDeployed() && target != null && target.isAlive() && this.turret.distanceToSqr(target) < 1225.0D;
            }

            @Override
            public void start() { this.turret.getNavigation().stop(); }

            @Override
            public void stop() { this.turret.setShooting(false); }

            @Override
            public boolean requiresUpdateEveryTick() { return true; }

            @Override
            public void tick() {
                LivingEntity target = this.turret.getTarget();
                if (target == null) return;
                this.turret.getSensing().tick();
                this.turret.performRangedAttack(target, 1.0F);
            }
        });

        // Цели (используем оригинальные, они уже учитывают isAlliedTo)
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                entity -> {
                    if (this.isAlliedTo(entity)) return false;
                    UUID ownerUUID = this.getOwnerUUID();
                    if (ownerUUID != null) {
                        Player owner = this.level().getPlayerByUUID(ownerUUID);
                        if (owner != null) {
                            if (owner.getLastHurtByMob() == entity) return true;
                            if (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == owner) return true;
                        }
                    }
                    return false;
                }));

        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                entity -> {
                    if (this.isAlliedTo(entity)) return false;
                    List<Entity> allies = this.level().getEntities(this, this.getBoundingBox().inflate(16.0D));
                    for (Entity e : allies) {
                        if (e != this && e instanceof LivingEntity ally && this.isAlliedTo(ally)) {
                            if (ally.getLastHurtByMob() == entity) return true;
                            if (entity instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == ally) return true;
                        }
                    }
                    return false;
                }));

        this.targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers(TurretLightEntity.class));

        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                entity -> {
                    if (this.isAlliedTo(entity)) return false;
                    UUID ownerUUID = this.getOwnerUUID();
                    if (ownerUUID != null) {
                        Player owner = this.level().getPlayerByUUID(ownerUUID);
                        return owner != null && owner.getLastHurtMob() == entity;
                    }
                    return false;
                }));

        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                entity -> !this.isAlliedTo(entity) && TurretLightEntity.this.isDeployed()));
        this.targetSelector.addGoal(6, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                entity -> !this.isAlliedTo(entity) && TurretLightEntity.this.isDeployed()));
    }

    // -------------------- ДОСТУП К ДЕБАГУ (через компьютер) --------------------

    public Vec3 getDebugTargetPoint() { return computer.debugTargetPoint; }
    public Vec3 getDebugBallisticVelocity() { return computer.debugBallisticVelocity; }
    public List<Pair<Vec3, Boolean>> getDebugScanPoints() { return computer.debugScanPoints; }

    // -------------------- СЕТТЕРЫ ДЛЯ ПОРТАТИВНОГО ПРЕДМЕТА --------------------

    public void setOwner(Player player) {
        this.entityData.set(OWNER_UUID, Optional.of(player.getUUID()));
    }

    public void setOwnerUUIDDirect(UUID uuid) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    public UUID getOwnerUUID() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    public void setLifetime(int ticks) {
        this.lifetimeTicks = ticks;
    }

    public void setAmmo(int count) {
        this.ammoCount = count;
    }

    // -------------------- ОСТАЛЬНЫЕ ГЕТТЕРЫ/СЕТТЕРЫ --------------------

    public void setShooting(boolean shooting) {
        this.entityData.set(SHOOTING, shooting);
    }

    public boolean isShooting() {
        return this.entityData.get(SHOOTING);
    }

    public boolean isDeployed() {
        return this.entityData.get(DEPLOYED);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public double getBoneResetTime() { return 0; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockIn) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return this.cache; }
}