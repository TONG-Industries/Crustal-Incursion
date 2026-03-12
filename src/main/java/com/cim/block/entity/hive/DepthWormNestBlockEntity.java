package com.cim.block.entity.hive;

import com.cim.api.hive.HiveNetwork;
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
import com.cim.api.hive.HiveNetworkMember;
import com.cim.block.entity.ModBlockEntities;
import com.cim.entity.mobs.DepthWormEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DepthWormNestBlockEntity extends BlockEntity implements HiveNetworkMember {
    private UUID networkId;
    private final List<CompoundTag> storedWorms = new ArrayList<>();

    public DepthWormNestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEPTH_WORM_NEST.get(), pos, state);
    }

    @Override
    public UUID getNetworkId() {
        return networkId;
    }

    @Override
    public void setNetworkId(UUID id) {
        this.networkId = id;
        setChanged();
    }

    public boolean isFull() {
        return storedWorms.size() >= 3;
    }

    public void addWorm(DepthWormEntity worm) {
        if (isFull()) return;

        CompoundTag tag = new CompoundTag();
        worm.save(tag);

        // Сохраняем привязку к этому гнезду
        tag.putLong("BoundNest", this.worldPosition.asLong());

        storedWorms.add(tag);
        worm.discard();

        if (!level.isClientSide && networkId != null) {
            HiveNetworkManager manager = HiveNetworkManager.get(level);
            if (manager != null) {
                manager.updateWormCount(networkId, worldPosition, 1);
                // НОВОЕ: Добавляем данные в сеть для отслеживания
                HiveNetwork network = manager.getNetwork(networkId);
                if (network != null) {
                    network.addWormDataToNest(worldPosition, tag);
                }
            }
        }
        setChanged();
    }

    public void addWormTag(CompoundTag tag) {
        // Убедимся что есть привязка к этому гнезду
        if (!tag.contains("BoundNest")) {
            tag.putLong("BoundNest", this.worldPosition.asLong());
        }
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
                    if (spawnNode != null) {
                        blockEntity.releaseWorms(spawnNode, target);
                    } else {
                        blockEntity.releaseWorms(blockEntity.worldPosition, target);
                    }
                } else {
                    blockEntity.releaseWorms(blockEntity.worldPosition, target);
                }

                if (blockEntity.networkId != null) {
                    manager.updateWormCount(blockEntity.networkId, blockEntity.worldPosition, -blockEntity.storedWorms.size());
                }
            }
        }
    }

    public List<CompoundTag> getStoredWorms() {
        return this.storedWorms;
    }

    public int getStoredWormsCount() {
        return this.storedWorms.size();
    }

    public boolean hasInjuredWorms() {
        for (CompoundTag tag : storedWorms) {
            float h = tag.contains("Health") ? tag.getFloat("Health") : 20.0f;
            if (h < 19.0f) return true;
        }
        return false;
    }

    public boolean healOneWorm() {
        for (CompoundTag tag : storedWorms) {
            float h = tag.contains("Health") ? tag.getFloat("Health") : 20.0f;
            if (h < 20.0f) {
                tag.putFloat("Health", Math.min(20.0f, h + 2.0f));
                this.setChanged();
                return true;
            }
        }
        return false;
    }

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
            if (level.getBlockState(relative).isAir()) {
                return relative;
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos check = center.offset(x, y, z);
                    if (level.getBlockState(check).isAir()) {
                        return check;
                    }
                }
            }
        }
        for (int y = 1; y <= 10; y++) {
            BlockPos above = center.above(y);
            if (level.getBlockState(above).isAir()) {
                return above;
            }
        }
        return center;
    }

    public void releaseWorms(BlockPos spawnPos, LivingEntity target) {
        if (this.level == null || this.level.isClientSide) return;
        String entityId = "cim:depth_worm";

        int countBefore = this.storedWorms.size();
        if (countBefore == 0) return;

        // НОВОЕ: Очищаем данные в сети ПЕРЕД выпуском
        if (this.networkId != null) {
            HiveNetworkManager manager = HiveNetworkManager.get(this.level);
            if (manager != null) {
                HiveNetwork network = manager.getNetwork(this.networkId);
                if (network != null) {
                    network.clearNestWormData(this.worldPosition);
                }
            }
        }

        for (CompoundTag wormTag : this.storedWorms) {
            wormTag.putString("id", entityId);
            wormTag.remove("UUID");
            wormTag.remove("UUIDMost");
            wormTag.remove("UUIDLeast");

            // Убедимся что привязка к гнезду сохранена
            if (!wormTag.contains("BoundNest")) {
                wormTag.putLong("BoundNest", this.worldPosition.asLong());
            }

            Entity entity = EntityType.loadEntityRecursive(wormTag, level, (e) -> {
                BlockPos actualSpawn = findSpawnPos(spawnPos);
                e.moveTo(actualSpawn.getX() + 0.5, actualSpawn.getY(), actualSpawn.getZ() + 0.5,
                        level.random.nextFloat() * 360F, 0);
                e.setUUID(UUID.randomUUID());

                if (e instanceof DepthWormEntity worm) {
                    worm.setHomePos(actualSpawn);
                    worm.bindToNest(this.worldPosition); // НОВОЕ: Привязываем к этому гнезду
                    if (target != null) {
                        worm.setTarget(target);
                    }
                }
                return e;
            });

            if (entity != null) {
                level.addFreshEntity(entity);
                ((ServerLevel)level).sendParticles(ParticleTypes.POOF,
                        entity.getX(), entity.getY(), entity.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
            }
        }

        this.storedWorms.clear();
        this.setChanged();

        if (this.networkId != null) {
            HiveNetworkManager manager = HiveNetworkManager.get(this.level);
            if (manager != null) {
                manager.updateWormCount(this.networkId, this.worldPosition, -countBefore);
                System.out.println("[Hive] Nest at " + this.worldPosition + " emptied. Released: " + countBefore);
            }
        }
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
        for (int i = 0; i < list.size(); i++) {
            storedWorms.add(list.getCompound(i));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            if (this.networkId == null) {
                this.networkId = UUID.randomUUID();
                this.setChanged();
            }
            HiveNetworkManager.get(this.level).addNode(this.networkId, this.worldPosition, true);
        }
    }
}