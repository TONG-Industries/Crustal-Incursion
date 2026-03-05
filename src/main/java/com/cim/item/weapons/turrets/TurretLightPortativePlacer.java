package com.cim.item.weapons.turrets;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import com.cim.entity.ModEntities;
import com.cim.entity.weapons.turrets.TurretLightEntity;

public class TurretLightPortativePlacer extends Item {
    public TurretLightPortativePlacer(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            TurretLightEntity turret = ModEntities.TURRET_LIGHT.get().create(level);
            if (turret != null) {
                turret.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);

                // Устанавливаем владельца
                Player player = context.getPlayer();
                if (player != null) {
                    turret.setOwner(player);
                }

                // Задаём время жизни (3 минуты) и боезапас (20 патронов)
                turret.setLifetime(3600);
                turret.setAmmo(250);

                level.addFreshEntity(turret);
                context.getItemInHand().shrink(1);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
    }
}