package com.cim.block.entity.industrial.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.cim.api.rotation.RotationNetworkHelper;
import com.cim.api.rotation.RotationSource;
import com.cim.api.rotation.RotationalNode;
import com.cim.block.basic.industrial.rotation.DrillHeadBlock;
import com.cim.block.entity.ModBlockEntities;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

public class DrillHeadBlockEntity extends BlockEntity implements GeoBlockEntity, RotationalNode {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private long speed = 0;
    private long torque = 0;

    private long lastBreakTick = 0;
    private RotationSource cachedSource;
    private long cacheTimestamp;
    private static final long CACHE_LIFETIME = 10;

    private float currentAnimationSpeed = 0f;
    private static final float ACCELERATION = 0.1f;
    private static final float DECELERATION = 0.03f;
    private static final int STOP_DELAY_TICKS = 5;
    private static final float MIN_ANIM_SPEED = 0.005f;
    private int ticksWithoutPower = 0;
    private static final RawAnimation ROTATION = RawAnimation.begin().thenLoop("rotation");

    @Nullable
    private BlockPos placerPos;

    public DrillHeadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRILL_HEAD_BE.get(), pos, state);
    }

    public void setPlacerPos(BlockPos pos) {
        this.placerPos = pos;
        setChanged();
        sync();
    }

    @Nullable
    public BlockPos getPlacerPos() { return placerPos; }

    // ========== Rotational ==========
    @Override public long getSpeed() { return speed; }
    @Override public long getTorque() { return torque; }
    @Override public long getMaxSpeed() { return 0; }
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
    @Override public void invalidateCache() {
        if (this.cachedSource != null) {
            this.cachedSource = null;
            if (level != null && !level.isClientSide) {
                invalidateNeighborCaches();
            }
        }
    }
    private void invalidateNeighborCaches() {
        if (level == null || level.isClientSide) return;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            if (level.getBlockEntity(neighborPos) instanceof RotationalNode node) {
                node.invalidateCache();
            }
        }
    }
    @Override
    public Direction[] getPropagationDirections(@Nullable Direction fromDir) {
        Direction myFacing = getBlockState().getValue(DrillHeadBlock.FACING);
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

    // ========== Break delay ==========
    private int getBreakDelay(Level level, BlockPos pos, Direction facing) {
        BlockPos frontPos = pos.relative(facing);
        BlockState frontState = level.getBlockState(frontPos);
        if (frontState.isAir() || frontState.canBeReplaced()) {
            return 10; // 0.5 секунды
        }
        float destroySpeed = frontState.getDestroySpeed(level, frontPos);
        int delay = 20 + (int)(destroySpeed * 2.4);
        return Math.min(140, Math.max(20, delay));
    }

    // ========== Ticking ==========
    public static void tick(Level level, BlockPos pos, BlockState state, DrillHeadBlockEntity be) {
        if (level.isClientSide) {
            be.handleClientAnimation();
            return;
        }

        long currentTime = level.getGameTime();
        if (!be.isCacheValid(currentTime)) {
            RotationSource source = RotationNetworkHelper.findSource(be, null);
            be.setCachedSource(source, currentTime);
        }

        RotationSource src = be.getCachedSource();
        be.speed = (src != null) ? src.speed() : 0;
        be.torque = (src != null) ? src.torque() : 0;

        if (be.speed > 0) {
            int delay = be.getBreakDelay(level, pos, state.getValue(DrillHeadBlock.FACING));
            if (level.getGameTime() - be.lastBreakTick >= delay) {
                if (be.placerPos != null && level.getBlockEntity(be.placerPos) instanceof ShaftPlacerBlockEntity placer) {
                    if (!placer.isSwitchedOn() || placer.isBusy()) {
                        return;
                    }
                    Direction facing = state.getValue(DrillHeadBlock.FACING);
                    BlockPos frontPos = pos.relative(facing);
                    BlockState frontState = level.getBlockState(frontPos);

                    if (frontState.isAir() || frontState.canBeReplaced()) {
                        if (placer.hasResourcesForNext()) {
                            be.moveForward(level, pos, state);
                            be.lastBreakTick = level.getGameTime();
                        }
                    } else {
                        if (be.tryBreakBlock(level, pos, state)) {
                            be.lastBreakTick = level.getGameTime();
                        }
                    }
                }
            }
        }
    }

    // ========== Breaking ==========
    private List<BlockPos> getBlocksToBreak(BlockPos pos, Direction facing) {
        List<BlockPos> targets = new ArrayList<>();
        BlockPos front = pos.relative(facing);
        Direction[] perp = getPerpendicularDirections(facing);
        Direction dirA = perp[0];
        Direction dirB = perp[1];

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                targets.add(front.relative(dirA, a).relative(dirB, b));
            }
        }
        return targets;
    }

    private Direction[] getPerpendicularDirections(Direction facing) {
        if (facing.getAxis() == Direction.Axis.X) {
            return new Direction[]{Direction.UP, Direction.NORTH};
        } else if (facing.getAxis() == Direction.Axis.Y) {
            return new Direction[]{Direction.NORTH, Direction.EAST};
        } else { // Z
            return new Direction[]{Direction.UP, Direction.EAST};
        }
    }

    private boolean tryBreakBlock(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(DrillHeadBlock.FACING);
        List<BlockPos> breakPositions = getBlocksToBreak(pos, facing);

        if (placerPos != null && level.getBlockEntity(placerPos) instanceof ShaftPlacerBlockEntity placer) {
            if (!placer.hasResourcesForNext()) {
                return false;
            }
        }

        boolean anyBroken = false;
        List<ItemStack> allDrops = new ArrayList<>();

        for (BlockPos breakPos : breakPositions) {
            BlockState targetState = level.getBlockState(breakPos);
            if (targetState.isAir()) continue;
            float destroySpeed = targetState.getDestroySpeed(level, breakPos);
            if (destroySpeed < 0 || destroySpeed > 50) continue;

            List<ItemStack> drops = Block.getDrops(targetState, (ServerLevel) level, breakPos, level.getBlockEntity(breakPos));
            allDrops.addAll(drops);
            level.destroyBlock(breakPos, false);
            anyBroken = true;
        }

        if (!anyBroken) return false;

        boolean collected = false;
        if (placerPos != null && level.getBlockEntity(placerPos) instanceof ShaftPlacerBlockEntity placer) {
            BlockPos portPos = placer.getMiningPortPos();
            if (portPos != null && level.getBlockEntity(portPos) instanceof MiningPortBlockEntity port) {
                for (ItemStack stack : allDrops) {
                    ItemStack remainder = port.addItem(stack);
                    if (!remainder.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
                    }
                }
                collected = true;
            }
        }

        if (!collected) {
            for (ItemStack stack : allDrops) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }

        return true;
    }

    // ========== Movement ==========
    private void moveForward(Level level, BlockPos oldPos, BlockState oldState) {
        if (!(level.getBlockEntity(placerPos) instanceof ShaftPlacerBlockEntity placer)) return;

        // Проверяем, нужен ли порт
        boolean needPort = placer.getShaftsAfterLastPort() >= 5;
        Direction facing = oldState.getValue(DrillHeadBlock.FACING);

        if (needPort) {
            // Проверяем возможность установки порта на месте старой головки
            if (!placer.canPlacePortAt(level, oldPos, facing)) {
                return; // не можем поставить порт – не двигаемся
            }
        } else {
            if (!placer.hasResourcesForNext()) return;
        }

        // Далее код перемещения без изменений
        BlockPos newPos = oldPos.relative(facing);
        // ... остальной код

        long currentSpeed = this.speed;
        long currentTorque = this.torque;
        BlockPos currentPlacerPos = this.placerPos;

        level.removeBlock(oldPos, false);

        BlockState newState = oldState.setValue(DrillHeadBlock.FACING, facing);
        level.setBlock(newPos, newState, 3);
        BlockEntity newBe = level.getBlockEntity(newPos);
        if (newBe instanceof DrillHeadBlockEntity newDrill) {
            newDrill.setSpeed(currentSpeed);
            newDrill.setTorque(currentTorque);
            newDrill.setPlacerPos(currentPlacerPos);
            newDrill.lastBreakTick = this.lastBreakTick;

            long currentTime = level.getGameTime();
            RotationSource source = RotationNetworkHelper.findSource(newDrill, null);
            newDrill.setCachedSource(source, currentTime);
            newDrill.setSpeed(source != null ? source.speed() : 0);
            newDrill.setTorque(source != null ? source.torque() : 0);
        }

        if (currentPlacerPos != null && level.getBlockEntity(currentPlacerPos) instanceof ShaftPlacerBlockEntity) {
            placer.handleHeadMoved(oldPos, newPos);
        }
    }

    // ========== Animation ==========
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
    public void setSpeed(long speed) {
        this.speed = speed;
        setChanged();
        sync();
        invalidateNeighborCaches();
    }

    @Override
    public void setTorque(long torque) {
        this.torque = torque;
        setChanged();
        sync();
        invalidateNeighborCaches();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "drill_controller", 0, this::animationPredicate));
    }

    private <E extends GeoBlockEntity> PlayState animationPredicate(AnimationState<E> event) {
        if (currentAnimationSpeed < MIN_ANIM_SPEED) return PlayState.STOP;
        event.getController().setAnimation(ROTATION);
        event.getController().setAnimationSpeed(currentAnimationSpeed);
        return PlayState.CONTINUE;
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ========== NBT ==========
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Speed", speed);
        tag.putLong("Torque", torque);
        tag.putLong("LastBreakTick", lastBreakTick);
        if (placerPos != null) {
            tag.putLong("PlacerPos", placerPos.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        speed = tag.getLong("Speed");
        torque = tag.getLong("Torque");
        lastBreakTick = tag.getLong("LastBreakTick");
        cachedSource = null;
        placerPos = tag.contains("PlacerPos") ? BlockPos.of(tag.getLong("PlacerPos")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}