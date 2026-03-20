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

    public static final int TOTAL_SLOTS = 17;
    public static final int FILL_IN_START = 0, FILL_IN_END = 4;
    public static final int FILL_OUT_START = 4, FILL_OUT_END = 8;
    public static final int DRAIN_OUT_START = 8, DRAIN_OUT_END = 12;
    public static final int DRAIN_IN_START = 12, DRAIN_IN_END = 16;

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
    public final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) { setChanged(); }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            // Разрешаем класть вручную только во входные слоты (и в слот защитника)
            if (slot >= FILL_IN_START && slot < FILL_IN_END) {
                return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
            }
            if (slot >= DRAIN_IN_START && slot < DRAIN_IN_END) {
                return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
            }
            if (slot == 16) return true; // Можно добавить строгую проверку на предметы-защитники

            return false; // В аутпуты руками класть нельзя
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            super.deserializeNBT(nbt);
            // Защита от крашей при загрузке старых сохранений.
            // Если Forge сжал инвентарь из-за старого NBT, восстанавливаем правильный размер!
            if (this.getSlots() != TOTAL_SLOTS) {
                this.setSize(TOTAL_SLOTS);
            }
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
        // 1. Опустошение предметов (Слоты 12-15 -> переходят в 8-11)
        for (int i = DRAIN_IN_START; i < DRAIN_IN_END; i++) {
            ItemStack inStack = itemHandler.getStackInSlot(i);
            if (inStack.isEmpty()) continue;

            // Пытаемся вылить жидкость ИЗ предмета В бочку
            var result = FluidUtil.tryEmptyContainer(inStack, fluidTank, fluidTank.getSpace(), null, true);
            if (result.isSuccess()) {
                ItemStack emptyOut = result.getResult();
                // Ищем свободный выходной слот
                if (insertToOutput(DRAIN_OUT_START, DRAIN_OUT_END, emptyOut)) {
                    inStack.shrink(1); // Уничтожаем предмет во входе, если успешно переложили пустой
                }
            }
        }

        // 2. Наполнение предметов (Слоты 0-3 -> переходят в 4-7)
        if (fluidTank.getFluidAmount() > 0) {
            for (int i = FILL_IN_START; i < FILL_IN_END; i++) {
                ItemStack inStack = itemHandler.getStackInSlot(i);
                if (inStack.isEmpty()) continue;

                // Пытаемся залить жидкость ИЗ бочки В предмет
                var result = FluidUtil.tryFillContainer(inStack, fluidTank, fluidTank.getFluidAmount(), null, true);
                if (result.isSuccess()) {
                    ItemStack fullOut = result.getResult();
                    // Ищем свободный выходной слот
                    if (insertToOutput(FILL_OUT_START, FILL_OUT_END, fullOut)) {
                        inStack.shrink(1); // Уничтожаем предмет во входе, если успешно переложили полный
                    }
                }
            }
        }
    }

    private boolean insertToOutput(int startSlot, int endSlot, ItemStack stackToInsert) {
        if (stackToInsert.isEmpty()) return true; // Ведро пропало (например, одноразовое), считаем успехом

        // Сначала ищем слот с таким же предметом, чтобы застакать
        for (int i = startSlot; i < endSlot; i++) {
            ItemStack existing = itemHandler.getStackInSlot(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, stackToInsert)) {
                if (existing.getCount() + stackToInsert.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(stackToInsert.getCount());
                    return true;
                }
            }
        }

        // Если не нашли куда стакнуть, ищем первый пустой слот
        for (int i = startSlot; i < endSlot; i++) {
            ItemStack existing = itemHandler.getStackInSlot(i);
            if (existing.isEmpty()) {
                itemHandler.setStackInSlot(i, stackToInsert.copy());
                return true;
            }
        }

        return false; // Нет места
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