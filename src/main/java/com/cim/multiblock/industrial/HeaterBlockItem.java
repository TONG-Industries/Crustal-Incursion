//package com.cim.multiblock.industrial;
//
//import net.minecraft.world.InteractionResult;
//import net.minecraft.world.item.BlockItem;
//import net.minecraft.world.item.context.BlockPlaceContext;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.level.block.Block;
//
//public class HeaterBlockItem extends BlockItem {
//    public HeaterBlockItem(Block block, Properties properties) {
//        super(block, properties);
//    }
//
//    @Override
//    public InteractionResult place(BlockPlaceContext context) {
//        Level level = context.getLevel();
//        if (!level.isClientSide) {
//            // Теперь метод существует в HeaterBlock
//            if (!HeaterBlock.canPlace(level, context.getClickedPos())) {
//                return InteractionResult.FAIL;
//            }
//        }
//        return super.place(context);
//    }
//}