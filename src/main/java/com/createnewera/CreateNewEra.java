package com.createnewera;

import com.createnewera.config.ClientConfig;
import com.createnewera.bus.BusEvents;
import com.createnewera.network.ModNetwork;
import com.createnewera.registry.ModBlockEntities;
import com.createnewera.registry.ModBlocks;
import com.createnewera.registry.ModComponents;
import com.createnewera.registry.ModCreativeTabs;
import com.createnewera.registry.ModItems;
import com.createnewera.registry.ModLang;
import com.createnewera.registry.ModRecipes;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CreateNewEra.MOD_ID)
public class CreateNewEra {
    public static final String MOD_ID = "create_new_era";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Create-style "hold Shift" tooltips, read from <key>.tooltip.summary lang entries, plus the
    // stress line Create shows for kinetic blocks.
    // No default tab: Registrate would also push every item into the search tab, which already
    // collects them from our own tab, and the duplicates crash the creative inventory.
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
            .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                    .andThen(TooltipModifier.mapNull(KineticStats.create(item))));

    public CreateNewEra(IEventBus modEventBus, ModContainer modContainer) {
        // before any content: builders hang client-setup listeners (Flywheel visuals among them)
        // off this bus while they're declared, and before it's set those are silently dropped
        REGISTRATE.registerEventListeners(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModBlocks.register();
        ModBlockEntities.register();
        ModItems.register();
        ModComponents.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModLang.register();
        ModNetwork.register(modEventBus);
        BusEvents.register(NeoForge.EVENT_BUS);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        LOGGER.info("Create: New Era loaded");
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
