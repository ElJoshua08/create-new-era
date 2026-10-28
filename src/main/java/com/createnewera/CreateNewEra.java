package com.createnewera;

import com.createnewera.config.ClientConfig;
import com.createnewera.registry.ModBlocks;
import com.createnewera.registry.ModCreativeTabs;
import com.createnewera.registry.ModItems;
import com.createnewera.registry.ModLang;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(CreateNewEra.MOD_ID)
public class CreateNewEra {
    public static final String MOD_ID = "create_new_era";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Create-style "hold Shift" tooltips, read from <key>.tooltip.summary lang entries.
    // No default tab: Registrate would also push every item into the search tab, which already
    // collects them from our own tab, and the duplicates crash the creative inventory.
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
            .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));

    public CreateNewEra(IEventBus modEventBus, ModContainer modContainer) {
        ModCreativeTabs.register(modEventBus);
        ModBlocks.register();
        ModItems.register();
        ModLang.register();
        REGISTRATE.registerEventListeners(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        LOGGER.info("Create: New Era loaded");
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
