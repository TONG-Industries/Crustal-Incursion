package com.cim.multiblock.industrial;

import com.cim.block.entity.ModBlockEntities;
import com.cim.menu.SmelterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
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
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmelterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_TEMP = 1600;
    public static final int TANK_CAPACITY = 4000; // 4 слитка по 1000мб или 36 слитков по 111мб

    // 8 слотов: 0-3 верх (рецепты), 4-7 низ (обычная)
    private final ItemStackHandler inventory = new ItemStackHandler(8) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                recalculateSmelting();
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return getSmeltingResult(stack) != null;
        }
    };

    // Жидкостной буфер: Fluid -> amount (mB)
    private final Map<Fluid, Integer> fluidTank = new HashMap<>();
    private int totalFluidAmount = 0;

    // Тепло
    private int temperature = 0;

    // Прогресс плавки (0-10000 для точности) для 2 рядов
    private final int[] smeltProgress = new int[2];
    private final int[] smeltMaxProgress = new int[2]; // максимальное тепло для текущей партии
    private final boolean[] isSmelting = new boolean[2];

    // Данные для GUI: [temp, progressTop(0-100), progressBot(0-100), isSmeltingTop(0/1), isSmeltingBot(0/1)]
    private final ContainerData data = new SimpleContainerData(5);

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmelterBlockEntity be) {
        // === ТЕПЛО ===
        int baseCooling = (be.temperature * be.temperature) / 512000;
        if (baseCooling == 0 && be.temperature > 0) baseCooling = 1;

        int thermalNoise = (be.temperature > 200 && baseCooling > 1) ? level.random.nextInt(5) - 2 : 0;
        int cooling = Math.max(1, baseCooling + thermalNoise);

        // Перенос тепла от нагревателя снизу
        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof HeaterBlockEntity heater && heater.getTemperature() > be.temperature) {
            int transfer = (heater.getTemperature() - be.temperature) / 10 + 1;
            be.temperature = Math.min(MAX_TEMP, be.temperature + transfer);
        } else if (be.temperature > 0) {
            be.temperature = Math.max(0, be.temperature - cooling);
        }

        // === ПЛАВКА ===
        be.tickRow(0, 0, 4); // верхний ряд
        be.tickRow(1, 4, 8); // нижний ряд

        // === СИНХРОНИЗАЦИЯ ДАННЫХ ===
        be.data.set(0, be.temperature);
        be.data.set(1, be.smeltProgress[0] / 100); // 0-100%
        be.data.set(2, be.smeltProgress[1] / 100);
        be.data.set(3, be.isSmelting[0] ? 1 : 0);
        be.data.set(4, be.isSmelting[1] ? 1 : 0);

        if (be.isSmelting[0] || be.isSmelting[1] || be.temperature > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void tickRow(int rowIndex, int startSlot, int endSlot) {
        // Проверяем, не полный ли бак
        if (totalFluidAmount >= TANK_CAPACITY) {
            isSmelting[rowIndex] = false;
            return;
        }

        // Считаем требования для ряда
        int totalHeatRequired = 0;
        int itemCount = 0;
        boolean canSmelt = false;

        for (int i = startSlot; i < endSlot; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                SmeltData data = getSmeltingResult(stack);
                if (data != null) {
                    totalHeatRequired += data.heatRequired;
                    itemCount++;
                    canSmelt = true;
                }
            }
        }

        if (!canSmelt) {
            smeltProgress[rowIndex] = 0;
            smeltMaxProgress[rowIndex] = 0;
            isSmelting[rowIndex] = false;
            return;
        }

        isSmelting[rowIndex] = true;

        // Если только начали - устанавливаем максимум
        if (smeltMaxProgress[rowIndex] == 0) {
            smeltMaxProgress[rowIndex] = totalHeatRequired;
        }

        // Плавка: тратим тепло на процесс
        // Эффективность: чем выше температура, тем быстрее плавим
        if (temperature > 100) { // минимальная температура для плавки
            int heatPerTick = Math.min(temperature / 20 + 1, 20); // 1-20 тепла в тик
            smeltProgress[rowIndex] += heatPerTick;

            // Тепло "расходуется" на плавку (уходит в материал)
            temperature = Math.max(0, temperature - (heatPerTick / 4)); // 25% тепла теряется в процессе

            // Готово?
            if (smeltProgress[rowIndex] >= smeltMaxProgress[rowIndex]) {
                completeSmelting(rowIndex, startSlot, endSlot);
                smeltProgress[rowIndex] = 0;
                smeltMaxProgress[rowIndex] = 0;
            }
        }
    }

    private void completeSmelting(int rowIndex, int startSlot, int endSlot) {
        for (int i = startSlot; i < endSlot; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                SmeltData data = getSmeltingResult(stack);
                if (data != null) {
                    // Добавляем жидкость
                    fluidTank.merge(data.fluid, data.outputMb, Integer::sum);
                    totalFluidAmount += data.outputMb;
                    // Уменьшаем предмет
                    stack.shrink(1);
                }
            }
        }
        setChanged();
    }

    // Рецепты (временно хардкод, заменишь на нормальную систему рецептов)
    @Nullable
    public static SmeltData getSmeltingResult(ItemStack stack) {
        // Железо: 1536мб (9 слитков = 1 блок?), давай сделаем 1 слиток = 111мб
        if (stack.is(Items.IRON_ORE) || stack.is(Items.RAW_IRON)) {
            return new SmeltData(Fluids.LAVA, 111, 300, 600); // тестовая жидкость, заменишь на molten_iron
        }
        if (stack.is(Items.COPPER_ORE) || stack.is(Items.RAW_COPPER)) {
            return new SmeltData(Fluids.WATER, 111, 200, 400); // тест
        }
        if (stack.is(Items.GOLD_ORE) || stack.is(Items.RAW_GOLD)) {
            return new SmeltData(Fluids.LAVA, 111, 250, 500);
        }
        return null;
    }

    public static class SmeltData {
        public final Fluid fluid;
        public final int outputMb;     // сколько мб выходит
        public final int heatPerTick;  // минимальное тепло в тик (не используется пока)
        public final int heatRequired; // общее тепло для полной плавки

        public SmeltData(Fluid fluid, int outputMb, int heatPerTick, int heatRequired) {
            this.fluid = fluid;
            this.outputMb = outputMb;
            this.heatPerTick = heatPerTick;
            this.heatRequired = heatRequired;
        }
    }

    private void recalculateSmelting() {
        // Сброс прогресса если предметы изменились (опционально)
    }

    // Геттеры для GUI
    public ItemStackHandler getInventory() { return inventory; }
    public ContainerData getData() { return data; }
    public int getTemperature() { return temperature; }

    public List<FluidStack> getFluidStacks() {
        List<FluidStack> list = new ArrayList<>();
        fluidTank.forEach((fluid, amount) -> {
            if (amount > 0) list.add(new FluidStack(fluid, amount));
        });
        return list;
    }

    // === СЕРИАЛИЗАЦИЯ ===

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Temperature", temperature);
        tag.putIntArray("Progress", smeltProgress);
        tag.putIntArray("MaxProgress", smeltMaxProgress);

        // Жидкости
        ListTag fluids = new ListTag();
        fluidTank.forEach((fluid, amount) -> {
            if (amount > 0 && fluid != Fluids.EMPTY) {
                CompoundTag ft = new CompoundTag();
                ft.putString("Fluid", ForgeRegistries.FLUIDS.getKey(fluid).toString());
                ft.putInt("Amount", amount);
                fluids.add(ft);
            }
        });
        tag.put("Fluids", fluids);
        tag.putInt("TotalFluid", totalFluidAmount);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        temperature = tag.getInt("Temperature");

        int[] prog = tag.getIntArray("Progress");
        if (prog.length == 2) System.arraycopy(prog, 0, smeltProgress, 0, 2);
        int[] max = tag.getIntArray("MaxProgress");
        if (max.length == 2) System.arraycopy(max, 0, smeltMaxProgress, 0, 2);

        // Жидкости
        fluidTank.clear();
        totalFluidAmount = 0;
        ListTag fluids = tag.getList("Fluids", Tag.TAG_COMPOUND);
        for (int i = 0; i < fluids.size(); i++) {
            CompoundTag ft = fluids.getCompound(i);
            Fluid f = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(ft.getString("Fluid")));
            int amt = ft.getInt("Amount");
            if (f != null && f != Fluids.EMPTY) {
                fluidTank.put(f, amt);
                totalFluidAmount += amt;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        load(tag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cim.smelter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SmelterMenu(id, inv, this, data);
    }
}