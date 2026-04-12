package com.cim.api.rotation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.cim.api.rotation.Rotational; // Твой интерфейс для получения Speed/Torque

import static com.cim.main.CrustalIncursionMod.LOGGER;

public class KineticNetwork {
    private final UUID networkId;

    // Все участники сети (валы, шестерни и т.д.)
    private final Set<BlockPos> members = new HashSet<>();
    // Только источники энергии (моторы, ветряки)
    private final Set<BlockPos> generators = new HashSet<>();

    private long currentSpeed = 0;
    private long totalGeneratedTorque = 0;
    private long totalConsumedTorque = 0;
    private long totalInertia = 1; // Защита от деления на ноль
    private long totalFriction = 0;
    private long targetNetworkSpeed = 0;
    private boolean needsRecalculation = true;// К чему стремится мотор

    public KineticNetwork() {
        this.networkId = UUID.randomUUID();
    }

    // Конструктор специально для загрузки сети из памяти
    public KineticNetwork(UUID id) {
        this.networkId = id;
    }

    public UUID getId() { return networkId; }

    /**
     * Главный мозг сети. Вызывается при любом изменении состава блоков.
     */

    public boolean tick(ServerLevel level) {
        if (members.isEmpty()) return false;

        if (this.needsRecalculation) {
            this.recalculate(level);
            this.needsRecalculation = false;
        }

        long oldSpeed = this.currentSpeed;
        long deltaSpeed = 0;

        // 1. РАЗГОН (если генераторы работают)
        if (totalGeneratedTorque > 0) {
            // Формула из плана
            long effectiveTorque = totalGeneratedTorque - totalFriction;

            if (effectiveTorque > 0) {
                // Вычисляем ускорение
                deltaSpeed = effectiveTorque / totalInertia;
                if (deltaSpeed == 0) deltaSpeed = 1; // Минимальный шаг

                // Направляем ускорение в нужную сторону
                deltaSpeed = targetNetworkSpeed > 0 ? deltaSpeed : -deltaSpeed;
            }
        }
        // 2. ИНЕРЦИЯ И ОСТАНОВКА (моторы выключены) [cite: 30]
        else {
            if (this.currentSpeed != 0) {
                // Трение всегда против движения [cite: 28, 29]
                deltaSpeed = (totalFriction / totalInertia);
                if (deltaSpeed == 0) deltaSpeed = 1;

                if (this.currentSpeed > 0) {
                    deltaSpeed = -deltaSpeed;
                }
            }
        }

        // 3. ПРИМЕНЯЕМ УСКОРЕНИЕ
        if (deltaSpeed != 0) {
            long newSpeed = this.currentSpeed + deltaSpeed;

            // Ограничения скорости
            if (totalGeneratedTorque > 0) {
                // Не даем разогнаться быстрее мотора
                if ((targetNetworkSpeed > 0 && newSpeed > targetNetworkSpeed) ||
                        (targetNetworkSpeed < 0 && newSpeed < targetNetworkSpeed)) {
                    newSpeed = targetNetworkSpeed;
                }
            } else {
                // Если тормозим, то останавливаемся ровно в нуле [cite: 30]
                if ((this.currentSpeed > 0 && newSpeed < 0) ||
                        (this.currentSpeed < 0 && newSpeed > 0)) {
                    newSpeed = 0;
                }
            }

            // 4. РАССЫЛАЕМ ОБНОВЛЕНИЯ
            if (this.currentSpeed != newSpeed) {
                this.currentSpeed = newSpeed;

                // Флаг: достигли ли мы предела разгона или полной остановки?
                boolean reachedTarget = (this.currentSpeed == targetNetworkSpeed) || (this.currentSpeed == 0);

                for (BlockPos pos : members) {
                    if (level.isLoaded(pos)) {
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be instanceof Rotational node) {
                            node.setSpeed(this.currentSpeed);

                            // Если стабилизировались — заставляем клиент обновиться на 100%
                            if (reachedTarget) {
                                node.forceSyncVisuals(level, pos);
                            }
                        }
                    }
                }
            }
        }
        return oldSpeed != this.currentSpeed;
    }

    public void recalculate(ServerLevel level) {
        this.totalGeneratedTorque = 0;
        this.totalInertia = 0;
        this.totalFriction = 0;
        this.targetNetworkSpeed = 0;

        // 1. Собираем физику со всех участников
        for (BlockPos pos : members) {
            // ВАЖНО: Проверяем загрузку чанка! [cite: 42]
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof Rotational node) {
                this.totalInertia += node.getInertiaContribution();
                this.totalFriction += node.getFrictionContribution();

                node.setSpeed(this.currentSpeed);
            }


        }

        // 2. Опрашиваем генераторы
        for (BlockPos genPos : generators) {
            if (level.isLoaded(genPos) && level.getBlockEntity(genPos) instanceof Rotational gen) {
                totalGeneratedTorque += gen.getTorque();

                if (targetNetworkSpeed == 0) {
                    long speed = gen.getGeneratedSpeed();
                    // ... (Тут твоя текущая магия синхронизации осей через Direction)
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(genPos);
                    if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                        net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                        if (facing == net.minecraft.core.Direction.SOUTH || facing == net.minecraft.core.Direction.EAST || facing == net.minecraft.core.Direction.UP) {
                            speed = -speed;
                        }
                    }
                    targetNetworkSpeed = speed;
                }
            }
        }

        // Защита от нулевой инерции
        if (this.totalInertia <= 0) this.totalInertia = 1;
    }



    public boolean checkConflict(ServerLevel level) {
        // Если мотор один или их вообще нет — конфликта быть не может
        if (generators.size() <= 1) return false;

        long baselineSpeed = 0;

        for (BlockPos genPos : generators) {
            BlockEntity be = level.getBlockEntity(genPos);
            if (be instanceof Rotational gen) {
                long speed = gen.getSpeed();

                // Синхронизируем оси так же, как мы это делаем при расчетах скорости
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(genPos);
                if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                    net.minecraft.core.Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                    if (facing == net.minecraft.core.Direction.SOUTH ||
                            facing == net.minecraft.core.Direction.EAST ||
                            facing == net.minecraft.core.Direction.UP) {
                        speed = -speed;
                    }
                }

                // Запоминаем скорость первого мотора
                if (baselineSpeed == 0) {
                    baselineSpeed = speed;
                } else if (baselineSpeed != speed) {
                    // Как только находим мотор с другой скоростью (или направлением) — трубим тревогу!
                    return true;
                }
            }
        }
        return false;
    }

    private void updateMembers(ServerLevel level) {
        for (BlockPos pos : members) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Rotational node) {
                node.setSpeed(this.currentSpeed);
                // Тут мы будем вызывать обновление визуалов Flywheel через пакеты [cite: 11, 12]
            }
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("Id", networkId);
        nbt.putLong("Speed", currentSpeed);

        ListTag membersTag = new ListTag();
        for (BlockPos pos : members) {
            membersTag.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        }
        nbt.put("Members", membersTag);

        ListTag generatorsTag = new ListTag();
        for (BlockPos pos : generators) {
            generatorsTag.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));
        }
        nbt.put("Generators", generatorsTag);

        return nbt;
    }

    public static KineticNetwork deserializeNBT(CompoundTag nbt) {
        // Создаем сеть с сохраненным UUID
        KineticNetwork net = new KineticNetwork(nbt.getUUID("Id"));
        net.currentSpeed = nbt.getLong("Speed");

        ListTag membersTag = nbt.getList("Members", Tag.TAG_LONG);
        for (int i = 0; i < membersTag.size(); i++) {
            // Берем тег из списка, кастуем его к LongTag и вытаскиваем long
            long posLong = ((net.minecraft.nbt.LongTag) membersTag.get(i)).getAsLong();
            net.members.add(BlockPos.of(posLong));
        }

        ListTag gensTag = nbt.getList("Generators", Tag.TAG_LONG);
        for (int i = 0; i < gensTag.size(); i++) {
            long posLong = ((net.minecraft.nbt.LongTag) gensTag.get(i)).getAsLong();
            net.generators.add(BlockPos.of(posLong));
        }

        return net;
    }

    public void addMember(BlockPos pos) {
        this.members.add(pos);
    }

    public void addGenerator(BlockPos pos) {
        this.generators.add(pos);
        this.members.add(pos); // Генератор всегда является и обычным участником [cite: 2]
    }

    public void removeMember(BlockPos pos) {
        this.members.remove(pos);
        this.generators.remove(pos);
    }

    public void requestRecalculation() {
        this.needsRecalculation = true;
    }

    // Также добавь геттер для members, если его нет (нужен для Merge)
    public Set<BlockPos> getMembers() {
        return members;
    }

    // Добавь эти методы в KineticNetwork.java
    public long getSpeed() { return currentSpeed; }
    public Set<BlockPos> getGenerators() { return generators; }

    public long getTargetSpeed() {
        return targetNetworkSpeed;
    }

    public void setCurrentSpeed(long speed) {
        this.currentSpeed = speed;
    }


}