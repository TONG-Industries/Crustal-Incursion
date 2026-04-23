package com.cim.block.entity.industrial.casting;

import com.cim.api.metallurgy.system.Metal;
import com.cim.api.metallurgy.system.MetalUnits2;
import com.cim.api.metallurgy.system.MetallurgyRegistry;
import com.cim.api.metallurgy.system.recipe.SmeltRecipe;
import com.cim.block.basic.industrial.casting.CastingDescentBlock;
import com.cim.block.entity.ModBlockEntities;
import com.cim.event.HotItemHandler;
import com.cim.event.SlagItem;
import com.cim.menu.SmallSmelterMenu;
import com.cim.multiblock.industrial.SmelterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

public class SmallSmelterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_TEMP = 1050;
    public static final int BLOCK_CAPACITY = 1;
    public static final int TANK_CAPACITY = BLOCK_CAPACITY * MetalUnits2.UNITS_PER_BLOCK;

    // Слоты: 0 = топливо, 1 = зола, 2 = плавка
    private final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0) return isFuel(stack);
            if (slot == 1) return false; // Зола только для извлечения
            if (slot == 2) {
                return stack.getItem() instanceof SlagItem ||
                        MetallurgyRegistry.getSmeltRecipe(stack.getItem()) != null;
            }
            return false;
        }
    };

    // === ТОПЛИВО И НАГРЕВ ===
    // {heatPerTick, burnTicks}
    private static final float[][] TIER_STATS = {
            {1f, 125},
            {2f, 250},
            {3f, 500},
            {4f, 800},
            {6f, 1200},
            {8f, 2400}
    };

    // Шансы выпадения золы по тирам (в процентах)
    private static final int[] ASH_CHANCES = {0, 0, 40, 60, 80, 100};

    private float temperature = 0.0f;
    private int burnTime = 0;
    private int totalBurnTime = 0;
    private int fuelTier = 0;

    // === ПЛАВКА ===
    private float smeltProgress = 0;
    private float smeltMaxProgress = 0;
    private boolean isSmelting = false;
    private SmeltRecipe currentRecipe = null;
    private int requiredTemp = 0;
    private float heatConsumption = 0;

    // Температура предмета в слоте плавки
    private float itemTemperature = 20.0f;
    private static final float HEAT_RATE = 5.0f;

    private final ContainerData data = new SimpleContainerData(10) {
        @Override
        public void set(int index, int value) {
            super.set(index, value);
        }
    };

    private ItemStack previousSmeltStack = ItemStack.EMPTY;

    public SmallSmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMALL_SMELTER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmallSmelterBlockEntity be) {
        boolean changed = false;

        // === ОХЛАЖДЕНИЕ ===
        float baseCooling = (be.temperature * be.temperature) / 512000.0f;
        if (baseCooling < 0.05f && be.temperature > 0) baseCooling = 0.05f;

        float thermalNoise = 0.0f;
        if (be.temperature > 200.0f && baseCooling > 0.5f) {
            thermalNoise = (level.random.nextFloat() * 0.4f) - 0.2f;
        }

        float cooling = Math.max(0.05f, baseCooling + thermalNoise);
        if (be.temperature > 0.0f) {
            be.temperature = Math.max(0.0f, be.temperature - cooling);
            changed = true;
        }

        // === НАГРЕВ ОТ ТОПЛИВА ===
        if (be.burnTime > 0) {
            be.burnTime--;
            float heatPerTick = TIER_STATS[be.fuelTier][0];
            be.temperature = Math.min(MAX_TEMP, be.temperature + heatPerTick);
            changed = true;

            // Зола по окончании горения
            if (be.burnTime == 0 && be.fuelTier >= 2) {
                int chance = ASH_CHANCES[be.fuelTier];
                if (level.random.nextInt(100) < chance) {
                    ItemStack ash = new ItemStack(com.cim.item.ModItems.FUEL_ASH.get(), 1);
                    ItemStack remaining = be.inventory.insertItem(1, ash, false);
                    if (!remaining.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remaining);
                    }
                }
            }
        } else {
            // Пытаемся взять новое топливо
            ItemStack fuel = be.inventory.getStackInSlot(0);
            if (!fuel.isEmpty()) {
                int tier = be.getFuelTier(fuel);
                if (tier >= 0) {
                    be.fuelTier = tier;
                    be.burnTime = (int) TIER_STATS[tier][1];
                    be.totalBurnTime = be.burnTime;

                    ItemStack remainder = fuel.getCraftingRemainingItem();
                    fuel.shrink(1);

                    if (fuel.isEmpty() && !remainder.isEmpty()) {
                        be.inventory.setStackInSlot(0, remainder);
                    } else if (!remainder.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
                    }
                    changed = true;
                }
            }
        }

        // === ПЛАВКА ===
        be.tickSmelting();

        // === СИНХРОНИЗАЦИЯ ДАННЫХ ===
        be.data.set(0, (int) (be.temperature * 10.0f)); // Температура * 10
        be.data.set(1, be.burnTime);
        be.data.set(2, be.totalBurnTime);
        be.data.set(3, be.burnTime > 0 ? 1 : 0);
        be.data.set(4, (int) be.smeltProgress);
        be.data.set(5, (int) be.smeltMaxProgress);
        be.data.set(6, be.isSmelting ? 1 : 0);
        be.data.set(7, be.requiredTemp);
        be.data.set(8, (int) be.heatConsumption);
        be.data.set(9, be.isTankFull() ? 1 : 0);

        if (changed || be.isSmelting || be.temperature > 0) {
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void tickSmelting() {
        ItemStack stack = inventory.getStackInSlot(2);

        // Проверяем изменение предмета
        if (!areItemsSameIgnoreHeat(stack, previousSmeltStack)) {
            resetSmelting();
            previousSmeltStack = stack.copy();
            return;
        }

        if (stack.isEmpty()) {
            if (isSmelting || smeltProgress > 0) resetSmelting();
            return;
        }

        // Проверяем место в резервуаре
        if (totalMetalAmount >= TANK_CAPACITY) {
            if (isSmelting) resetSmelting();
            return;
        }

        // Определяем рецепт
        SmeltRecipe recipe = null;
        int outputUnits = 0;
        Metal outputMetal = null;

        if (stack.getItem() instanceof SlagItem) {
            Metal slagMetal = SlagItem.getMetal(stack);
            int slagAmount = SlagItem.getAmount(stack);
            if (slagMetal != null && slagAmount > 0) {
                outputMetal = slagMetal;
                outputUnits = slagAmount;
                requiredTemp = slagMetal.getMeltingPoint();
                heatConsumption = slagMetal.getHeatConsumptionPerTick();
                int smeltTime = slagMetal.calculateSmeltTimeForUnits(slagAmount);
                smeltMaxProgress = heatConsumption * smeltTime;
            }
        } else {
            recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
            if (recipe != null) {
                currentRecipe = recipe;
                outputMetal = recipe.output();
                outputUnits = recipe.outputUnits();
                requiredTemp = recipe.minTemp();
                heatConsumption = recipe.heatConsumption();
                smeltMaxProgress = recipe.getTotalHeatConsumption();
            }
        }

        if (outputMetal == null || outputUnits <= 0) {
            if (isSmelting) resetSmelting();
            return;
        }

        // Проверяем место для результата
        if (!hasSpaceFor(outputUnits)) {
            isSmelting = false;
            return;
        }

        // === НАГРЕВ ПРЕДМЕТА ===
        float itemTemp = getItemTemperature(stack);
        if (itemTemp < requiredTemp * 0.95f) {
            isSmelting = false;

            if (temperature > itemTemp) {
                float heatNeeded = (requiredTemp * 0.95f) - itemTemp;
                float heatTransfer = Math.min(HEAT_RATE * 3, heatNeeded);
                heatTransfer = Math.min(heatTransfer, temperature * 0.1f);

                float newTemp = itemTemp + heatTransfer;
                setItemTemperature(stack, newTemp);
                itemTemperature = newTemp;
                temperature -= heatTransfer * 0.5f;
            }
            return;
        }

        // === ПЛАВКА ===
        if (temperature < requiredTemp * 0.9f) {
            isSmelting = false;
            return;
        }

        // Всё готово, плавим!
        isSmelting = true;
        float availableHeat = Math.min(heatConsumption, temperature);
        float heatToApply = Math.min(availableHeat, smeltMaxProgress - smeltProgress);

        smeltProgress += heatToApply;
        temperature = Math.max(0, temperature - heatToApply);

        // Завершение
        if (smeltProgress >= smeltMaxProgress * 0.999f) {
            completeSmelting(outputMetal, outputUnits);
            resetSmelting();
        }
    }

    private void completeSmelting(Metal metal, int units) {
        ItemStack stack = inventory.getStackInSlot(2);
        if (!stack.isEmpty()) {
            stack.shrink(1);
            addMetal(metal, units);
        }
    }

    private void resetSmelting() {
        isSmelting = false;
        currentRecipe = null;
        smeltProgress = 0;
        smeltMaxProgress = 0;
        requiredTemp = 0;
        heatConsumption = 0;
        itemTemperature = 20.0f;
    }

    // ==================== ТЕПЛООБМЕН ПРЕДМЕТОВ ====================

    private float getItemTemperature(ItemStack stack) {
        if (HotItemHandler.isHot(stack)) {
            return HotItemHandler.getTemperature(stack);
        }
        return HotItemHandler.ROOM_TEMP;
    }

    private void setItemTemperature(ItemStack stack, float temp) {
        if (temp <= HotItemHandler.ROOM_TEMP) {
            if (HotItemHandler.isHot(stack)) {
                HotItemHandler.clearHotTags(stack);
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
            return;
        }

        int meltingPoint = getMeltingPointForItem(stack);
        if (meltingPoint <= 0) meltingPoint = 1000;

        float heatRatio = (temp - HotItemHandler.ROOM_TEMP) / (meltingPoint - HotItemHandler.ROOM_TEMP);
        heatRatio = Math.max(0, Math.min(1.2f, heatRatio));

        int maxTime = HotItemHandler.BASE_COOLING_TIME_HANDS;
        float hotTime = heatRatio * maxTime;

        stack.getOrCreateTag().putFloat("HotTime", hotTime);
        stack.getOrCreateTag().putInt("HotTimeMax", maxTime);
        stack.getOrCreateTag().putInt("MeltingPoint", meltingPoint);
        stack.getOrCreateTag().putBoolean("CooledInPot", false);

        if (level != null && !level.isClientSide && (int) hotTime % 10 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private int getMeltingPointForItem(ItemStack stack) {
        SmeltRecipe recipe = MetallurgyRegistry.getSmeltRecipe(stack.getItem());
        if (recipe != null) return recipe.minTemp();

        if (stack.getItem() instanceof SlagItem) {
            return SlagItem.getMeltingPoint(stack);
        }

        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 300;
        return 800;
    }

    private boolean areItemsSameIgnoreHeat(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem() || a.getCount() != b.getCount()) return false;

        if (a.hasTag() == b.hasTag()) {
            if (!a.hasTag()) return true;

            CompoundTag tagA = a.getTag().copy();
            CompoundTag tagB = b.getTag().copy();

            tagA.remove("HotTime");
            tagA.remove("HotTimeMax");
            tagA.remove("MeltingPoint");
            tagA.remove("CooledInPot");

            tagB.remove("HotTime");
            tagB.remove("HotTimeMax");
            tagB.remove("MeltingPoint");
            tagB.remove("CooledInPot");

            return tagA.equals(tagB);
        }
        return false;
    }

    // ==================== ТОПЛИВО ====================

    public boolean isFuel(ItemStack stack) {
        return getFuelTier(stack) >= 0;
    }

    public int getFuelTier(ItemStack stack) {
        Item item = stack.getItem();

        // Тир 0: Дерево
        if (item == Items.STICK) return 0;
        if (item == Items.SCAFFOLDING) return 0;
        if (item == Items.OAK_PLANKS || item == Items.SPRUCE_PLANKS ||
                item == Items.BIRCH_PLANKS || item == Items.JUNGLE_PLANKS ||
                item == Items.ACACIA_PLANKS || item == Items.DARK_OAK_PLANKS ||
                item == Items.MANGROVE_PLANKS || item == Items.CHERRY_PLANKS ||
                item == Items.BAMBOO_PLANKS || item == Items.BAMBOO_MOSAIC) return 0;
        if (item == Items.OAK_SLAB || item == Items.SPRUCE_SLAB ||
                item == Items.BIRCH_SLAB || item == Items.JUNGLE_SLAB ||
                item == Items.ACACIA_SLAB || item == Items.DARK_OAK_SLAB ||
                item == Items.MANGROVE_SLAB || item == Items.CHERRY_SLAB ||
                item == Items.BAMBOO_SLAB || item == Items.BAMBOO_MOSAIC_SLAB) return 0;
        if (item == Items.OAK_STAIRS || item == Items.SPRUCE_STAIRS ||
                item == Items.BIRCH_STAIRS || item == Items.JUNGLE_STAIRS ||
                item == Items.ACACIA_STAIRS || item == Items.DARK_OAK_STAIRS ||
                item == Items.MANGROVE_STAIRS || item == Items.CHERRY_STAIRS ||
                item == Items.BAMBOO_STAIRS || item == Items.BAMBOO_MOSAIC_STAIRS) return 0;
        if (item == Items.OAK_FENCE || item == Items.SPRUCE_FENCE ||
                item == Items.BIRCH_FENCE || item == Items.JUNGLE_FENCE ||
                item == Items.ACACIA_FENCE || item == Items.DARK_OAK_FENCE ||
                item == Items.MANGROVE_FENCE || item == Items.CHERRY_FENCE ||
                item == Items.BAMBOO_FENCE) return 0;
        if (item == Items.OAK_FENCE_GATE || item == Items.SPRUCE_FENCE_GATE ||
                item == Items.BIRCH_FENCE_GATE || item == Items.JUNGLE_FENCE_GATE ||
                item == Items.ACACIA_FENCE_GATE || item == Items.DARK_OAK_FENCE_GATE ||
                item == Items.MANGROVE_FENCE_GATE || item == Items.CHERRY_FENCE_GATE ||
                item == Items.BAMBOO_FENCE_GATE) return 0;
        if (item == Items.OAK_DOOR || item == Items.SPRUCE_DOOR ||
                item == Items.BIRCH_DOOR || item == Items.JUNGLE_DOOR ||
                item == Items.ACACIA_DOOR || item == Items.DARK_OAK_DOOR ||
                item == Items.MANGROVE_DOOR || item == Items.CHERRY_DOOR ||
                item == Items.BAMBOO_DOOR) return 0;
        if (item == Items.OAK_TRAPDOOR || item == Items.SPRUCE_TRAPDOOR ||
                item == Items.BIRCH_TRAPDOOR || item == Items.JUNGLE_TRAPDOOR ||
                item == Items.ACACIA_TRAPDOOR || item == Items.DARK_OAK_TRAPDOOR ||
                item == Items.MANGROVE_TRAPDOOR || item == Items.CHERRY_TRAPDOOR ||
                item == Items.BAMBOO_TRAPDOOR) return 0;
        if (item == Items.OAK_BUTTON || item == Items.SPRUCE_BUTTON ||
                item == Items.BIRCH_BUTTON || item == Items.JUNGLE_BUTTON ||
                item == Items.ACACIA_BUTTON || item == Items.DARK_OAK_BUTTON ||
                item == Items.MANGROVE_BUTTON || item == Items.CHERRY_BUTTON ||
                item == Items.BAMBOO_BUTTON) return 0;
        if (item == Items.OAK_PRESSURE_PLATE || item == Items.SPRUCE_PRESSURE_PLATE ||
                item == Items.BIRCH_PRESSURE_PLATE || item == Items.JUNGLE_PRESSURE_PLATE ||
                item == Items.ACACIA_PRESSURE_PLATE || item == Items.DARK_OAK_PRESSURE_PLATE ||
                item == Items.MANGROVE_PRESSURE_PLATE || item == Items.CHERRY_PRESSURE_PLATE ||
                item == Items.BAMBOO_PRESSURE_PLATE) return 0;
        if (item == Items.OAK_SIGN || item == Items.SPRUCE_SIGN ||
                item == Items.BIRCH_SIGN || item == Items.JUNGLE_SIGN ||
                item == Items.ACACIA_SIGN || item == Items.DARK_OAK_SIGN ||
                item == Items.MANGROVE_SIGN || item == Items.CHERRY_SIGN ||
                item == Items.BAMBOO_SIGN || item == Items.OAK_HANGING_SIGN ||
                item == Items.SPRUCE_HANGING_SIGN || item == Items.BIRCH_HANGING_SIGN ||
                item == Items.JUNGLE_HANGING_SIGN || item == Items.ACACIA_HANGING_SIGN ||
                item == Items.DARK_OAK_HANGING_SIGN || item == Items.MANGROVE_HANGING_SIGN ||
                item == Items.CHERRY_HANGING_SIGN || item == Items.BAMBOO_HANGING_SIGN) return 0;
        if (item == Items.OAK_LOG || item == Items.SPRUCE_LOG ||
                item == Items.BIRCH_LOG || item == Items.JUNGLE_LOG ||
                item == Items.ACACIA_LOG || item == Items.DARK_OAK_LOG ||
                item == Items.MANGROVE_LOG || item == Items.CHERRY_LOG ||
                item == Items.BAMBOO_BLOCK || item == Items.STRIPPED_BAMBOO_BLOCK ||
                item == Items.STRIPPED_OAK_LOG || item == Items.STRIPPED_SPRUCE_LOG ||
                item == Items.STRIPPED_BIRCH_LOG || item == Items.STRIPPED_JUNGLE_LOG ||
                item == Items.STRIPPED_ACACIA_LOG || item == Items.STRIPPED_DARK_OAK_LOG ||
                item == Items.STRIPPED_MANGROVE_LOG || item == Items.STRIPPED_CHERRY_LOG ||
                item == Items.OAK_WOOD || item == Items.SPRUCE_WOOD ||
                item == Items.BIRCH_WOOD || item == Items.JUNGLE_WOOD ||
                item == Items.ACACIA_WOOD || item == Items.DARK_OAK_WOOD ||
                item == Items.MANGROVE_WOOD || item == Items.CHERRY_WOOD ||
                item == Items.STRIPPED_OAK_WOOD || item == Items.STRIPPED_SPRUCE_WOOD ||
                item == Items.STRIPPED_BIRCH_WOOD || item == Items.STRIPPED_JUNGLE_WOOD ||
                item == Items.STRIPPED_ACACIA_WOOD || item == Items.STRIPPED_DARK_OAK_WOOD ||
                item == Items.STRIPPED_MANGROVE_WOOD || item == Items.STRIPPED_CHERRY_WOOD) return 0;
        if (item == Items.BOWL) return 0;
        if (item == Items.OAK_BOAT || item == Items.SPRUCE_BOAT ||
                item == Items.BIRCH_BOAT || item == Items.JUNGLE_BOAT ||
                item == Items.ACACIA_BOAT || item == Items.DARK_OAK_BOAT ||
                item == Items.MANGROVE_BOAT || item == Items.CHERRY_BOAT ||
                item == Items.BAMBOO_RAFT || item == Items.OAK_CHEST_BOAT ||
                item == Items.SPRUCE_CHEST_BOAT || item == Items.BIRCH_CHEST_BOAT ||
                item == Items.JUNGLE_CHEST_BOAT || item == Items.ACACIA_CHEST_BOAT ||
                item == Items.DARK_OAK_CHEST_BOAT || item == Items.MANGROVE_CHEST_BOAT ||
                item == Items.CHERRY_CHEST_BOAT || item == Items.BAMBOO_CHEST_RAFT) return 0;
        if (item == Items.NOTE_BLOCK) return 0;
        if (item == Items.JUKEBOX) return 0;
        if (item == Items.BOOKSHELF) return 0;
        if (item == Items.CHISELED_BOOKSHELF) return 0;
        if (item == Items.COMPOSTER) return 0;
        if (item == Items.BARREL) return 0;
        if (item == Items.CHEST || item == Items.TRAPPED_CHEST) return 0;
        if (item == Items.CRAFTING_TABLE) return 0;
        if (item == Items.FLETCHING_TABLE) return 0;
        if (item == Items.SMITHING_TABLE) return 0;
        if (item == Items.CARTOGRAPHY_TABLE) return 0;
        if (item == Items.LOOM) return 0;
        if (item == Items.ITEM_FRAME) return 0;
        if (item == Items.GLOW_ITEM_FRAME) return 0;
        if (item == Items.PAINTING) return 0;
        if (item == Items.WHITE_BED || item == Items.ORANGE_BED ||
                item == Items.MAGENTA_BED || item == Items.LIGHT_BLUE_BED ||
                item == Items.YELLOW_BED || item == Items.LIME_BED ||
                item == Items.PINK_BED || item == Items.GRAY_BED ||
                item == Items.LIGHT_GRAY_BED || item == Items.CYAN_BED ||
                item == Items.PURPLE_BED || item == Items.BLUE_BED ||
                item == Items.BROWN_BED || item == Items.GREEN_BED ||
                item == Items.RED_BED || item == Items.BLACK_BED) return 0;
        if (item == Items.WOODEN_SWORD || item == Items.WOODEN_PICKAXE ||
                item == Items.WOODEN_AXE || item == Items.WOODEN_SHOVEL ||
                item == Items.WOODEN_HOE) return 0;
        if (item == Items.SHIELD) return 0;
        if (item == Items.BOW) return 0;
        if (item == Items.CROSSBOW) return 0;
        if (item == Items.FISHING_ROD) return 0;
        if (item == Items.CAMPFIRE || item == Items.SOUL_CAMPFIRE) return 0;
        if (item == Items.TORCH || item == Items.SOUL_TORCH ||
                item == Items.REDSTONE_TORCH) return 0;

        // Тир 1: Уголь
        if (item == Items.COAL || item == Items.CHARCOAL || item == Items.BLAZE_POWDER) return 1;

        // Тир 2: Blaze rod
        if (item == Items.BLAZE_ROD || item == Items.MAGMA_CREAM || item == Items.PORKCHOP) return 2;

        // Тир 3: Блок угля
        if (item == Item.byBlock(net.minecraft.world.level.block.Blocks.COAL_BLOCK)) return 3;

        // Тир 4: Лава
        if (item == Items.LAVA_BUCKET) return 4;

        // Тир 5: Специальное
        if (item == com.cim.item.ModItems.MORY_LAH.get() || item == Items.DRAGON_BREATH) return 5;

        return -1;
    }

    // ==================== МЕТАЛЛ ====================

    private final Map<Metal, Integer> metalTank = new LinkedHashMap<>();
    private int totalMetalAmount = 0;

    private void addMetal(Metal metal, int units) {
        if (units <= 0) return;
        if (totalMetalAmount + units > TANK_CAPACITY) {
            units = TANK_CAPACITY - totalMetalAmount;
            if (units <= 0) return;
        }
        metalTank.merge(metal, units, Integer::sum);
        recalculateTotal();
    }

    private void recalculateTotal() {
        totalMetalAmount = metalTank.values().stream().mapToInt(Integer::intValue).sum();
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

    public int extractMetal(Metal metal, int maxUnits) {
        Integer current = metalTank.get(metal);
        if (current == null || current <= 0) return 0;
        int toExtract = Math.min(maxUnits, current);
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

    public List<ItemStack> dumpMetalAsSlag() {
        List<ItemStack> slagItems = new ArrayList<>();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) {
                ItemStack slag = SlagItem.createSlag(metal, amount);
                if (!slag.getTag().contains("HotTime")) {
                    slag.getOrCreateTag().putInt("HotTime", SlagItem.BASE_COOLING_TIME);
                    slag.getOrCreateTag().putInt("HotTimeMax", SlagItem.BASE_COOLING_TIME);
                }
                slagItems.add(slag);
            }
        });
        metalTank.clear();
        totalMetalAmount = 0;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
        return slagItems;
    }

    public boolean hasMetal() { return totalMetalAmount > 0; }
    public boolean isTankFull() { return totalMetalAmount >= TANK_CAPACITY * 0.95f; }
    public boolean hasSpaceFor(int units) { return totalMetalAmount + units <= TANK_CAPACITY; }

    // ==================== ГЕТТЕРЫ ====================

    public ItemStackHandler getInventory() { return inventory; }
    public ContainerData getData() { return data; }
    public float getTemperature() { return temperature; }
    public Map<Metal, Integer> getMetalTank() { return Collections.unmodifiableMap(metalTank); }
    public int getTotalMetalAmount() { return totalMetalAmount; }
    public int getBlockCapacity() { return BLOCK_CAPACITY; }
    public int getRequiredTemp() { return requiredTemp; }
    public float getSmeltProgress() { return smeltProgress; }
    public float getSmeltMaxProgress() { return smeltMaxProgress; }
    public boolean isSmelting() { return isSmelting; }
    public int getBurnTime() { return burnTime; }
    public int getTotalBurnTime() { return totalBurnTime; }
    public boolean isBurning() { return burnTime > 0; }

    public List<SmelterBlockEntity.MetalStack> getMetalStacks() {
        List<SmelterBlockEntity.MetalStack> list = new ArrayList<>();
        metalTank.forEach((metal, amount) -> {
            if (amount > 0) list.add(new SmelterBlockEntity.MetalStack(metal, amount));
        });
        return list;
    }

    public Metal getMetalForCasting(List<Metal> preferredMetals) {
        if (metalTank.isEmpty()) return null;
        if (preferredMetals != null && !preferredMetals.isEmpty()) {
            for (Metal preferred : preferredMetals) {
                if (metalTank.containsKey(preferred) && metalTank.get(preferred) > 0) {
                    return preferred;
                }
            }
        }
        return metalTank.keySet().iterator().next();
    }

    private SmallSmelterBlockEntity findSmallSmelter(Level level, BlockPos pos) {
        Direction facing = level.getBlockState(pos).getValue(CastingDescentBlock.FACING);
        Direction back = facing.getOpposite();
        BlockPos smelterPos = pos.relative(back);

        BlockEntity be = level.getBlockEntity(smelterPos);
        if (be instanceof SmallSmelterBlockEntity smelter) return smelter;
        return null;
    }

    // ==================== NBT ====================

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putFloat("Temperature", temperature);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("TotalBurnTime", totalBurnTime);
        tag.putInt("FuelTier", fuelTier);
        tag.putFloat("SmeltProgress", smeltProgress);
        tag.putFloat("SmeltMaxProgress", smeltMaxProgress);
        tag.putBoolean("IsSmelting", isSmelting);
        tag.putInt("RequiredTemp", requiredTemp);
        tag.putFloat("HeatConsumption", heatConsumption);
        tag.putFloat("ItemTemperature", itemTemperature);

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
        temperature = tag.getFloat("Temperature");
        burnTime = tag.getInt("BurnTime");
        totalBurnTime = tag.getInt("TotalBurnTime");
        fuelTier = tag.getInt("FuelTier");
        smeltProgress = tag.getFloat("SmeltProgress");
        smeltMaxProgress = tag.getFloat("SmeltMaxProgress");
        isSmelting = tag.getBoolean("IsSmelting");
        requiredTemp = tag.getInt("RequiredTemp");
        heatConsumption = tag.getFloat("HeatConsumption");
        itemTemperature = tag.getFloat("ItemTemperature");

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

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) load(pkt.getTag());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cim.small_smelter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SmallSmelterMenu(id, inv, this, data);
    }
}