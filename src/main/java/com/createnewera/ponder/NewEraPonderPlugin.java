package com.createnewera.ponder;

import com.createnewera.CreateNewEra;
import com.createnewera.registry.ModBlocks;
import com.createnewera.registry.ModItems;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class NewEraPonderPlugin implements PonderPlugin {
    public static final ResourceLocation TAG = CreateNewEra.asResource("computing");

    private static final ItemProviderEntry<?, ?>[] CONTROLS = {
            ModItems.CONTROL_PLATE, ModBlocks.PANEL_BUTTON, ModBlocks.PANEL_SWITCH, ModBlocks.PANEL_KNOB,
            ModBlocks.PANEL_SLIDER, ModBlocks.PANEL_LAMP, ModBlocks.NIXIE_DIGIT, ModBlocks.PANEL_GAUGE,
            ModBlocks.PANEL_KEYPAD};

    @Override
    public String getModId() {
        return CreateNewEra.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> scenes = helper.withKeyFunction(RegistryEntry::getId);
        scenes.forComponents(ModBlocks.BRASS_COMPUTER).addStoryBoard("computer/intro", NewEraPonderScenes::computer, TAG);
        scenes.forComponents(ModBlocks.BRASS_SCREEN).addStoryBoard("screen/join", NewEraPonderScenes::screen, TAG);
        scenes.forComponents(ModBlocks.UNIVERSAL_ADAPTER).addStoryBoard("adapter/attach", NewEraPonderScenes::adapter, TAG);
        scenes.forComponents(ModBlocks.BEARING_SERVO).addStoryBoard("servo/intro", NewEraPonderScenes::servo, TAG);
        scenes.forComponents(CONTROLS).addStoryBoard("controls/desk", NewEraPonderScenes::controls, TAG);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TAG)
                .addToIndex()
                .item(ModBlocks.BRASS_COMPUTER.get())
                .title("Create: New Era")
                .description("Computers, screens and panel controls for programming your contraptions")
                .register();

        PonderTagRegistrationHelper<ItemProviderEntry<?, ?>> tags = helper.withKeyFunction(RegistryEntry::getId);
        tags.addToTag(TAG)
                .add(ModBlocks.BRASS_COMPUTER).add(ModBlocks.BRASS_SCREEN)
                .add(ModBlocks.UNIVERSAL_ADAPTER).add(ModBlocks.BEARING_SERVO);
        for (ItemProviderEntry<?, ?> control : CONTROLS)
            tags.addToTag(TAG).add(control);
    }
}
