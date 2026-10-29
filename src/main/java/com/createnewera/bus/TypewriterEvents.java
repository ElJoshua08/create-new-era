package com.createnewera.bus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Every key pressed or released on each linked typewriter (TypewriterMixin reports them), kept
 * briefly and numbered, so a program reads each one in order however many happen in a tick.
 */
public final class TypewriterEvents {
    /** How many recent key events each typewriter keeps */
    private static final int KEPT = 64;

    /** @param seq numbered from 1 per typewriter, so a program asks for the ones after the last it saw */
    public record Event(long seq, String key, boolean down) {
        Map<String, Object> asTable() {
            return Map.of("seq", seq, "key", key, "down", down);
        }
    }

    private static final class Log {
        final Deque<Event> events = new ArrayDeque<>();
        long seq;
    }

    private static final Map<BlockEntity, Log> LOGS = new WeakHashMap<>();

    public static synchronized void key(BlockEntity typewriter, int code, boolean down) {
        if (typewriter.getLevel() == null || typewriter.getLevel().isClientSide)
            return;
        Log log = LOGS.computeIfAbsent(typewriter, k -> new Log());
        log.events.addLast(new Event(++log.seq, Typewriter.KEYS.getOrDefault(code, "key_" + code), down));
        while (log.events.size() > KEPT)
            log.events.removeFirst();
    }

    /** The number of the latest key event, 0 before any: what onChange watches. */
    static synchronized long latest(BlockEntity typewriter) {
        Log log = LOGS.get(typewriter);
        return log == null ? 0 : log.seq;
    }

    /** The key events after {@code since}, oldest first, as tables for a program. */
    static synchronized List<Map<String, Object>> since(BlockEntity typewriter, long since) {
        Log log = LOGS.get(typewriter);
        if (log == null)
            return List.of();
        return log.events.stream().filter(e -> e.seq() > since).map(Event::asTable).toList();
    }

    private TypewriterEvents() {}
}
