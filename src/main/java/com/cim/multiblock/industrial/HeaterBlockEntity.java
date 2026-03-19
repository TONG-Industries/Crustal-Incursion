package com.cim.multiblock.industrial;

import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.ModBlockEntities;
import com.cim.multiblock.MultiblockPattern;
import com.cim.multiblock.part.IMultiblockController;
import com.cim.multiblock.part.MultiblockPartBlock;
import com.cim.multiblock.part.MultiblockPartEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HeaterBlockEntity extends BlockEntity implements IMultiblockController, BlockEntityTicker<HeaterBlockEntity> {
    private HeaterMultiblock controller;
    private List<BlockPos> partPositions = new ArrayList<>();
    private boolean isDestroying = false;
    private BlockPos minPos; // Минимальная позиция мультиблока (origin)
    private BlockPos maxPos; // Максимальная позиция мультиблока
    private BlockPos controllerPos; // Позиция контроллера (может отличаться от origin)

    public HeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEATER_BE.get(), pos, state);
    }

    public void createMultiblock(Level level, BlockPos origin, BlockPos controllerPosition) {
        this.controllerPos = controllerPosition;
        this.controller = new HeaterMultiblock(level, origin);
        MultiblockPattern pattern = controller.getPattern();

        // Вычисляем границы для коллизии
        minPos = origin;
        maxPos = origin.offset(pattern.getWidth() - 1, pattern.getHeight() - 1, pattern.getDepth() - 1);

        for (int y = 0; y < pattern.getHeight(); y++) {
            for (int x = 0; x < pattern.getWidth(); x++) {
                for (int z = 0; z < pattern.getDepth(); z++) {
                    BlockPos partPos = origin.offset(x, y, z);

                    // Пропускаем позицию контроллера — там уже стоит HeaterBlock
                    if (partPos.equals(controllerPosition)) continue;

                    MultiblockPattern.PatternEntry entry = pattern.getEntry(x, y, z);
                    if (entry == MultiblockPattern.PatternEntry.EMPTY || entry == MultiblockPattern.PatternEntry.AIR) {
                        continue;
                    }

                    level.setBlock(partPos, ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(), 3);
                    BlockEntity be = level.getBlockEntity(partPos);
                    if (be instanceof MultiblockPartEntity part) {
                        part.setControllerPos(controllerPosition); // Важно: сохраняем позицию контроллера, не origin!
                    }
                    partPositions.add(partPos);
                }
            }
        }
        controller.validate();
        setChanged();
    }

    @Override
    public void destroyMultiblock() {
        if (isDestroying || level == null) return;
        isDestroying = true;

        for (BlockPos pos : partPositions) {
            if (level.getBlockState(pos).getBlock() instanceof MultiblockPartBlock) {
                level.removeBlock(pos, false);
            }
        }
        // Не удаляем контроллер здесь — он удаляется вызывающим кодом или остаётся для пересоздания

        if (controller != null) {
            controller.onBreak();
        }
    }

    @Override
    public void destroyMultiblockFromPart(BlockPos brokenPartPos) {
        if (isDestroying || level == null) return;
        isDestroying = true;

        // Удаляем все части кроме сломанной (она уже удаляется)
        for (BlockPos pos : partPositions) {
            if (!pos.equals(brokenPartPos) && level.getBlockState(pos).getBlock() instanceof MultiblockPartBlock) {
                level.removeBlock(pos, false);
            }
        }

        // Удаляем контроллер — дроп будет в onRemove HeaterBlock
        level.removeBlock(worldPosition, false);

        if (controller != null) {
            controller.onBreak();
        }
    }

    @Override
    public VoxelShape getMultiblockShape() {
        if (minPos == null || maxPos == null) return Shapes.block();
        return Shapes.block();
    }

    // Альтернативный метод для получения полной формы мультиблока
    public VoxelShape getFullMultiblockShape(BlockPos relativePos) {
        if (minPos == null || maxPos == null) return Shapes.block();

        // Вычисляем смещение относительно minPos
        int relX = relativePos.getX() - minPos.getX();
        int relY = relativePos.getY() - minPos.getY();
        int relZ = relativePos.getZ() - minPos.getZ();

        // Возвращаем форму, которая охватывает весь мультиблок относительно текущей позиции
        double x1 = -relX;
        double y1 = -relY;
        double z1 = -relZ;
        double x2 = x1 + (maxPos.getX() - minPos.getX() + 1);
        double y2 = y1 + (maxPos.getY() - minPos.getY() + 1);
        double z2 = z1 + (maxPos.getZ() - minPos.getZ() + 1);

        return Shapes.box(x1, y1, z1, x2, y2, z2);
    }

    public boolean isDestroying() {
        return isDestroying;
    }

    @Override
    public InteractionResult onUse(Player player, InteractionHand hand, BlockHitResult hit, BlockPos clickedPos) {
        if (controller != null) {
            return controller.onUse(player, hand, hit, clickedPos);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state, HeaterBlockEntity be) {
        if (controller != null && !isDestroying) {
            controller.tick();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controller != null) {
            CompoundTag controllerTag = new CompoundTag();
            controller.save(controllerTag);
            tag.put("Controller", controllerTag);
        }
        tag.putLongArray("Parts", partPositions.stream().mapToLong(BlockPos::asLong).toArray());
        if (minPos != null) tag.putLong("MinPos", minPos.asLong());
        if (maxPos != null) tag.putLong("MaxPos", maxPos.asLong());
        if (controllerPos != null) tag.putLong("ControllerPos", controllerPos.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Controller") && level != null) {
            // Восстанавливаем origin из сохранённых данных
            BlockPos savedOrigin = tag.contains("MinPos") ? BlockPos.of(tag.getLong("MinPos")) : worldPosition;
            controller = new HeaterMultiblock(level, savedOrigin);
            controller.load(tag.getCompound("Controller"));
        }
        partPositions.clear();
        for (long l : tag.getLongArray("Parts")) {
            partPositions.add(BlockPos.of(l));
        }
        if (tag.contains("MinPos")) minPos = BlockPos.of(tag.getLong("MinPos"));
        if (tag.contains("MaxPos")) maxPos = BlockPos.of(tag.getLong("MaxPos"));
        if (tag.contains("ControllerPos")) controllerPos = BlockPos.of(tag.getLong("ControllerPos"));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }
}