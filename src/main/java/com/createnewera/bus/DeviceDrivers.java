package com.createnewera.bus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.Varargs;

import com.createnewera.block.control.PanelControlBlock;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * What a program can do with a device, worked out from what the block is: anything that
 * turns, anything that holds items, anything that holds fluids. Values come back as plain
 * Java objects (numbers, strings, booleans, lists, maps) and are turned into Lua elsewhere.
 */
public final class DeviceDrivers {
    @FunctionalInterface
    public interface Method {
        Object call(Varargs args) throws LuaError;
    }

    /**
     * A function a program can call on a device, with what the editor shows for it.
     *
     * @param params  its arguments as the editor writes them, e.g. "value: 0-15"
     * @param returns what it gives back, e.g. "number 0-15", or "" for nothing
     * @param doc     lang key of what it does: create_new_era.api.(family).(name)
     * @param reads   reads the device (needs an output port) rather than giving it an order
     */
    public record Fn(String name, String params, String returns, String doc, boolean reads, Method call) {}

    /** Collects a device's functions, keeping only those its ports allow. */
    public static final class Api {
        private final Map<String, Fn> fns = new LinkedHashMap<>();
        private final boolean readable, writable;
        private String family = "machine";

        Api(boolean readable, boolean writable) {
            this.readable = readable;
            this.writable = writable;
        }

        /** Which group of docs the functions added next belong to: "kinetic", "items", "panel_lamp"... */
        public Api family(String family) {
            this.family = family;
            return this;
        }

        public void read(String name, String params, String returns, Method call) {
            if (readable)
                fns.put(name, new Fn(name, params, returns, docKey(family, name), true, call));
        }

        public void order(String name, String params, String returns, Method call) {
            if (writable)
                fns.put(name, new Fn(name, params, returns, docKey(family, name), false, call));
        }
    }

    public static String docKey(String family, String name) {
        return "create_new_era.api." + family + "." + name;
    }

    /** Readers need an output port on the device, writers an input port. */
    public static Map<String, Method> methods(ServerLevel level, BlockPos pos, boolean readable, boolean writable) {
        Map<String, Method> out = new LinkedHashMap<>();
        describe(level, pos, readable, writable).forEach(fn -> out.put(fn.name(), fn.call()));
        return out;
    }

    /**
     * Every function the device's ports allow, in the order the editor lists them, ending with
     * read(): the device's state in one value, which onChange also hands its function.
     */
    public static List<Fn> describe(ServerLevel level, BlockPos pos, boolean readable, boolean writable) {
        Api api = new Api(readable, writable);
        if (level.getBlockState(pos).getBlock() instanceof PanelControlBlock control) {
            api.family(BuiltInRegistries.BLOCK.getKey(control).getPath());
            control.methods(api, level, pos);
            api.read("read", "", control.valueType(), args -> control.value(level.getBlockState(pos)));
            return List.copyOf(api.fns.values());
        }
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof KineticBlockEntity kinetic) {
            api.family("kinetic");
            api.read("getSpeed", "", "RPM", args -> kinetic.getSpeed());
            api.read("isOverstressed", "", "boolean", args -> overstressed(kinetic));
            api.read("getStress", "", "{used, capacity}", args -> stress(kinetic));
            ScrollValueBehaviour speedSetting = speedSetting(kinetic);
            if (speedSetting != null) {
                api.read("getTargetSpeed", "", "RPM", args -> speedSetting.getValue());
                api.read("getMinValue", "", "RPM", args -> -256);
                api.read("getMaxValue", "", "RPM", args -> 256);
                api.order("setSpeed", "rpm: -256..256", "", args -> {
                    int rpm = args.arg(1).checkInteger();
                    speedSetting.setValue(Mth.clamp(rpm, -256, 256));
                    return null;
                });
            }
        }

        IItemHandler items = itemHandler(level, pos);
        if (items != null) {
            api.family("items");
            api.read("items", "", "{[item id] = count}", args -> itemCounts(items));
            api.read("count", "item: id", "number", args -> itemCounts(items).getOrDefault(args.arg(1).checkString(), 0));
            api.read("getSlots", "", "number", args -> items.getSlots());
        }

