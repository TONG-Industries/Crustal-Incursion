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
import org.jetbrains.annotations.Nullable;

import com.cim.menu.FluidBarrelMenu;
import com.cim.block.entity.ModBlockEntities;
import com.cim.item.ModItems; // для защитников

public class FluidBarrelBlockEntity extends BlockEntity implements MenuProvider {

    // 0 = BOTH, 1 = INPUT, 2 = OUTPUT, 3 = DISABLED
    public int mode = 0;

    public String fluidFilter = "none";

    // Хранилище жидкости (16 ведёр = 16000 mB)
    public final FluidTank fluidTank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        // Проверка фильтра
        @Override
        public boolean isFluidValid(FluidStack stack) {
            if (!fluidFilter.equals("none")) {
                ResourceLocation stackLoc = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                if (stackLoc != null && !stackLoc.toString().equals(fluidFilter)) {
                    return false;
                }
            }
            return super.isFluidValid(stack);
        }
    };

    // 0: Full in, 1: Empty out | 2: Empty in, 3: Full out | 4: Protector
    public final ItemStackHandler itemHandler = new ItemStackHandler(5) { // увеличено до 5
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == 4) {
                // Слот для защитника: только PROTECTOR_STEEL, LEAD, TUNGSTEN
                return stack.getItem() == ModItems.PROTECTOR_STEEL.get() ||
                        stack.getItem() == ModItems.PROTECTOR_LEAD.get() ||
                        stack.getItem() == ModItems.PROTECTOR_TUNGSTEN.get();
            }
            // Для слотов вёдер (0 и 2) требуется наличие жидкости в предмете
            if (slot == 0 || slot == 2) {
                return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
            }
            return false; // слоты 1 и 3 автоматически не принимают предметы (только вывод)
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
        super(ModBlockEntities.FLUID_BARREL_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FluidBarrelBlockEntity be) {
        if (level.isClientSide) return;
        be.processBuckets();
    }

    private void processBuckets() {
        // Если режим отключён — ничего не делаем
        if (mode == 3) return;

        // 1. Опустошение полного ведра (Слот 0 -> Слот 1) — разрешено в режимах BOTH и INPUT
        if (mode == 0 || mode == 1) {
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
        }

        // 2. Наполнение пустого ведра (Слот 2 -> Слот 3) — разрешено в режимах BOTH и OUTPUT
        if (mode == 0 || mode == 2) {
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
                fluidTank.setFluid(FluidStack.EMPTY);
            }
        }

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void changeMode() {
        this.mode = (this.mode + 1) % 4;
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

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
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