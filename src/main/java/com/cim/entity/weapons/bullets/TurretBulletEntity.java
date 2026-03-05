package com.cim.entity.weapons.bullets;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;
import com.cim.block.entity.weapons.TurretLightPlacerBlockEntity;
import com.cim.entity.ModEntities;
import com.cim.entity.weapons.turrets.TurretLightLinkedEntity;
import com.cim.item.tags.AmmoRegistry;
import com.cim.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Arrays;
import java.util.List;

public class TurretBulletEntity extends AbstractArrow implements GeoEntity, IEntityAdditionalSpawnData {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<String> AMMO_ID = SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> AMMO_TYPE = SynchedEntityData.defineId(TurretBulletEntity.class, EntityDataSerializers.STRING);

    public static final float BULLET_GRAVITY = 0.01F;
    public static final float AIR_RESISTANCE = 0.99F;
    public static final float MAX_FLIGHT_DISTANCE = 256.0F;

    private float baseDamage = 4.0f;
    private float baseSpeed = 3.0f;
    private AmmoType ammoType = AmmoType.NORMAL;
    private float initialSpeed = 0.0f;
    private Vec3 initialPosition = null;
    private int flightDuration = 0;
    public float spin = 0;

    // === ДЛЯ РАДИО: ЦЕЛЬ И ТАЙМЕР ===
    private LivingEntity lastHitTarget = null;
    private int hitTickTimer = 0;
    private static final double CENTER_DETONATE_RADIUS_SQR = 0.09D; // 0.3 блока
    // =================================

    public enum AmmoType {
        NORMAL("normal"), PIERCING("piercing"), HOLLOW("hollow"), INCENDIARY("incendiary"), RADIO("radio");
        public final String id;
        AmmoType(String id) { this.id = id; }
        public static AmmoType fromString(String str) {
            for (AmmoType type : AmmoType.values()) if (type.id.equals(str)) return type;
            return NORMAL;
        }
    }

