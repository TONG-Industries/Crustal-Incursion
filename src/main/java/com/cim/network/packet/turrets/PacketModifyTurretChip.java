package com.cim.network.packet.turrets;




import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import com.cim.item.weapons.turrets.TurretChipItem;
import com.cim.menu.TurretLightMenu;
import com.cim.network.ModPacketHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketModifyTurretChip {
    private final int action; // 0 = REMOVE, 1 = ADD
    private final String payload; // Индекс для удаления или Ник для добавления

    public PacketModifyTurretChip(int action, String payload) {
        this.action = action;
        this.payload = payload;
    }

    public PacketModifyTurretChip(FriendlyByteBuf buf) {
        this.action = buf.readInt();
        this.payload = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(action);
        buf.writeUtf(payload);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof TurretLightMenu menu) {
                ItemStack stack = menu.getAmmoContainer().getStackInSlot(9);

                if (stack.getItem() instanceof TurretChipItem) {
                    CompoundTag nbt = stack.getOrCreateTag();
                    ListTag list = nbt.getList("TurretOwners", Tag.TAG_STRING);

                    // --- УДАЛЕНИЕ ---
                    if (action == 0) {
                        try {
                            int index = Integer.parseInt(payload);
                            if (index >= 0 && index < list.size()) {
                                list.remove(index);
                                nbt.put("TurretOwners", list);
                                stack.setTag(nbt);
                            }
                        } catch (Exception ignored) {}
                    }

                    // --- ДОБАВЛЕНИЕ ---
                    else if (action == 1) {
                        String targetName = payload;
                        // Ищем профиль игрока (даже оффлайн)
                        Optional<com.mojang.authlib.GameProfile> profile = player.getServer().getProfileCache().get(targetName);

                        if (profile.isPresent()) {
                            UUID id = profile.get().getId();
                            String name = profile.get().getName();
                            String entry = id.toString() + "|" + name;

                            // Проверка на дубликаты
                            boolean exists = false;
                            for (Tag t : list) {
                                if (t.getAsString().equals(entry)) exists = true;
                            }

                            if (!exists && list.size() < 5) {
                                list.add(StringTag.valueOf(entry));
                                nbt.put("TurretOwners", list);
                                stack.setTag(nbt);
                                // Отправляем успех
                                sendFeedback(player, true);
                            } else {
                                // Дубликат или место кончилось -> считаем как успех (чтобы не пугать 404), но не добавляем
                                // Либо можно сделать статус "FULL"
                                sendFeedback(player, true);
                            }
                        } else {
                            // Игрок не найден -> 404
                            sendFeedback(player, false);
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    private void sendFeedback(ServerPlayer player, boolean success) {
        ModPacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketChipFeedback(success)
        );
    }
}
