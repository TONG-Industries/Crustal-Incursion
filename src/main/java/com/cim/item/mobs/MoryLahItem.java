package com.cim.item.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class MoryLahItem extends Item {

    public MoryLahItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();

        if (!level.isClientSide) {
            // 4x4 огонь вокруг места куда нажал
            for (int x = -2; x < 2; x++) {
                for (int z = -2; z < 2; z++) {
                    BlockPos firePos = clickedPos.offset(x, 1, z);
                    BlockState below = level.getBlockState(firePos.below());

                    if (level.getBlockState(firePos).isAir()) {
                        level.setBlockAndUpdate(firePos,
                                BaseFireBlock.getState(level, firePos));
                    }
                }
            }

            // Спавним свинью на 1 блок выше места нажатия
            Pig pig = new Pig(net.minecraft.world.entity.EntityType.PIG, level);
            pig.setPos(
                    clickedPos.getX() + 0.5,
                    clickedPos.getY() + 2,
                    clickedPos.getZ() + 0.5
            );
            level.addFreshEntity(pig);
        }

        // КД 3 секунды = 60 тиков
        if (player != null) {
            player.getCooldowns().addCooldown(this, 60);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}