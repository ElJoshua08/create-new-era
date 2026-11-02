package com.createnewera.bus;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * The redstone input ports give the blocks they're on, ready for Level.getSignal (see
 * LevelSignalMixin), which the game calls constantly: by the spot just outside the face the port
 * is on, the side the block looks through to see it. Rebuilt from BusData whenever it changes.
 */
public final class PortSignals {
    private static final Map<Level, Map<Long, int[]>> SIGNALS = new WeakHashMap<>();

    /** The signal a port gives a block looking at {@code from} through its {@code side}, or 0. */
    public static int at(Level level, BlockPos from, Direction side) {
        Map<Long, int[]> signals = SIGNALS.get(level);
        if (signals == null)
            return 0;
        int[] faces = signals.get(from.asLong());
        return faces == null ? 0 : faces[side.ordinal()];
    }

    static void rebuild(Level level, BusData data) {
        Map<Long, int[]> signals = new HashMap<>();
        data.signals().forEach((id, strength) -> {
            BusPort port = data.port(id);
            if (port == null || strength <= 0)
                return;
            int[] faces = signals.computeIfAbsent(port.pos().relative(port.face()).asLong(), k -> new int[6]);
            faces[port.face().ordinal()] = Math.max(faces[port.face().ordinal()], strength);
        });
        if (signals.isEmpty())
            SIGNALS.remove(level);
        else
            SIGNALS.put(level, signals);
    }

    private PortSignals() {}
}
