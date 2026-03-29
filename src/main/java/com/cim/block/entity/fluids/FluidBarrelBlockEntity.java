package com.cim.block.entity.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.cim.menu.FluidBarrelMenu;
import com.cim.block.entity.ModBlockEntities;

public class FluidBarrelBlockEntity extends BlockEntity implements MenuProvider {

    // ПРОПУСКНАЯ СПОСОБНОСТЬ СЕТИ: 1000 mB/tick (1 ведро в тик = 20 ведер в секунду)
    public static final int MAX_TRANSFER_RATE = 200;

    // 0 = BOTH, 1 = INPUT, 2 = OUTPUT, 3 = DISABLED
    public int mode = 0;

    public static final int TOTAL_SLOTS = 17;
    public static final int FILL_IN_START = 0, FILL_IN_END = 4;
    public static final int FILL_OUT_START = 4, FILL_OUT_END = 8;
    public static final int DRAIN_OUT_START = 8, DRAIN_OUT_END = 12;
    public static final int DRAIN_IN_START = 12, DRAIN_IN_END = 16;

    public String fluidFilter = "none";

    // Физическое хранилище жидкости
    public final FluidTank fluidTank = new FluidTank(16000) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            // === НОВАЯ СТРОКА: БЛОКИРУЕМ БОЧКУ, ПОКА НЕ ТКНУЛИ ИДЕНТИФИКАТОРОМ ===
            if (fluidFilter.equals("none")) {
                return false;
            }

            if (!fluidFilter.equals("none")) {
                ResourceLocation stackLoc = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
                if (stackLoc != null && !stackLoc.toString().equals(fluidFilter)) {
                    return false;
                }
            }
            return super.isFluidValid(stack);
        }
    };

    // ==========================================
    // СЕТЕВАЯ ОБЕРТКА (Лимиты + Режимы)
    // ==========================================
    private final IFluidHandler networkFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() { return fluidTank.getTanks(); }
        @Override
        public @NotNull FluidStack getFluidInTank(int tank) { return fluidTank.getFluidInTank(tank); }
        @Override
        public int getTankCapacity(int tank) { return fluidTank.getTankCapacity(tank); }
        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return fluidTank.isFluidValid(tank, stack); }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (mode == 2 || mode == 3) return 0; // В режиме OUTPUT или DISABLED нельзя принимать жидкость из трубы

            FluidStack toFill = resource.copy();
            toFill.setAmount(Math.min(toFill.getAmount(), MAX_TRANSFER_RATE));
            return fluidTank.fill(toFill, action);
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            if (mode == 1 || mode == 3) return FluidStack.EMPTY; // В режиме INPUT или DISABLED нельзя отдавать жидкость

            int maxDrain = Math.min(resource.getAmount(), MAX_TRANSFER_RATE);
            FluidStack toDrain = resource.copy();
            toDrain.setAmount(maxDrain);
            return fluidTank.drain(toDrain, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            if (mode == 1 || mode == 3) return FluidStack.EMPTY;
            return fluidTank.drain(Math.min(maxDrain, MAX_TRANSFER_RATE), action);
        }
    };

    public final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) { setChanged(); }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot >= FILL_IN_START && slot < FILL_IN_END) return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
            if (slot >= DRAIN_IN_START && slot < DRAIN_IN_END) {
                if (stack.getItem() instanceof com.cim.item.tools.InfiniteFluidBarrelItem) return true;
                return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
            }
            if (slot == 16) return true;
            return false;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            super.deserializeNBT(nbt);
            if (this.getSlots() != TOTAL_SLOTS) this.setSize(TOTAL_SLOTS);
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
        for (int i = DRAIN_IN_START; i < DRAIN_IN_END; i++) {
            ItemStack inStack = itemHandler.getStackInSlot(i);
            if (inStack.isEmpty()) continue;

            if (inStack.getItem() instanceof com.cim.item.tools.InfiniteFluidBarrelItem) {
                if (!this.fluidFilter.equals("none")) {
                    net.minecraft.world.level.material.Fluid filterFluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(this.fluidFilter));
                    if (filterFluid != null && filterFluid != net.minecraft.world.level.material.Fluids.EMPTY) {
                        int space = fluidTank.getSpace();
                        if (space > 0) {
                            fluidTank.fill(new FluidStack(filterFluid, space), IFluidHandler.FluidAction.EXECUTE);
                        }
                    }
                }
                continue;
            }

            var result = FluidUtil.tryEmptyContainer(inStack, fluidTank, fluidTank.getSpace(), null, true);
            if (result.isSuccess()) {
                ItemStack emptyOut = result.getResult();
                if (insertToOutput(DRAIN_OUT_START, DRAIN_OUT_END, emptyOut)) inStack.shrink(1);
            }
        }

        if (fluidTank.getFluidAmount() > 0) {
            for (int i = FILL_IN_START; i < FILL_IN_END; i++) {
                ItemStack inStack = itemHandler.getStackInSlot(i);
                if (inStack.isEmpty()) continue;

                var result = FluidUtil.tryFillContainer(inStack, fluidTank, fluidTank.getFluidAmount(), null, true);
                if (result.isSuccess()) {
                    ItemStack fullOut = result.getResult();
                    if (insertToOutput(FILL_OUT_START, FILL_OUT_END, fullOut)) inStack.shrink(1);
                }
            }
        }
    }

    private boolean insertToOutput(int startSlot, int endSlot, ItemStack stackToInsert) {
        if (stackToInsert.isEmpty()) return true;
        for (int i = startSlot; i < endSlot; i++) {
            ItemStack existing = itemHandler.getStackInSlot(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stackToInsert)) {
                if (existing.getCount() + stackToInsert.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(stackToInsert.getCount());
                    return true;
                }
            }
        }
        for (int i = startSlot; i < endSlot; i++) {
            ItemStack existing = itemHandler.getStackInSlot(i);
            if (existing.isEmpty()) {
                itemHandler.setStackInSlot(i, stackToInsert.copy());
                return true;
            }
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

            // === ВОЛШЕБНЫЙ ПИНОК СЕТИ ===
            // Заставляем бочку переподключиться к трубам с новыми правилами
            com.cim.api.fluids.FluidNetworkManager manager = com.cim.api.fluids.FluidNetworkManager.get((net.minecraft.server.level.ServerLevel) level);
            manager.removeNode(this.getBlockPos());
            manager.addNode(this.getBlockPos());
        }
    }

    public void changeMode() {
        this.mode = (this.mode + 1) % 4;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    // ==========================================
    // ЖИЗНЕННЫЙ ЦИКЛ БОЧКИ В СЕТИ
    // ==========================================
    @Override
    public void onLoad() {
        super.onLoad();
        // Используем нашу защитную обертку!
        lazyFluidHandler = LazyOptional.of(() -> networkFluidHandler);
        lazyItemHandler = LazyOptional.of(() -> itemHandler);

        // Интегрируем бочку в жидкостную сеть при установке
        if (level != null && !level.isClientSide) {
            com.cim.api.fluids.FluidNetworkManager.get((ServerLevel) level).addNode(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Удаляем бочку из жидкостной сети при разрушении
        if (this.level != null && !this.level.isClientSide) {
            com.cim.api.fluids.FluidNetworkManager.get((ServerLevel) this.level).removeNode(this.getBlockPos());
        }
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
        if (tag.contains("FluidFilter")) this.fluidFilter = tag.getString("FluidFilter");
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        fluidTank.writeToNBT(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
        tag.putInt("Mode", mode);
        tag.putString("FluidFilter", this.fluidFilter);
    }

    @Override
    public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

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