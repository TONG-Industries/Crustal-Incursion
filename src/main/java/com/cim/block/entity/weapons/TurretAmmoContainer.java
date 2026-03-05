package com.cim.block.entity.weapons;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import com.cim.item.tags.IAmmoItem;
import com.cim.item.weapons.turrets.TurretChipItem;

public class TurretAmmoContainer extends ItemStackHandler {

    private static final int SLOT_COUNT = 10;
    private Runnable onContentsChanged;


    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        if (slot == 9) {
            return stack.getItem() instanceof TurretChipItem;
        }
        // Для остальных слотов (0-8) логика патронов (если она была)
        return true;
    }

    public TurretAmmoContainer() {
        super(SLOT_COUNT);
    }

    public void setOnContentsChanged(Runnable callback) {
        this.onContentsChanged = callback;
    }

    @Override
    public void onContentsChanged(int slot) {
        if (onContentsChanged != null) {
            onContentsChanged.run();
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }


    /**
     * Проверяет наличие патрона нужного калибра, но НЕ забирает его.
     * Нужен для расчетов баллистики перед выстрелом.
     */
    public IAmmoItem peekAmmo(String caliber) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAmmoItem ammo) {
                if (ammo.getCaliber().equals(caliber)) {
                    return ammo;
                }
            }
        }
        return null;
    }


    public IAmmoItem takeAmmoAndGet(String caliber) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAmmoItem ammo) {
                // Для совместимости с IAmmoItem который может зависеть от NBT, лучше проверять stack
                // Но в твоем случае getCaliber() без аргументов в интерфейсе
                if (ammo.getCaliber().equals(caliber)) { // или ammo.getCaliber(stack)
                    stack.shrink(1);
                    return ammo; // Возвращаем сам предмет (интерфейс)
                }
            }
        }
        return null;
    }


    public int countAmmo(String caliber) {
        int count = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAmmoItem ammo) {
                if (ammo.getCaliber().equals(caliber)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    public boolean takeAmmo(String caliber) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof IAmmoItem ammo) {
                if (ammo.getCaliber().equals(caliber)) {
                    stack.shrink(1);
                    return true;
                }
            }
        }
        return false;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag nbtList = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!stacks.get(i).isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                stacks.get(i).save(itemTag);
                nbtList.add(itemTag);
            }
        }
        tag.put("Items", nbtList);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        ListTag nbtList = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < nbtList.size(); i++) {
            CompoundTag itemTag = nbtList.getCompound(i);
            int slot = itemTag.getByte("Slot") & 0xFF;
            if (slot < SLOT_COUNT) {
                stacks.set(slot, ItemStack.of(itemTag));
            }
        }
    }
}