    public TurretBulletEntity(EntityType<? extends AbstractArrow> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public TurretBulletEntity(Level level, LivingEntity shooter) {
        super((EntityType<? extends AbstractArrow>) ModEntities.TURRET_BULLET.get(), shooter, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (getAmmoType() == AmmoType.RADIO && this.flightDuration >= 5) {
            // ✅ ИСПРАВЛЕНО: fixed() = абсолютные размеры (1.5 x 1.5 блока)
            return EntityDimensions.fixed(1.5F, 1.5F);
        }
        return super.getDimensions(pose);
    }


    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        Vec3 motion = this.getDeltaMovement();
        buffer.writeDouble(motion.x);
        buffer.writeDouble(motion.y);
        buffer.writeDouble(motion.z);
        buffer.writeDouble(this.getX());
        buffer.writeDouble(this.getY());
        buffer.writeDouble(this.getZ());
        buffer.writeFloat(this.getYRot());
        buffer.writeFloat(this.getXRot());
        buffer.writeInt(this.flightDuration);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf additionalData) {
        double vx = additionalData.readDouble();
        double vy = additionalData.readDouble();
        double vz = additionalData.readDouble();
        double x = additionalData.readDouble();
        double y = additionalData.readDouble();
        double z = additionalData.readDouble();
        float rotY = additionalData.readFloat();
        float rotX = additionalData.readFloat();
        this.flightDuration = additionalData.readInt();

        this.setPos(x, y, z);
        this.setDeltaMovement(vx, vy, vz);
        this.setYRot(rotY);
        this.setXRot(rotX);
        this.yRotO = rotY;
        this.xRotO = rotX;
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.alignToVelocity();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(AMMO_ID, "default");
        this.entityData.define(AMMO_TYPE, "normal");
    }

    public void setAmmoType(AmmoRegistry.AmmoType ammoType) {
        if (ammoType == null) return;
        this.baseDamage = ammoType.damage;
        this.baseSpeed = ammoType.speed;
        this.entityData.set(AMMO_ID, ammoType.id);

        if (ammoType.id.contains("piercing")) {
            this.ammoType = AmmoType.PIERCING;
            this.entityData.set(AMMO_TYPE, "piercing");
            this.setPierceLevel((byte) 0);
        } else if (ammoType.id.contains("hollow")) {
            this.ammoType = AmmoType.HOLLOW;
            this.entityData.set(AMMO_TYPE, "hollow");
            this.setPierceLevel((byte) 0);
        } else if (ammoType.id.contains("fire") || ammoType.id.contains("incendiary")) {
            this.ammoType = AmmoType.INCENDIARY;
            this.entityData.set(AMMO_TYPE, "incendiary");
            this.setPierceLevel((byte) 0);
        } else if (ammoType.id.contains("radio")) {
            this.ammoType = AmmoType.RADIO;
            this.entityData.set(AMMO_TYPE, "radio");
            this.setPierceLevel((byte) 1);
        } else {
            this.ammoType = AmmoType.NORMAL;
            this.entityData.set(AMMO_TYPE, "normal");
            this.setPierceLevel((byte) 0);
        }

        this.setBaseDamage(baseDamage);
    }

    public String getAmmoId() { return this.entityData.get(AMMO_ID); }
    public AmmoType getAmmoType() { return AmmoType.fromString(this.entityData.get(AMMO_TYPE)); }

    public void setBallisticTrajectory(Vec3 startPos, Vec3 velocity) {
        this.setPos(startPos.x, startPos.y, startPos.z);
        this.setDeltaMovement(velocity);
        this.initialSpeed = (float) velocity.length();
        this.initialPosition = startPos;
        this.alignToVelocity();
    }

    public void shootBallisticFromRotation(LivingEntity shooter, float pitch, float yaw, float rollOffset, float speed, float divergence) {
        Vec3 lookDir = getLookDirFromRotation(pitch, yaw);
        if (divergence > 0) lookDir = addDispersion(lookDir, divergence);
        Vec3 velocity = lookDir.scale(speed);
        double startX = shooter.getX();
        double startY = shooter.getEyeY() - 0.1;
        double startZ = shooter.getZ();
        Vec3 offset = lookDir.normalize().scale(0.5);
        Vec3 startPos = new Vec3(startX, startY, startZ).add(offset);
        setBallisticTrajectory(startPos, velocity);
    }

    private static Vec3 getLookDirFromRotation(float pitch, float yaw) {
        float pitchRad = pitch * ((float) Math.PI / 180.0F);
        float yawRad = yaw * ((float) Math.PI / 180.0F);
        return new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
    }

    private Vec3 addDispersion(Vec3 baseDir, float divergence) {
        Vec3 normalized = baseDir.normalize();
        double dx = normalized.x + (this.random.nextGaussian() * divergence * 0.1);
        double dy = normalized.y + (this.random.nextGaussian() * divergence * 0.1);
        double dz = normalized.z + (this.random.nextGaussian() * divergence * 0.1);
        return new Vec3(dx, dy, dz).normalize().scale(baseDir.length());
    }

    @Override
    public void tick() {
        if (this.isRemoved() || this.inGround) {
            this.discard();
            return;
        }

        this.spin += 20.0F;
        this.flightDuration++;

        // 1. Расширение хитбокса с сохранением центра (Centering fix)
        if (this.flightDuration == 5 && getAmmoType() == AmmoType.RADIO) {
            float oldHeight = this.getBbHeight(); // 1. Запоминаем старую высоту (0.5)

            this.refreshDimensions();             // 2. Применяем новые размеры (1.5)

            float newHeight = this.getBbHeight(); // 3. Получаем новую высоту

            // reapplyPosition вызывать не нужно, setPos сам пересчитает AABB
        }

        if (initialPosition != null && this.position().distanceTo(initialPosition) > MAX_FLIGHT_DISTANCE) {
            this.discard();
            return;
        }

        if (this.tickCount > 200) {
            this.discard();
            return;
        }

        // ====================================================================
        // 💥 ЛОГИКА ДЕТОНАЦИИ (RADIO)
        // ====================================================================
        if (getAmmoType() == AmmoType.RADIO && lastHitTarget != null) {
            this.hitTickTimer++;

            // Взрываемся безусловно через 1 тик после касания
            // или если цель умерла
            if (this.hitTickTimer >= 1 || !lastHitTarget.isAlive()) {
                applyRadioExplosion(this.position());
                this.discard();
                return;
            }
        }
        // ====================================================================

        // Сохраняем позицию ДО движения
        Vec3 startPos = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 endPos = startPos.add(motion);

        // Обработка коллизий
        HitResult hit = traceHit(startPos, endPos);
        if (hit.getType() != HitResult.Type.MISS) {
            handleHitResult(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        // Движение (ТОЛЬКО ОДИН РАЗ!)
        this.setPos(endPos.x, endPos.y, endPos.z);
        motion = motion.scale(AIR_RESISTANCE).add(0.0, -BULLET_GRAVITY, 0.0);
        this.setDeltaMovement(motion);
        this.alignToVelocity();
    }


    private void applyRadioExplosion(Vec3 center) {
        playHitSound();

        if (!this.level().isClientSide) {
            AABB box = new AABB(
                    center.x - 1.8D, center.y - 1.8D, center.z - 1.8D,
                    center.x + 1.8D, center.y + 1.8D, center.z + 1.8D
            );

            List<Entity> entities = this.level().getEntities(this, box, e ->
                    e instanceof LivingEntity living && living.isAlive() && living != this.getOwner()
            );

            for (Entity e : entities) {
                LivingEntity living = (LivingEntity) e;
                double distSqr = living.distanceToSqr(center);

                if (distSqr > (1.8D * 1.8D)) continue;

                double dist = Math.sqrt(distSqr);
                float falloff = (float)(1.0 - (dist / 1.8D) * 0.7F);
                float hollowDamage = calculateHollowDamage(living.getArmorValue());

                float finalDamage = Math.max(hollowDamage * falloff * 0.6f, hollowDamage * 0.3f);

                Entity owner = this.getOwner();
                DamageSource source = owner instanceof LivingEntity livingOwner ?
                        this.damageSources().mobProjectile(this, livingOwner) :
                        this.damageSources().arrow(this, owner);

                // 🔥 [MINIGUN MODE] СБРАСЫВАЕМ НЕУЯЗВИМОСТЬ
                living.invulnerableTime = 0;

                living.hurt(source, finalDamage);
                checkAndCountKill(living);

            }
        }
    }


    public void alignToVelocity() {
        Vec3 velocity = this.getDeltaMovement();
        double horizontalDist = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        this.setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180D / Math.PI)));
        this.setXRot((float) (Math.atan2(velocity.y, horizontalDist) * (180D / Math.PI)));
        if (this.tickCount == 0) {
            this.yRotO = this.getYRot();
            this.xRotO = this.getXRot();
        }
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
        super.lerpMotion(x, y, z);
        this.alignToVelocity();
    }

