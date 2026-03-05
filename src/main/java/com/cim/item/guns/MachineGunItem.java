package com.cim.item.guns;


import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import com.cim.client.config.ModKeyBindings;
import com.cim.client.gecko.item.guns.MachineGunRenderer;
import com.cim.entity.weapons.bullets.TurretBulletEntity;
import com.cim.item.tags.AmmoRegistry;
import com.cim.main.CrustalIncursionMod;
import com.cim.network.ModPacketHandler;
import com.cim.network.packet.guns.PacketReloadGun;
import com.cim.network.packet.guns.PacketShoot;
import com.cim.sound.ModSounds;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class MachineGunItem extends Item implements GeoItem {

    private static final int SHOT_ANIM_TICKS = 14;
    private static final int MAG_CAPACITY = 24;
    private static final int MAX_TOTAL_AMMO = MAG_CAPACITY + 1;
    private static final int RELOAD_ANIM_TICKS = 100;
    private static final int FLIP_ANIM_TICKS = 80;
    private static final int RELOAD_AMMO_ADD_TICK = 95;
    private static final String LOADED_AMMO_ID_TAG = "LoadedAmmoID";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MachineGunItem(Properties properties) {
        super(properties.stacksTo(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (oldStack.getItem() == newStack.getItem() && !slotChanged) return false;
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    // === NBT МЕТОДЫ ===
    public int getAmmo(ItemStack stack) { return stack.getOrCreateTag().getInt("Ammo"); }
    public void setAmmo(ItemStack stack, int ammo) { stack.getOrCreateTag().putInt("Ammo", Math.max(0, Math.min(ammo, MAX_TOTAL_AMMO))); }
    public int getShootDelay(ItemStack stack) { return stack.getOrCreateTag().getInt("ShootDelay"); }
    public void setShootDelay(ItemStack stack, int delay) { stack.getOrCreateTag().putInt("ShootDelay", delay); }
    public int getReloadTimer(ItemStack stack) { return stack.getOrCreateTag().getInt("ReloadTimer"); }
    public void setReloadTimer(ItemStack stack, int timer) { stack.getOrCreateTag().putInt("ReloadTimer", timer); }
    public int getPendingAmmo(ItemStack stack) { return stack.getOrCreateTag().getInt("PendingAmmo"); }
    public void setPendingAmmo(ItemStack stack, int ammo) { stack.getOrCreateTag().putInt("PendingAmmo", ammo); }
    public String getLoadedAmmoID(ItemStack stack) { return stack.getOrCreateTag().getString(LOADED_AMMO_ID_TAG); }
    public void setLoadedAmmoID(ItemStack stack, String ammoID) { stack.getOrCreateTag().putString(LOADED_AMMO_ID_TAG, ammoID); }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        if (!level.isClientSide && entity instanceof Player player) {

            // 1. СНАЧАЛА проверяем, держит ли игрок предмет
            if (!isSelected) {
                // Если таймер был запущен, мы его сбрасываем
                if (getReloadTimer(stack) > 0) {
                    setReloadTimer(stack, 0);
                    setPendingAmmo(stack, 0);
                    // Важно: помечаем инвентарь как "грязный", чтобы сервер переслал его клиенту
                    player.getInventory().setChanged();
                }
                // ВАЖНО: Выходим сразу, не даем выполняться коду ниже
                return;
            }

            // 2. Только если предмет в руках — выполняем остальную логику
            int delay = getShootDelay(stack);
            if (delay > 0) setShootDelay(stack, delay - 1);

            int reloadTimer = getReloadTimer(stack);
            if (reloadTimer > 0) {
                setReloadTimer(stack, reloadTimer - 1);

                // ✅ НОВОЕ: Изъятие патронов на 50 тике (2.5 сек)
                if (reloadTimer == (RELOAD_ANIM_TICKS - 50) || reloadTimer == (FLIP_ANIM_TICKS - 50)) {
                    // Забираем патроны из инвентаря ЗДЕСЬ
                    int pending = getPendingAmmo(stack);
                    if (pending > 0 && !player.isCreative()) {
                        String loadedId = getLoadedAmmoID(stack);
                        if (loadedId != null && !loadedId.isEmpty()) {
                            consumeAmmoById(player, loadedId, pending);
                            player.getInventory().setChanged();
                        }
                    }
                }

                // Добавление патронов в оружие на 10 тике (конец анимации)
                if (reloadTimer == (RELOAD_ANIM_TICKS - RELOAD_AMMO_ADD_TICK) ||
                        reloadTimer == (FLIP_ANIM_TICKS - RELOAD_AMMO_ADD_TICK)) {
                    int pending = getPendingAmmo(stack);
                    if (pending > 0) {
                        setAmmo(stack, getAmmo(stack) + pending);
                        setPendingAmmo(stack, 0);
                        syncHand(player, stack);
                    }
                }
            }
        }
    }


    /** Подсчитывает количество патронов конкретного ID в инвентаре (не изымая). */
    private int countAmmoById(Player player, String ammoId) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;
            String id = ForgeRegistries.ITEMS.getKey(slot.getItem()).toString();
            if (!ammoId.equals(id)) continue;
            count += slot.getCount();
        }
        return count;
    }


    private void syncHand(Player player, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = serverPlayer.getInventory().selected;
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(-2, 0, slot, stack));
        }
    }

    // === ПЕРЕЗАРЯДКА ===
    public void reloadGun(Player player, ItemStack stack) {
        if (player.level().isClientSide) return;
        if (getReloadTimer(stack) > 0) return;

        long instanceId = GeoItem.getOrAssignId(stack, (ServerLevel) player.level());
        int currentAmmo = getAmmo(stack);

        // 1) Полный магазин -> FLIP (разрядить/проверить)
        if (currentAmmo >= MAX_TOTAL_AMMO) {
            triggerAnim(player, instanceId, "controller", "flip");
            setReloadTimer(stack, FLIP_ANIM_TICKS);
            playSound(player, 1.0F);
            return;
        }

        String currentLoadedID = getLoadedAmmoID(stack);

        // Ищем патрон, который МОЖНО зарядить.
        // Если в магазине что-то есть (currentAmmo > 0) -> ищем СТРОГО такой же ID.
        // Если магазин пуст -> ищем любой подходящий калибра "20mm_turret".
        String targetAmmoId = findAmmoIdForReload(player, (currentAmmo > 0 && currentLoadedID != null && !currentLoadedID.isEmpty()) ? currentLoadedID : null);

        // 2) Если подходящих патронов в инвентаре НЕТ -> FLIP (даже в креативе!)
        if (targetAmmoId == null) {
            triggerAnim(player, instanceId, "controller", "flip");
            setReloadTimer(stack, FLIP_ANIM_TICKS);
            playSound(player, 1.5F); // Звук "пусто" или "затвор"
            return;
        }

        // 3) Патроны ЕСТЬ (мы нашли targetAmmoId). Начинаем перезарядку.

        // КРЕАТИВ:
        if (player.isCreative()) {
            int toAdd = MAX_TOTAL_AMMO - currentAmmo;
            setPendingAmmo(stack, toAdd);

            // Если магазин был пуст — ставим тип найденного патрона
            if (currentAmmo == 0) {
                setLoadedAmmoID(stack, targetAmmoId);
            }

            triggerAnim(player, instanceId, "controller", "reload");
            setReloadTimer(stack, RELOAD_ANIM_TICKS);
            playSound(player, 1.0F);
            return;
        }

        // ВЫЖИВАНИЕ:
        // ВЫЖИВАНИЕ:
        int needed = MAX_TOTAL_AMMO - currentAmmo;
        // ✅ Теперь просто ПРОВЕРЯЕМ, есть ли патроны, но НЕ изымаем их сразу
        int available = countAmmoById(player, targetAmmoId);
        int taken = Math.min(needed, available);
        if (taken > 0) {
        }

        if (taken > 0) {
            if (currentAmmo == 0) {
                setLoadedAmmoID(stack, targetAmmoId);
            }
            setPendingAmmo(stack, taken);
            player.getInventory().setChanged();
            triggerAnim(player, instanceId, "controller", "reload");
            setReloadTimer(stack, RELOAD_ANIM_TICKS);
            playSound(player, 1.0F);
        } else {
            // На всякий случай (хотя проверка выше должна была отловить) -> FLIP
            triggerAnim(player, instanceId, "controller", "flip");
            setReloadTimer(stack, FLIP_ANIM_TICKS);
            playSound(player, 1.5F);
        }
    }

    // === ЛОГИКА РАЗРЯДКИ ===
    public void unloadGun(ServerPlayer player, ItemStack stack) {
        // 1. Проверяем, есть ли патроны и не идет ли перезарядка
        int currentAmmo = getAmmo(stack);
        if (currentAmmo <= 0) return;
        if (getReloadTimer(stack) > 0) return; // Нельзя разрядить во время перезарядки

        String loadedID = getLoadedAmmoID(stack);
        if (loadedID == null || loadedID.isEmpty()) return;

        // 2. Определяем предмет патрона
        Item ammoItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(loadedID));
        if (ammoItem == null) return;

        // 3. Рассчитываем, сколько вернуть
        int amountToReturn;
        if (player.isCreative()) {
            amountToReturn = 1; // В креативе возвращаем 1 шт просто чтобы взять в руку
        } else {
            amountToReturn = currentAmmo; // В выживании всё, что было в магазине
        }

        // 4. Создаем стак для выдачи
        ItemStack returnedStack = new ItemStack(ammoItem, amountToReturn);

        // 5. Пытаемся добавить в инвентарь, если не влезает - кидаем под ноги
        if (!player.getInventory().add(returnedStack)) {
            player.drop(returnedStack, false);
        }

        // 6. Очищаем оружие
        setAmmo(stack, 0);
        setLoadedAmmoID(stack, "");

        // 7. Звук разрядки (можно добавить свой, пока возьмем щелчок)
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 1.0F, 1.5F);

        // (Опционально) Анимация разрядки, если есть
        // triggerAnim(player, GeoItem.getOrAssignId(stack, (ServerLevel)player.level()), "controller", "unload");
    }


    /** Ищет первый подходящий ID патрона в инвентаре. Если requiredId != null, ищет строго его. */
    @Nullable
    private String findAmmoIdForReload(Player player, @Nullable String requiredId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;

            String caliber = AmmoRegistry.getCaliber(slot);
            if (!"20mm_turret".equals(caliber)) continue;

            String id = ForgeRegistries.ITEMS.getKey(slot.getItem()).toString();

            // Если нам нужен конкретный ID (дозарядка), пропускаем все остальные
            if (requiredId != null && !requiredId.equals(id)) continue;

            return id; // Нашли подходящий!
        }
        return null;
    }

    /** Забирает патроны конкретного ID из инвентаря. */
    private int consumeAmmoById(Player player, String ammoId, int needed) {
        int taken = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (taken >= needed) break;

            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) continue;
            if (!AmmoRegistry.isValidAmmo(slot)) continue;

            String id = ForgeRegistries.ITEMS.getKey(slot.getItem()).toString();
            if (!ammoId.equals(id)) continue;

            int toTake = Math.min(slot.getCount(), needed - taken);
            slot.shrink(toTake);
            taken += toTake;
            if (slot.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        return taken;
    }


    // Вспомогательный метод поиска ID патрона
    private String findAmmoIDInInventory(Player player, ItemStack gunStack) {
        String currentLoadedID = getLoadedAmmoID(gunStack);
        int currentAmmo = getAmmo(gunStack);

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slotStack = player.getInventory().getItem(i);
            if (!slotStack.isEmpty() && AmmoRegistry.isValidAmmo(slotStack)) {

                // Проверка калибра
                String caliber = AmmoRegistry.getCaliber(slotStack);
                if (!"20mm_turret".equals(caliber)) continue;

                String slotItemID = ForgeRegistries.ITEMS.getKey(slotStack.getItem()).toString();

                // Если в оружии уже есть патроны, ищем только такие же
                if (currentAmmo > 0 && currentLoadedID != null && !currentLoadedID.isEmpty()) {
                    if (currentLoadedID.equals(slotItemID)) return slotItemID;
                } else {
                    // Если оружие пустое, возвращаем первый подходящий
                    return slotItemID;
                }
            }
        }
        return null;
    }

    // Вспомогательный метод изъятия патронов
    private int consumeAmmo(Player player, String targetID, int countNeeded) {
        int gathered = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (gathered >= countNeeded) break;

            ItemStack slotStack = player.getInventory().getItem(i);
            if (!slotStack.isEmpty() && AmmoRegistry.isValidAmmo(slotStack)) {
                String slotItemID = ForgeRegistries.ITEMS.getKey(slotStack.getItem()).toString();
                if (targetID.equals(slotItemID)) {
                    int take = Math.min(slotStack.getCount(), countNeeded - gathered);
                    slotStack.shrink(take);
                    gathered += take;
                    if (slotStack.isEmpty()) {
                        player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
        return gathered;
    }

    // Сделали метод пустым, чтобы не ломать код в местах вызова,
    private void playSound(Player player, float pitch) {
        // Ничего не делаем, звуки теперь управляются через GeckoLib
    }

    // === СТРЕЛЬБА ===
    public void performShooting(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) return;
        if (getReloadTimer(stack) > 0 || getShootDelay(stack) > 0) return;

        int ammo = getAmmo(stack);

        // ✅ НОВАЯ ЛОГИКА: Пустой выстрел (ammo == 0)
        if (ammo <= 0) {
            // Звук сухого выстрела
            SoundEvent drySound = ModSounds.DRY_FIRE.isPresent() ? ModSounds.DRY_FIRE.get() : SoundEvents.DISPENSER_FAIL;
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    drySound, SoundSource.PLAYERS, 1.0F, 1.0F);

            // Задержка как при обычном выстреле
            setShootDelay(stack, SHOT_ANIM_TICKS);

            // Запускаем анимацию пустого выстрела
            if (level instanceof ServerLevel serverLevel) {
                triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", "shot_empty");
            }

            return; // Пуля НЕ вылетает
        }

        // Дальше идет обычная стрельба (твой старый код без изменений)
        String loadedID = getLoadedAmmoID(stack);

        if (!player.isCreative()) {
            setAmmo(stack, ammo - 1);
        }

        syncHand(player, stack);
        setShootDelay(stack, SHOT_ANIM_TICKS);

        // ✅ СПАВН ПУЛИ НА СЕРВЕРЕ
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 1. Создаем пулю
        TurretBulletEntity bullet = new TurretBulletEntity(serverLevel, player);

        // 2. Боеприпас - ✅ ИСПРАВЛЕНО
        AmmoRegistry.AmmoType ammoInfo = null;

        if (loadedID != null && !loadedID.isEmpty()) {
            // ✅ ПРАВИЛЬНО: ищем через ForgeRegistries
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(loadedID));
            if (item != null) {
                ammoInfo = AmmoRegistry.getAmmoTypeFromItem(item);
            }
        }

        if (ammoInfo == null) {
            ammoInfo = new AmmoRegistry.AmmoType("default", "20mm_turret", 6.0f, 3.0f, false);
        }

        bullet.setAmmoType(ammoInfo);

        // 3. Параметры выстрела
        Vec3 lookDir = player.getLookAngle();
        Vec3 velocity = lookDir.normalize().add(
                level.random.nextGaussian() * 0.0075 * 1.0F,
                level.random.nextGaussian() * 0.0075 * 1.0F,
                level.random.nextGaussian() * 0.0075 * 1.0F
        ).scale(ammoInfo.speed);

        // 4. Смещение вправо
        Vec3 right = lookDir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 spawnPos = player.position().add(right.scale(0.2)).add(0, player.getEyeY() - player.getY() - 0.1, 0);

        // 5. Устанавливаем
        bullet.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        bullet.setDeltaMovement(velocity);
        bullet.alignToVelocity();

        // ✅ ДОБАВЛЯЕМ В МИР
        serverLevel.addFreshEntity(bullet);

        // Звук
        float pitch = 0.9F + level.random.nextFloat() * 0.2F;
        SoundEvent shotSound = ModSounds.TURRET_FIRE.isPresent() ? ModSounds.TURRET_FIRE.get() : SoundEvents.GENERIC_EXPLODE;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), shotSound, SoundSource.PLAYERS, 1.0F, pitch);

        // Анимация
        triggerAnim(player, GeoItem.getOrAssignId(stack, serverLevel), "controller", "shot");
    }


    // === GECKOLIB КОНТРОЛЛЕР ===
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, event -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return PlayState.CONTINUE;

            ItemStack mainHandStack = mc.player.getMainHandItem();
            if (mainHandStack.getItem() != this) {
                // Мягкий сброс, если убрали предмет
                return PlayState.STOP;
            }

            // 1. ЗАЩИТА: Если играют приоритетные анимации — не трогаем
            if (event.getController().getAnimationState() == AnimationController.State.RUNNING) {
                String currentAnim = event.getController().getCurrentAnimation().animation().name();
                if ("reload".equals(currentAnim) || "flip".equals(currentAnim) || "shot_empty".equals(currentAnim)) {
                    return PlayState.CONTINUE;
                }
                // ВАЖНО: Если "shot" уже играет, мы тоже даем ему доиграть!
                // Это решает проблему рывков при зажиме.
                if ("shot".equals(currentAnim)) {
                    return PlayState.CONTINUE;
                }
            }

            boolean isKeyDown = mc.options.keyAttack.isDown();
            boolean hasAmmo = getAmmo(mainHandStack) > 0;
            boolean isReloading = getReloadTimer(mainHandStack) > 0;
            int shootDelay = getShootDelay(mainHandStack);

            // 2. Логика запуска стрельбы
            if (isKeyDown && !isReloading) {
                if (hasAmmo || shootDelay > 10) {
                    // Запускаем shot БЕЗ forceAnimationReset.
                    return event.setAndContinue(RawAnimation.begin().thenPlay("shot"));
                }
                // Патронов нет, но кнопка нажата -> ждем shot_empty от сервера
                return PlayState.CONTINUE;
            }

            // 3. Если ничего не нажато и ничего важного не играет -> стоп
            return PlayState.STOP;
        })
                .triggerableAnim("reload", RawAnimation.begin().thenPlay("reload"))
                .triggerableAnim("flip", RawAnimation.begin().thenPlay("flip"))
                .triggerableAnim("shot", RawAnimation.begin().thenPlay("shot"))
                .triggerableAnim("shot_empty", RawAnimation.begin().thenPlay("shot_empty"))

                // ✅ ДОБАВЛЕН ОБРАБОТЧИК ЗВУКОВ
                .setSoundKeyframeHandler(event -> {
                    String soundName = event.getKeyframeData().getSound();
                    if (soundName == null || soundName.isEmpty()) return;

                    // Пытаемся найти звук по полному ID
                    SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundName));

                    // Если не нашли по полному, пробуем добавить modid (RefStrings.MODID)
                    if (sound == null && !soundName.contains(":")) {
                        sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(CrustalIncursionMod.MOD_ID, soundName));
                    }

                    if (sound != null) {
                        Player player = Minecraft.getInstance().player;
                        if (player != null) {
                            // Играем звук только для клиента владельца
                            player.playSound(sound, 1.0F, 1.0F);
                        }
                    } else {
                        // Раскомментируй для отладки, если звуки не играют
                        // System.out.println("GeckoLib sound not found: " + soundName);
                    }
                }));
    }


    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private MachineGunRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new MachineGunRenderer();
                return renderer;
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        int ammoCount = getAmmo(stack);
        String ammoId = getLoadedAmmoID(stack);

        // 1. Количество патронов
        if (ammoCount > 0) {
            int inMag = ammoCount - 1; // Патроны в ленте
            tooltip.add(Component.literal("Патроны: " + inMag + " + 1 / " + MAX_TOTAL_AMMO).withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.literal("Патроны: нет").withStyle(ChatFormatting.RED));
            return; // Если патронов нет, дальше не показываем
        }

        // 2. Если ID пустой
        if (ammoId == null || ammoId.isEmpty()) {
            tooltip.add(Component.literal("Тип: обычный").withStyle(ChatFormatting.GRAY));
            return;
        }

        // 3. Получаем тип патрона
        AmmoRegistry.AmmoType ammoType = AmmoRegistry.getAmmoTypeById(ammoId);
        if (ammoType == null) {
            tooltip.add(Component.literal("Тип: неизвестный").withStyle(ChatFormatting.GRAY));
            return;
        }

        // 4. Определяем название типа
        String typeText = "обычный";
        if (ammoId.contains("piercing")) typeText = "бронебойный";
        else if (ammoId.contains("hollow")) typeText = "экспансивный";
        else if (ammoId.contains("fire") || ammoId.contains("incendiary")) typeText = "зажигательный";

        // 5. Выводим тип и урон
        tooltip.add(Component.literal("Тип: " + typeText).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal(String.format("Урон: %.1f", ammoType.damage)).withStyle(ChatFormatting.DARK_RED));
    }



    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public double getBoneResetTime() { return 0; }

    // === КЛИЕНТ ===
    @Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientHandlers {
        private static int clientShootTimer = 0;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;

            ItemStack stack = mc.player.getMainHandItem();
            if (!(stack.getItem() instanceof MachineGunItem item)) {
                clientShootTimer = 15;
                return;
            }

            if (clientShootTimer > 0) clientShootTimer--;

            if (ModKeyBindings.RELOAD_KEY.consumeClick()) {
                ModPacketHandler.INSTANCE.sendToServer(new PacketReloadGun());
                return;
            }

            if (item.getReloadTimer(stack) > 0) return;

            if (mc.options.keyAttack.isDown()) {
                // ✅ УБРАЛИ ПРОВЕРКУ "if (item.getAmmo(stack) <= 0) return;"
                // Теперь пакет отправится даже с пустым магазином, а сервер решит, что делать.
                if (clientShootTimer <= 0) {
                    ModPacketHandler.INSTANCE.sendToServer(new PacketShoot());
                    mc.player.attackAnim = 0;
                    mc.player.oAttackAnim = 0;
                    mc.player.swinging = false;
                    clientShootTimer = SHOT_ANIM_TICKS;
                }
            } else {
                if (clientShootTimer < SHOT_ANIM_TICKS - 2) clientShootTimer = 0;
            }
        }


        @SubscribeEvent
        public static void onInput(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
            if (event.isAttack()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof MachineGunItem) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class CommonHandlers {
        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            if (event.getItemStack().getItem() instanceof MachineGunItem && !event.getEntity().isCreative()) {
                event.setCanceled(true);
            }
        }
    }
}

