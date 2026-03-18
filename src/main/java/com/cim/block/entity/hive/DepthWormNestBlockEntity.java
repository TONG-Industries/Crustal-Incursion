package com.cim.block.entity.hive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.cim.api.hive.HiveNetworkManager;
import com.cim.api.hive.HiveNetwork;
import com.cim.api.hive.HiveNetworkMember;
import com.cim.block.entity.ModBlockEntities;
import com.cim.entity.mobs.DepthWormEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DepthWormNestBlockEntity extends BlockEntity implements HiveNetworkMember {
    private UUID networkId;
    private final List<CompoundTag> storedWorms = new ArrayList<>();
    private long lastReleaseTime = 0;

    public DepthWormNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEPTH_WORM_NEST.get(), pos, state);
    }

    @Override
    public UUID getNetworkId() { return networkId; }

    @Override
    public void setNetworkId(UUID id) {
        this.networkId = id;
        this.setChanged();
    }

    public boolean isFull() { return storedWorms.size() >= 3; }

    public void addWorm(DepthWormEntity worm) {
        if (isFull()) return;
        CompoundTag tag = new CompoundTag();
        worm.save(tag);
        tag.putLong("BoundNest", this.worldPosition.asLong());
        storedWorms.add(tag);
        worm.discard();
        if (!level.isClientSide && networkId != null) {
            HiveNetworkManager manager = HiveNetworkManager.get(level);
            if (manager != null) manager.updateWormCount(networkId, worldPosition, 1);
        }
        setChanged();
    }

    public void addWormTag(CompoundTag tag) {
        if (!tag.contains("BoundNest")) tag.putLong("BoundNest", this.worldPosition.asLong());
        storedWorms.add(tag);
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DepthWormNestBlockEntity blockEntity) {
        if (level.isClientSide) return;
        if (level.getGameTime() % 20 == 0 && !blockEntity.storedWorms.isEmpty()) {
            AABB searchArea = new AABB(pos).inflate(10);
            List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class, searchArea,
                    e -> e.isAlive() && e.deathTime <= 0 && !(e instanceof DepthWormEntity) &&
                            !(e instanceof Player p && (p.isCreative() || p.isSpectator())));

            if (!enemies.isEmpty()) {
                LivingEntity target = enemies.get(0);
                Vec3 targetPos = target.position();
                HiveNetworkManager manager = HiveNetworkManager.get(level);

                if (manager != null && blockEntity.networkId != null) {
                    BlockPos spawnNode = manager.findNearestNode(blockEntity.networkId, targetPos, level);
                    blockEntity.releaseWorms(spawnNode != null ? spawnNode : blockEntity.worldPosition, target);
                } else {
                    blockEntity.releaseWorms(blockEntity.worldPosition, target);
                }
            }
        }
    }

    public List<CompoundTag> getStoredWorms() { return new ArrayList<>(this.storedWorms); }
    public int getStoredWormsCount() { return this.storedWorms.size(); }

    public void releaseWormsAndNotify() {
        int count = storedWorms.size();
        if (!level.isClientSide && networkId != null) {
            HiveNetworkManager manager = HiveNetworkManager.get(level);
            if (manager != null) manager.updateWormCount(networkId, worldPosition, -count);
        }
        releaseWorms(this.worldPosition, null);
    }

    private BlockPos findSpawnPos(BlockPos center) {
        for (Direction dir : Direction.values()) {
            BlockPos relative = center.relative(dir);
            if (level.getBlockState(relative).isAir()) return relative;
        }
        for (int y = 1; y <= 3; y++) {
            BlockPos above = center.above(y);
            if (level.getBlockState(above).isAir()) return above;
        }
        return center;
    }

    public void releaseWorms(BlockPos spawnPos, LivingEntity target) {
        if (this.level == null || this.level.isClientSide) return;
        int countBefore = this.storedWorms.size();
        if (countBefore == 0) return;

        if (this.level.getGameTime() - lastReleaseTime < 5) return; // Защита от спама
        lastReleaseTime = this.level.getGameTime();

        if (this.networkId != null) {
            HiveNetworkManager manager = HiveNetworkManager.get(this.level);
            if (manager != null) {
                HiveNetwork network = manager.getNetwork(this.networkId);
                // ⭐ ИСПРАВЛЕНО: Добавляем активных червей в глобальный пул сети
                if (network != null) network.addActiveWorms(countBefore);
            }
        }

        for (CompoundTag wormTag : this.storedWorms) {
            wormTag.putString("id", "cim:depth_worm");
            wormTag.remove("UUID");
            if (!wormTag.contains("BoundNest")) wormTag.putLong("BoundNest", this.worldPosition.asLong());

            final UUID netId = this.networkId;

            Entity entity = EntityType.loadEntityRecursive(wormTag, level, (e) -> {
                BlockPos actualSpawn = findSpawnPos(spawnPos);
                e.moveTo(actualSpawn.getX() + 0.5, actualSpawn.getY(), actualSpawn.getZ() + 0.5, level.random.nextFloat() * 360F, 0);
                e.setUUID(UUID.randomUUID());

                if (e instanceof DepthWormEntity worm) {
                    worm.setHomePos(actualSpawn);
                    worm.bindToNest(this.worldPosition);

                    // ⭐ ИСПРАВЛЕНО: Callback просто уменьшает глобальный счетчик при смерти
                    worm.setOnDeathCallback(() -> {
                        if (netId != null) {
                            HiveNetworkManager mgr = HiveNetworkManager.get(worm.level());
                            if (mgr != null) {
                                HiveNetwork net = mgr.getNetwork(netId);
                                if (net != null) {
                                    net.removeActiveWorm();
                                    System.out.println("[Hive] Active worm died. Active total: " + net.activeWorms);
                                }
                            }
                        }
                    });
                    if (target != null) worm.setTarget(target);
                }
                return e;
            });

            if (entity != null) {
                level.addFreshEntity(entity);
                ((ServerLevel)level).sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY(), entity.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
            }
        }

        this.storedWorms.clear();
        this.setChanged();
        System.out.println("[Hive] Nest at " + this.worldPosition + " released " + countBefore + " worms.");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (networkId != null) tag.putUUID("NetworkId", networkId);
        ListTag list = new ListTag();
        list.addAll(storedWorms);
        tag.put("StoredWorms", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("NetworkId")) this.networkId = tag.getUUID("NetworkId");
        ListTag list = tag.getList("StoredWorms", 10);
        storedWorms.clear();
        for (int i = 0; i < list.size(); i++) storedWorms.add(list.getCompound(i));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide && this.networkId != null) {
            HiveNetworkManager.get(this.level).addNode(this.networkId, this.worldPosition, true);
        }
    }

    public boolean hasInjuredWorms() {
        for (CompoundTag tag : storedWorms) {
            float h = tag.contains("Health") ? tag.getFloat("Health") : 15.0f;
            if (h < 15.0f) return true; // Максимальное здоровье червя - 15.0
        }
        return false;
    }

    public boolean healOneWorm() {
        for (CompoundTag tag : storedWorms) {
            float h = tag.contains("Health") ? tag.getFloat("Health") : 15.0f;
            if (h < 15.0f) {
                tag.putFloat("Health", 15.0f); // 1 очко = полное исцеление червя
                this.setChanged();
                return true;
            }
        }
        return false;
    }
}