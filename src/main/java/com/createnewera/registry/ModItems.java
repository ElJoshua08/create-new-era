package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import com.createnewera.bus.BusPartItem;
import com.createnewera.bus.BusPort;
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

    /** A data port: sticks to any face, on any block. The wrench turns it between input and output. */
    public static final ItemEntry<BusPartItem> BUS_PORT = REGISTRATE
            .item("bus_port", p -> new BusPartItem(p, BusPort.Kind.PORT))
            .lang("Data Port")
            .register();

    /** More ports for a computer, stuck onto it like a port. */
    public static final ItemEntry<BusPartItem> PORT_EXPANSION = REGISTRATE
            .item("port_expansion", p -> new BusPartItem(p, BusPort.Kind.EXPANSION))
            .lang("Port Expansion")
            .register();

    public static void register() {}

    private ModItems() {}
}
