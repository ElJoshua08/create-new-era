package com.createnewera.bus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.createnewera.bus.DeviceDrivers.Api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Aeronautics' Linked Typewriter (from its Simulated core) on the bus: which keys are held on it,
 * who's typing, what's been typed. Reached by reflection, so the mod needs no Aeronautics to
 * build or run; without it there's simply no typewriter to find.
 */
final class Typewriter {
    private static final String CLASS = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity";
    /** The keys, by the GLFW codes the typewriter reports, as programs name them */
    static final Map<Integer, String> KEYS = new LinkedHashMap<>();

    static {
        for (char c = 'a'; c <= 'z'; c++)
            KEYS.put((int) Character.toUpperCase(c), String.valueOf(c));
        for (char c = '0'; c <= '9'; c++)
            KEYS.put((int) c, String.valueOf(c));
        String[][] named = {{"32", "space"}, {"256", "escape"}, {"257", "enter"}, {"258", "tab"}, {"259", "backspace"},
                {"262", "right"}, {"263", "left"}, {"264", "down"}, {"265", "up"}, {"340", "left_shift"},
                {"341", "left_control"}, {"342", "left_alt"}, {"344", "right_shift"}, {"345", "right_control"},
                {"346", "right_alt"}, {"39", "'"}, {"44", ","}, {"45", "-"}, {"46", "."}, {"47", "/"}, {"59", ";"},
                {"61", "="}, {"91", "["}, {"92", "\\"}, {"93", "]"}, {"96", "`"}};
        for (String[] k : named)
            KEYS.put(Integer.parseInt(k[0]), k[1]);
        for (int f = 1; f <= 12; f++)
            KEYS.put(289 + f, "f" + f);
    }

    static boolean is(@Nullable BlockEntity be) {
        return be != null && be.getClass().getName().equals(CLASS);
    }

    static void add(Api api, ServerLevel level, BlockEntity be) {
        if (!is(be))
            return;
        api.family("typewriter");
        api.read("getPressedKeys", "", "{\"w\", \"space\", ...}", args -> pressed(be));
        api.read("isPressed", "key: name", "boolean", args -> pressed(be).contains(args.arg(1).checkString().toLowerCase(java.util.Locale.ROOT)));
        api.choices("isPressed", List.copyOf(KEYS.values()));
        api.read("isInUse", "", "boolean", args -> (Boolean) call(be, "isInUse"));
        api.read("getUser", "", "string | nil", args -> {
            Object user = field(be, "currentUser");
            if (!(user instanceof UUID id))
                return null;
            var player = level.getServer().getPlayerList().getPlayer(id);
            return player == null ? null : player.getGameProfile().getName();
        });
        api.read("getKeyEvents", "since?: number", "{{key, down, seq}, ...}",
                args -> TypewriterEvents.since(be, args.arg(1).isNil() ? 0 : (long) args.arg(1).checkDouble()));
        api.read("getTyped", "", "string", args -> {
            Object typed = call(be, "getTypedEntry");
            return typed == null ? "" : typed.toString();
        });
    }

    /** The keys held right now, by name. */
    static List<String> pressed(BlockEntity be) {
        List<String> out = new ArrayList<>();
        if (call(be, "getPressedKeys") instanceof List<?> codes)
            for (Object code : codes)
                if (code instanceof Integer c)
                    out.add(KEYS.getOrDefault(c, "key_" + c));
        return out;
    }

    /** What onChange notices: every key event, and the keys held. */
    static @Nullable Object snapshot(@Nullable BlockEntity be) {
        return is(be) ? List.of(TypewriterEvents.latest(be), pressed(be)) : null;
    }

    private static @Nullable Object call(BlockEntity be, String method) {
        try {
            Method m = be.getClass().getMethod(method);
            return m.invoke(be);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Object field(BlockEntity be, String name) {
        try {
            Field f = be.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(be);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private Typewriter() {}
}
