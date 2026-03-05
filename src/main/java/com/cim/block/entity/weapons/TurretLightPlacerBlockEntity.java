package com.cim.block.entity.weapons;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.cim.api.energy.EnergyNetworkManager;
import com.cim.api.energy.IEnergyConnector;
import com.cim.api.energy.IEnergyReceiver;
import com.cim.api.energy.LongEnergyWrapper;
import com.cim.block.entity.ModBlockEntities;
import com.cim.capability.ModCapabilities;
import com.cim.entity.ModEntities;
import com.cim.entity.weapons.turrets.TurretLightLinkedEntity;
import com.cim.item.weapons.turrets.TurretChipItem;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class TurretLightPlacerBlockEntity extends BlockEntity implements GeoBlockEntity, IEnergyReceiver, IEnergyConnector {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final TurretAmmoContainer ammoContainer = new TurretAmmoContainer();
    private final LazyOptional<ItemStackHandler> itemHandlerOptional = LazyOptional.of(() -> ammoContainer);

    private long energyStored = 0;
    private final long MAX_ENERGY = 100000;
    private final long MAX_RECEIVE = 10000;

    private int respawnTimer = 0;
    private static final int RESPAWN_DELAY_TICKS = 1200;
    private static final long DRAIN_TRACKING = 13;
    private static final long DRAIN_HEALING = 25;
    private static final float HEAL_PER_TICK = 0.05F;
    // ... другие поля ...
    private int killCount = 0;
    private long lifetimeTicks = 0; // Используем long, чтобы не переполнилось веками


    // ... после cachedHealth
    private boolean isSwitchedOn = false; // По умолчанию ВЫКЛЮЧЕНА
    private int bootTimer = 0; // Таймер загрузки (3 сек = 60 тиков)

    // 1. Поля для хранения настроек (сохраняем их в NBT в saveAdditional!)
    private boolean targetHostile = true;  // По умолчанию стреляем
    private boolean targetNeutral = false; // По умолчанию НЕ стреляем
    private boolean targetPlayers = true;  // По умолчанию стреляем

    // Кэш здоровья для GUI (0-100)
    private int cachedHealth = 0;

    private final LazyOptional<IEnergyReceiver> hbmReceiverOptional = LazyOptional.of(() -> this);
    private final LazyOptional<IEnergyStorage> forgeEnergyOptional = LazyOptional.of(
            () -> new LongEnergyWrapper(this, LongEnergyWrapper.BitMode.LOW)
    );

    private UUID turretUUID;
    private UUID ownerUUID;

    public TurretLightPlacerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURRET_LIGHT_PLACER_BE.get(), pos, state);
        this.ammoContainer.setOnContentsChanged(this::setChanged);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TurretLightPlacerBlockEntity entity) {
        if (level.isClientSide) return;
        entity.lifetimeTicks++;
        // Фикс сети... (без изменений)
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager manager = EnergyNetworkManager.get(serverLevel);
            if (!manager.hasNode(pos)) manager.addNode(pos);
        }

        // --- ЛОГИКА ВЫКЛЮЧАТЕЛЯ ---
        // Если выключено -> НЕ спавним, НЕ тратим энергию, турель засыпает
        if (!entity.isSwitchedOn) {
            // Если турель уже была заспавнена, отключаем ей ИИ
            if (entity.turretUUID != null) {
                if (level instanceof ServerLevel sL) {
                    Entity e = sL.getEntity(entity.turretUUID);
                    if (e instanceof TurretLightLinkedEntity t && t.isAlive()) {
                        t.setPowered(false);
                    }
                }
            }
            // Просто копим энергию (логика receiveEnergy работает сама),
            // но выходим из tick, чтобы не было спавна и трат.
            return;
        }

        // --- ЛОГИКА ЗАГРУЗКИ ---
        if (entity.bootTimer > 0) {
            entity.bootTimer--;
            // Пока идет загрузка, турель еще не активна, но энергия уже не копится (или копится, но не тратится)
            // Возвращаемся, чтобы не спавнить и не стрелять, пока грузится
            return;
        }

        // --- 1. ПРОВЕРКА ТУРЕЛИ ---
        TurretLightLinkedEntity existingTurret = null;
        if (entity.turretUUID != null) {
            ServerLevel serverLevel = (ServerLevel) level;
            Entity e = serverLevel.getEntity(entity.turretUUID);
            if (e instanceof TurretLightLinkedEntity t && t.isAlive()) {
                existingTurret = t;
            } else {
                var nearby = level.getEntitiesOfClass(TurretLightLinkedEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(2.0));
                for (var t : nearby) {
                    if (t.getUUID().equals(entity.turretUUID)) {
                        if (t.isAlive()) existingTurret = t;
                        else handleTurretDeath(entity);
                        break;
                    }
                }
                if (existingTurret == null && e != null && !e.isAlive()) {
                    handleTurretDeath(entity);
                }
            }
        }

        // --- 2. ПОТРЕБЛЕНИЕ ---
        if (existingTurret != null) {
            long totalDrain = 0;
            boolean needsHeal = existingTurret.needsHealing();
            boolean isTracking = existingTurret.isTrackingTarget();
            entity.cachedHealth = (int)((existingTurret.getHealth() / existingTurret.getMaxHealth()) * 100);
            if (needsHeal) totalDrain = DRAIN_HEALING;
            else if (isTracking) totalDrain = DRAIN_TRACKING;

            if (entity.energyStored >= totalDrain) {
                entity.energyStored -= totalDrain;
                existingTurret.setPowered(true);
                if (needsHeal) existingTurret.healFromPower(HEAL_PER_TICK);
                if (totalDrain > 0) entity.setChanged();
            } else {
                existingTurret.setPowered(false);
            }
            entity.respawnTimer = 0;

            // Проверяем 9-й слот (чип)
            ItemStack chipStack = entity.ammoContainer.getStackInSlot(9);
            if (!chipStack.isEmpty() && chipStack.getItem() instanceof TurretChipItem) {
                CompoundTag tag = chipStack.getTag();
                if (tag != null && tag.contains("TurretOwners")) {
                    net.minecraft.nbt.ListTag owners = tag.getList("TurretOwners", 8); // 8 = String

                    // Создаем список UUID
                    java.util.List<UUID> allowedUsers = new java.util.ArrayList<>();

                    // Добавляем основного владельца блока
                    if (entity.ownerUUID != null) allowedUsers.add(entity.ownerUUID);

                    for (net.minecraft.nbt.Tag t : owners) {
                        String s = t.getAsString();
                        // Формат: "UUID|Name" или просто "UUID"
                        String uuidStr = s.contains("|") ? s.split("\\|")[0] : s;
                        try {
                            allowedUsers.add(UUID.fromString(uuidStr));
                        } catch (IllegalArgumentException ignored) {}
                    }

                    // Передаем в турель (нужно добавить этот метод в TurretLightLinkedEntity!)
                    existingTurret.setAllowedUsers(allowedUsers);
                }
            } else {
                // Если чипа нет, сбрасываем (оставляем только владельца блока)
                existingTurret.clearAllowedUsers();
            }

        }

        // --- 3. ВОЗРОЖДЕНИЕ И СПАВН ---
        else if (entity.turretUUID == null) {

            boolean readyToSpawn = false;

            // А) Режим таймера (после смерти)
            if (entity.respawnTimer > 0) {
                // Тикаем таймер, только если буфер ПОЧТИ полон.
                // (MAX - DRAIN) нужно, чтобы таймер не останавливался на 99975 энергии.
                if (entity.energyStored >= entity.MAX_ENERGY - DRAIN_HEALING) {
                    entity.energyStored -= DRAIN_HEALING;
                    entity.respawnTimer--;
                    entity.setChanged();
                }

                if (entity.respawnTimer <= 0) {
                    readyToSpawn = true;
                }
            }
            // Б) Режим первого запуска (таймер 0)
            else {
                // Ждем полного заряда
                if (entity.energyStored >= entity.MAX_ENERGY) {
                    readyToSpawn = true;
                }
            }

            // В) Сам спавн
            if (readyToSpawn) {
                spawnTurret(level, pos, entity);

                // 🔥 ГЛАВНОЕ ИЗМЕНЕНИЕ: Списываем половину буфера при спавне
                if (entity.turretUUID != null) {
                    entity.energyStored -= (entity.MAX_ENERGY / 2); // -50,000 HE
                    entity.setChanged();
                }
            }
        }
    }
    // 2. Метод обновления данных
    public void updateAttackSetting(int index, boolean value) {
        switch (index) {
            case 0 -> this.targetHostile = value;
            case 1 -> this.targetNeutral = value;
            case 2 -> this.targetPlayers = value;
        }
        setChanged(); // Сохраняем NBT

        // ИСПРАВЛЕНИЕ: Ищем турель в мире, если сервер
        if (this.level instanceof ServerLevel serverLevel && this.turretUUID != null) {
            Entity entity = serverLevel.getEntity(this.turretUUID);
            if (entity instanceof TurretLightLinkedEntity turret && turret.isAlive()) {
                turret.setAttackSettings(targetHostile, targetNeutral, targetPlayers);
            }
        }
    }


    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
        hbmReceiverOptional.invalidate();
        forgeEnergyOptional.invalidate();
        itemHandlerOptional.invalidate();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.level != null && !this.level.isClientSide) {
            EnergyNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
    }

    private static void handleTurretDeath(TurretLightPlacerBlockEntity entity) {
        entity.energyStored = 0;
        entity.turretUUID = null;
        entity.respawnTimer = RESPAWN_DELAY_TICKS;
        entity.setChanged();
    }

    private static void spawnTurret(Level level, BlockPos pos, TurretLightPlacerBlockEntity entity) {
        TurretLightLinkedEntity turret = ModEntities.TURRET_LIGHT_LINKED.get().create(level);
        if (turret != null) {
            turret.setParentBlock(pos);
            turret.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0);
            turret.setPersistenceRequired();
            turret.yBodyRot = 0;
            turret.yHeadRot = 0;
            turret.setAmmoContainer(entity.getAmmoContainer());

            if (entity.ownerUUID != null) {
                Player owner = level.getPlayerByUUID(entity.ownerUUID);
                if (owner != null) turret.setOwner(owner);
                else turret.setOwnerUUIDDirect(entity.ownerUUID);
            }

            level.addFreshEntity(turret);
            entity.turretUUID = turret.getUUID();
            entity.setChanged();
        }
    }

    // ... Остальные геттеры/сеттеры/capability ...
    public TurretAmmoContainer getAmmoContainer() { return ammoContainer; }
    public void setOwner(UUID owner) { this.ownerUUID = owner; setChanged(); }

    @Override public long receiveEnergy(long amount, boolean simulate) {
        if (!canReceive()) return 0;
        long energyReceived = Math.min(MAX_ENERGY - energyStored, Math.min(MAX_RECEIVE, amount));
        if (!simulate && energyReceived > 0) { energyStored += energyReceived; setChanged(); }
        return energyReceived;
    }
    @Override public long getEnergyStored() { return energyStored; }
    @Override public void setEnergyStored(long energy) { this.energyStored = Math.max(0, Math.min(energy, MAX_ENERGY)); setChanged(); }
    @Override public long getMaxEnergyStored() { return MAX_ENERGY; }
    @Override public boolean canReceive() { return energyStored < MAX_ENERGY; }
    @Override public long getReceiveSpeed() { return MAX_RECEIVE; }
    @Override public Priority getPriority() { return Priority.NORMAL; }
    @Override public boolean canConnectEnergy(Direction side) { return side != Direction.UP; }

    public int getEnergyStoredInt() { return (int) Math.min(energyStored, Integer.MAX_VALUE); }
    public int getMaxEnergyStoredInt() { return (int) Math.min(MAX_ENERGY, Integer.MAX_VALUE); }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (side == Direction.UP) return super.getCapability(cap, side);
        if (cap == ModCapabilities.ENERGY_RECEIVER || cap == ModCapabilities.ENERGY_CONNECTOR) return hbmReceiverOptional.cast();
        if (cap == ForgeCapabilities.ENERGY) return forgeEnergyOptional.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemHandlerOptional.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        hbmReceiverOptional.invalidate();
        forgeEnergyOptional.invalidate();
        itemHandlerOptional.invalidate();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("AmmoContainer", ammoContainer.serializeNBT());
        tag.putLong("Energy", energyStored);
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        if (turretUUID != null) tag.putUUID("TurretUUID", turretUUID);
        tag.putInt("RespawnTimer", respawnTimer);
        tag.putBoolean("SwitchedOn", isSwitchedOn);
        tag.putInt("BootTimer", bootTimer);
        // НОВЫЕ ПОЛЯ
        tag.putBoolean("TargetHostile", targetHostile);
        tag.putBoolean("TargetNeutral", targetNeutral);
        tag.putBoolean("TargetPlayers", targetPlayers);
        tag.putInt("KillCount", killCount);
        tag.putLong("Lifetime", lifetimeTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("AmmoContainer")) ammoContainer.deserializeNBT(tag.getCompound("AmmoContainer"));
        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        if (tag.contains("Energy")) energyStored = tag.getLong("Energy");
        if (tag.hasUUID("TurretUUID")) turretUUID = tag.getUUID("TurretUUID");
        if (tag.contains("RespawnTimer")) respawnTimer = tag.getInt("RespawnTimer");
        isSwitchedOn = tag.getBoolean("SwitchedOn");
        bootTimer = tag.getInt("BootTimer");
        // НОВЫЕ ПОЛЯ
        if (tag.contains("TargetHostile")) targetHostile = tag.getBoolean("TargetHostile");
        if (tag.contains("TargetNeutral")) targetNeutral = tag.getBoolean("TargetNeutral");
        if (tag.contains("TargetPlayers")) targetPlayers = tag.getBoolean("TargetPlayers");
        if (tag.contains("KillCount")) killCount = tag.getInt("KillCount");
        if (tag.contains("Lifetime")) lifetimeTicks = tag.getLong("Lifetime");
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // Логика статусов:
    // 0            = OFFLINE (Турели нет, энергии мало)
    // 1            = ONLINE (Турель жива и здорова)
    // 200..300     = REPAIRING (200 + % здоровья)
    // 1000+        = RESPAWNING (1000 + тики таймера)

    private int getStatusInt() {
        if (turretUUID == null) {
            if (respawnTimer > 0) return 1000 + respawnTimer; // Режим возрождения
            return 0; // Режим накопления (Offline)
        } else {
            // Если здоровье меньше 100%, значит идет ремонт
            if (cachedHealth < 100) {
                return 200 + cachedHealth;
            }
            return 1; // Online
        }
    }

    // 3. ContainerData (Синхронизация с GUI)
    protected final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                // ИСПРАВЛЕНИЕ: Используем getEnergyStoredInt() вместо energyStorage.get...
                case 0 -> TurretLightPlacerBlockEntity.this.getEnergyStoredInt();
                case 1 -> TurretLightPlacerBlockEntity.this.getMaxEnergyStoredInt();

                // ИСПРАВЛЕНИЕ: Используем метод getStatusInt() вместо несуществующего поля status
                case 2 -> TurretLightPlacerBlockEntity.this.getStatusInt();

                case 3 -> TurretLightPlacerBlockEntity.this.isSwitchedOn ? 1 : 0;
                case 4 -> TurretLightPlacerBlockEntity.this.bootTimer;

                // Новые слоты настроек
                case 5 -> TurretLightPlacerBlockEntity.this.targetHostile ? 1 : 0;
                case 6 -> TurretLightPlacerBlockEntity.this.targetNeutral ? 1 : 0;
                case 7 -> TurretLightPlacerBlockEntity.this.targetPlayers ? 1 : 0;
                case 8 -> TurretLightPlacerBlockEntity.this.killCount;
                case 9 -> (int)(TurretLightPlacerBlockEntity.this.lifetimeTicks / 20); // Передаем секунды
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                // ИСПРАВЛЕНИЕ: Устанавливаем энергию напрямую в поле
                case 0 -> TurretLightPlacerBlockEntity.this.energyStored = value;
                // Макс энергию менять через GUI обычно не нужно, пропускаем case 1

                // Статус вычисляемый, его нельзя "установить", пропускаем case 2

                case 3 -> TurretLightPlacerBlockEntity.this.isSwitchedOn = (value == 1);

                // Настройки
                case 5 -> TurretLightPlacerBlockEntity.this.targetHostile = (value == 1);
                case 6 -> TurretLightPlacerBlockEntity.this.targetNeutral = (value == 1);
                case 7 -> TurretLightPlacerBlockEntity.this.targetPlayers = (value == 1);
            }
        }

        @Override
        public int getCount() {
            return 10; // Размер данных
        }
    };

    // Поле называется data, поэтому возвращаем this.data
    public ContainerData getDataAccess() { return this.data; }


    public void togglePower() {
        this.isSwitchedOn = !this.isSwitchedOn;
        if (this.isSwitchedOn) {
            // Если включили -> запускаем таймер загрузки (3 секунды)
            this.bootTimer = 60;
        } else {
            // Если выключили -> сбрасываем таймер
            this.bootTimer = 0;
        }
        setChanged();
    }

    public void incrementKills() {
        this.killCount++;
        setChanged();
    }

    public int getKillCount() { return killCount; }
    public long getLifetimeTicks() { return lifetimeTicks; }


}
