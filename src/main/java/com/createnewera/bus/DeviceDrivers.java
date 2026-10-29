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

    /** Readers need an output port on the device, writers an input port. */
    public static Map<String, Method> methods(ServerLevel level, BlockPos pos, boolean readable, boolean writable) {
        Map<String, Method> out = new LinkedHashMap<>();
        if (level.getBlockState(pos).getBlock() instanceof PanelControlBlock control) {
            control.methods(out, level, pos);
            return out;
        }
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof KineticBlockEntity kinetic) {
            if (readable) {
                out.put("getSpeed", args -> kinetic.getSpeed());
                out.put("isOverstressed", args -> overstressed(kinetic));
                out.put("getStress", args -> stress(kinetic));
            }
            ScrollValueBehaviour speedSetting = speedSetting(kinetic);
            if (writable && speedSetting != null) {
                out.put("setSpeed", args -> {
                    int rpm = args.arg(1).checkInteger();
                    speedSetting.setValue(Mth.clamp(rpm, -256, 256));
                    return null;
                });
            }
            if (readable && speedSetting != null)
                out.put("getTargetSpeed", args -> speedSetting.getValue());
        }

        IItemHandler items = itemHandler(level, pos);
        if (items != null && readable) {
            out.put("items", args -> itemCounts(items));
            out.put("count", args -> itemCounts(items).getOrDefault(args.arg(1).checkString(), 0));
            out.put("getSlots", args -> items.getSlots());
        }

        IFluidHandler fluids = fluidHandler(level, pos);
        if (fluids != null && readable)
            out.put("fluids", args -> tanks(fluids));
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
