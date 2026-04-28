package com.cim.item.mobs;


import com.cim.entity.ModEntities;
import com.cim.entity.mobs.depth_worm.DepthWormBrutalEntity;
import com.cim.entity.mobs.depth_worm.DepthWormEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class DepthWormBrutalSpawnEggItem extends Item {
    public DepthWormBrutalSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            DepthWormBrutalEntity worm = ModEntities.DEPTH_WORM_BRUTAL.get().create(level);
            if (worm != null) {
                worm.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                level.addFreshEntity(worm);
                context.getItemInHand().shrink(1); // Тратим предмет
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
    }
}