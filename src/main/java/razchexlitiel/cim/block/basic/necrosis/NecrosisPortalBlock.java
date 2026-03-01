package razchexlitiel.cim.block.basic.necrosis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Properties;

public class NecrosisPortalBlock extends Block {
    public NecrosisPortalBlock(Properties props) { super(props); }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Получаем ключ измерения
            ResourceKey<Level> necrosisKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("cim", "necrosis"));
            ServerLevel necrosisLevel = serverPlayer.server.getLevel(necrosisKey);

            if (necrosisLevel != null) {
                // Телепортируем игрока в самый верхний слой (Y = 580)
                serverPlayer.teleportTo(necrosisLevel, pos.getX(), 580, pos.getZ(), player.getYRot(), player.getXRot());
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.CONSUME;
    }
}
