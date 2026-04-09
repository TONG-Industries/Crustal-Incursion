package com.cim.block.basic.conglomerate;

import com.cim.api.vein.VeinManager;
import com.cim.block.basic.ModBlocks;
import com.cim.block.entity.conglomerate.ConglomerateBlockEntity;
import com.cim.item.ModItems;
import com.cim.item.conglomerates.ConglomerateItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public class ConglomerateBlock extends BaseEntityBlock {
    public ConglomerateBlock(Properties properties) {
        super(properties
                .strength(-1.0F, 3600000.0F) // Нерушим как бедрок
                .pushReaction(PushReaction.BLOCK));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConglomerateBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // Будет меняться текстура после истощения?
    }

    // Запрещаем обычное выпадение дропа
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return ItemStack.EMPTY; // Нельзя подобрать в креативе как обычный блок
    }

    public void mineWithCastPickaxe(ServerLevel level, BlockPos pos, Player player, CastPickaxeTier tier) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ConglomerateBlockEntity entity)) return;

        VeinManager manager = VeinManager.get(level);
        VeinManager.VeinData vein = manager.getVein(entity.getVeinId());

        // Проверяем, не истощен ли уже сам блок (или жила потеряна)
        if (vein == null || entity.isDepleted() || entity.getBlockOu() <= 0) {
            convertToDepleted(level, pos);
            return;
        }

        // Шансы добычи
        float chunkChance = switch(tier) {
            case IRON -> 0.30f;
            case STEEL -> 0.45f;
            case TITANIUM -> 0.60f;
        };

        if (level.random.nextFloat() < chunkChance) {
            // Успех — даём кусок конгломерата с составом ИМЕННО ЭТОЙ жилы
            ItemStack chunk = ConglomerateItem.createFromVein(
                    vein.getComposition(),
                    100,
                    vein.getTypeName()
            );
            Block.popResource(level, pos, chunk);

            // Тратим 100 OU у блока и у глобальной жилы
            entity.consumeOu(100);
            vein.consumeUnits(100);
        } else {
            // Неудача — твёрдая порода (ОТМЕНЕН РАСХОД OU!)
            Block.popResource(level, pos, new ItemStack(ModItems.HARD_ROCK.get()));
            // vein.consumeUnits(15); <--- Убрали! Неудачная попытка больше не сажает жилу
        }

        // Проверяем истощение конкретно ЭТОГО блока
        if (entity.getBlockOu() <= 0) {
            entity.markDepleted();
            convertToDepleted(level, pos);
        } else {
            // Визуал обеднения теперь базируется на блоке (от 1000 до 0)
            entity.setLocalDepletion(1.0f - (entity.getBlockOu() / 1000.0f));
        }

        manager.setDirty();
    }

    private void convertToDepleted(ServerLevel level, BlockPos pos) {
        BlockState depletedState = ModBlocks.DEPLETED_CONGLOMERATE.get().defaultBlockState();
        level.setBlock(pos, depletedState, 3);
        // Сохраняем VeinId в depleted блоке для регенерации (если планируется)
    }

    public enum CastPickaxeTier {
        IRON, STEEL, TITANIUM // На будущее
    }
}