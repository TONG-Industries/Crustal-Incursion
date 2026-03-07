package com.cim.main;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import com.cim.block.basic.ModBlocks;
import com.cim.item.ModItems;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CrustalIncursionMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> CIM_BUILD_TAB = CREATIVE_MODE_TABS.register("cim_build_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CrustalIncursionMod.MOD_ID + ".cim_build_tab"))
                    .icon(() -> new ItemStack(ModBlocks.CONCRETE.get()))
                    .build());

    public static final RegistryObject<CreativeModeTab> CIM_TECH_TAB = CREATIVE_MODE_TABS.register("cim_tech_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CrustalIncursionMod.MOD_ID + ".cim_tech_tab"))
                    .icon(() -> new ItemStack(ModBlocks.STOPPER.get()))
                    .build());

    public static final RegistryObject<CreativeModeTab> CIM_WEAPONS_TAB = CREATIVE_MODE_TABS.register("cim_weapons_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CrustalIncursionMod.MOD_ID + ".cim_weapons_tab"))
                    .icon(()  -> new ItemStack(ModItems.GRENADE_NUC.get()))
                    .build());

    public static final RegistryObject<CreativeModeTab> CIM_TOOLS_TAB = CREATIVE_MODE_TABS.register("cim_tools_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CrustalIncursionMod.MOD_ID + ".cim_tools_tab"))
                    .icon(() -> new ItemStack(ModItems.CROWBAR.get()))
                    .build());

    public static final RegistryObject<CreativeModeTab> CIM_NATURE_TAB = CREATIVE_MODE_TABS.register("cim_nature_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + CrustalIncursionMod.MOD_ID + ".cim_nature_tab"))
                    .icon(() -> new ItemStack(ModItems.DEPTH_WORM_SPAWN_EGG.get()))
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
