package com.createnewera.client.bus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createnewera.bus.BusPort;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/** The bus parts in the chunks this client has loaded, as the server last sent them. */
public final class ClientBusParts {
    private static final Map<Long, List<BusPort>> BY_CHUNK = new HashMap<>();

    public static void set(long chunk, List<BusPort> parts) {
        if (parts.isEmpty())
            BY_CHUNK.remove(chunk);
        else
            BY_CHUNK.put(chunk, List.copyOf(parts));
    }

    public static void forget(long chunk) {
        BY_CHUNK.remove(chunk);
    }

    public static void clear() {
        BY_CHUNK.clear();
    }

    public static List<BusPort> at(BlockPos pos) {
        List<BusPort> out = new ArrayList<>();
        for (BusPort part : BY_CHUNK.getOrDefault(ChunkPos.asLong(pos), List.of()))
            if (part.pos().equals(pos))
                out.add(part);
        return out;
    }

    public static Iterable<List<BusPort>> chunks() {
        return BY_CHUNK.values();
    }

    private ClientBusParts() {}
}
