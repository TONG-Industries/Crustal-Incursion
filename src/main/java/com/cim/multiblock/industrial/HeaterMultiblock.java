//package com.cim.multiblock.industrial;
//
//import com.cim.multiblock.system.MultiblockController;
//import com.cim.multiblock.system.MultiblockPattern;
//import net.minecraft.core.BlockPos;
//import net.minecraft.nbt.CompoundTag;
//import net.minecraft.network.chat.Component;
//import net.minecraft.world.InteractionHand;
//import net.minecraft.world.InteractionResult;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.level.Level;
//import net.minecraft.world.phys.BlockHitResult;
//
//public class HeaterMultiblock extends MultiblockController {
//
//    private int heatLevel = 0;
//    private static final int MAX_HEAT = 1000;
//
//    public HeaterMultiblock(Level level, BlockPos origin) {
//        super(level, origin, createPattern());
//    }
//
//    private static MultiblockPattern createPattern() {
//        // 3x1x3 полностью заполненный (все 9 блоков)
//        return MultiblockPattern.fromLayers(
//                """
//                ###
//                ###
//                ###
//                """
//        );
//    }
//
//    @Override
//    public void tick() {
//        if (!isValid) return;
//        if (heatLevel < MAX_HEAT) {
//            heatLevel++;
//        }
//    }
//
//    @Override
//    public InteractionResult onUse(Player player, InteractionHand hand, BlockHitResult hit, BlockPos clickedPos) {
//        if (!level.isClientSide()) {
//            player.sendSystemMessage(Component.literal(
//                    "§6[HEATER] §fHeat: §c" + heatLevel + "§f/§c" + MAX_HEAT +
//                            " §7| Valid: " + (isValid ? "§aYES" : "§cNO") +
//                            " §7| Origin: " + origin
//            ));
//        }
//        return InteractionResult.SUCCESS;
//    }
//
//    @Override
//    public void onBreak() {
//        notifyPlayers("§cHeater broken at " + origin);
//    }
//
//    @Override
//    public CompoundTag save(CompoundTag tag) {
//        tag.putInt("Heat", heatLevel);
//        return tag;
//    }
//
//    @Override
//    public void load(CompoundTag tag) {
//        heatLevel = tag.getInt("Heat");
//    }
//}