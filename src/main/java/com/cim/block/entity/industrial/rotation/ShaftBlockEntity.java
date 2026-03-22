package com.cim.block.entity.industrial.rotation;

import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.basic.ModBlocks;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.basic.industrial.rotation.ShaftType;
import com.cim.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class ShaftBlockEntity extends BlockEntity implements GeoBlockEntity, RotationalNode {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long speed = 0;
    private long torque = 0;

    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    // Эти поля больше не статические, получаем из блока
    // private static final long MAX_SPEED = 300;
    // private static final long MAX_TORQUE = 150;

    private static final float ACCELERATION = 0.1f;
    private static final float DECELERATION = 0.03f;
    private static final int STOP_DELAY_TICKS = 5;
    private static final float MIN_ANIM_SPEED = 0.005f;

    private float currentAnimationSpeed = 0f;
    private int ticksWithoutPower = 0;

    private static final RawAnimation ROTATION = RawAnimation.begin().thenLoop("rotation");

    public ShaftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHAFT_BLOCK_BE.get(), pos, state);
    }

    // ========== NBT и Sync ==========
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        this.speed = tag.getLong("Speed");
        this.torque = tag.getLong("Torque");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ========== Rotational ==========
    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }
    @Override public void setSpeed(long speed) { this.speed = speed; setChanged(); sync(); invalidateNeighborCaches(); }
    @Override public void setTorque(long torque) { this.torque = torque; setChanged(); sync(); invalidateNeighborCaches(); }
    @Override public long getMaxSpeed() { return 0; } // не используется для вала?
    @Override public long getMaxTorque() { return 0; }

    // ========== RotationalNode ==========
    @Override @Nullable public RotationSource getCachedSource() { return cachedSource; }
    @Override public void setCachedSource(@Nullable RotationSource source, long gameTime) {
        this.cachedSource = source;
        this.cacheTimestamp = gameTime;
    }
    @Override public boolean isCacheValid(long currentTime) {
        return cachedSource != null && (currentTime - cacheTimestamp) <= CACHE_LIFETIME;
    }

    @Override
    public void invalidateCache() {
        if (this.cachedSource != null) {
            this.cachedSource = null;
            if (level != null && !level.isClientSide) {
                invalidateNeighborCaches();
            }
        }
    }

    private void invalidateNeighborCaches() {
        if (level == null || level.isClientSide) return;
        Direction facing = getBlockState().getValue(ShaftBlock.FACING);
        for (Direction dir : new Direction[]{facing, facing.getOpposite()}) {
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getBlockEntity(neighborPos) instanceof RotationalNode node) {
                node.invalidateCache();
            }
        }
    }

    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        Direction myFacing = getBlockState().getValue(ShaftBlock.FACING);
        if (fromDir != null) {
            if (fromDir == myFacing || fromDir == myFacing.getOpposite()) {
                return new Direction[]{fromDir.getOpposite()};
            } else {
                return new Direction[0];
            }
        } else {
            return new Direction[]{myFacing, myFacing.getOpposite()};
        }
    }

    // ========== Logic ==========
    public static void tick(Level level, BlockPos pos, BlockState state, ShaftBlockEntity be) {
        if (level.isClientSide) {
            be.handleClientAnimation();
            return;
        }

        // Получаем тип вала из блока
        ShaftType type = ((ShaftBlock) state.getBlock()).getShaftType();

        long currentTime = level.getGameTime();

        // 1. Обновляем источник, если нужно
        if (!be.isCacheValid(currentTime)) {
            RotationSource source = RotationNetworkHelper.findSource(be, null);
            be.setCachedSource(source, currentTime);
        }

        RotationSource src = be.getCachedSource();
        long newSpeed = (src != null) ? src.speed() : 0;
        long newTorque = (src != null) ? src.torque() : 0;

        // 2. Проверка перегрузки (используем лимиты из типа)
        if (newSpeed > type.getMaxSpeed() || newTorque > type.getMaxTorque()) {
            level.removeBlock(pos, false);
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(ModBlocks.SHAFT_IRON.get())); // TODO: заменить на предмет этого вала
            return;
        }

        // 3. Применение изменений (остальное без изменений)
        boolean changed = false;
        if (be.speed != newSpeed) {
            be.speed = newSpeed;
            changed = true;
        }
        if (be.torque != newTorque) {
            be.torque = newTorque;
            changed = true;
        }

        if (changed) {
            be.setChanged();
            be.sync();
            be.invalidateNeighborCaches();
        }
    }

    private void handleClientAnimation() {
        float targetSpeed = (speed > 0) ? Math.max(0.1f, speed / 100f) : 0f;
        if (targetSpeed > 0) {
            ticksWithoutPower = 0;
            if (currentAnimationSpeed < targetSpeed) currentAnimationSpeed = Math.min(currentAnimationSpeed + ACCELERATION, targetSpeed);
            else if (currentAnimationSpeed > targetSpeed) currentAnimationSpeed = Math.max(currentAnimationSpeed - ACCELERATION, targetSpeed);
        } else {
            if (currentAnimationSpeed > 0) {
                ticksWithoutPower++;
                if (ticksWithoutPower > STOP_DELAY_TICKS) currentAnimationSpeed = Math.max(currentAnimationSpeed - DECELERATION, 0f);
            } else ticksWithoutPower = 0;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "shaft_controller", 0, this::animationPredicate));
    }

    private <E extends GeoBlockEntity> PlayState animationPredicate(AnimationState<E> event) {
        if (currentAnimationSpeed < MIN_ANIM_SPEED) return PlayState.STOP;
        event.getController().setAnimation(ROTATION);
        event.getController().setAnimationSpeed(currentAnimationSpeed);
        return PlayState.CONTINUE;
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public double getBoneResetTime() { return 0; }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putLong("Speed", speed); tag.putLong("Torque", torque); }
    @Override public void load(CompoundTag tag) { super.load(tag); speed = tag.getLong("Speed"); torque = tag.getLong("Torque"); cachedSource = null; }
}