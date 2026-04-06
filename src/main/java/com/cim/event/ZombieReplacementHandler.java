package com.cim.event;

import com.cim.entity.ModEntities;
import com.cim.entity.mobs.grenadier.GrenadierZombieEntity;
import com.cim.main.CrustalIncursionMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID)
public class ZombieReplacementHandler {

    // Шанс замены: 5% = 1 из 20
    private static final double REPLACEMENT_CHANCE = 0.05;

    @SubscribeEvent
    public static void onZombieFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        // Проверяем, что это ванильный зомби
        if (event.getEntity().getType() != EntityType.ZOMBIE) return;
        if (event.getEntity() instanceof GrenadierZombieEntity) return;

        // Не заменяем если из яйца призыва или спавнера
        if (event.getSpawnType() == MobSpawnType.SPAWN_EGG) return;
        if (event.getSpawnType() == MobSpawnType.SPAWNER) return;

        // Приводим ServerLevelAccessor к ServerLevel
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Zombie zombie = (Zombie) event.getEntity();

        // Проверяем шанс
        if (zombie.getRandom().nextDouble() < REPLACEMENT_CHANCE) {
            // Отменяем спавн ванильного зомби
            event.setSpawnCancelled(true);

            // Создаём гренадёра
            GrenadierZombieEntity grenadier = ModEntities.GRENADIER_ZOMBIE.get().create(level);
            if (grenadier != null) {
                grenadier.moveTo(
                        zombie.getX(),
                        zombie.getY(),
                        zombie.getZ(),
                        zombie.getYRot(),
                        zombie.getXRot()
                );

                // Копируем броню
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack item = zombie.getItemBySlot(slot);
                    if (!item.isEmpty()) {
                        grenadier.setItemSlot(slot, item.copy());
                        grenadier.setDropChance(slot, 0.085F);
                    }
                }

                // Спавним гренадёра
                level.addFreshEntity(grenadier);

                // Вызываем finalizeSpawn для гренадёра
                grenadier.finalizeSpawn(
                        event.getLevel(),
                        event.getDifficulty(),
                        event.getSpawnType(),
                        null,
                        null
                );
            }
        }
    }
}