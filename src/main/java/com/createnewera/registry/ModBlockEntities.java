package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import com.createnewera.block.control.PanelControlBlockEntity;
import com.createnewera.client.control.PanelControlRenderer;
import com.createnewera.client.render.BrassComputerRenderer;
import com.createnewera.computer.BrassComputerBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public final class ModBlockEntities {
    public static final BlockEntityEntry<BrassComputerBlockEntity> BRASS_COMPUTER = REGISTRATE
            .blockEntity("brass_computer", BrassComputerBlockEntity::new)
            // the half shaft turning in the hole in its back, like Create's cuckoo clock
            .visual(() -> OrientedRotatingVisual.backHorizontal(AllPartialModels.SHAFT_HALF))
            .validBlocks(ModBlocks.BRASS_COMPUTER)
            .renderer(() -> BrassComputerRenderer::new)
            .register();

    /** Every panel control: goggle info, knobs and sliders moving smoothly, lamps and nixies glowing. */
    public static final BlockEntityEntry<PanelControlBlockEntity> PANEL_CONTROL = REGISTRATE
            .blockEntity("panel_control", PanelControlBlockEntity::new)
            .validBlocks(ModBlocks.PANEL_BUTTON, ModBlocks.PANEL_SWITCH, ModBlocks.PANEL_KNOB, ModBlocks.PANEL_SLIDER,
                    ModBlocks.PANEL_LAMP, ModBlocks.NIXIE_DIGIT, ModBlocks.PANEL_GAUGE, ModBlocks.PANEL_KEYPAD)
            .renderer(() -> PanelControlRenderer::new)
            .register();

    public static void register() {}

    private ModBlockEntities() {}
}
