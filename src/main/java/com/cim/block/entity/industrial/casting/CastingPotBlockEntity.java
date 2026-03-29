package com.cim.block.entity.industrial.casting;

import com.cim.api.metal.Metal;
import com.cim.api.metal.MetalUnits;
import com.cim.api.metal.MetallurgyRegistry;
import com.cim.block.entity.ModBlockEntities;
import com.cim.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CastingPotBlockEntity extends BlockEntity {
    public static final int CAPACITY_MOLD_INGOT = MetalUnits.MB_PER_INGOT; // 111 мб

    private ItemStack mold = ItemStack.EMPTY;
    private ItemStack outputItem = ItemStack.EMPTY;

    private Metal currentMetal = null;
    private int storedMb = 0;
    private int capacity = 0;
    private int coolingTimer = 0;
    private int solidifyTimer = 0;
    private static final int SOLIDIFY_TIME = 100; // 5 секунд
    private static final int COOLING_TIME = 100;
    private int transferCooldown = 0;
    public static final int BASE_COOLING_TIME = 100; // 10 секунд база
    public CastingPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_POT.get(), pos, state);
    }
    private int getCoolingTimeForMold() {
        // Сейчас: только слиток = 1.0f
        if (mold.is(ModItems.MOLD_INGOT.get())) {
            return BASE_COOLING_TIME; // 200 тиков
        }
        // ПОТОМ добавишь:
        // if (mold.is(ModItems.MOLD_BLOCK.get())) return BASE_COOLING_TIME * 9; // 1800
        // if (mold.is(ModItems.MOLD_NUGGET.get())) return (int)(BASE_COOLING_TIME * 0.11f); // 22

        return BASE_COOLING_TIME; // По умолчанию
    }
    private final ItemStackHandler itemHandler = new ItemStackHandler(1) {
        @Override
        public ItemStack getStackInSlot(int slot) {
            return outputItem;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            outputItem = stack;
            setChanged();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // КРИТИЧНО: Блокируем если горячий! (воронка ждёт остывания)
            if (coolingTimer > 0) {
                return ItemStack.EMPTY; // Воронка получит пустоту = подождёт
            }

            if (storedMb > 0 || solidifyTimer > 0) {
                return ItemStack.EMPTY;
            }

            ItemStack res = outputItem.copy().split(amount);
            if (!simulate) {
                outputItem.shrink(amount);
                setChanged();
            }
            return res;
        }
    };
    // В createOutputItem():
    private void createOutputItem() {
        if (currentMetal == null || storedMb < capacity) return;

        ItemStack result = ItemStack.EMPTY;

        // Сейчас только слиток, потом добавишь else if для других форм
        if (currentMetal.hasIngot()) {
            result = new ItemStack(currentMetal.getIngot());
        }

        if (!result.isEmpty()) {
            int coolTime = getCoolingTimeForMold();
            result.getOrCreateTag().putInt("HotTime", coolTime);
            result.getOrCreateTag().putInt("HotTimeMax", coolTime); // Для правильного отображения %
            this.outputItem = result;
        }

        this.storedMb = 0;
        this.currentMetal = null;
        this.solidifyTimer = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // Метод возврата горячего предмета:
    public boolean tryInsertHotItem(ItemStack stack) {
        // Принимаем только если: пустой котел, есть форма, нет металла, нет output
        if (!outputItem.isEmpty() || storedMb > 0 || mold.isEmpty()) return false;

        if (stack.hasTag() && stack.getTag().contains("HotTime")) {
            this.outputItem = stack.copy();
            this.coolingTimer = stack.getTag().getInt("HotTime");
            // Очищаем NBT у предмета в котле (чтобы не дублировалось)
            this.outputItem.removeTagKey("HotTime");
            this.outputItem.removeTagKey("HotTimeMax");
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return true;
        }
        return false;
    }
    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingPotBlockEntity be) {
        // 1. Кулдауны (всегда)
        if (be.transferCooldown > 0) be.transferCooldown--;

        // === ОСТЫВАНИЕ ПРЕДМЕТА ===
        // Работает ВСЕГДА, даже если в котле лежит готовый слиток!
        if (be.coolingTimer > 0) {
            be.coolingTimer--;
            be.setChanged();
            if (be.coolingTimer == 0) {
                level.sendBlockUpdated(pos, state, state, 3);
                // Звук остывания (опционально)
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 2.0f);
            }
        }

        // 2. Теперь проверяем есть ли предмет - если да, дальше не идём (только остывать)
        if (!be.outputItem.isEmpty()) return;


        // 3. Если формы нет — очищаем металл (испарение)
        if (be.mold.isEmpty()) {
            if (be.storedMb > 0) be.clearMetal();
            return;
        }

        be.updateCapacity();

        // 4. ЛОГИКА ПЕРЕДАЧИ СОСЕДЯМ
        if (be.storedMb > 0 && be.transferCooldown <= 0) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(dir);
                if (level.getBlockEntity(neighborPos) instanceof CastingPotBlockEntity neighborPot) {
                    // Проверяем canAcceptMetal (там уже есть проверка coolingTimer и outputItem)
                    if (neighborPot.canAcceptMetal(be.currentMetal) && neighborPot.getRemainingCapacity() > 0) {
                        int toTransfer = Math.min(10, be.storedMb);
                        int accepted = neighborPot.addMetal(be.currentMetal, toTransfer);

                        if (accepted > 0) {
                            be.storedMb -= accepted;
                            be.transferCooldown = 5;
                            if (be.storedMb <= 0) be.currentMetal = null;
                            be.setChanged();
                            level.sendBlockUpdated(pos, state, state, 3);
                            break;
                        }
                    }
                }
            }
        }

        // 5. ЛОГИКА ЗАСТЫВАНИЯ
        if (be.storedMb >= be.capacity && be.capacity > 0 && be.transferCooldown <= 0) {
            if (be.solidifyTimer < SOLIDIFY_TIME) {
                be.solidifyTimer++;
                be.setChanged();

                if (be.solidifyTimer % 10 == 0) {
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            } else {
                // МОМЕНТ ЗАВЕРШЕНИЯ ЗАСТЫВАНИЯ
                be.createOutputItem();
                be.storedMb = 0;
                be.solidifyTimer = 0;
                be.coolingTimer = COOLING_TIME;

                // ЧАСТИЦЫ POOF
                if (!level.isClientSide) {
                    ((ServerLevel)level).sendParticles(ParticleTypes.POOF,
                            pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                            8, 0.25, 0.1, 0.25, 0.03);
                }

                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 2.6f);
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        } else {
            // Если металл начал убывать (перетекать к соседу), сбрасываем прогресс застывания
            if (be.solidifyTimer > 0) {
                be.solidifyTimer = 0;
                be.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }


    private void tryTransferToNeighbor() {
        if (this.storedMb <= 0 || this.currentMetal == null) return;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockEntity neighbor = level.getBlockEntity(worldPosition.relative(dir));
            if (neighbor instanceof CastingPotBlockEntity neighborPot) {
                // Если сосед может принять этот металл и он не полон
                if (neighborPot.canAcceptMetal(this.currentMetal) && neighborPot.getRemainingCapacity() > 0) {
                    int toTransfer = Math.min(10, this.storedMb); // Скорость перетекания
                    int accepted = neighborPot.addMetal(this.currentMetal, toTransfer);

                    if (accepted > 0) {
                        this.storedMb -= accepted;
                        this.transferCooldown = 2; // Блокирует застывание на время передачи
                        if (this.storedMb <= 0) this.currentMetal = null;
                        this.setChanged();
                        return; // Передаем только одному за тик
                    }
                }
            }
        }
    }




    public boolean isCompatibleWith(Metal metal) {
        if (storedMb == 0) return true;
        return currentMetal != null && currentMetal.equals(metal);
    }



    public int extractMetal(int maxAmount) {
        if (storedMb <= 0 || solidifyTimer > 0) return 0;
        int toExtract = Math.min(maxAmount, storedMb);
        storedMb -= toExtract;
        if (storedMb <= 0) {
            currentMetal = null;
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return toExtract;
    }





    private final LazyOptional<IItemHandler> inventoryCap = LazyOptional.of(() -> itemHandler);

    // ЛОГИКА СЕТИ (Поиск до 7 котлов)
    public List<CastingPotBlockEntity> findNetwork() {
        List<CastingPotBlockEntity> network = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(worldPosition); visited.add(worldPosition);

        while (!queue.isEmpty() && network.size() < 7) {
            BlockPos curr = queue.poll();
            if (level.getBlockEntity(curr) instanceof CastingPotBlockEntity pot) {
                network.add(pot);
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos next = curr.relative(d);
                    if (!visited.contains(next) && level.getBlockState(next).is(getBlockState().getBlock())) {
                        visited.add(next); queue.add(next);
                    }
                }
            }
        }
        return network;
    }

    public int fillNetwork(Metal metal, int amount) {
        List<CastingPotBlockEntity> network = findNetwork();

        // Фильтруем только котлы, которые могут принять металл
        List<CastingPotBlockEntity> availablePools = network.stream()
                .filter(p -> p.canAcceptMetal(metal))
                .toList();

        if (availablePools.isEmpty()) return 0;

        int totalSpace = availablePools.stream().mapToInt(CastingPotBlockEntity::getRemainingCapacity).sum();
        if (totalSpace <= 0) return 0;

        int toFillTotal = Math.min(amount, totalSpace);
        int actuallyFilled = 0;
        int count = availablePools.size();
        int perPool = toFillTotal / count;
        int remainder = toFillTotal % count;

        for (CastingPotBlockEntity pool : availablePools) {
            int fillAmount = perPool + (remainder-- > 0 ? 1 : 0);
            if (fillAmount > 0) {
                int accepted = pool.addMetal(metal, fillAmount);
                actuallyFilled += accepted;
            }
        }
        return actuallyFilled;
    }


    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return inventoryCap.cast();
        return super.getCapability(cap, side);
    }

    private void updateCapacity() {
        if (mold.is(ModItems.MOLD_INGOT.get())) {
            this.capacity = CAPACITY_MOLD_INGOT;
        } else {
            this.capacity = 0;
        }
    }



    private void clearMetal() {
        this.storedMb = 0;
        this.currentMetal = null;
        this.solidifyTimer = 0;
        setChanged();
    }

    public ItemStack getMold() { return mold; }
    public ItemStack getOutputItem() { return outputItem; }
    public Metal getCurrentMetal() { return currentMetal; }
    public int getStoredMb() { return storedMb; }
    public int getCapacity() { updateCapacity(); return capacity; }
    public int getSolidifyProgress() { return solidifyTimer; }
    public int getSolidifyTime() { return SOLIDIFY_TIME; }

    public float getFillLevel() {
        if (capacity <= 0) return 0;
        return (float) storedMb / capacity;
    }

    public boolean canRemoveMold() {
        return storedMb <= 0 && outputItem.isEmpty();
    }

    public void setMold(ItemStack stack) {
        this.mold = stack.copy();
        updateCapacity();
        if (mold.isEmpty()) {
            clearMetal();
        }
        this.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }


    public ItemStack takeOutput() {
        ItemStack result = outputItem.copy();

        // Если предмет ещё горячий - сохраняем время остывания в NBT
        if (coolingTimer > 0) {
            result.getOrCreateTag().putInt("HotTime", coolingTimer);
        }

        this.outputItem = ItemStack.EMPTY;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return result;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Mold", mold.save(new CompoundTag()));
        tag.put("Output", outputItem.save(new CompoundTag()));
        tag.putInt("StoredMb", storedMb);
        tag.putInt("SolidifyTimer", solidifyTimer);
        tag.putInt("CoolingTimer", coolingTimer); // ДОБАВЬ ЭТО
        if (currentMetal != null) {
            tag.putString("MetalId", currentMetal.getId().toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.mold = ItemStack.of(tag.getCompound("Mold"));
        this.outputItem = ItemStack.of(tag.getCompound("Output"));
        this.storedMb = tag.getInt("StoredMb");
        this.solidifyTimer = tag.getInt("SolidifyTimer");
        this.coolingTimer = tag.getInt("CoolingTimer"); // И ЭТО
        if (tag.contains("MetalId")) {
            ResourceLocation id = new ResourceLocation(tag.getString("MetalId"));
            MetallurgyRegistry.get(id).ifPresent(m -> this.currentMetal = m);
        }
        updateCapacity();
    }

    // Добавь геттер
    public int getCoolingTimer() {
        return coolingTimer;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("Mold", mold.save(new CompoundTag()));
        tag.put("Output", outputItem.save(new CompoundTag()));
        tag.putInt("StoredMb", storedMb);
        tag.putInt("SolidifyTimer", solidifyTimer);
        tag.putInt("CoolingTimer", coolingTimer);
        if (currentMetal != null) {
            tag.putString("MetalId", currentMetal.getId().toString());
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            CompoundTag tag = pkt.getTag();
            this.mold = ItemStack.of(tag.getCompound("Mold"));
            this.outputItem = ItemStack.of(tag.getCompound("Output"));
            this.storedMb = tag.getInt("StoredMb");
            this.solidifyTimer = tag.getInt("SolidifyTimer");
            this.coolingTimer = tag.getInt("CoolingTimer");
            if (tag.contains("MetalId")) {
                ResourceLocation id = new ResourceLocation(tag.getString("MetalId"));
                MetallurgyRegistry.get(id).ifPresent(m -> this.currentMetal = m);
            }
            updateCapacity();
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        this.load(tag);
    }


    public boolean canAcceptMetal(Metal metal) {
        if (mold.isEmpty()) return false;
        if (!outputItem.isEmpty()) return false;
        if (coolingTimer > 0) return false; // ДОБАВЬ ЭТУ СТРОКУ
        updateCapacity();
        if (storedMb >= capacity) return false;
        if (storedMb > 0 && currentMetal != null && !currentMetal.equals(metal)) {
            return false;
        }
        return true;
    }

    public int getRemainingCapacity() {
        updateCapacity();
        // Если котел остывает или содержит готовый предмет - свободного места нет!
        if (coolingTimer > 0 || !outputItem.isEmpty()) return 0;
        return capacity - storedMb;
    }

    public int addMetal(Metal metal, int amount) {
        // КРИТИЧНО: Не принимаем металл если остываем или есть готовый предмет
        if (this.coolingTimer > 0 || !this.outputItem.isEmpty()) {
            return 0;
        }

        if (this.storedMb == 0) {
            this.currentMetal = metal;
        } else if (!this.currentMetal.equals(metal)) {
            return 0;
        }

        int toAdd = Math.min(amount, this.capacity - this.storedMb);
        if (toAdd > 0) {
            this.storedMb += toAdd;
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
        return toAdd;
    }
    public float getCoolingProgress() {
        return (float) coolingTimer / COOLING_TIME;
    }
}