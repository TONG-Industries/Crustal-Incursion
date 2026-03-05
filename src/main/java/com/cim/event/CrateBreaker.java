package com.cim.event;


import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import com.cim.block.basic.ModBlocks;
import com.cim.item.ModItems;
import com.cim.sound.ModSounds;

import java.util.List;
import java.util.Map;
import java.util.Random;

@Mod.EventBusSubscriber(modid = "cim", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CrateBreaker {

    private static final Random RANDOM = new Random();

    private static final List<RegistryObject<Block>> BREAKABLE_CRATES = List.of(
            ModBlocks.CRATE,
            ModBlocks.CRATE_AMMO
    );

    private static final List<RegistryObject<?>> CRACK_SOUNDS = List.of(
            ModSounds.CRATE_BREAK

    );

    // Дроп с шансом для каждого ящика: список (предмет, шанс выпадения)
    private static final Map<RegistryObject<Block>, List<DropChance>> CRATE_DROPS = Map.of(
            ModBlocks.CRATE, List.of(

                    new DropChance(ModBlocks.MACHINE_BATTERY, 0.1),
                    new DropChance(ModBlocks.SWITCH, 0.1),
                    new DropChance(ModBlocks.STOPPER, 0.1),
                    new DropChance(ModBlocks.SHAFT_IRON, 0.1),
                    new DropChance(ModItems.SCREWDRIVER, 0.1),
                    new DropChance(ModItems.TURRET_CHIP, 0.1)


            ),

            ModBlocks.CRATE_AMMO, List.of(

                    new DropChance(ModItems.AMMO_TURRET, 0.1),
                    new DropChance(ModItems.AMMO_TURRET_HOLLOW, 0.1),
                    new DropChance(ModItems.AMMO_TURRET_PIERCING, 0.1),
                    new DropChance(ModItems.AMMO_TURRET_RADIO, 0.1),
                    new DropChance(ModItems.AMMO_TURRET_FIRE, 0.1)
            )


    );

    private record DropChance(RegistryObject<?> item, double chance, int count) { public DropChance(RegistryObject<?> item, double chance) {
        this(item, chance, 1); // если количество не указано, будет 1
    }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.is(ModItems.CROWBAR.get())) return;

        BlockPos pos = event.getPos();
        Block block = level.getBlockState(pos).getBlock();

        RegistryObject<Block> matchedCrate = null;
        for (RegistryObject<Block> crate : BREAKABLE_CRATES) {
            Block b = crate.orElse(null);
            if (b != null && b == block) {
                matchedCrate = crate;
                break;
            }
        }
        if (matchedCrate == null) return;

        event.setCanceled(true);

        // Анимация руки
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.swing(event.getHand(), true);
        }

        // Ломаем блок без ванильного дропа
        level.destroyBlock(pos, false);

        // Случайный звук треска
        RegistryObject<?> soundObj = CRACK_SOUNDS.get(RANDOM.nextInt(CRACK_SOUNDS.size()));
        if (soundObj != null) {
            var soundEvent = soundObj.get();
            if (soundEvent instanceof net.minecraft.sounds.SoundEvent se) {
                level.playSound(null, pos, se, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

        // Дропы для конкретного ящика
        List<DropChance> dropChances = CRATE_DROPS.get(matchedCrate);
        if (dropChances == null) return;

        // 4 независимых ролла
        for (int i = 0; i < 4; i++) {
            dropChances.stream()
                    .filter(dc -> RANDOM.nextDouble() <= dc.chance())
                    .findAny()
                    .ifPresent(dc -> {
                        // Получаем объект для дропа
                        var obj = dc.item().get();
                        Item itemToDrop = null;

                        if (obj instanceof Item it) {
                            itemToDrop = it;
                        } else if (obj instanceof Block bl) {
                            itemToDrop = Item.byBlock(bl);
                        }

                        if (itemToDrop == null || itemToDrop == Items.AIR) return;

                        // Определяем стабильное количество:
                        // если у DropChance есть компонент count() — используем его,
                        // иначе дефолт 1.
                        int count = 1;

                        try {
                            // Вызов аксессора записи через отражение, если он существует
                            var m = dc.getClass().getMethod("count");
                            Object val = m.invoke(dc);
                            if (val instanceof Integer c && c > 0) {
                                count = c;
                            }
                        } catch (ReflectiveOperationException ignored) {
                            // компонента count нет — оставляем 1
                        }

                        ItemStack stack = new ItemStack(itemToDrop, count);
                        ItemEntity dropEntity = new ItemEntity(
                                level,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                stack
                        );
                        dropEntity.setDeltaMovement(
                                (RANDOM.nextDouble() - 0.5) * 0.5,
                                RANDOM.nextDouble() * 0.3 + 0.1,
                                (RANDOM.nextDouble() - 0.5) * 0.5
                        );
                        level.addFreshEntity(dropEntity);
                    });
        }
    }

}
