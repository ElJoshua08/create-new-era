package com.createnewera.registry;

import static com.createnewera.CreateNewEra.REGISTRATE;

import com.createnewera.bus.BusPartItem;
import com.createnewera.bus.BusPort;
import com.createnewera.cable.CableSpoolItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

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

    /** A data port in brass: read and given orders at once, so no mode to pick. */
    public static final ItemEntry<BusPartItem> BRASS_PORT = REGISTRATE
            .item("brass_port", p -> new BusPartItem(p, BusPort.Kind.BRASS_PORT))
            .lang("Brass Data Port")
            .register();

    /** More ports for a computer, stuck onto it like a port. Retired for the port hub; kept so placed ones still load. */
    public static final ItemEntry<BusPartItem> PORT_EXPANSION = REGISTRATE
            .item("port_expansion", p -> new BusPartItem(p, BusPort.Kind.EXPANSION))
            .lang("Port Expansion")
            .register();

    /** Joins cables mid-run: sticks to any face like a port, but it isn't a device. */
    public static final ItemEntry<BusPartItem> CABLE_SPLICE = REGISTRATE
            .item("cable_splice", p -> new BusPartItem(p, BusPort.Kind.SPLICE))
            .lang("Cable Splice")
            .register();

    /** Data cable on a reel; its durability is the metres left. Models in resources, one per colour. */
    public static final ItemEntry<CableSpoolItem> CABLE_SPOOL = REGISTRATE
            .item("cable_spool", CableSpoolItem::new)
            .lang("Cable Spool")
            .model(NonNullBiConsumer.noop())
            .register();

    public static void register() {}

    private ModItems() {}
}
