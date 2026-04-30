package com.cim.item.rotation;

import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class BeltItem extends Item {
    public static final int MAX_BELT_LENGTH = 16;

    public BeltItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos posB = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState stateB = level.getBlockState(posB);

        if (!(stateB.getBlock() instanceof ShaftBlock)) return InteractionResult.PASS;

        if (level.getBlockEntity(posB) instanceof ShaftBlockEntity beB) {
            // ЗАЩИТА: Кликать можно только по валам СО ШКИВАМИ
            if (!beB.hasPulley()) {
                if (!level.isClientSide) player.displayClientMessage(Component.literal("§cРемень можно натянуть только на шкивы!"), true);
                return InteractionResult.FAIL;
            }

            if (level.isClientSide) return InteractionResult.SUCCESS;

            CompoundTag nbt = stack.getOrCreateTag();

            // ШАГ 1: Запоминаем первый шкив
            if (!nbt.contains("SelectedPulley")) {
                if (beB.getConnectedPulley() != null) {
                    player.displayClientMessage(Component.literal("§cЭтот шкив уже соединен ремнем!"), true);
                    return InteractionResult.FAIL;
                }
                nbt.put("SelectedPulley", NbtUtils.writeBlockPos(posB));
                player.displayClientMessage(Component.literal("§aПервый шкив выбран. Кликните по второму."), true);
                level.playSound(null, posB, SoundEvents.LEASH_KNOT_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
                return InteractionResult.SUCCESS;
            }

            // ШАГ 2: Пытаемся соединить со вторым
            BlockPos posA = NbtUtils.readBlockPos(nbt.getCompound("SelectedPulley"));

            if (posA.equals(posB)) {
                player.displayClientMessage(Component.literal("§eЛинковка отменена."), true);
                nbt.remove("SelectedPulley");
                return InteractionResult.SUCCESS;
            }

            if (posA.distSqr(posB) > MAX_BELT_LENGTH * MAX_BELT_LENGTH) {
                player.displayClientMessage(Component.literal("§cСлишком далеко! (Макс. " + MAX_BELT_LENGTH + " блоков)"), true);
                return InteractionResult.FAIL;
            }

            BlockState stateA = level.getBlockState(posA);
            if (!(stateA.getBlock() instanceof ShaftBlock) || !(level.getBlockEntity(posA) instanceof ShaftBlockEntity beA) || !beA.hasPulley()) {
                player.displayClientMessage(Component.literal("§cПервый шкив был разрушен или снят."), true);
                nbt.remove("SelectedPulley");
                return InteractionResult.FAIL;
            }

            Direction.Axis axisA = stateA.getValue(ShaftBlock.FACING).getAxis();
            Direction.Axis axisB = stateB.getValue(ShaftBlock.FACING).getAxis();

            if (axisA != axisB) {
                player.displayClientMessage(Component.literal("§cОси шкивов не параллельны!"), true);
                return InteractionResult.FAIL;
            }

            // Проверка копланарности (лежат ли в одной плоскости)
            if ((axisA == Direction.Axis.X && posA.getX() != posB.getX()) ||
                    (axisA == Direction.Axis.Y && posA.getY() != posB.getY()) ||
                    (axisA == Direction.Axis.Z && posA.getZ() != posB.getZ())) {
                player.displayClientMessage(Component.literal("§cШкивы должны лежать в одной плоскости!"), true);
                return InteractionResult.FAIL;
            }

            if (beB.getConnectedPulley() != null || beA.getConnectedPulley() != null) {
                player.displayClientMessage(Component.literal("§cОдин из шкивов уже занят!"), true);
                nbt.remove("SelectedPulley");
                return InteractionResult.FAIL;
            }

            // УСПЕХ: Связываем
            beA.setConnectedPulley(posB);
            beB.setConnectedPulley(posA);

            nbt.remove("SelectedPulley");
            if (!player.isCreative()) stack.shrink(1);

            player.displayClientMessage(Component.literal("§aРемень успешно натянут!"), true);
            level.playSound(null, posB, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);

            // Обновляем сеть
            com.cim.api.rotation.KineticNetworkManager manager = com.cim.api.rotation.KineticNetworkManager.get((net.minecraft.server.level.ServerLevel) level);
            manager.updateNetworkAfterRemove(posA);
            manager.updateNetworkAfterPlace(posA);

            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
