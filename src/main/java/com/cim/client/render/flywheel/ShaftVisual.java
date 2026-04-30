package com.cim.client.render.flywheel;

import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShaftVisual extends AbstractBlockEntityVisual<ShaftBlockEntity> implements SimpleDynamicVisual {

    private final TransformedInstance shaftInstance;
    @Nullable private TransformedInstance gearInstance;
    @Nullable private TransformedInstance pulleyInstance;

    private final Direction facing;

    private final List<TransformedInstance> beltTracks = new ArrayList<>();
    private BlockPos lastConnectedPos = null;

    private float phaseOffset = 0f;
    private net.minecraft.world.item.Item currentGearItem;
    private net.minecraft.world.item.Item currentPulleyItem;

    private final float localX;
    private final float localY;
    private final float localZ;

    private static final float TRACK_LENGTH = 3.0f / 16.0f;
    private static final int ORBIT_RESOLUTION = 200;

    private float orbitTotalLength = 0f;
    private int trackCount = 0;
    private float trackSpacing = 0f;
    private TrackPose[] orbitCache;

    private static class TrackPose {
        final float u, v;
        final float angle;
        TrackPose(float u, float v, float angle) {
            this.u = u;
            this.v = v;
            this.angle = angle;
        }
    }

    private static class Point2D {
        float u, v;
        Point2D(float u, float v) { this.u = u; this.v = v; }
    }

    public ShaftVisual(VisualizationContext ctx, ShaftBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        this.facing = blockState.getValue(ShaftBlock.FACING);

        Vec3i origin = ctx.renderOrigin();
        this.localX = pos.getX() - origin.getX();
        this.localY = pos.getY() - origin.getY();
        this.localZ = pos.getZ() - origin.getZ();

        net.minecraft.resources.ResourceLocation shaftId = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(blockState.getBlock());
        String shaftName = shaftId != null ? shaftId.getPath() : "";
        PartialModel shaftModel = ModModels.SHAFT_MODELS.getOrDefault(shaftName, ModModels.HALF_SHAFT);
        this.shaftInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel)).createInstance();

        this.currentGearItem = blockEntity.getAttachedGear().getItem();
        this.currentPulleyItem = blockEntity.getAttachedPulley().getItem();

        rebuildGear();
        rebuildPulley();

        setupStatic(shaftInstance, 0);
        updateLight(partialTick);

        if (com.cim.main.CrustalIncursionMod.LOGGER.isInfoEnabled()) {
            com.cim.main.CrustalIncursionMod.LOGGER.info("[CIM-Visual] ShaftVisual CREATED at {} | model={} | origin=({},{},{})",
                    pos, shaftModel != null ? "OK" : "NULL",
                    ctx.renderOrigin().getX(), ctx.renderOrigin().getY(), ctx.renderOrigin().getZ());
        }
    }

    private void rebuildGear() {
        if (this.gearInstance != null) {
            this.gearInstance.delete();
            this.gearInstance = null;
        }

        net.minecraft.world.item.ItemStack gearStack = blockEntity.getAttachedGear();
        int gearSize = blockState.getValue(ShaftBlock.GEAR_SIZE);

        if (gearSize > 0 && !gearStack.isEmpty() && gearStack.getItem() instanceof com.cim.item.rotation.GearItem) {
            net.minecraft.resources.ResourceLocation gearId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gearStack.getItem());
            String gearName = gearId != null ? gearId.getPath() : "";
            PartialModel gearModel = ModModels.GEAR_MODELS.get(gearName);

            if (gearModel != null) {
                this.gearInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(gearModel)).createInstance();

                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();

                int axisCoord = 0;
                if (facing.getAxis() == Direction.Axis.X) axisCoord = x;
                else if (facing.getAxis() == Direction.Axis.Y) axisCoord = y;
                else if (facing.getAxis() == Direction.Axis.Z) axisCoord = z;

                int parity = Math.abs(x + y + z + axisCoord + (gearSize == 2 ? 1 : 0)) % 2;
                float halfToothAngle = gearSize == 2 ? 11.25f : 22.5f;
                this.phaseOffset = (float) Math.toRadians(parity == 0 ? halfToothAngle : 0);

                setupStatic(this.gearInstance, this.phaseOffset);
            }
        }
    }

    private void rebuildPulley() {
        if (this.pulleyInstance != null) {
            this.pulleyInstance.delete();
            this.pulleyInstance = null;
        }

        int pulleySize = blockState.getValue(ShaftBlock.PULLEY_SIZE);
        if (pulleySize > 0 && blockEntity.hasPulley()) {
            PartialModel pulleyModel = ModModels.PULLEY_MODELS.get("pulley");
            if (pulleyModel != null) {
                this.pulleyInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(pulleyModel)).createInstance();
                setupStatic(this.pulleyInstance, 0);
            }
        }
    }

    private void setupStatic(TransformedInstance instance, float initialRotationZ) {
        instance.setIdentityTransform()
                .translate(localX, localY, localZ)
                .translate(0.5f, 0.5f, 0.5f);

        Direction.Axis axis = facing.getAxis();
        if (axis == Direction.Axis.X) {
            instance.rotateY((float) Math.toRadians(facing == Direction.EAST ? 270 : 90));
        } else if (axis == Direction.Axis.Y) {
            instance.rotateX((float) Math.toRadians(facing == Direction.UP ? 90 : -90));
        } else if (facing == Direction.SOUTH) {
            instance.rotateY((float) Math.toRadians(180));
        }

        if (initialRotationZ != 0) {
            instance.rotateZ(initialRotationZ);
        }

        instance.translate(-0.5f, -0.5f, -0.5f);
        instance.setChanged();
    }

    @Override
    public void update(float pt) {
        super.update(pt);
        if (blockEntity.getAttachedGear().getItem() != this.currentGearItem) {
            this.currentGearItem = blockEntity.getAttachedGear().getItem();
            rebuildGear();
            updateLight(pt);
        }
        if (blockEntity.getAttachedPulley().getItem() != this.currentPulleyItem) {
            this.currentPulleyItem = blockEntity.getAttachedPulley().getItem();
            rebuildPulley();
            updateLight(pt);
        }
    }

    private float smoothedSpeed = 0f;
    private float currentAngle = 0f;
    private long lastFrameTime = -1;
    private float lastLoggedSpeed = Float.NaN;
    private boolean phaseSynced = false;

    @Override
    public void beginFrame(Context ctx) {
        if (blockEntity.getAttachedGear().getItem() != this.currentGearItem) {
            this.currentGearItem = blockEntity.getAttachedGear().getItem();
            rebuildGear();
            if (this.gearInstance != null) relight(pos, this.gearInstance);
        }
        if (blockEntity.getAttachedPulley().getItem() != this.currentPulleyItem) {
            this.currentPulleyItem = blockEntity.getAttachedPulley().getItem();
            rebuildPulley();
            if (this.pulleyInstance != null) relight(pos, this.pulleyInstance);
        }

        BlockPos connectedPos = blockEntity.getConnectedPulley();
        if (connectedPos != lastConnectedPos || (connectedPos != null && beltTracks.isEmpty())) {
            lastConnectedPos = connectedPos;
            rebuildBelt();
        }

        long now = System.currentTimeMillis();
        if (lastFrameTime == -1) lastFrameTime = now;
        float deltaSeconds = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;

        float targetSpeed = blockEntity.getVisualSpeed();

        if (targetSpeed != lastLoggedSpeed) {
            com.cim.main.CrustalIncursionMod.LOGGER.info("[VISUAL-DIAG] beginFrame at {} | speed changed: {} -> {}",
                    pos, lastLoggedSpeed, targetSpeed);
            lastLoggedSpeed = targetSpeed;
        }

        float speedDiff = targetSpeed - smoothedSpeed;
        if (Math.abs(speedDiff) > 0.001f) {
            smoothedSpeed += speedDiff * 4.0f * deltaSeconds;
        } else {
            smoothedSpeed = targetSpeed;
        }

        currentAngle += smoothedSpeed * 2.0f * deltaSeconds;
        float twoPi = (float) (2 * Math.PI);
        currentAngle = currentAngle % twoPi;
        if (currentAngle < 0) currentAngle += twoPi;

        if (smoothedSpeed == targetSpeed && targetSpeed != 0) {
            float time = (float) (now % 100000) / 50f;
            float globalAngle = (time * targetSpeed * 0.1f) % twoPi;
            if (globalAngle < 0) globalAngle += twoPi;

            if (!this.phaseSynced) {
                currentAngle = globalAngle;
                this.phaseSynced = true;
            } else {
                float angleDiff = (globalAngle - currentAngle) % twoPi;
                if (angleDiff > Math.PI) angleDiff -= twoPi;
                if (angleDiff < -Math.PI) angleDiff += twoPi;

                float maxCorrection = 0.5f * deltaSeconds;
                float correction = Math.signum(angleDiff) * Math.min(Math.abs(angleDiff), maxCorrection);
                currentAngle += correction;
            }
        } else {
            this.phaseSynced = false;
        }

        if (targetSpeed == 0 && Math.abs(smoothedSpeed) < 5.0f) {
            float PI_OVER_4 = (float) (Math.PI / 4.0);
            float targetSnap = Math.round(currentAngle / PI_OVER_4) * PI_OVER_4;
            float snapDiff = targetSnap - currentAngle;

            if (Math.abs(snapDiff) > 0.001f) {
                float pull = 8.0f * (1.0f - (Math.abs(smoothedSpeed) / 5.0f));
                currentAngle += snapDiff * pull * deltaSeconds;
            } else {
                currentAngle = targetSnap;
            }
        }

        // =========================================================
        // 5. АНИМАЦИЯ РЕМНЯ (3D Траки)
        // =========================================================
        if (orbitCache != null && orbitTotalLength > 0 && trackCount > 0) {
            float r1 = getPulleyRadius(blockEntity);
            boolean invert = (facing == Direction.SOUTH || facing == Direction.WEST || facing == Direction.DOWN);
            float animatedAngle = invert ? currentAngle : -currentAngle;
            float beltOffset = (animatedAngle * r1) % orbitTotalLength;
            if (beltOffset < 0) beltOffset += orbitTotalLength;

            Direction.Axis axis = facing.getAxis();

            for (int i = 0; i < trackCount; i++) {
                float D = (beltOffset + i * trackSpacing) % orbitTotalLength;
                
                int index = (int) ((D / orbitTotalLength) * orbitCache.length);
                if (index >= orbitCache.length) index = orbitCache.length - 1;
                if (index < 0) index = 0;
                
                TrackPose pose = orbitCache[index];
                TransformedInstance track = beltTracks.get(i);
                
                track.setIdentityTransform()
                     .translate(localX + 0.5f, localY + 0.5f, localZ + 0.5f);
                     
                if (axis == Direction.Axis.X) {
                    track.translate(0, pose.v, pose.u);
                    track.rotateX(-pose.angle);
                } else if (axis == Direction.Axis.Y) {
                    track.translate(pose.u, 0, pose.v);
                    track.rotateY(-pose.angle + (float) Math.PI / 2f);
                    track.rotateZ((float) Math.PI / 2f);
                } else if (axis == Direction.Axis.Z) {
                    track.translate(pose.u, pose.v, 0);
                    track.rotateZ(pose.angle);
                    track.rotateY((float) Math.PI / 2f);
                }
                
                track.translate(-0.5f, -0.5f, -1.5f / 16.0f);
                track.setChanged();
            }
        }

        setupStatic(shaftInstance, currentAngle);
        if (gearInstance != null) setupStatic(gearInstance, currentAngle + this.phaseOffset);
        if (pulleyInstance != null) setupStatic(pulleyInstance, currentAngle);
    }

    private float getPulleyRadius(ShaftBlockEntity be) {
        if (be.hasPulley() && be.getAttachedPulley().getItem() instanceof com.cim.item.rotation.PulleyItem pulley) {
            return (pulley.getDiameterPixels() / 2.0f + 1.0f) / 16.0f;
        }
        return 0f;
    }

    private void rebuildBelt() {
        beltTracks.forEach(Instance::delete);
        beltTracks.clear();
        orbitCache = null;
        trackCount = 0;
        orbitTotalLength = 0f;

        BlockPos connectedPos = blockEntity.getConnectedPulley();
        if (connectedPos == null) return;
        if (pos.compareTo(connectedPos) > 0) return;

        if (!(level.getBlockEntity(connectedPos) instanceof ShaftBlockEntity otherBE)) return;
        if (!blockEntity.hasPulley() || !otherBE.hasPulley()) return;

        float r1 = getPulleyRadius(blockEntity);
        float r2 = getPulleyRadius(otherBE);
        if (r1 == 0 || r2 == 0) return;

        Direction.Axis axis = facing.getAxis();
        float dx = connectedPos.getX() - pos.getX();
        float dy = connectedPos.getY() - pos.getY();
        float dz = connectedPos.getZ() - pos.getZ();

        float du = 0, dv = 0;
        if (axis == Direction.Axis.X) { du = dz; dv = dy; }
        else if (axis == Direction.Axis.Y) { du = dx; dv = dz; }
        else if (axis == Direction.Axis.Z) { du = dx; dv = dy; }

        float distance = (float) Math.sqrt(du * du + dv * dv);
        if (distance == 0) return;

        float baseAngle = (float) Math.atan2(dv, du);
        float alpha = (float) Math.asin((r1 - r2) / distance);
        float straightLength = (float) Math.sqrt(distance * distance - (r1 - r2) * (r1 - r2));

        float dirAngle1 = baseAngle - alpha;
        float touchAngle1 = dirAngle1 + (float) Math.PI / 2f;

        float dirAngle2 = baseAngle + alpha;
        float touchAngle2 = dirAngle2 - (float) Math.PI / 2f;

        float arcBStart = touchAngle1;
        float arcBEnd = touchAngle2;
        while (arcBEnd >= arcBStart) arcBEnd -= (float) (2 * Math.PI);
        float arcLengthB = (arcBStart - arcBEnd) * r2;

        float arcAStart = touchAngle2;
        float arcAEnd = touchAngle1;
        while (arcAEnd >= arcAStart) arcAEnd -= (float) (2 * Math.PI);
        float arcLengthA = (arcAStart - arcAEnd) * r1;

        orbitTotalLength = straightLength * 2 + arcLengthB + arcLengthA;

        trackCount = (int) Math.ceil(orbitTotalLength / TRACK_LENGTH);
        trackSpacing = orbitTotalLength / trackCount;

        for (int i = 0; i < trackCount; i++) {
            TransformedInstance track = instancerProvider()
                    .instancer(InstanceTypes.TRANSFORMED, Models.partial(ModModels.BELT_SEGMENT))
                    .createInstance();
            beltTracks.add(track);
            relight(pos, track);
        }

        int totalPoints = (int) Math.ceil(orbitTotalLength * ORBIT_RESOLUTION);
        orbitCache = new TrackPose[totalPoints];

        for (int i = 0; i < totalPoints; i++) {
            float D = ((float) i / totalPoints) * orbitTotalLength;
            
            Point2D pointA = getPointOnPath(D, straightLength, arcLengthB, r1, r2, du, dv, touchAngle1, touchAngle2, arcBStart, arcBEnd, arcAStart, arcAEnd);
            
            float dB = (D - TRACK_LENGTH + orbitTotalLength) % orbitTotalLength;
            Point2D pointB = getPointOnPath(dB, straightLength, arcLengthB, r1, r2, du, dv, touchAngle1, touchAngle2, arcBStart, arcBEnd, arcAStart, arcAEnd);

            float cx = (pointA.u + pointB.u) / 2.0f;
            float cy = (pointA.v + pointB.v) / 2.0f;
            float angle = (float) Math.atan2(pointA.v - pointB.v, pointA.u - pointB.u);

            orbitCache[i] = new TrackPose(cx, cy, angle);
        }

        com.cim.main.CrustalIncursionMod.LOGGER.info("[BELT-DEBUG] rebuildBelt: orbitLength={}, trackCount={}, spacing={}", orbitTotalLength, trackCount, trackSpacing);
    }

    private Point2D getPointOnPath(float d, float straightLength, float arcLengthB, float r1, float r2, float du, float dv, float touchAngle1, float touchAngle2, float arcBStart, float arcBEnd, float arcAStart, float arcAEnd) {
        if (d < straightLength) {
            float fraction = d / straightLength;
            float uA = r1 * (float) Math.cos(touchAngle1);
            float vA = r1 * (float) Math.sin(touchAngle1);
            float uB = du + r2 * (float) Math.cos(touchAngle1);
            float vB = dv + r2 * (float) Math.sin(touchAngle1);
            return new Point2D(uA + fraction * (uB - uA), vA + fraction * (vB - vA));
        }
        
        d -= straightLength;
        if (d < arcLengthB) {
            float fraction = d / arcLengthB;
            float angle = arcBStart - fraction * (arcBStart - arcBEnd);
            return new Point2D(du + r2 * (float) Math.cos(angle), dv + r2 * (float) Math.sin(angle));
        }

        d -= arcLengthB;
        if (d < straightLength) {
            float fraction = d / straightLength;
            float uB = du + r2 * (float) Math.cos(touchAngle2);
            float vB = dv + r2 * (float) Math.sin(touchAngle2);
            float uA = r1 * (float) Math.cos(touchAngle2);
            float vA = r1 * (float) Math.sin(touchAngle2);
            return new Point2D(uB + fraction * (uA - uB), vB + fraction * (vA - vB));
        }

        d -= straightLength;
        float fraction = d / Math.max(0.0001f, (orbitTotalLength - straightLength * 2 - arcLengthB));
        float angle = arcAStart - fraction * (arcAStart - arcAEnd);
        return new Point2D(r1 * (float) Math.cos(angle), r1 * (float) Math.sin(angle));
    }

    @Override
    public void updateLight(float partialTick) {
        relight(pos, shaftInstance);
        if (gearInstance != null) relight(pos, gearInstance);
        if (pulleyInstance != null) relight(pos, pulleyInstance);
        for (TransformedInstance track : beltTracks) {
            relight(pos, track);
        }
    }

    @Override
    protected void _delete() {
        shaftInstance.delete();
        if (gearInstance != null) gearInstance.delete();
        if (pulleyInstance != null) pulleyInstance.delete();
        beltTracks.forEach(Instance::delete);
        beltTracks.clear();
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(shaftInstance);
        if (gearInstance != null) consumer.accept(gearInstance);
        if (pulleyInstance != null) consumer.accept(pulleyInstance);
        for (TransformedInstance track : beltTracks) {
            consumer.accept(track);
        }
    }
}