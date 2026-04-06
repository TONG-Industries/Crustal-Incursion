package com.cim.entity.mobs.grenadier;

import com.cim.entity.ModEntities;
import com.cim.entity.weapons.grenades.*;
import com.cim.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.ArrayList;
import java.util.List;

public class GrenadierZombieEntity extends Zombie {

    public enum GrenadierType {
        STANDARD,
        IMPACT,
        HYDROGEN
    }

    private static final EntityDataAccessor<String> GRENADIER_TYPE =
            SynchedEntityData.defineId(GrenadierZombieEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> GRENADES_LEFT =
            SynchedEntityData.defineId(GrenadierZombieEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Boolean> IN_RETREAT_MODE =
            SynchedEntityData.defineId(GrenadierZombieEntity.class, EntityDataSerializers.BOOLEAN);

    private int grenadeCooldown = 0;
    private List<ItemStack> grenadeInventory = new ArrayList<>();
    private boolean initialized = false;
    private ItemStack hiddenGrenade = ItemStack.EMPTY; // Спрятанная граната при отступлении

    public GrenadierZombieEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(GRENADIER_TYPE, GrenadierType.STANDARD.name());
        this.entityData.define(GRENADES_LEFT, 5);
        this.entityData.define(IN_RETREAT_MODE, false);
    }

    @Override
    protected void registerGoals() {
        // Goal 2: Ближний бой - только когда закончились гранаты
        this.goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0D, false) {
            @Override
            public boolean canUse() {
                return super.canUse() && !GrenadierZombieEntity.this.hasGrenades();
            }
            @Override
            public boolean canContinueToUse() {
                return super.canContinueToUse() && !GrenadierZombieEntity.this.hasGrenades();
            }
        });

        // Goal 3: Унифицированная логика гренадёра (атака + отступление)
        this.goalSelector.addGoal(3, new GrenadierCombatGoal(this));

        this.goalSelector.addGoal(6, new MoveThroughVillageGoal(this, 1.0D, true, 4, () -> true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(Zombie.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData spawnData, CompoundTag dataTag) {
        spawnData = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

        RandomSource random = level.getRandom();
        double roll = random.nextDouble();

        GrenadierType type;
        int grenadeCount;

        if (roll < 0.05) {
            type = GrenadierType.HYDROGEN;
            grenadeCount = 1;
        } else if (roll < 0.25) {
            type = GrenadierType.IMPACT;
            grenadeCount = 3;
        } else {
            type = GrenadierType.STANDARD;
            grenadeCount = 5;
        }

        this.entityData.set(GRENADIER_TYPE, type.name());
        this.entityData.set(GRENADES_LEFT, grenadeCount);

        this.populateGrenadeInventory(type, grenadeCount);
        this.updateHeldItem();

        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.GRENADIER_GOGGLES.get()));
        this.setDropChance(EquipmentSlot.HEAD, 0.025F);

        this.initialized = true;

        return spawnData;
    }

