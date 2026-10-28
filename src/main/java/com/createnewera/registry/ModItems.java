package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.world.item.Item;

public final class ModItems {
    static {
        REGISTRATE.setCreativeTab(ModCreativeTabs.MAIN);
    }

    /** Andesite base every panel control is built on. */
    public static final ItemEntry<Item> CONTROL_PLATE = REGISTRATE
            .item("control_plate", Item::new)
            .lang("Control Plate")
            .register();

    public static void register() {}

    private ModItems() {}
}
