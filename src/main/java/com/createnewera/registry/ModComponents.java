package com.createnewera.registry;

import java.util.function.Supplier;

import com.createnewera.CreateNewEra;
import com.createnewera.cable.CableStart;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.DyeColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateNewEra.MOD_ID);

    /** The dye a spool was dyed with; bare copper has none. */
    public static final Supplier<DataComponentType<DyeColor>> CABLE_COLOR = COMPONENTS.registerComponentType("cable_color",
            b -> b.persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC));

    /** The part a spool's cable is being run from, between the two clicks. */
    public static final Supplier<DataComponentType<CableStart>> CABLE_START = COMPONENTS.registerComponentType("cable_start",
            b -> b.persistent(CableStart.CODEC).networkSynchronized(CableStart.STREAM_CODEC));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    private ModComponents() {}
}