        IFluidHandler fluids = fluidHandler(level, pos);
        if (fluids != null) {
            api.family("fluids");
            api.read("fluids", "", "{{name, amount, capacity}, ...}", args -> tanks(fluids));
        }

        if (!api.fns.isEmpty()) {
            api.family("machine");
            api.read("read", "", "{speed, overstressed, stress, items, fluids...}", args -> state(level, pos));
        }
        return List.copyOf(api.fns.values());
    }

    /** What read() gives for a machine: everything readable about it, by name. */
    private static Map<String, Object> state(ServerLevel level, BlockPos pos) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic) {
            out.put("speed", kinetic.getSpeed());
            out.put("overstressed", overstressed(kinetic));
            out.put("stress", stress(kinetic));
            ScrollValueBehaviour speedSetting = speedSetting(kinetic);
            if (speedSetting != null)
                out.put("targetSpeed", speedSetting.getValue());
        }
        IItemHandler items = itemHandler(level, pos);
        if (items != null)
            out.put("items", itemCounts(items));
        IFluidHandler fluids = fluidHandler(level, pos);
        if (fluids != null)
            out.put("fluids", tanks(fluids));
        return out;
    }

    /**
     * Everything readable about the device in one comparable value, so a computer can tell
     * when it changed without knowing what kind of device it is.
     */
    public static Object snapshot(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PanelControlBlock control)
            return control.snapshot(state);
        List<Object> parts = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic) {
            parts.add(kinetic.getSpeed());
            parts.add(overstressed(kinetic));
            ScrollValueBehaviour speedSetting = speedSetting(kinetic);
            if (speedSetting != null)
                parts.add(speedSetting.getValue());
        }
        IItemHandler items = itemHandler(level, pos);
        if (items != null)
            parts.add(itemCounts(items));
        IFluidHandler fluids = fluidHandler(level, pos);
        if (fluids != null)
            parts.add(tanks(fluids));
        return parts;
    }

    private static boolean overstressed(KineticBlockEntity kinetic) {
        // Create reports 0 RPM for an overstressed network while remembering the real speed
        return kinetic.getSpeed() == 0 && kinetic.getTheoreticalSpeed() != 0;
    }

    private static Map<String, Object> stress(KineticBlockEntity kinetic) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (kinetic.hasNetwork()) {
            KineticNetwork network = kinetic.getOrCreateNetwork();
            out.put("used", network.calculateStress());
            out.put("capacity", network.calculateCapacity());
        } else {
            out.put("used", 0);
            out.put("capacity", 0);
        }
        return out;
    }

    /** The speed a creative motor or a rotation speed controller is set to, if this is one. */
    private static @Nullable ScrollValueBehaviour speedSetting(KineticBlockEntity kinetic) {
        if (kinetic instanceof CreativeMotorBlockEntity motor)
            return motor.generatedSpeed;
        if (kinetic instanceof SpeedControllerBlockEntity controller)
            return controller.targetSpeed;
        return null;
    }

    private static @Nullable IItemHandler itemHandler(ServerLevel level, BlockPos pos) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null)
            return handler;
        // some blocks only answer for a side
        for (Direction side : Direction.values())
            if ((handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)) != null)
                return handler;
        return null;
    }

    private static @Nullable IFluidHandler fluidHandler(ServerLevel level, BlockPos pos) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler != null)
            return handler;
        for (Direction side : Direction.values())
            if ((handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side)) != null)
                return handler;
        return null;
    }

    /** Item id → total count, sorted by id so the same contents always compare equal. */
    private static Map<String, Integer> itemCounts(IItemHandler items) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty())
                counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static List<Map<String, Object>> tanks(IFluidHandler fluids) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < fluids.getTanks(); i++) {
            FluidStack stack = fluids.getFluidInTank(i);
            Map<String, Object> tank = new LinkedHashMap<>();
            tank.put("name", stack.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
            tank.put("amount", stack.getAmount());
            tank.put("capacity", fluids.getTankCapacity(i));
            out.add(tank);
        }
        return out;
    }

    private DeviceDrivers() {}
}
