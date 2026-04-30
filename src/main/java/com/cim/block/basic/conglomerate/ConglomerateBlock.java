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
                .strength(-1.0F, 3600000.0F)
                .pushReaction(PushReaction.BLOCK));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConglomerateBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return ItemStack.EMPTY;
    }

    /**
     * Добыча литой киркой.
     * @param tierLevel 0=iron(30%), 1=steel(45%), 2=titanium(60%)
     */
    public void mineWithCastPickaxe(ServerLevel level, BlockPos pos, Player player, int tierLevel) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ConglomerateBlockEntity entity)) return;

        VeinManager manager = VeinManager.get(level);
        VeinManager.VeinData vein = manager.getVein(entity.getVeinId());

        if (vein == null || entity.isDepleted() || entity.getBlockOu() <= 0) {
            convertToDepleted(level, pos);
            return;
        }

        int ouPerHit = 81;
        entity.consumeOu(ouPerHit);
        vein.consumeUnits(ouPerHit);

        float chunkChance = switch (tierLevel) {
            case 0 -> 0.30f;
            case 1 -> 0.45f;
            case 2 -> 0.60f;
            default -> 0.30f;
        };

        if (level.random.nextFloat() < chunkChance) {
            ItemStack chunk = ConglomerateItem.createFromVein(
                    vein.getComposition().getFullComposition(),
                    ouPerHit,
                    vein.getTypeName()
            );
            Block.popResource(level, pos, chunk);
        } else {
            Block.popResource(level, pos, new ItemStack(ModItems.HARD_ROCK.get()));
        }

        if (entity.getBlockOu() <= 0) {
            entity.markDepleted();
            convertToDepleted(level, pos);
        } else {
            entity.setLocalDepletion(1.0f - (entity.getBlockOu() / 810.0f));
        }

        manager.setDirty();
    }

    private void convertToDepleted(ServerLevel level, BlockPos pos) {
        BlockState depletedState = ModBlocks.DEPLETED_CONGLOMERATE.get().defaultBlockState();
        level.setBlock(pos, depletedState, 3);
    }
}