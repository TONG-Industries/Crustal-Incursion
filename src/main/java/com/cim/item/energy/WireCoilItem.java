package com.cim.item.energy;

import com.cim.block.entity.energy.ConnectorBlockEntity;
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

public class WireCoilItem extends Item {
    public WireCoilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);

        // Если кликнули НЕ по коннектору
        if (!(be instanceof ConnectorBlockEntity currentConnector)) {
            // Если игрок кликнул с Shift по воздуху/другому блоку — сбрасываем сохраненные координаты
            if (player != null && player.isShiftKeyDown() && stack.hasTag() && stack.getTag().contains("FirstPos")) {
                stack.getTag().remove("FirstPos");
                player.displayClientMessage(Component.literal("§eСоединение отменено."), true);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        CompoundTag tag = stack.getOrCreateTag();

        // ================= ПЕРВЫЙ КЛИК =================
        if (!tag.contains("FirstPos")) {
            // Проверяем, есть ли свободные слоты для проводов у этого коннектора
            if (currentConnector.getConnections().size() >= currentConnector.getTier().maxConnections()) {
                if (player != null) player.displayClientMessage(Component.literal("§cЭтот коннектор уже полностью занят!"), true);
                return InteractionResult.FAIL;
            }

            // Сохраняем координаты в предмет
            tag.put("FirstPos", NbtUtils.writeBlockPos(pos));
            if (player != null) player.displayClientMessage(Component.literal("§aНачато соединение... Кликните по второму коннектору."), true);
            return InteractionResult.SUCCESS;
        }

        // ================= ВТОРОЙ КЛИК =================
        else {
            BlockPos firstPos = NbtUtils.readBlockPos(tag.getCompound("FirstPos"));

            // Очищаем тег в любом случае, чтобы игрок не застрял, если произойдёт ошибка
            tag.remove("FirstPos");

            // 1. Проверка на клик по тому же самому блоку
            if (pos.equals(firstPos)) {
                if (player != null) player.displayClientMessage(Component.literal("§cНельзя соединить коннектор с самим собой!"), true);
                return InteractionResult.FAIL;
            }

            BlockEntity firstBe = level.getBlockEntity(firstPos);
            if (!(firstBe instanceof ConnectorBlockEntity firstConnector)) {
                if (player != null) player.displayClientMessage(Component.literal("§cПервый коннектор был разрушен или потерян."), true);
                return InteractionResult.FAIL;
            }

            // 2. Проверка лимитов подключений для ОБОИХ коннекторов
            if (firstConnector.getConnections().size() >= firstConnector.getTier().maxConnections()) {
                if (player != null) player.displayClientMessage(Component.literal("§cПервый коннектор уже полностью занят!"), true);
                return InteractionResult.FAIL;
            }
            if (currentConnector.getConnections().size() >= currentConnector.getTier().maxConnections()) {
                if (player != null) player.displayClientMessage(Component.literal("§cВторой коннектор уже полностью занят!"), true);
                return InteractionResult.FAIL;
            }

            // 3. Проверка: не соединены ли они уже друг с другом?
            if (firstConnector.getConnections().contains(pos) || currentConnector.getConnections().contains(firstPos)) {
                if (player != null) player.displayClientMessage(Component.literal("§cЭти коннекторы уже соединены!"), true);
                return InteractionResult.FAIL;
            }

            // 4. Проверка дистанции (берём наименьшую из двух, чтобы нельзя было обмануть систему слабым коннектором)
            double distance = Math.sqrt(firstPos.distSqr(pos));
            int maxDist1 = firstConnector.getTier().maxLength();
            int maxDist2 = currentConnector.getTier().maxLength();
            int maxAllowed = Math.min(maxDist1, maxDist2);

            if (distance > maxAllowed) {
                if (player != null) player.displayClientMessage(Component.literal("§cСлишком далеко! Максимальная длина: " + maxAllowed + " блоков."), true);
                return InteractionResult.FAIL;
            }

            // ================= УСПЕХ: СОЕДИНЯЕМ =================
            // Записываем друг друга в память
            firstConnector.connectTo(pos);
            currentConnector.connectTo(firstPos);

            // Тратим 1 предмет из стака, если игрок не в креативе
            if (player != null && !player.isCreative()) {
                stack.shrink(1);
            }

            if (player != null) player.displayClientMessage(Component.literal("§bСоединение успешно установлено!"), true);
            return InteractionResult.SUCCESS;
        }
    }
}