    private void populateGrenadeInventory(GrenadierType type, int count) {
        this.grenadeInventory.clear();

        for (int i = 0; i < count; i++) {
            switch (type) {
                case STANDARD -> {
                    double rand = this.random.nextDouble();
                    if (rand < 0.3) {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADE.get()));
                    } else if (rand < 0.5) {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADEHE.get()));
                    } else if (rand < 0.7) {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADEFIRE.get()));
                    } else if (rand < 0.85) {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADESLIME.get()));
                    } else {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADESMART.get()));
                    }
                }
                case IMPACT -> {
                    double rand = this.random.nextDouble();
                    if (rand < 0.4) {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADE_IF.get()));
                    } else if (rand < 0.7) {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADE_IF_HE.get()));
                    } else if (rand < 0.85) {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADE_IF_FIRE.get()));
                    } else {
                        grenadeInventory.add(new ItemStack(ModItems.GRENADE_IF_SLIME.get()));
                    }
                }
                case HYDROGEN -> {
                    grenadeInventory.add(new ItemStack(ModItems.GRENADE_NUC.get()));
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.grenadeCooldown > 0) {
            this.grenadeCooldown--;
        }

        if (!this.level().isClientSide) {
            updateHeldItem();
        }
    }

    // === УПРАВЛЕНИЕ ГРАНАТОЙ В РУКАХ ===

    public void updateHeldItem() {
        if (this.hasGrenades() && !this.isInRetreatMode()) {
            int left = getGrenadesLeft();
            if (left > 0 && left <= grenadeInventory.size()) {
                ItemStack grenade = grenadeInventory.get(left - 1).copy();
                this.setItemInHand(InteractionHand.MAIN_HAND, grenade);
            }
        } else {
            this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** Вызывается при начале отступления - сохраняет и убирает гранату */
    public void hideGrenadeForRetreat() {
        int left = getGrenadesLeft();
        if (left > 0 && left <= grenadeInventory.size()) {
            this.hiddenGrenade = this.grenadeInventory.get(left - 1).copy();
        }
        this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** Возвращает гранату в руки (при выходе из паники) */
    public void restoreGrenadeInHand() {
        this.hiddenGrenade = ItemStack.EMPTY;
        updateHeldItem();
    }

    public void clearHiddenGrenade() {
        this.hiddenGrenade = ItemStack.EMPTY;
    }

    public ItemStack getHiddenGrenade() {
        return this.hiddenGrenade;
    }

    // === ГЕТТЕРЫ/СЕТТЕРЫ ===

    public boolean hasGrenades() {
        return this.entityData.get(GRENADES_LEFT) > 0;
    }

    public boolean isInRetreatMode() {
        return this.entityData.get(IN_RETREAT_MODE);
    }

    public void setInRetreatMode(boolean retreat) {
        this.entityData.set(IN_RETREAT_MODE, retreat);
    }

    public GrenadierType getGrenadierType() {
        try {
            return GrenadierType.valueOf(this.entityData.get(GRENADIER_TYPE));
        } catch (IllegalArgumentException e) {
            return GrenadierType.STANDARD;
        }
    }

    public int getGrenadesLeft() {
        return this.entityData.get(GRENADES_LEFT);
    }

    public boolean canThrowGrenade() {
        return this.hasGrenades() && this.grenadeCooldown <= 0;
    }

    // === БРОСОК ГРАНАТЫ ===

    public void throwGrenade(LivingEntity target) {
        if (!this.canThrowGrenade()) return;

        Level level = this.level();
        if (level.isClientSide) return;

        int grenadesLeft = this.entityData.get(GRENADES_LEFT);
        if (grenadesLeft > this.grenadeInventory.size() || grenadesLeft <= 0) return;

        ItemStack grenadeStack = this.grenadeInventory.get(grenadesLeft - 1);
        GrenadierType type = getGrenadierType();

        ThrowableItemProjectile projectile = createGrenadeProjectile(level, grenadeStack, type);

        if (projectile != null) {
            double x = this.getX();
            double y = this.getY() + this.getEyeHeight() - 0.1;
            double z = this.getZ();

            projectile.setPos(x, y, z);
            projectile.setOwner(this);

            double dx = target.getX() - x;
            double dy = target.getY() + target.getEyeHeight() / 2.0 - y;
            double dz = target.getZ() - z;

            double distance = Math.sqrt(dx * dx + dz * dz);
            double speed = type == GrenadierType.HYDROGEN ? 1.2 : 1.5;
            double arc = Math.max(0.1, distance * 0.08);

            projectile.shoot(dx, dy + arc, dz, (float) speed, 1.0F);

            level.addFreshEntity(projectile);

            int newCount = grenadesLeft - 1;
            this.entityData.set(GRENADES_LEFT, newCount);

            this.grenadeCooldown = 60;
        }
    }

    private ThrowableItemProjectile createGrenadeProjectile(Level level, ItemStack stack, GrenadierType type) {
        return switch (type) {
            case STANDARD -> {
                if (stack.is(ModItems.GRENADE.get())) {
                    yield new GrenadeProjectileEntity(ModEntities.GRENADE_PROJECTILE.get(), level, this, GrenadeType.STANDARD);
                } else if (stack.is(ModItems.GRENADEHE.get())) {
                    yield new GrenadeProjectileEntity(ModEntities.GRENADEHE_PROJECTILE.get(), level, this, GrenadeType.HE);
                } else if (stack.is(ModItems.GRENADEFIRE.get())) {
                    yield new GrenadeProjectileEntity(ModEntities.GRENADEFIRE_PROJECTILE.get(), level, this, GrenadeType.FIRE);
                } else if (stack.is(ModItems.GRENADESLIME.get())) {
                    yield new GrenadeProjectileEntity(ModEntities.GRENADESLIME_PROJECTILE.get(), level, this, GrenadeType.SLIME);
                } else if (stack.is(ModItems.GRENADESMART.get())) {
                    yield new GrenadeProjectileEntity(ModEntities.GRENADESMART_PROJECTILE.get(), level, this, GrenadeType.SMART);
                }
                yield new GrenadeProjectileEntity(ModEntities.GRENADE_PROJECTILE.get(), level, this, GrenadeType.STANDARD);
            }
            case IMPACT -> {
                GrenadeIfType ifType;
                if (stack.is(ModItems.GRENADE_IF.get())) {
                    ifType = GrenadeIfType.GRENADE_IF;
                } else if (stack.is(ModItems.GRENADE_IF_HE.get())) {
                    ifType = GrenadeIfType.GRENADE_IF_HE;
                } else if (stack.is(ModItems.GRENADE_IF_FIRE.get())) {
                    ifType = GrenadeIfType.GRENADE_IF_FIRE;
                } else if (stack.is(ModItems.GRENADE_IF_SLIME.get())) {
                    ifType = GrenadeIfType.GRENADE_IF_SLIME;
                } else {
                    ifType = GrenadeIfType.GRENADE_IF;
                }
                yield new GrenadeIfProjectileEntity(level, this, ifType);
            }
            case HYDROGEN -> new GrenadeNucProjectileEntity(level, this);
        };
    }

    // === СОХРАНЕНИЕ ===

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("GrenadierType", this.entityData.get(GRENADIER_TYPE));
        tag.putInt("GrenadesLeft", this.entityData.get(GRENADES_LEFT));
        tag.putBoolean("Initialized", this.initialized);
        tag.putBoolean("InRetreatMode", this.isInRetreatMode());
        if (!this.hiddenGrenade.isEmpty()) {
            tag.put("HiddenGrenade", this.hiddenGrenade.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("GrenadierType")) {
            this.entityData.set(GRENADIER_TYPE, tag.getString("GrenadierType"));
        }

        if (tag.contains("GrenadesLeft")) {
            int left = tag.getInt("GrenadesLeft");
            this.entityData.set(GRENADES_LEFT, left);
            populateGrenadeInventory(getGrenadierType(), left);
            if (left > 0) {
                updateHeldItem();
            }
        }

        if (tag.contains("Initialized")) {
            this.initialized = tag.getBoolean("Initialized");
        }

        if (tag.contains("InRetreatMode")) {
            this.setInRetreatMode(tag.getBoolean("InRetreatMode"));
        }

        if (tag.contains("HiddenGrenade")) {
            this.hiddenGrenade = ItemStack.of(tag.getCompound("HiddenGrenade"));
        }
    }

    // === ДРОП ПРИ СМЕРТИ ===

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);

        // Если умер во время отступления (граната была спрятана) - дропаем её
        if (!this.hiddenGrenade.isEmpty()) {
            this.spawnAtLocation(this.hiddenGrenade);
            this.hiddenGrenade = ItemStack.EMPTY;
        }

        // Дропаем оставшиеся гранаты из инвентаря
        if (!this.level().isClientSide && this.hasGrenades()) {
            int grenadesLeft = this.entityData.get(GRENADES_LEFT);

            // Текущая активная граната (если не в режиме отступления)
            if (!this.isInRetreatMode() && grenadesLeft > 0 && grenadesLeft <= this.grenadeInventory.size()) {
                ItemStack current = this.grenadeInventory.get(grenadesLeft - 1);
                if (!current.isEmpty()) {
                    this.spawnAtLocation(current.copy());
                }
            }

            // Дополнительные гранаты с шансом от лутинга
            int extraDrops = (looting > 0) ? this.random.nextInt(looting) + 1 : 0;
            int dropped = 0;
            for (int i = 0; i < grenadesLeft - 1 && dropped < extraDrops; i++) {
                if (this.random.nextFloat() < 0.5F) {
                    ItemStack extra = this.grenadeInventory.get(i);
                    if (!extra.isEmpty()) {
                        this.spawnAtLocation(extra.copy());
                        dropped++;
                    }
                }
            }
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.FOLLOW_RANGE, 35.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
    }
}