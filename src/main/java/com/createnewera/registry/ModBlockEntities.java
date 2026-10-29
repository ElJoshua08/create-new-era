package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import com.createnewera.block.control.PanelDialBlockEntity;
import com.createnewera.client.control.PanelDialRenderer;
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

    /** Knobs and sliders: drawn turning and sliding smoothly. */
    public static final BlockEntityEntry<PanelDialBlockEntity> PANEL_DIAL = REGISTRATE
            .blockEntity("panel_dial", PanelDialBlockEntity::new)
            .validBlocks(ModBlocks.PANEL_KNOB, ModBlocks.PANEL_SLIDER)
            .renderer(() -> PanelDialRenderer::new)
            .register();

    public static void register() {}

    private ModBlockEntities() {}
}
