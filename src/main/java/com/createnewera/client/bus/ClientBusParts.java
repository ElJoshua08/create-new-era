package com.createnewera.client.bus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.createnewera.bus.BusPort;
import com.createnewera.bus.BusSpan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;

/** The bus parts and cables in the chunks this client has loaded, as the server last sent them. */
public final class ClientBusParts {
    private static final Map<Long, List<BusPort>> BY_CHUNK = new HashMap<>();
    /** Cables, filed under the chunk of their first end */
    private static final Map<Long, List<BusSpan>> SPANS = new HashMap<>();

    public static void set(long chunk, List<BusPort> parts, List<BusSpan> spans) {
        if (parts.isEmpty())
            BY_CHUNK.remove(chunk);
        else
            BY_CHUNK.put(chunk, List.copyOf(parts));
        if (spans.isEmpty())
            SPANS.remove(chunk);
        else
            SPANS.put(chunk, List.copyOf(spans));
    }

    public static void forget(long chunk) {
        BY_CHUNK.remove(chunk);
        SPANS.remove(chunk);
    }

    public static void clear() {
        BY_CHUNK.clear();
        SPANS.clear();
    }

    public static List<BusSpan> spans() {
        List<BusSpan> out = new ArrayList<>();
        for (List<BusSpan> chunk : SPANS.values())
            out.addAll(chunk);
        return out;
    }

    /** The part with this id, if its chunk is loaded. */
    public static @Nullable BusPort part(long id) {
        for (List<BusPort> chunk : BY_CHUNK.values())
            for (BusPort part : chunk)
                if (part.id() == id)
                    return part;
        return null;
    }

    /** The colour a part's cables have fixed, as far as this client can see. */
    public static @Nullable DyeColor colorAt(long port) {
        for (List<BusSpan> chunk : SPANS.values())
            for (BusSpan span : chunk)
                if (span.touches(port) && span.color() != null)
                    return span.color();
        return null;
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
