package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import com.createnewera.computer.BrassComputerBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

public final class ModBlockEntities {
    public static final BlockEntityEntry<BrassComputerBlockEntity> BRASS_COMPUTER = REGISTRATE
            .blockEntity("brass_computer", BrassComputerBlockEntity::new)
            .validBlocks(ModBlocks.BRASS_COMPUTER)
            .register();

    public static void register() {}

    private ModBlockEntities() {}
}
