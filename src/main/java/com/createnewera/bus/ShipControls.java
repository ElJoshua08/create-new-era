package com.createnewera.bus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.squiddev.cobalt.LuaError;

import com.createnewera.bus.DeviceDrivers.Api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * What flies an Aeronautics ship, given to programs: the throttle lever and the steering wheel,
 * the physics assembler that makes and unmakes the ship, the navigation table, the optical
 * sensor, and the propellers' thrust. Reached by reflection, so the mod needs no Aeronautics.
 */
final class ShipControls {
    private static final String SIM = "dev.simulated_team.simulated.content.blocks.";
    private static final String THROTTLE = SIM + "throttle_lever.ThrottleLeverBlockEntity",
            WHEEL = SIM + "steering_wheel.SteeringWheelBlockEntity",
            ASSEMBLER = SIM + "physics_assembler.PhysicsAssemblerBlockEntity",
            NAV_TABLE = SIM + "nav_table.NavTableBlockEntity",
            OPTICAL = SIM + "lasers.optical_sensor.OpticalSensorBlockEntity",
            PROPELLER_BEARING = "dev.eriksonn.aeronautics.content.blocks.propeller.bearing.propeller_bearing.PropellerBearingBlockEntity",
            PROPELLER = "dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity";

    static void add(Api api, ServerLevel level, BlockPos pos, BlockEntity be) {
        String type = be.getClass().getName();
        if (type.equals(THROTTLE)) {
            api.family("throttle_lever");
            api.read("getValue", "", "number 0-15", args -> throttle(level, pos, be));
            api.order("setValue", "value: 0-15", "", args -> {
                int value = args.arg(1).checkInteger();
                if (value < 0 || value > 15)
                    throw new LuaError("bad argument #1 to 'setValue' (0-15 expected, got " + value + ")");
                // setSignal takes the output and turns the lever to give it, inverted or not
                call(be, "setSignal", value);
                return null;
            });
            api.read("getMinValue", "", "number", args -> 0);
            api.read("getMaxValue", "", "number", args -> 15);
        }
        if (type.equals(WHEEL)) {
            api.family("steering_wheel");
            api.read("getAngle", "", "degrees", args -> round(number(call(be, "getAngle"))));
            api.read("getTargetAngle", "", "degrees", args -> round(number(field(be, "targetAngle"))));
            api.read("getMaxAngle", "", "degrees", args -> maxAngle(be));
            api.order("setAngle", "degrees: number", "", args -> {
                // turned as a player's hands would turn it, within its limit, the wheel catching up
                call(be, "updateTargetAngle", (float) args.arg(1).checkDouble());
                return null;
            });
        }
        if (type.equals(ASSEMBLER)) {
            api.family("physics_assembler");
            api.read("isAssembled", "", "boolean", args -> Ships.inPlot(level, new ChunkPos(pos)));
            api.read("getError", "", "string | nil", args -> {
                Object error = field(be, "lastException");
                return error instanceof Throwable t ? t.getMessage() : null;
            });
            api.order("assemble", "", "", args -> {
                if (!Ships.inPlot(level, new ChunkPos(pos)))
                    later(level, () -> call(be, "assembleOrDisassemble"));
                return null;
            });
            api.order("disassemble", "", "", args -> {
                if (Ships.inPlot(level, new ChunkPos(pos)))
                    later(level, () -> call(be, "assembleOrDisassemble"));
                return null;
            });
        }
        if (type.equals(NAV_TABLE)) {
            api.family("navigation_table");
            api.read("hasTarget", "", "boolean", args -> field(be, "currentTarget") != null);
            api.read("getTarget", "", "{x, y, z} | nil", args -> field(be, "currentTarget") instanceof Vec3 v
                    ? Map.of("x", round(v.x), "y", round(v.y), "z", round(v.z)) : null);
            api.read("getDistance", "", "blocks", args -> round(number(call(be, "distanceToTarget"))));
            api.read("getRelativeAngle", "", "degrees", args -> round(number(call(be, "getRelativeAngle"))));
        }
        if (type.equals(OPTICAL)) {
            api.family("optical_sensor");
            api.read("hasHit", "", "boolean", args -> call(be, "hasHit"));
            api.read("getDistance", "", "blocks", args -> round(number(call(be, "getHitBlockDistance"))));
            api.read("getBlock", "", "block id | nil", args -> call(be, "getHitBlock") instanceof Block block
                    && Boolean.TRUE.equals(call(be, "hasHit")) ? BuiltInRegistries.BLOCK.getKey(block).toString() : null);
            api.read("getRange", "", "blocks", args -> call(be, "getRange"));
            api.order("setRange", "blocks: number", "", args -> {
                call(be, "setRange", args.arg(1).checkInteger());
                return null;
            });
        }
        if (is(be, PROPELLER_BEARING) || is(be, PROPELLER)) {
            api.family("propeller");
            api.read("getThrust", "", "number", args -> round(number(call(be, "getThrust"))));
            api.read("getAirflow", "", "number", args -> round(number(call(be, "getAirflow"))));
            api.read("isActive", "", "boolean", args -> call(be, "isActive"));
        }
    }

    /** What onChange notices: the readings that move. */
    static @Nullable Object snapshot(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        if (be == null)
            return null;
        String type = be.getClass().getName();
        if (type.equals(THROTTLE))
            return throttle(level, pos, be);
        if (type.equals(WHEEL))
            return List.of(round(number(call(be, "getAngle"))), round(number(field(be, "targetAngle"))));
        if (type.equals(NAV_TABLE))
            return List.of(String.valueOf(field(be, "currentTarget")), Math.round(number(call(be, "getRelativeAngle"))));
        if (type.equals(OPTICAL))
            return List.of(String.valueOf(call(be, "hasHit")), Math.round(number(call(be, "getHitBlockDistance")) * 10));
        if (is(be, PROPELLER_BEARING) || is(be, PROPELLER))
            return Math.round(number(call(be, "getThrust")) * 10);
        return null;
    }

    /**
     * After this tick, not in the middle of it: a program on the ship it builds would otherwise
     * be moved while it's running.
     */
    private static void later(ServerLevel level, Runnable task) {
        var server = level.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount(), task));
    }

    /** The lever's output: its position, or the other way round on an inverted lever. */
    private static int throttle(ServerLevel level, BlockPos pos, BlockEntity be) {
        int state = call(be, "getState") instanceof Integer i ? i : 0;
        BlockState block = level.getBlockState(pos);
        for (var property : block.getProperties())
            if (property.getName().equals("inverted") && property instanceof BooleanProperty inverted && block.getValue(inverted))
                return 15 - state;
        return state;
    }

    private static Object maxAngle(BlockEntity be) {
        Object input = field(be, "angleInput");
        return input == null ? null : call(input, "getValue");
    }

    private static boolean is(BlockEntity be, String className) {
        for (Class<?> c = be.getClass(); c != null; c = c.getSuperclass())
            if (c.getName().equals(className))
                return true;
        return false;
    }

    private static double number(@Nullable Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static @Nullable Object call(Object target, String method, Object... args) {
        try {
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass())
                for (Method m : c.getDeclaredMethods())
                    if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                        m.setAccessible(true);
                        return m.invoke(target, args);
                    }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private static @Nullable Object field(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                // look further up
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private ShipControls() {}
}
