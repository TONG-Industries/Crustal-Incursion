package com.cim.block.basic.weapons.explosives;

import com.cim.api.vein.VeinManager;
import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.conglomerate.ConglomerateBlockEntity;
import com.cim.item.ModItems;
import com.cim.item.conglomerates.ConglomerateItem;
import com.cim.main.CrustalIncursionMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DetMinerBlock extends Block implements IDetonatable {

    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);

    private static final int BLAST_RADIUS = 4;
    private static final float BLAST_NOISE = 0.2f;
    private static final float HARDNESS_LIMIT = 30.0f;
    private static final float CONGLOMERATE_CHUNK_CHANCE = 0.50f;
    private static final int CONGLOMERATE_OU_EXTRACT = 270;

    // Радиус цепной детонации
    private static final int CHAIN_RADIUS = 6;

    public DetMinerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.BlockGetter level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.cim.detminer.desc")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.cim.detminer.hardness")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.cim.detminer.conglomerate")
                .withStyle(ChatFormatting.GREEN));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level.hasNeighborSignal(pos)) {
            onDetonate(level, pos, state, null);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public boolean onDetonate(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.isClientSide) return false;

        ServerLevel serverLevel = (ServerLevel) level;

        // 1. Собираем блоки для разрушения
        BlastResult blast = calculateNaturalBlast(serverLevel, pos);

        // 2. Собираем ВСЕ дропы в одну точку — позицию заряда
        List<ItemStack> allDrops = new ArrayList<>();

        // Обычные блоки — дропы
        for (BlockPos targetPos : blast.normalPositions) {
            BlockState targetState = serverLevel.getBlockState(targetPos);
            LootParams.Builder lootParams = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(targetPos))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
            allDrops.addAll(targetState.getDrops(lootParams));
        }

        // Конгломераты — обеднённый блок + ресурсы
        VeinManager manager = VeinManager.get(serverLevel);
        for (BlockPos targetPos : blast.conglomeratePositions) {
            allDrops.add(new ItemStack(ModBlocks.DEPLETED_CONGLOMERATE.get()));

            BlockEntity be = serverLevel.getBlockEntity(targetPos);
            VeinManager.VeinData vein = null;

            if (be instanceof ConglomerateBlockEntity entity && entity.getVeinId() != null) {
                vein = manager.getVein(entity.getVeinId());
                if (vein != null) {
                    vein.consumeUnits(CONGLOMERATE_OU_EXTRACT);
                }
            }

            if (vein != null && serverLevel.random.nextFloat() < CONGLOMERATE_CHUNK_CHANCE) {
                ItemStack chunk = ConglomerateItem.createFromVein(
                        vein.getComposition().getFullComposition(),
                        CONGLOMERATE_OU_EXTRACT,
                        vein.getTypeName()
                );
                allDrops.add(chunk);
            } else {
                allDrops.add(new ItemStack(ModItems.HARD_ROCK.get()));
            }

            if (vein != null) {
                manager.setDirty();
            }
        }

        // 3. Разрушаем все блоки
        for (BlockPos targetPos : blast.normalPositions) {
            serverLevel.setBlockAndUpdate(targetPos, Blocks.AIR.defaultBlockState());
            serverLevel.gameEvent(null, GameEvent.BLOCK_DESTROY, targetPos);
        }
        for (BlockPos targetPos : blast.conglomeratePositions) {
            serverLevel.setBlockAndUpdate(targetPos, Blocks.AIR.defaultBlockState());
            serverLevel.gameEvent(null, GameEvent.BLOCK_DESTROY, targetPos);
        }

        // 4. Выпадаем ВСЁ на позицию заряда
        for (ItemStack drop : allDrops) {
            if (!drop.isEmpty()) {
                spawnDropAt(serverLevel, pos, drop);
            }
        }

        // 5. Цепная детонация (ДО удаления этого блока!)
        triggerChainDetonation(serverLevel, pos, player);

        // 6. Эффекты и удаление заряда
        serverLevel.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F, 0.8F + serverLevel.random.nextFloat() * 0.4F);
        serverLevel.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        serverLevel.gameEvent(null, GameEvent.BLOCK_DESTROY, pos);

        return true;
    }

    private BlastResult calculateNaturalBlast(ServerLevel level, BlockPos center) {
        List<BlockPos> normalBlocks = new ArrayList<>();
        List<BlockPos> conglomerateBlocks = new ArrayList<>();

        for (int x = -BLAST_RADIUS; x <= BLAST_RADIUS; x++) {
            for (int y = -BLAST_RADIUS; y <= BLAST_RADIUS; y++) {
                for (int z = -BLAST_RADIUS; z <= BLAST_RADIUS; z++) {
                    BlockPos checkPos = center.offset(x, y, z);

                    double dist = Math.sqrt(x*x + y*y + z*z) / BLAST_RADIUS;
                    double noise = getSimpleNoise(checkPos.getX(), checkPos.getY(), checkPos.getZ());
                    double threshold = 1.0 + (noise * BLAST_NOISE);

                    if (dist > threshold) continue;

                    BlockState blockState = level.getBlockState(checkPos);
                    if (blockState.isAir() || blockState.is(Blocks.BEDROCK)) continue;
                    if (blockState.is(this)) continue;

                    float hardness = blockState.getDestroySpeed(level, checkPos);
                    if (hardness >= HARDNESS_LIMIT) continue;

                    if (blockState.is(ModBlocks.CONGLOMERATE.get())) {
                        conglomerateBlocks.add(checkPos);
                    } else {
                        normalBlocks.add(checkPos);
                    }
                }
            }
        }

        return new BlastResult(normalBlocks, conglomerateBlocks);
    }

    private double getSimpleNoise(int x, int y, int z) {
        long seed = x * 374761393L + y * 668265263L + z * 2086444801L;
        seed = (seed ^ (seed >> 13)) * 1274126177L;
        return ((seed ^ (seed >> 16)) & 0x7FFFFFFF) / (double) 0x7FFFFFFF * 2.0 - 1.0;
    }

    private void spawnDropAt(ServerLevel level, BlockPos pos, ItemStack stack) {
        double offsetX = (level.random.nextDouble() - 0.5) * 0.8;
        double offsetY = level.random.nextDouble() * 0.5;
        double offsetZ = (level.random.nextDouble() - 0.5) * 0.8;

        ItemEntity itemEntity = new ItemEntity(level,
                pos.getX() + 0.5 + offsetX,
                pos.getY() + 0.5 + offsetY,
                pos.getZ() + 0.5 + offsetZ,
                stack
        );
        itemEntity.setDeltaMovement(
                (level.random.nextDouble() - 0.5) * 0.3,
                0.3 + level.random.nextDouble() * 0.3,
                (level.random.nextDouble() - 0.5) * 0.3
        );
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
    }

    /**
     * Цепная детонация: активирует ВСЕ IDetonatable блоки в радиусе 6 блоков
     */
    private void triggerChainDetonation(ServerLevel serverLevel, BlockPos center, Player player) {
        for (int x = -CHAIN_RADIUS; x <= CHAIN_RADIUS; x++) {
            for (int y = -CHAIN_RADIUS; y <= CHAIN_RADIUS; y++) {
                for (int z = -CHAIN_RADIUS; z <= CHAIN_RADIUS; z++) {
                    // Пропускаем центр (это мы сами)
                    if (x == 0 && y == 0 && z == 0) continue;

                    double dist = Math.sqrt(x*x + y*y + z*z);
                    if (dist > CHAIN_RADIUS) continue;

                    BlockPos checkPos = center.offset(x, y, z);
                    BlockState checkState = serverLevel.getBlockState(checkPos);
                    Block block = checkState.getBlock();

                    // Проверяем что блок реализует IDetonatable
                    if (block instanceof IDetonatable detonatable) {
                        // Задержка пропорциональна расстоянию (эффект волны)
                        int delay = (int)(dist * 2.5);

                        serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + delay, () -> {
                            // Проверяем что блок всё ещё на месте
                            if (serverLevel.getBlockState(checkPos).getBlock() == block) {
                                detonatable.onDetonate(serverLevel, checkPos, checkState, player);
                            }
                        }));
                    }
                }
            }
        }
    }

    private record BlastResult(List<BlockPos> normalPositions, List<BlockPos> conglomeratePositions) {}
}