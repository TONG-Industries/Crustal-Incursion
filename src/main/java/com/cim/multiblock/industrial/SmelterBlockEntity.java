package com.cim.multiblock.industrial;

import com.cim.api.metal.Metal;
import com.cim.api.metal.MetallurgyRegistry;
import com.cim.api.metal.MetalUnits;
import com.cim.api.metal.recipe.SmeltingRecipe;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.*;

public class SmelterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_TEMP = 1600;
    public static final int BLOCK_CAPACITY = 4;
    public static final int TANK_CAPACITY = BLOCK_CAPACITY * MetalUnits.MB_PER_BLOCK;

    private final ItemStackHandler inventory = new ItemStackHandler(8) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot < 4) {
                return true;
            } else {
                return MetallurgyRegistry.findSimpleRecipe(stack.getItem()) != null;
            }
        }
    };

    private final Map<Metal, Integer> metalTank = new LinkedHashMap<>();
    private int totalMetalAmount = 0;
    private int temperature = 0;
    private int lastInventoryHash = 0;
    private final int[] smeltProgress = new int[2];
    private final int[] smeltMaxProgress = new int[2];
    private final boolean[] isSmelting = new boolean[2];
    private final int[] currentHeatConsumption = new int[2];

    // Требуемые температуры для отображения в GUI
    private int requiredTempTop = 0;
    private int requiredTempBottom = 0;

    private SmeltingRecipe currentTopRecipe = null;
    private MetallurgyRegistry.SimpleSmeltingRecipe currentBottomRecipe = null;

    private final ContainerData data = new SimpleContainerData(11);

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmelterBlockEntity be) {
        // Проверяем изменения инвентаря и сбрасываем плавку если нужно
        int currentHash = be.calculateInventoryHash();
        if (currentHash != be.lastInventoryHash) {
            be.lastInventoryHash = currentHash;
            be.resetSmeltingIfChanged();
        }

        // Тепло
        int baseCooling = (be.temperature * be.temperature) / 512000;
        if (baseCooling == 0 && be.temperature > 0) baseCooling = 1;

        int thermalNoise = (be.temperature > 200 && baseCooling > 1) ? level.random.nextInt(5) - 2 : 0;
        int cooling = Math.max(1, baseCooling + thermalNoise);

        BlockEntity below = level.getBlockEntity(pos.below());
        if (below instanceof HeaterBlockEntity heater && heater.getTemperature() > be.temperature) {
            int transfer = (heater.getTemperature() - be.temperature) / 10 + 1;
            be.temperature = Math.min(MAX_TEMP, be.temperature + transfer);
        } else if (be.temperature > 0) {
            be.temperature = Math.max(0, be.temperature - cooling);
        }

        // Плавка
        be.tickTopRow();
        be.tickBottomRow();

        // Синхронизация данных для GUI
        be.data.set(0, be.temperature);
        be.data.set(1, be.smeltProgress[0]);
        be.data.set(2, be.smeltMaxProgress[0]);
        be.data.set(3, be.smeltProgress[1]);
        be.data.set(4, be.smeltMaxProgress[1]);
        be.data.set(5, be.isSmelting[0] ? 1 : 0);
        be.data.set(6, be.isSmelting[1] ? 1 : 0);
        be.data.set(7, be.currentTopRecipe != null ? 1 : 0);
        be.data.set(8, be.currentBottomRecipe != null ? 1 : 0);
        be.data.set(9, be.requiredTempTop);
        be.data.set(10, be.requiredTempBottom);

        if (be.isSmelting[0] || be.isSmelting[1] || be.temperature > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // Хеш инвентаря для отслеживания изменений
    private int calculateInventoryHash() {
        int hash = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            hash = hash * 31 + (stack.isEmpty() ? 0 : stack.getItem().hashCode() + stack.getCount());
        }
        return hash;
    }

    // Сброс плавки при изменении инвентаря
    private void resetSmeltingIfChanged() {
        // Сбрасываем верхний ряд
        if (isSmelting[0] || currentTopRecipe != null) {
            smeltProgress[0] = 0;
            smeltMaxProgress[0] = 0;
            isSmelting[0] = false;
            currentTopRecipe = null;
            requiredTempTop = 0;
        }

        // Сбрасываем нижний ряд
        if (isSmelting[1]) {
            smeltProgress[1] = 0;
            smeltMaxProgress[1] = 0;
            isSmelting[1] = false;
            currentBottomRecipe = null;
            requiredTempBottom = 0;
        }
    }




    private void completeBottomRecipes(Map<MetallurgyRegistry.SimpleSmeltingRecipe, Integer> counts) {
        for (var entry : counts.entrySet()) {
            var recipe = entry.getKey();
            int needed = entry.getValue(); // Всегда 1 или сколько слотов заполнено

            // Плавим ровно 1 предмет из каждого слота где есть этот предмет
            for (int i = 4; i < 8 && needed > 0; i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack.getItem() == recipe.input() && !stack.isEmpty()) {
                    // Проверка на переполнение
                    if (totalMetalAmount + recipe.outputMb() > TANK_CAPACITY) {
                        break;
                    }

                    // Удаляем только 1 предмет!
                    stack.shrink(1);
                    needed--;

                    addMetal(recipe.output(), recipe.outputMb());
                }
            }
        }
        setChanged();
    }

    private void tickBottomRow() {
        if (totalMetalAmount >= TANK_CAPACITY) {
            if (requiredTempBottom != 0) requiredTempBottom = 0;
            return;
        }

        Map<MetallurgyRegistry.SimpleSmeltingRecipe, Integer> recipeCounts = new HashMap<>();
        int totalOutput = 0;
        boolean hasAnyItem = false;

        for (int i = 4; i < 8; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                hasAnyItem = true;
                var recipe = MetallurgyRegistry.findSimpleRecipe(stack.getItem());
                if (recipe != null) {
                    recipeCounts.merge(recipe, 1, Integer::sum);
                    totalOutput += recipe.outputMb();
                }
            }
        }

        if (!hasAnyItem || recipeCounts.isEmpty()) {
            if (isSmelting[1] || requiredTempBottom != 0) {
                smeltProgress[1] = 0;
                smeltMaxProgress[1] = 0;
                isSmelting[1] = false;
                currentBottomRecipe = null;
                requiredTempBottom = 0;
            }
            return;
        }

        int maxMinTemp = recipeCounts.keySet().stream()
                .mapToInt(r -> r.minTemp())
                .max()
                .orElse(0);

        requiredTempBottom = maxMinTemp;

        if (temperature < maxMinTemp) {
            isSmelting[1] = false;
            smeltProgress[1] = 0;
            // Показываем макс прогресс для отображения времени
            int totalHeat = recipeCounts.entrySet().stream()
                    .mapToInt(e -> e.getKey().totalHeat() * e.getValue())
                    .sum();
            smeltMaxProgress[1] = totalHeat;
            return;
        }

        if (totalMetalAmount + totalOutput > TANK_CAPACITY) {
            return;
        }

        int totalHeatRequired = 0;
        int totalHeatPerTick = 0;

        for (var entry : recipeCounts.entrySet()) {
            var recipe = entry.getKey();
            int count = entry.getValue();
            totalHeatRequired += recipe.totalHeat() * count;
            totalHeatPerTick += recipe.heatPerTick() * count;
        }

        if (!isSmelting[1]) {
            isSmelting[1] = true;
            smeltProgress[1] = 0;
            smeltMaxProgress[1] = totalHeatRequired;
            currentHeatConsumption[1] = totalHeatPerTick;
        }

        if (smeltMaxProgress[1] > 0) {
            int heatPerTick = Math.min(currentHeatConsumption[1], temperature / 20 + 1);
            heatPerTick = Math.min(heatPerTick, temperature);

            smeltProgress[1] += heatPerTick;
            temperature = Math.max(0, temperature - (heatPerTick / 2));

            if (smeltProgress[1] >= smeltMaxProgress[1]) {
                completeBottomRecipes(recipeCounts);
                smeltProgress[1] = 0;
                smeltMaxProgress[1] = 0;
                isSmelting[1] = false;
                requiredTempBottom = 0;
            }
        }
    }
    private void tickTopRow() {
        if (totalMetalAmount >= TANK_CAPACITY) {
            if (requiredTempTop != 0) requiredTempTop = 0;
            return;
        }

        ItemStack[] inputs = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            inputs[i] = inventory.getStackInSlot(i);
        }

        SmeltingRecipe recipe = MetallurgyRegistry.findAlloyRecipe(inputs);

        if (recipe == null) {
            // Нет рецепта - сбрасываем если было что-то
            if (isSmelting[0] || requiredTempTop != 0) {
                smeltProgress[0] = 0;
                smeltMaxProgress[0] = 0;
                isSmelting[0] = false;
                currentTopRecipe = null;
                requiredTempTop = 0;
            }
            return;
        }

        // Есть рецепт - показываем температуру ВСЕГДА
        requiredTempTop = recipe.getMinTemp();

        if (temperature < recipe.getMinTemp()) {
            // Температура недостаточна - не плавим, но показываем прогрессбар
            isSmelting[0] = false;
            smeltProgress[0] = 0;
            smeltMaxProgress[0] = recipe.getTotalHeat();
            return;
        }

        int potentialAmount = totalMetalAmount + recipe.getOutputMb();
        if (potentialAmount > TANK_CAPACITY) {
            return;
        }

        // Начинаем/продолжаем плавку
        if (!isSmelting[0] || currentTopRecipe != recipe) {
            isSmelting[0] = true;
            currentTopRecipe = recipe;
            smeltProgress[0] = 0;
            smeltMaxProgress[0] = recipe.getTotalHeat();
            currentHeatConsumption[0] = recipe.getHeatPerTick();
        }

        if (smeltMaxProgress[0] > 0) {
            int heatPerTick = Math.min(currentHeatConsumption[0], temperature / 20 + 1);
            heatPerTick = Math.min(heatPerTick, temperature);

            smeltProgress[0] += heatPerTick;
            temperature = Math.max(0, temperature - (heatPerTick / 2));

            if (smeltProgress[0] >= smeltMaxProgress[0]) {
                completeTopRecipe(recipe);
                smeltProgress[0] = 0;
                smeltMaxProgress[0] = 0;
                isSmelting[0] = false;
                currentTopRecipe = null;
                requiredTempTop = 0;
            }
        }
    }


    private void completeTopRecipe(SmeltingRecipe recipe) {
        // Удаляем предметы согласно рецепту (с учетом количества!)
        for (int i = 0; i < 4; i++) {
            SmeltingRecipe.Slot req = recipe.getSlot(i);
            if (!req.isEmpty()) {
                ItemStack current = inventory.getStackInSlot(i);
                current.shrink(req.count()); // Теперь shrink на нужное количество!
            }
        }

        addMetal(recipe.getOutput(), recipe.getOutputMb());
        setChanged();
    }


    private void addMetal(Metal metal, int amount) {
        if (amount <= 0) return;
        if (totalMetalAmount + amount > TANK_CAPACITY) {
            amount = TANK_CAPACITY - totalMetalAmount;
            if (amount <= 0) return;
        }

        metalTank.merge(metal, amount, Integer::sum);
        recalculateTotal();
    }

    private void recalculateTotal() {
        totalMetalAmount = metalTank.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        // Защита от переполнения
        if (totalMetalAmount > TANK_CAPACITY) {
            int excess = totalMetalAmount - TANK_CAPACITY;
            for (var entry : metalTank.entrySet()) {
                if (entry.getValue() >= excess) {
                    entry.setValue(entry.getValue() - excess);
                    break;
                }
            }
            recalculateTotal();
        }
    }


    public ItemStackHandler getInventory() { return inventory; }
    public ContainerData getData() { return data; }
    public int getTemperature() { return temperature; }
    public Map<Metal, Integer> getMetalTank() { return Collections.unmodifiableMap(metalTank); }
    public int getTotalMetalAmount() { return totalMetalAmount; }
    public int getBlockCapacity() { return BLOCK_CAPACITY; }
    public int getRequiredTempTop() { return requiredTempTop; }
    public int getRequiredTempBottom() { return requiredTempBottom; }

    public List<MetalStack> getMetalStacks() {
        List<MetalStack> list = new ArrayList<>();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) list.add(new MetalStack(metal, amount));
        });
        list.sort((a, b) -> Integer.compare(b.amount, a.amount));
        return list;
    }

    public static class MetalStack {
        public final Metal metal;
        public final int amount;

        public MetalStack(Metal metal, int amount) {
            this.metal = metal;
            this.amount = amount;
        }

        public String getFormattedAmount() {
            return MetalUnits.format(amount);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("Temperature", temperature);
        tag.putIntArray("Progress", smeltProgress);
        tag.putIntArray("MaxProgress", smeltMaxProgress);
        tag.putInt("RequiredTempTop", requiredTempTop);
        tag.putInt("RequiredTempBottom", requiredTempBottom);

        if (currentTopRecipe != null) {
            tag.putString("CurrentTopRecipe", currentTopRecipe.getId());
        }

        ListTag metals = new ListTag();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) {
                CompoundTag mt = new CompoundTag();
                mt.putString("Metal", metal.getId().toString());
                mt.putInt("Amount", amount);
                metals.add(mt);
            }
        });
        tag.put("Metals", metals);
        tag.putInt("TotalMetal", totalMetalAmount);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        temperature = tag.getInt("Temperature");
        requiredTempTop = tag.getInt("RequiredTempTop");
        requiredTempBottom = tag.getInt("RequiredTempBottom");

        int[] prog = tag.getIntArray("Progress");
        if (prog.length == 2) System.arraycopy(prog, 0, smeltProgress, 0, 2);
        int[] max = tag.getIntArray("MaxProgress");
        if (max.length == 2) System.arraycopy(max, 0, smeltMaxProgress, 0, 2);

        if (tag.contains("CurrentTopRecipe")) {
            String recipeId = tag.getString("CurrentTopRecipe");
        }

        metalTank.clear();
        ListTag metals = tag.getList("Metals", Tag.TAG_COMPOUND);
        for (int i = 0; i < metals.size(); i++) {
            CompoundTag mt = metals.getCompound(i);
            ResourceLocation id = new ResourceLocation(mt.getString("Metal"));
            int amt = mt.getInt("Amount");
            MetallurgyRegistry.get(id).ifPresent(metal -> metalTank.put(metal, amt));
        }
        recalculateTotal();
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

    // В SmelterBlockEntity.java добавь:

    /**
     * Извлекает металл из буфера (используется каналом отливки)
     * @param metal металл для извлечения
     * @param maxAmount максимальное количество мб
     * @return фактически извлеченное количество мб
     */
    public int extractMetal(Metal metal, int maxAmount) {
        Integer current = metalTank.get(metal);
        if (current == null || current <= 0) return 0;

        int toExtract = Math.min(maxAmount, current);
        if (toExtract <= 0) return 0;

        if (current <= toExtract) {
            metalTank.remove(metal);
        } else {
            metalTank.put(metal, current - toExtract);
        }

        recalculateTotal();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }

        return toExtract;
    }


    // Исправь метод getBottomMetal() - берём первый добавленный (самый нижний)
    public Metal getBottomMetal() {
        if (metalTank.isEmpty()) return null;
        // LinkedHashMap сохраняет порядок вставки, берём первый элемент
        return metalTank.keySet().iterator().next();
    }
}