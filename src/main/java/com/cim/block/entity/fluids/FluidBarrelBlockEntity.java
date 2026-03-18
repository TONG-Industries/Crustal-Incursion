package com.cim.block.entity.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import com.cim.menu.FluidBarrelMenu;
import com.cim.block.entity.ModBlockEntities;

public class FluidBarrelBlockEntity extends BlockEntity implements MenuProvider {

    // 0 = BOTH, 1 = INPUT, 2 = OUTPUT, 3 = DISABLED
    public int mode = 0;

    public String fluidFilter = "none";

    // Хранилище жидкости (например, 16000 mB = 16 ведер)
    public final FluidTank fluidTank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        // Запрещаем заливать жидкость, если она не совпадает с фильтром
        @Override
        public boolean isFluidValid(FluidStack stack) {
            if (!fluidFilter.equals("none")) {
                ResourceLocation stackLoc = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                if (stackLoc != null && !stackLoc.toString().equals(fluidFilter)) {
                    return false; // Отклоняем чужую жидкость!
                }
            }
            return super.isFluidValid(stack);
        }
    };

    // 0: Full in, 1: Empty out | 2: Empty in, 3: Full out
    public final ItemStackHandler itemHandler = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) { setChanged(); }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 0 || slot == 2) {
                return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
            }
            return false;
        }
    };

    private LazyOptional<IFluidHandler> lazyFluidHandler = LazyOptional.empty();
    private LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.empty();

    protected final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return mode; }
        @Override public void set(int index, int value) { mode = value; }
        @Override public int getCount() { return 1; }
    };

    public FluidBarrelBlockEntity(BlockPos pos, BlockState state) {
        // Убедись, что зарегистрировал FLUID_BARREL_BE в ModBlockEntities
        super(ModBlockEntities.FLUID_BARREL_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidBarrelBlockEntity be) {
        if (level.isClientSide) return;
        be.processBuckets();
    }

    private void processBuckets() {
        // 1. Опустошение полного ведра (Слот 0 -> Слот 1)
        ItemStack fullIn = itemHandler.getStackInSlot(0);
        if (!fullIn.isEmpty()) {
            var result = FluidUtil.tryEmptyContainer(fullIn, fluidTank, fluidTank.getSpace(), null, true);
            if (result.isSuccess()) {
                ItemStack emptyOut = result.getResult();
                if (insertToOutput(1, emptyOut)) {
                    fullIn.shrink(1);
                }
            }
        }

        // 2. Наполнение пустого ведра (Слот 2 -> Слот 3)
        ItemStack emptyIn = itemHandler.getStackInSlot(2);
        if (!emptyIn.isEmpty() && fluidTank.getFluidAmount() > 0) {
            var result = FluidUtil.tryFillContainer(emptyIn, fluidTank, fluidTank.getFluidAmount(), null, true);
            if (result.isSuccess()) {
                ItemStack fullOut = result.getResult();
                if (insertToOutput(3, fullOut)) {
                    emptyIn.shrink(1);
                }
            }
        }
    }

    private boolean insertToOutput(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        ItemStack existing = itemHandler.getStackInSlot(slot);
        if (existing.isEmpty()) {
            itemHandler.setStackInSlot(slot, stack);
            return true;
        }
        if (ItemHandlerHelper.canItemStacksStack(existing, stack) && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
            existing.grow(stack.getCount());
            return true;
        }
        return false;
    }

    public void setFilter(String newFilter) {
        this.fluidFilter = newFilter;

        // Если фильтр изменился и в бочке есть чужая жидкость — уничтожаем её
        if (!newFilter.equals("none") && !fluidTank.isEmpty()) {
            ResourceLocation currentFluidLoc = ForgeRegistries.FLUIDS.getKey(fluidTank.getFluid().getFluid());
            if (currentFluidLoc != null && !currentFluidLoc.toString().equals(newFilter)) {
                fluidTank.setFluid(FluidStack.EMPTY); // Сливаем в никуда
            }
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        lazyFluidHandler = LazyOptional.of(() -> fluidTank);
        lazyItemHandler = LazyOptional.of(() -> itemHandler);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyFluidHandler.invalidate();
        lazyItemHandler.invalidate();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) return lazyFluidHandler.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return lazyItemHandler.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fluidTank.readFromNBT(tag);
        itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        mode = tag.getInt("Mode");
        if (tag.contains("FluidFilter")) {
            this.fluidFilter = tag.getString("FluidFilter");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        fluidTank.writeToNBT(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
        tag.putInt("Mode", mode);
        tag.putString("FluidFilter", this.fluidFilter);
    }

    public void changeMode() {
        this.mode = (this.mode + 1) % 4; // Переключаем 0-1-2-3
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FluidBarrelMenu(id, inv, this, this.data);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cim.fluid_barrel");
    }
}