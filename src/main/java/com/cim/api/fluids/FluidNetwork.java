package com.cim.api.fluids;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;

public class FluidNetwork {
    private final FluidNetworkManager manager;
    private final Set<FluidNode> nodes = new HashSet<>();
    private final UUID id = UUID.randomUUID();

    // === ФЛАГ ОПТИМИЗАЦИИ (ПРОВЕРЯЕМ ТРУБЫ ТОЛЬКО 1 РАЗ) ===
    private boolean hasCheckedMeltdown = false;

    public FluidNetwork(FluidNetworkManager manager) {
        this.manager = manager;
    }

    public void tick(ServerLevel level) {
        if (nodes.isEmpty()) return;

        nodes.removeIf(node -> !node.isValid(level) || node.getNetwork() != this);
        if (nodes.size() < 2) return;

        List<IFluidHandler> pureProviders = new ArrayList<>();
        List<IFluidHandler> pureReceivers = new ArrayList<>();
        List<IFluidHandler> buffers = new ArrayList<>();

        for (FluidNode node : nodes) {
            BlockPos pos = node.getPos();
            if (!level.isLoaded(pos)) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null || be instanceof com.cim.block.entity.fluids.FluidPipeBlockEntity) continue;

            be.getCapability(ForgeCapabilities.FLUID_HANDLER).ifPresent(handler -> {
                if (be instanceof com.cim.block.entity.fluids.FluidBarrelBlockEntity barrel) {
                    if (barrel.mode == 1) pureReceivers.add(handler);
                    else if (barrel.mode == 2) pureProviders.add(handler);
                    else if (barrel.mode == 0) buffers.add(handler);
                } else {
                    pureProviders.add(handler);
                    pureReceivers.add(handler);
                }
            });
        }

