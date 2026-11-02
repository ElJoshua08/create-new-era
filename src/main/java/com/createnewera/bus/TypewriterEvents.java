package com.createnewera.bus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
        /** keys held that the typewriter itself doesn't track: those with no frequency bound */
        final Set<Integer> held = new LinkedHashSet<>();
        final StringBuilder typed = new StringBuilder();
        long seq;
    }

    private static final Map<BlockEntity, Log> LOGS = new WeakHashMap<>();

    public static synchronized void key(BlockEntity typewriter, int code, boolean down) {
        if (typewriter.getLevel() == null || typewriter.getLevel().isClientSide)
            return;
        Log log = LOGS.computeIfAbsent(typewriter, k -> new Log());
        log.events.addLast(new Event(++log.seq, Typewriter.KEYS.getOrDefault(code, "key_" + code), down));
        if (down)
            log.held.add(code);
        else
            log.held.remove(code);
        if (down)
            type(log.typed, Typewriter.KEYS.getOrDefault(code, ""));
        while (log.events.size() > KEPT)
            log.events.removeFirst();
    }

    /**
     * A key with no frequency bound, sent by the typing player's client (TypewriterKeysMixin):
     * taken only from whoever is using that typewriter.
     */
    public static void typedBy(ServerLevel level, BlockPos pos, UUID player, int code, boolean down) {
        BlockEntity be = level.getBlockEntity(pos);
        if (Typewriter.is(be) && Typewriter.usedBy(be, player))
            key(be, code, down);
    }

    /** How many characters getTyped() keeps */
    private static final int TYPED = 256;

    private static void type(StringBuilder typed, String key) {
        if (key.equals("backspace")) {
            if (!typed.isEmpty())
                typed.setLength(typed.length() - 1);
            return;
        }
        if (key.equals("space"))
            typed.append(' ');
        else if (key.length() == 1)
            typed.append(key);
        if (typed.length() > TYPED)
            typed.delete(0, typed.length() - TYPED);
    }

    /** Everything typed on it, bound keys or not, the last few hundred characters. */
    static synchronized String typed(BlockEntity typewriter) {
        Log log = LOGS.get(typewriter);
        return log == null ? "" : log.typed.toString();
    }

    /** Keys held down that the typewriter doesn't list itself. */
    static synchronized Set<Integer> held(BlockEntity typewriter) {
        Log log = LOGS.get(typewriter);
        return log == null ? Set.of() : Set.copyOf(log.held);
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
