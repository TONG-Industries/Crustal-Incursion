package com.cim.menu;


import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import com.cim.block.entity.weapons.TurretAmmoContainer;
import com.cim.item.weapons.turrets.TurretChipItem;

public class TurretLightMenu extends AbstractContainerMenu {

    private final TurretAmmoContainer ammoContainer;
    private final ContainerData data;
    private final BlockPos pos;

    public static final int AMMO_SLOT_COUNT = 9;
    public static final int CHIP_SLOT_INDEX = 9;
    public static final int TOTAL_TURRET_SLOTS = 10;

    // --- КОНСТАНТЫ ДЛЯ ДАННЫХ ---
    public static final int DATA_ENERGY = 0;
    public static final int DATA_MAX_ENERGY = 1;
    public static final int DATA_STATUS = 2;
    public static final int DATA_SWITCH = 3;
    public static final int DATA_BOOT_TIMER = 4;
    // Новые слоты настроек
    public static final int DATA_TARGET_HOSTILE = 5;
    public static final int DATA_TARGET_NEUTRAL = 6;
    public static final int DATA_TARGET_PLAYERS = 7;
    private static final int DATA_COUNT = 10;
    public static final int DATA_KILLS = 8;
    public static final int DATA_LIFETIME = 9; // В секундах (ticks / 20)

    public TurretLightMenu(int containerId, Inventory playerInventory, TurretAmmoContainer ammoContainer, ContainerData data, BlockPos pos) {
        super(ModMenuTypes.TURRET_AMMO_MENU.get(), containerId);

        // ВАЖНО: Проверяем размер данных, чтобы избежать крашей при обновлении
        checkContainerDataCount(data, DATA_COUNT);

        this.ammoContainer = ammoContainer;
        this.data = data;
        this.pos = pos;

        this.addDataSlots(data);

        // ... (Код слотов остаётся тем же: патроны, чип, инвентарь) ...
        // Код слотов скопируй из своего предыдущего файла, он не менялся

        // Слоты турели
        int ammoStartX = 115;
        int ammoStartY = 44;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new SlotItemHandler(ammoContainer, row * 3 + col, ammoStartX + col * 18, ammoStartY + row * 18) {
                    @Override public void setChanged() { super.setChanged(); ammoContainer.onContentsChanged(this.getSlotIndex()); }
                });
            }
        }

        // 2. Слот ЧИПА (Индекс 9) - Координаты 91, 80
        this.addSlot(new SlotItemHandler(ammoContainer, CHIP_SLOT_INDEX, 91, 80) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof TurretChipItem; }
            @Override public void setChanged() { super.setChanged(); ammoContainer.onContentsChanged(this.getSlotIndex()); }
        });

        // Инвентарь игрока и хотбар (стандартный код)
        int playerStartX = 8;
        int playerStartY = 106;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, playerStartX + column * 18, playerStartY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, playerStartX + column * 18, playerStartY + 58));
        }
    }

    public TurretLightMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        // Клиентский конструктор тоже должен знать про 8 слотов
        this(containerId, playerInventory, new TurretAmmoContainer(), new SimpleContainerData(DATA_COUNT), extraData.readBlockPos());
    }

    // Геттеры
    public BlockPos getPos() { return pos; }
    public int getDataSlot(int index) { return this.data.get(index); }
    public TurretAmmoContainer getAmmoContainer() { return ammoContainer; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // ... (Твой стандартный quickMoveStack) ...
        return ItemStack.EMPTY; // Заглушка, вставь свой код
    }

    @Override
    public boolean stillValid(Player player) { return true; }
}
