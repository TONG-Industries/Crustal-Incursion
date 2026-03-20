package com.cim.multiblock.system;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

public class MultiblockBlockItem extends BlockItem {

    public MultiblockBlockItem(Block block, Properties properties) {
        super(block, properties);
        // Защита от дурака: этот Item можно использовать только с блоками-контроллерами
        if (!(block instanceof IMultiblockController)) {
            throw new IllegalArgumentException("MultiblockBlockItem can only be used with blocks that implement IMultiblockController!");
        }
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        IMultiblockController controller = (IMultiblockController) this.getBlock();
        Level level = context.getLevel();
        Player player = context.getPlayer();

        // Убеждаемся, что у блока есть направление (FACING)
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return false;
        }

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        if (facing == null || facing.getAxis() == Direction.Axis.Y) {
            return false;
        }

        // Вызываем проверку из нашего Ядра ДО установки блока
        if (controller.getStructureHelper().checkPlacement(level, context.getClickedPos(), facing, player)) {
            return super.placeBlock(context, state);
        } else {
            return false; // Места нет - блок не ставится, предмет не тратится!
        }
    }
}