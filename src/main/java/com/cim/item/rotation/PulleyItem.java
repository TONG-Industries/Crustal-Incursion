package com.cim.item.rotation;

import com.cim.api.rotation.ShaftDiameter;
import com.cim.api.rotation.ShaftMaterial;
import com.cim.block.basic.industrial.rotation.ShaftBlock;
import com.cim.block.entity.industrial.rotation.ShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.List;

public class PulleyItem extends Item {
    private final int pulleySize; // Размер для BlockState (1, 2, 3)
    private final int diameterPixels; // Реальный диаметр для расчетов передаточного числа
    private final ShaftMaterial material;
    private final List<ShaftDiameter> compatibleShafts; // На какие валы можно надеть

    public PulleyItem(Properties properties, int pulleySize, int diameterPixels, ShaftMaterial material, ShaftDiameter... compatibleShafts) {
        super(properties);
        this.pulleySize = pulleySize;
        this.diameterPixels = diameterPixels;
        this.material = material;
        this.compatibleShafts = Arrays.asList(compatibleShafts);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof ShaftBlock shaftBlock) {
            // 1. СТРОГАЯ ПРОВЕРКА: Вал должен быть абсолютно пустым!
            if (state.getValue(ShaftBlock.GEAR_SIZE) != 0 || state.getValue(ShaftBlock.PULLEY_SIZE) != 0) {
                return InteractionResult.PASS;
            }

            // 2. Проверка совместимости диаметра вала
            if (!compatibleShafts.contains(shaftBlock.getDiameter())) {
                return InteractionResult.PASS;
            }

            if (!level.isClientSide) {
                if (level.getBlockEntity(pos) instanceof ShaftBlockEntity shaftBE) {
                    ItemStack pulleyStack = context.getItemInHand().copy();
                    pulleyStack.setCount(1);
                    shaftBE.setAttachedPulley(pulleyStack); // Сохраняем в память блока
                }

                // 3. Обновляем стейт блока (вызовет рендер и обновит хитбокс)
                level.setBlock(pos, state.setValue(ShaftBlock.PULLEY_SIZE, pulleySize), 3);

                context.getItemInHand().shrink(1);
                level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);

                // 4. Пересобираем кинетическую сеть
                com.cim.api.rotation.KineticNetworkManager manager = com.cim.api.rotation.KineticNetworkManager.get((net.minecraft.server.level.ServerLevel) level);
                manager.updateNetworkAfterRemove(pos);
                manager.updateNetworkAfterPlace(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    public int getDiameterPixels() { return diameterPixels; }
    public ShaftMaterial getMaterial() { return material; }
}
