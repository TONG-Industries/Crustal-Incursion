package com.cim.block.entity.industrial;

import com.cim.block.entity.ModBlockEntities;
import com.cim.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class MillstoneBlockEntity extends BlockEntity {

    public static final int GRIND_COOLDOWN = 20; // 1 секунда = 20 тиков
    public static final float ROTATION_SPEED = 360.0f / GRIND_COOLDOWN; // Градусов за тик

    public static final Map<Item, GrindRecipe> RECIPES = new HashMap<>();

    static {
        RECIPES.put(ModItems.LIMESTONE_CHUNK.get(), new GrindRecipe(
                ModItems.LIMESTONE_POWDER.get(), 4, 1
        ));
        RECIPES.put(ModItems.BAUXITE_CHUNK.get(), new GrindRecipe(
                ModItems.BAUXITE_POWDER.get(), 6, 1
        ));
        RECIPES.put(ModItems.DOLOMITE_CHUNK.get(), new GrindRecipe(
                ModItems.DOLOMITE_POWDER.get(), 5, 1
        ));
    }

    private final ItemStackHandler itemHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    };

    private int currentGrinds = 0;
    private int requiredGrinds = 0;
    private boolean isProcessing = false;

    // Кулдаун и вращение
    private int cooldownTicks = 0;
    private float rotationAngle = 0.0f;
    private boolean isGrinding = false; // Активно ли вращение прямо сейчас

    public MillstoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MILLSTONE.get(), pos, state);
    }

    // === Геттеры ===
    public ItemStack getInputStack() { return itemHandler.getStackInSlot(0); }
    public ItemStack getResultStack() { return itemHandler.getStackInSlot(1); }
    public boolean isProcessing() { return isProcessing; }
    public int getCurrentGrinds() { return currentGrinds; }
    public int getRequiredGrinds() { return requiredGrinds; }
    public int getRemainingGrinds() { return Math.max(0, requiredGrinds - currentGrinds); }
    public boolean canGrind() { return isProcessing && getRemainingGrinds() > 0 && cooldownTicks <= 0; }

    // === Для рендера ===
    public float getRotationAngle() { return rotationAngle; }
    public boolean isGrinding() { return isGrinding; }
    public int getCooldownProgress() {
        return cooldownTicks > 0 ? (GRIND_COOLDOWN - cooldownTicks) : GRIND_COOLDOWN;
    }

    public int getComparatorSignal() {
        if (!itemHandler.getStackInSlot(1).isEmpty()) return 15;
        if (isProcessing && requiredGrinds > 0) {
            return 1 + (int)((14.0f * currentGrinds) / requiredGrinds);
        }
        return 0;
    }

    // === Логика ===
    public ItemStack insertItem(ItemStack stack) {
        GrindRecipe recipe = RECIPES.get(stack.getItem());
        if (recipe == null) return stack;
        if (!itemHandler.getStackInSlot(0).isEmpty() || !itemHandler.getStackInSlot(1).isEmpty()) {
            return stack;
        }

        ItemStack single = stack.split(1);
        itemHandler.setStackInSlot(0, single);

        currentGrinds = 0;
        requiredGrinds = recipe.grindsRequired;
        isProcessing = true;
        cooldownTicks = 0;
        rotationAngle = 0;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        return stack;
    }

    public boolean doGrind() {
        if (!canGrind()) return false;

        // Запускаем кулдаун и вращение
        cooldownTicks = GRIND_COOLDOWN;
        isGrinding = true;

        currentGrinds++;

        if (currentGrinds >= requiredGrinds) {
            finishGrind();
        }

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    private void finishGrind() {
        ItemStack input = itemHandler.getStackInSlot(0);
        GrindRecipe recipe = RECIPES.get(input.getItem());

        if (recipe != null) {
            itemHandler.setStackInSlot(0, ItemStack.EMPTY);
            itemHandler.setStackInSlot(1, new ItemStack(recipe.output, 1));
        }

        isProcessing = false;
        isGrinding = false;
        currentGrinds = 0;
        requiredGrinds = 0;
        cooldownTicks = 0;
    }

    public ItemStack extractResult() {
        ItemStack result = itemHandler.getStackInSlot(1);
        if (!result.isEmpty()) {
            itemHandler.setStackInSlot(1, ItemStack.EMPTY);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return result;
        }
        return ItemStack.EMPTY;
    }

    // === Тик ===
    public static void tick(Level level, BlockPos pos, BlockState state, MillstoneBlockEntity be) {
        if (be.cooldownTicks > 0) {
            be.cooldownTicks--;

            // Вращаем пока есть кулдаун
            if (be.isGrinding) {
                be.rotationAngle += ROTATION_SPEED;
                if (be.rotationAngle >= 360.0f) {
                    be.rotationAngle -= 360.0f;
                }
            }

            // Конец вращения
            if (be.cooldownTicks <= 0) {
                be.isGrinding = false;
                be.rotationAngle = 0; // Сброс в начальное положение или оставить как есть?
                // be.rotationAngle = 0; // Раскомментируй если хочешь сброс
            }

            be.setChanged();
            // Не шлём обновление блока каждый тик для плавности - рендер сам интерполирует
        }
    }

    // === Сериализация ===
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        currentGrinds = tag.getInt("CurrentGrinds");
        requiredGrinds = tag.getInt("RequiredGrinds");
        isProcessing = tag.getBoolean("IsProcessing");
        cooldownTicks = tag.getInt("CooldownTicks");
        rotationAngle = tag.getFloat("RotationAngle");
        isGrinding = tag.getBoolean("IsGrinding");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
        tag.putInt("CurrentGrinds", currentGrinds);
        tag.putInt("RequiredGrinds", requiredGrinds);
        tag.putBoolean("IsProcessing", isProcessing);
        tag.putInt("CooldownTicks", cooldownTicks);
        tag.putFloat("RotationAngle", rotationAngle);
        tag.putBoolean("IsGrinding", isGrinding);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // === Капабилити ===
    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
    }

    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> itemHandler);

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return lazyItemHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    public void dropContents() {
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), itemHandler.getStackInSlot(0));
        Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), itemHandler.getStackInSlot(1));
    }

    public record GrindRecipe(Item output, int grindsRequired, int outputCount) {}
}