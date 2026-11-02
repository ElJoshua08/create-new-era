package com.createnewera.bus;

import java.lang.reflect.Method;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.createnewera.bus.DeviceDrivers.Api;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Aeronautics' own sensors on the bus: the altitude sensor, the gimbal sensor and the velocity
 * sensor give programs the numbers they measure, not only the redstone they turn them into.
 * Reached by reflection, like the typewriter, so the mod needs no Aeronautics to build or run.
 */
final class AeronauticsSensors {
    private static final String PACKAGE = "dev.simulated_team.simulated.content.blocks.";
    private static final String ALTITUDE = PACKAGE + "altitude_sensor.AltitudeSensorBlockEntity",
            GIMBAL = PACKAGE + "gimbal_sensor.GimbalSensorBlockEntity",
            VELOCITY = PACKAGE + "velocity_sensor.VelocitySensorBlockEntity";

    static void add(Api api, BlockEntity be) {
        switch (be.getClass().getName()) {
            case ALTITUDE -> {
                api.family("altitude_sensor");
                api.read("getAltitude", "", "number", args -> number(call(be, "getWorldHeight")));
                api.read("getAirPressure", "", "number 0-1", args -> number(call(be, "getAirPressure")));
                api.read("getSignal", "", "number 0-15", args -> call(be, "getSignal"));
            }
            case GIMBAL -> {
                api.family("gimbal_sensor");
                // it measures in radians; programs (and its own goggles) speak degrees
                api.read("getPitch", "", "degrees", args -> degrees(call(be, "getXAngle")));
                api.read("getRoll", "", "degrees", args -> degrees(call(be, "getZAngle")));
                api.read("getAngles", "", "{pitch, roll}", args -> java.util.Map.of(
                        "pitch", degrees(call(be, "getXAngle")), "roll", degrees(call(be, "getZAngle"))));
                api.read("getPower", "side: name", "number 0-15", args -> {
                    Direction side = Direction.byName(args.arg(1).checkString());
                    if (side == null)
                        throw new org.squiddev.cobalt.LuaError("bad argument #1 to 'getPower' (a side expected: up, down, north, south, east or west)");
                    return call(be, "getPower", side);
                });
                api.choices("getPower", List.of("up", "down", "north", "south", "east", "west"));
            }
            case VELOCITY -> {
                api.family("velocity_sensor");
                api.read("getSpeed", "", "m/s", args -> number(call(be, "getAdjustedVelocity")));
                api.read("getSignal", "", "number -15..15", args -> call(be, "getRedstoneStrength"));
            }
            default -> {}
        }
    }

    /** What onChange notices: the numbers move every tick on a moving ship, the signal less often. */
    static @Nullable Object snapshot(@Nullable BlockEntity be) {
        if (be == null)
            return null;
        return switch (be.getClass().getName()) {
            case ALTITUDE -> List.of(rounded(call(be, "getWorldHeight")), String.valueOf(call(be, "getSignal")));
            case GIMBAL -> List.of(rounded(degrees(call(be, "getXAngle"))), rounded(degrees(call(be, "getZAngle"))));
            case VELOCITY -> List.of(rounded(call(be, "getAdjustedVelocity")));
            default -> null;
        };
    }

    /** Tenths are plenty to wake onChange; finer would fire on every jitter. */
    private static Object rounded(@Nullable Object value) {
        return value instanceof Number n ? Math.round(n.doubleValue() * 10) / 10.0 : String.valueOf(value);
    }

    private static @Nullable Object number(@Nullable Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static @Nullable Object degrees(@Nullable Object radians) {
        return radians instanceof Number n ? Math.round(Math.toDegrees(n.doubleValue()) * 100) / 100.0 : null;
    }

    private static @Nullable Object call(BlockEntity be, String method, Object... args) {
        try {
            for (Method m : be.getClass().getMethods())
                if (m.getName().equals(method) && m.getParameterCount() == args.length)
                    return m.invoke(be, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    private AeronauticsSensors() {}
}
