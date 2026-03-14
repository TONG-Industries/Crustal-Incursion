package com.cim.block.basic.necrosis.hive;

import com.cim.block.basic.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class HiveRootsBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public HiveRootsBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    // Проверка, на что можно ставить (ваши блоки из PDF)
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos supportPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.below() : pos.above();
        // Если это верхняя часть, она крепится к нижней. Если нижняя — к почве.
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return level.getBlockState(pos.below()).is(this);
        }

        BlockState ground = level.getBlockState(pos.below());
        BlockState ceiling = level.getBlockState(pos.above());

        return ground.is(ModBlocks.HIVE_SOIL.get()) || ground.is(ModBlocks.DEPTH_WORM_NEST.get()) ||
                ceiling.is(ModBlocks.HIVE_SOIL.get()) || ceiling.is(ModBlocks.DEPTH_WORM_NEST.get());
    }

    // Логика превращения в высокий куст при клике тем же предметом
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack itemstack = player.getItemInHand(hand);
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && itemstack.is(this.asItem())) {
            BlockPos abovePos = pos.above();
            if (level.getBlockState(abovePos).isAir()) {
                if (!level.isClientSide) {
                    level.setBlock(abovePos, this.defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER), 3);
                    if (!player.getAbilities().instabuild) {
                        itemstack.shrink(1);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }
}