        if (transfer(level, pureProviders, pureReceivers)) return;
        if (transfer(level, pureProviders, buffers)) return;
        if (transfer(level, buffers, pureReceivers)) return;
        balance(level, buffers);
    }

    // --- УНИВЕРСАЛЬНЫЙ МЕТОД ПЕРЕДАЧИ (С ПРОВЕРКОЙ НА ПЛАВЛЕНИЕ) ---
    // Возвращает TRUE, если сеть взорвалась (чтобы прервать тик)
    private boolean transfer(ServerLevel level, List<IFluidHandler> sources, List<IFluidHandler> destinations) {
        for (IFluidHandler source : sources) {
            FluidStack available = source.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (available.isEmpty() || available.getAmount() <= 0) continue;

            int remaining = available.getAmount();

            for (IFluidHandler dest : destinations) {
                if (remaining <= 0) break;
                if (source == dest) continue;

                int accepted = dest.fill(new FluidStack(available.getFluid(), remaining), IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    FluidStack drained = source.drain(new FluidStack(available.getFluid(), accepted), IFluidHandler.FluidAction.EXECUTE);
                    if (!drained.isEmpty()) {
                        dest.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                        remaining -= drained.getAmount();

                        // === КАК ТОЛЬКО ЖИДКОСТЬ ПРОШЛА - ПРОВЕРЯЕМ ТРУБЫ ===
                        if (!hasCheckedMeltdown) {
                            if (checkMeltdown(level, drained.getFluid())) {
                                return true; // Сеть взорвалась, прерываем работу!
                            }
                            hasCheckedMeltdown = true; // Трубы выдержали

                            // === НОВОЕ: МАРКИРУЕМ ВЫЖИВШИЕ ТРУБЫ КАК "АКТИВНЫЕ" ===
                            for (FluidNode node : nodes) {
                                BlockPos nodePos = node.getPos();
                                if (level.isLoaded(nodePos)) {
                                    BlockEntity be = level.getBlockEntity(nodePos);
                                    if (be instanceof com.cim.block.entity.fluids.FluidPipeBlockEntity pipeBE) {
                                        pipeBE.setHasFlowed(true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void balance(ServerLevel level, List<IFluidHandler> buffers) {
        if (buffers.size() < 2) return;

        long totalFluid = 0;
        int validBuffers = 0;
        net.minecraft.world.level.material.Fluid type = null;

        for (IFluidHandler buf : buffers) {
            FluidStack fs = buf.getFluidInTank(0);
            if (!fs.isEmpty()) {
                totalFluid += fs.getAmount();
                if (type == null) type = fs.getFluid();
            }
        }

        for (IFluidHandler buf : buffers) {
            FluidStack fs = buf.getFluidInTank(0);
            if (fs.isEmpty() || fs.getFluid() == type) validBuffers++;
        }

        if (totalFluid == 0 || type == null || validBuffers < 2) return;

        int avg = (int) (totalFluid / validBuffers);
        List<IFluidHandler> donors = new ArrayList<>();
        List<IFluidHandler> receivers = new ArrayList<>();

        for (IFluidHandler buf : buffers) {
            FluidStack fs = buf.getFluidInTank(0);
            if (fs.isEmpty() || fs.getFluid() == type) {
                int amt = fs.isEmpty() ? 0 : fs.getAmount();
                if (amt > avg + 5) donors.add(buf);
                else if (amt < avg - 5) receivers.add(buf);
            }
        }

        for (IFluidHandler donor : donors) {
            int donorExcess = donor.getFluidInTank(0).getAmount() - avg;
            if (donorExcess <= 0) continue;

            for (IFluidHandler receiver : receivers) {
                int receiverAmt = receiver.getFluidInTank(0).isEmpty() ? 0 : receiver.getFluidInTank(0).getAmount();
                int receiverDeficit = avg - receiverAmt;
                if (receiverDeficit <= 0) continue;

                int acceptedSim = receiver.fill(new FluidStack(type, Math.min(donorExcess, receiverDeficit)), IFluidHandler.FluidAction.SIMULATE);

                if (acceptedSim > 0) {
                    FluidStack drained = donor.drain(new FluidStack(type, acceptedSim), IFluidHandler.FluidAction.EXECUTE);
                    if (!drained.isEmpty()) {
                        receiver.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                        donorExcess -= drained.getAmount();

                        // Проверяем балансировку тоже
                        if (!hasCheckedMeltdown) {
                            if (checkMeltdown(level, drained.getFluid())) return;
                            hasCheckedMeltdown = true;
                        }
                    }
                }
                if (donorExcess <= 0) break;
            }
        }
    }

    // ==========================================
    // ЛОГИКА ПЛАВЛЕНИЯ ТРУБ
    // ==========================================
    private boolean checkMeltdown(ServerLevel level, net.minecraft.world.level.material.Fluid fluid) {
        int fluidTemp = fluid.getFluidType().getTemperature();
        int fluidAcid = 0;
        int fluidRad = 0;

        if (fluid.getFluidType() instanceof BaseFluidType base) {
            fluidAcid = base.getAcidity();
            fluidRad = base.getRadiation();
        } else if (fluid == net.minecraft.world.level.material.Fluids.LAVA || fluid == net.minecraft.world.level.material.Fluids.FLOWING_LAVA) {
            fluidTemp = 1300;
        }

        List<BlockPos> toMelt = new ArrayList<>();

        // Копируем узлы, чтобы избежать ConcurrentModificationException
        for (FluidNode node : new ArrayList<>(nodes)) {
            BlockPos pos = node.getPos();
            if (!level.isLoaded(pos)) continue;

            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof com.cim.block.basic.fluids.FluidPipeBlock pipeBlock) {
                PipeTier tier = pipeBlock.getTier();
                // Сравниваем характеристики
                if (fluidTemp > tier.getMaxTemperature() || fluidAcid > tier.getMaxAcidity() || fluidRad > tier.getMaxRadiation()) {
                    toMelt.add(pos);
                }
            }
        }

        if (!toMelt.isEmpty()) {
            for (BlockPos pos : toMelt) {
                // 1. Ломаем трубу
                level.destroyBlock(pos, false);

                // 2. Ставим источник жидкости (если это не газ)
                BlockState fluidBlock = fluid.defaultFluidState().createLegacyBlock();
                if (!fluidBlock.isAir()) {
                    level.setBlock(pos, fluidBlock, 3);
                }

                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.LAVA_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return true; // Возвращаем true, чтобы остановить тик текущей разрушенной сети
        }
        return false;
    }

    // ==========================================
    // УПРАВЛЕНИЕ СЕТЬЮ И СБРОС КЭША
    // ==========================================
    public void addNode(FluidNode node) {
        node.setNetwork(this);
        nodes.add(node);
        this.hasCheckedMeltdown = false; // Состав изменился - нужно перепроверить!
    }

    public void removeNode(FluidNode node) {
        nodes.remove(node);
        node.setNetwork(null);
        this.hasCheckedMeltdown = false; // Состав изменился - нужно перепроверить!
        if (!nodes.isEmpty()) {
            rebuildNetwork();
        } else {
            manager.removeNetwork(this);
        }
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    private void rebuildNetwork() {
        if (nodes.isEmpty()) return;

        Set<FluidNode> allReachableNodes = new HashSet<>();
        Queue<FluidNode> queue = new LinkedList<>();

        FluidNode startNode = nodes.iterator().next();
        queue.add(startNode);
        allReachableNodes.add(startNode);

        while (!queue.isEmpty()) {
            FluidNode current = queue.poll();
            BlockPos pos = current.getPos();

            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                for (FluidNode potentialNeighbor : nodes) {
                    if (potentialNeighbor.getPos().equals(neighborPos) && !allReachableNodes.contains(potentialNeighbor)) {
                        allReachableNodes.add(potentialNeighbor);
                        queue.add(potentialNeighbor);
                    }
                }
            }
        }

        if (allReachableNodes.size() < nodes.size()) {
            Set<FluidNode> lostNodes = new HashSet<>(nodes);
            lostNodes.removeAll(allReachableNodes);
            nodes.removeAll(lostNodes);

            for (FluidNode lostNode : lostNodes) {
                lostNode.setNetwork(null);
                manager.reAddNode(lostNode.getPos(), this);
            }

            if (nodes.size() < 2) {
                for (FluidNode remainingNode : nodes) {
                    remainingNode.setNetwork(null);
                    manager.reAddNode(remainingNode.getPos(), this);
                }
                nodes.clear();
                manager.removeNetwork(this);
            }
        }
    }

    public void merge(FluidNetwork other) {
        if (this == other) return;
        if (other.nodes.size() > this.nodes.size()) {
            other.merge(this);
            return;
        }
        for (FluidNode node : other.nodes) {
            node.setNetwork(this);
            this.nodes.add(node);
        }
        other.nodes.clear();
        manager.removeNetwork(other);
        this.hasCheckedMeltdown = false; // Сети слились - сбрасываем кэш
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public Set<FluidNode> getNodes() {
        return nodes;
    }
}