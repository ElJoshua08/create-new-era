package com.createnewera;

import com.createnewera.client.bus.BusPartRenderer;
import com.createnewera.client.bus.CableRenderer;
import com.createnewera.ponder.NewEraPonderPlugin;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CreateNewEra.MOD_ID, dist = Dist.CLIENT)
public class CreateNewEraClient {
    public CreateNewEraClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(FMLClientSetupEvent.class, event -> PonderIndex.addPlugin(new NewEraPonderPlugin()));
        BusPartRenderer.register(modEventBus, NeoForge.EVENT_BUS);
        CableRenderer.register(modEventBus, NeoForge.EVENT_BUS);
    }
}
