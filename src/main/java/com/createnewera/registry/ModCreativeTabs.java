package com.createnewera.registry;

import com.createnewera.CreateNewEra;
import com.simibubi.create.AllCreativeModeTabs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateNewEra.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_new_era.main"))
                    // right after Create's own tabs
                    .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getKey())
                    .icon(ModBlocks.BRASS_COMPUTER::asStack)
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.BRASS_COMPUTER);
                        output.accept(ModBlocks.BRASS_SCREEN);
                        output.accept(ModBlocks.UNIVERSAL_ADAPTER);
                        output.accept(ModBlocks.BEARING_SERVO);
                        output.accept(ModItems.BUS_PORT);
                        output.accept(ModItems.PORT_EXPANSION);
                        output.accept(ModItems.CONTROL_PLATE);
                        output.accept(ModBlocks.PANEL_BUTTON);
                        output.accept(ModBlocks.PANEL_SWITCH);
                        output.accept(ModBlocks.PANEL_KNOB);
                        output.accept(ModBlocks.PANEL_SLIDER);
                        output.accept(ModBlocks.PANEL_LAMP);
                        output.accept(ModBlocks.NIXIE_DIGIT);
                        output.accept(ModBlocks.PANEL_GAUGE);
                        output.accept(ModBlocks.PANEL_KEYPAD);
                    })
                    .build());

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }

    private ModCreativeTabs() {}
}
