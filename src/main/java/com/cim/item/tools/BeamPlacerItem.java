package com.cim.item.tools;

import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.deco.BeamCollisionBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class BeamPlacerItem extends Item {
    public BeamPlacerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        ItemStack toolStack = context.getItemInHand();
        CompoundTag nbt = toolStack.getOrCreateTag();

        // Берем именно тот блок, по которому кликнули (например, бетон)
        BlockPos currentPos = context.getClickedPos();

        if (nbt.contains("FirstPos")) {
            BlockPos firstPos = NbtUtils.readBlockPos(nbt.getCompound("FirstPos"));

            if (firstPos.equals(currentPos)) {
                player.sendSystemMessage(Component.literal("§cТочки не могут совпадать! Сброс связи."));
                nbt.remove("FirstPos");
                return InteractionResult.FAIL;
            }

            // Высчитываем ЦЕНТРЫ блоков
            Vec3 startVec = Vec3.atCenterOf(firstPos);
            Vec3 endVec = Vec3.atCenterOf(currentPos);

            double distance = startVec.distanceTo(endVec);
            int requiredBeams = (int) Math.ceil(distance);
            Item beamItem = ModBlocks.BEAM_BLOCK.get().asItem();

            if (!player.isCreative() && countItems(player, beamItem) < requiredBeams) {
                player.sendSystemMessage(Component.literal("§cНедостаточно балок! Требуется: §e" + requiredBeams));
                return InteractionResult.FAIL;
            }

            // --- АЛГОРИТМ ПРОКЛАДКИ ЛУЧА ---
            Vec3 direction = endVec.subtract(startVec).normalize();
            double stepSize = 0.5; // Шагаем по полблока, чтобы не пропустить ни один куб
            int steps = (int) (distance / stepSize);

            boolean masterPlaced = false;
            BlockPos masterPos = null;

            // Начинаем с i = 1, чтобы не заменить сам Блок А (откуда начинаем)
            for (int i = 1; i < steps; i++) {
                Vec3 stepVec = startVec.add(direction.scale(i * stepSize));
                BlockPos posOnLine = BlockPos.containing(stepVec);

                // Если по этим координатам воздух или вода (можно заменить)
                if (level.getBlockState(posOnLine).canBeReplaced()) {
                    level.setBlock(posOnLine, ModBlocks.BEAM_COLLISION.get().defaultBlockState(), 3);

                    BlockEntity be = level.getBlockEntity(posOnLine);
                    if (be instanceof BeamCollisionBlockEntity collisionBE) {
                        if (!masterPlaced) {
                            // Самый первый пустой блок становится "Мастером". Он будет рендерить балку!
                            collisionBE.setMasterData(startVec, endVec);
                            masterPlaced = true;
                            masterPos = posOnLine;
                        } else {
                            // Остальные блоки просто знают, кто их Мастер (чтобы при поломке разрушить всю цепь)
                            collisionBE.setSlaveData(masterPos);
                        }
                    }
                }
            }

            if (!player.isCreative()) consumeItems(player, beamItem, requiredBeams);
            player.sendSystemMessage(Component.literal("§aБалка установлена! Потрачено: " + requiredBeams));
            nbt.remove("FirstPos");

        } else {
            nbt.put("FirstPos", NbtUtils.writeBlockPos(currentPos));
            player.sendSystemMessage(Component.literal("§aПервая точка (центр) закреплена."));
        }

        return InteractionResult.SUCCESS;
    }

    private int countItems(Player player, Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private void consumeItems(Player player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                int toTake = Math.min(stack.getCount(), remaining);
                stack.shrink(toTake);
                remaining -= toTake;
                if (remaining <= 0) break;
            }
        }
    }
}