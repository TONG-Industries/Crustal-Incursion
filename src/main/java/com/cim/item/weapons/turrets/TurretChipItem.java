package com.cim.item.weapons.turrets;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TurretChipItem extends Item {
    public TurretChipItem(Properties pProperties) {
        super(pProperties.stacksTo(1)); // Чип не стакается
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Работаем только на сервере и если нажат Shift
        if (!level.isClientSide && player.isShiftKeyDown()) {
            CompoundTag nbt = stack.getOrCreateTag();
            ListTag ownersList;

            // Получаем или создаем список владельцев
            if (nbt.contains("TurretOwners", Tag.TAG_LIST)) {
                ownersList = nbt.getList("TurretOwners", Tag.TAG_STRING);
            } else {
                ownersList = new ListTag();
            }

            String playerUUID = player.getUUID().toString();
            String playerName = player.getName().getString();

            // Проверяем, есть ли уже этот игрок
            boolean alreadyAdded = false;
            for (Tag tag : ownersList) {
                if (tag.getAsString().contains(playerUUID)) {
                    alreadyAdded = true;
                    break;
                }
            }

            if (alreadyAdded) {
                player.sendSystemMessage(Component.literal("You are already authorized on this chip!").withStyle(ChatFormatting.RED));
            } else {
                if (ownersList.size() >= 5) {
                    player.sendSystemMessage(Component.literal("Memory full! Max 5 owners.").withStyle(ChatFormatting.RED));
                } else {
                    // Сохраняем в формате "UUID|Name" для удобства отображения
                    ownersList.add(StringTag.valueOf(playerUUID + "|" + playerName));
                    nbt.put("TurretOwners", ownersList);
                    stack.setTag(nbt);
                    player.sendSystemMessage(Component.literal("Authorized: " + playerName).withStyle(ChatFormatting.GREEN));
                }
            }
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        if (stack.hasTag() && stack.getTag().contains("TurretOwners")) {
            tooltipComponents.add(Component.literal("Authorized Users:").withStyle(ChatFormatting.GRAY));
            ListTag list = stack.getTag().getList("TurretOwners", Tag.TAG_STRING);

            if (list.isEmpty()) {
                tooltipComponents.add(Component.literal("- None").withStyle(ChatFormatting.DARK_GRAY));
            }

            for (Tag tag : list) {
                // Разделяем UUID и Имя, показываем только Имя
                String entry = tag.getAsString();
                String name = entry.contains("|") ? entry.split("\\|")[1] : entry;
                tooltipComponents.add(Component.literal("- " + name).withStyle(ChatFormatting.GOLD));
            }
        } else {
            tooltipComponents.add(Component.literal("Shift+RClick to authorize yourself").withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}
