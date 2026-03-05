package com.cim.event; // Поменяй на свой пакет

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.cim.main.CrustalIncursionMod;
import com.cim.block.basic.ModBlocks;

@Mod.EventBusSubscriber(modid = CrustalIncursionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModColorHandlers {

    // Красим блок в мире
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            return level != null && pos != null ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor();
        }, ModBlocks.SEQUOIA_LEAVES.get());
    }

    // Красим предмет в инвентаре
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            return FoliageColor.getDefaultColor();
        }, ModBlocks.SEQUOIA_LEAVES.get());
    }
}
