//package com.cim.multiblock.industrial;
//
//import com.cim.block.basic.ModBlocks;
//import com.cim.block.entity.ModBlockEntities;
//import com.cim.multiblock.system.MultiblockBlockEntity;
//import com.cim.multiblock.system.MultiblockPattern;
//import net.minecraft.core.BlockPos;
//import net.minecraft.nbt.CompoundTag;
//import net.minecraft.network.chat.Component;
//import net.minecraft.world.InteractionHand;
//import net.minecraft.world.InteractionResult;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.phys.BlockHitResult;
//
//public class HeaterBlockEntity extends MultiblockBlockEntity {
//    private int heatLevel = 0;
//    private static final int MAX_HEAT = 1000;
//
//    public HeaterBlockEntity(BlockPos pos, BlockState state) {
//        super(ModBlockEntities.HEATER_BE.get(), pos, state);
//    }
//
//    /**
//     * Статический метод для тикера
//     */
//    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, HeaterBlockEntity be) {
//        be.tickMultiblock();
//    }
//
//    @Override
//    public void tickMultiblock() {
//        if (!isFormed || !isController) return;
//
//        if (heatLevel < MAX_HEAT) {
//            heatLevel++;
//        }
//
//        if (level.getGameTime() % 20 == 0) {
//            setChanged();
//        }
//    }
//
//    @Override
//    public InteractionResult onMultiblockUse(Player player, InteractionHand hand, BlockHitResult hit) {
//        if (!level.isClientSide()) {
//            player.sendSystemMessage(Component.literal(
//                    "§6[HEATER] §fHeat: §c" + heatLevel + "§f/§c" + MAX_HEAT +
//                            " §7| Valid: " + (isFormed ? "§aYES" : "§cNO")
//            ));
//        }
//        return InteractionResult.SUCCESS;
//    }
//
//    @Override
//    public MultiblockPattern getPattern() {
//        return MultiblockPattern.fromLayers(
//                """
//                ###
//                #O#
//                ###
//                """
//        );
//    }
//
//    @Override
//    protected Block getPartBlock() {
//        // Возвращаем блок частей, а не контроллер!
//        return ModBlocks.MULTIBLOCK_PART.get();
//    }
//
//    @Override
//    protected void onMultiblockBreak() {
//        if (level != null && !level.isClientSide) {
//            level.players().forEach(p -> {
//                if (p.distanceToSqr(worldPosition.getCenter()) < 256) {
//                    p.sendSystemMessage(Component.literal("§cHeater destroyed!"));
//                }
//            });
//        }
//    }
//
//    @Override
//    protected void saveAdditional(CompoundTag tag) {
//        super.saveAdditional(tag);
//        tag.putInt("Heat", heatLevel);
//    }
//
//    @Override
//    public void load(CompoundTag tag) {
//        super.load(tag);
//        heatLevel = tag.getInt("Heat");
//    }
//}