    private void handleHitResult(HitResult hit) {
        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            handleEntityHit(entityHit.getEntity());
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            this.onHitBlock((BlockHitResult) hit);
        }
    }

    private void handleEntityHit(Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) return;

        AmmoType currentType = getAmmoType();

        if (currentType == AmmoType.RADIO) {
            // Если это первая встреча с целью
            if (lastHitTarget == null) {
                this.lastHitTarget = livingTarget;
                this.hitTickTimer = 0;

                // Наносим 40% урона сразу (удар болванкой)
                float contactDamage = calculateHollowDamage(livingTarget.getArmorValue());
                Entity owner = this.getOwner();
                DamageSource source = owner instanceof LivingEntity livingOwner ?
                        this.damageSources().mobProjectile(this, livingOwner) :
                        this.damageSources().arrow(this, owner);

                // 🔥 [MINIGUN MODE] СБРАСЫВАЕМ НЕУЯЗВИМОСТЬ И ТУТ ТОЖЕ
                livingTarget.invulnerableTime = 0;

                livingTarget.hurt(source, contactDamage * 0.4f);
                checkAndCountKill(livingTarget);
            }
            // 🚨 ВАЖНО: Всегда выходим, если это RADIO.
            return;
        }

        // --- Дальше старый код для обычных пуль ---
        float finalDamage = calculateDamage(livingTarget, currentType);
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().mobProjectile(this, (LivingEntity) owner);

        // 🔥 [MINIGUN MODE] И ДЛЯ ОБЫЧНЫХ ПУЛЬ ТОЖЕ ПОЛЕЗНО
        livingTarget.invulnerableTime = 0;

        if (target.hurt(source, finalDamage)) {
            applySpecialEffect(livingTarget, currentType);
            checkAndCountKill(livingTarget);
        }

        playHitSound();
        this.discard();
    }


    private HitResult traceHit(Vec3 start, Vec3 end) {
        HitResult blockHit = this.level().clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));

        Vec3 endForEntities = end;
        if (blockHit.getType() != HitResult.Type.MISS) {
            endForEntities = blockHit.getLocation();
        }

        // ✅ УВЕЛИЧЕННЫЙ RAYCAST ДЛЯ РАДИО
        float raycastSize = getAmmoType() == AmmoType.RADIO && flightDuration >= 5 ? 1.0F : 0.5F;
        AABB sweep = this.getBoundingBox().expandTowards(end.subtract(start)).inflate(raycastSize);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                this.level(), this,
                start, endForEntities,
                sweep,
                e -> e.isAlive() && e != this.getOwner() && e.isPickable()
        );

        return entityHit != null ? entityHit : blockHit;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!this.level().isClientSide) {
            BlockState state = this.level().getBlockState(result.getBlockPos());
            if (state.getBlock() instanceof AbstractGlassBlock) {
                this.level().destroyBlock(result.getBlockPos(), true);
            }
            playGroundSound();
            this.discard();
        }
    }



    private float calculateDamage(LivingEntity target, AmmoType type) {
        float armor = (float) target.getArmorValue();
        switch (type) {
            case PIERCING: return calculatePiercingDamage(armor);
            case HOLLOW: return calculateHollowDamage(armor);
            case RADIO: return calculateHollowDamage(armor);
            case INCENDIARY: return calculateIncendiaryDamage(armor);
            default: return Math.max(baseDamage * (1.0f - armor * 0.02f), baseDamage * 0.4f);
        }
    }

    private float calculatePiercingDamage(float armor) {
        float armorPenetration = Math.min(35f, (baseDamage * 1.5f) + (baseSpeed * 3f));
        float armorEffectiveness = 1f - (armor / (armor + 40f));
        float damage = baseDamage * (1f + armorPenetration / 100f);
        return Math.max(damage * armorEffectiveness, baseDamage * 0.6f);
    }

    private float calculateHollowDamage(float armor) {
        float maxArmorEstimate = 20f;
        float armorMultiplier = Math.max(0.75f, 2f - (armor / maxArmorEstimate) * 1.0f);
        return baseDamage * armorMultiplier;
    }

    private float calculateIncendiaryDamage(float armor) {
        return Math.max(baseDamage * (1.0f - armor * 0.015f), baseDamage * 0.5f);
    }

    private void applySpecialEffect(LivingEntity target, AmmoType type) {
        if (type == AmmoType.INCENDIARY) target.setSecondsOnFire(5);
    }

    private void playHitSound() {
        RegistryObject<SoundEvent>[] candidates = new RegistryObject[] {
                ModSounds.BULLET_IMPACT
        };

        List<SoundEvent> available = Arrays.stream(candidates)
                .filter(RegistryObject::isPresent)
                .map(RegistryObject::get)
                .toList();

        if (!available.isEmpty()) {
            SoundEvent sound = available.get(this.random.nextInt(available.size()));
            this.playSound(sound, 0.5F, 0.9F + this.random.nextFloat() * 0.2F);
        } else {
            this.playSound(SoundEvents.GENERIC_HURT, 0.5F, 1.0F);
        }
    }

    private void playGroundSound() {
        RegistryObject<SoundEvent>[] candidates = new RegistryObject[] {
                ModSounds.BULLET_GROUND
        };

        List<SoundEvent> available = Arrays.stream(candidates)
                .filter(RegistryObject::isPresent)
                .map(RegistryObject::get)
                .toList();

        if (!available.isEmpty()) {
            SoundEvent sound = available.get(this.random.nextInt(available.size()));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    sound, net.minecraft.sounds.SoundSource.PLAYERS,
                    0.5F, 0.9F + this.random.nextFloat() * 0.2F);
        } else {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.STONE_HIT, net.minecraft.sounds.SoundSource.PLAYERS,
                    0.5F, 1.0F);
        }
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return ModSounds.BULLET_GROUND.isPresent() ? ModSounds.BULLET_GROUND.get() : SoundEvents.ARROW_HIT;
    }

    @Override
    protected ItemStack getPickupItem() { return ItemStack.EMPTY; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.initialPosition != null) {
            tag.putDouble("InitialX", this.initialPosition.x);
            tag.putDouble("InitialY", this.initialPosition.y);
            tag.putDouble("InitialZ", this.initialPosition.z);
        }
        tag.putFloat("InitialSpeed", this.initialSpeed);
        tag.putInt("FlightTime", this.flightDuration);
        if (lastHitTarget != null) {
            tag.putUUID("LastHitUUID", lastHitTarget.getUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("InitialX")) {
            this.initialPosition = new Vec3(
                    tag.getDouble("InitialX"),
                    tag.getDouble("InitialY"),
                    tag.getDouble("InitialZ")
            );
        }
        this.initialSpeed = tag.getFloat("InitialSpeed");
        this.flightDuration = tag.getInt("FlightTime");
        if (tag.hasUUID("LastHitUUID")) {
            // Восстановление цели по UUID (упрощенно)
        }
    }

    private void checkAndCountKill(LivingEntity target) {
        if (target.isDeadOrDying()) {
            Entity owner = this.getOwner();
            if (owner instanceof TurretLightLinkedEntity turret) {
                net.minecraft.core.BlockPos pos = turret.getParentBlock();
                if (pos != null && this.level().getBlockEntity(pos) instanceof TurretLightPlacerBlockEntity be) {
                    be.incrementKills();
                }
            }
        }
    }


}
