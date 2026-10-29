package com.createnewera.client.editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.createnewera.client.editor.LuaApi.Entry;
import com.createnewera.client.editor.LuaApi.Kind;
import com.createnewera.network.NetworkDevice;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Works out what to suggest at the cursor. Pure text in, ranked suggestions out. */
public final class Completions {
    private static final Pattern SIDE_ARG = Pattern.compile("\\b(?:redstone|rs)\\s*\\.\\s*\\w+\\s*\\(\\s*([\"'])(\\w*)$");
    private static final Pattern DEVICE_ARG = Pattern.compile("\\bbus\\s*[.:]\\s*(?:get|info)\\s*\\(\\s*([\"'])(\\w*)$");
    /** {@code local press = bus.get("press")}: the variable holds that device */
    private static final Pattern DEVICE_VAR = Pattern.compile("([A-Za-z_]\\w*)\\s*=\\s*bus\\s*[.:]\\s*get\\s*\\(\\s*[\"'](\\w+)[\"']\\s*\\)");
    /** {@code lamp.set("am}: a string as a device function's first argument */
    private static final Pattern STRING_ARG = Pattern.compile("([A-Za-z_]\\w*)\\s*[.:]\\s*([A-Za-z_]\\w*)\\s*\\(\\s*([\"'])([\\w:.\\-]*)$");
    private static final Pattern REQUIRE_ARG = Pattern.compile("\\brequire\\s*\\(?\\s*([\"'])([\\w./\\-]*)$");
    private static final Pattern MEMBER = Pattern.compile("([A-Za-z_]\\w*)\\s*([.:])\\s*(\\w*)$");
    private static final Pattern WORD = Pattern.compile("([A-Za-z_]\\w*)$");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_]\\w*");
    private static final Pattern FUNCTION_DEF = Pattern.compile("\\bfunction\\s+([A-Za-z_][\\w.:]*)|([A-Za-z_]\\w*)\\s*=\\s*function\\b");

    /**
     * @param from  column where the replaced text starts; accepting replaces from there to the cursor
     * @param query what's typed so far
     */
    public record Result(int from, String query, List<Match> items) {
        public static final Result NONE = new Result(0, "", List.of());
    }

    /** @param matched which characters of the label matched the query, to draw them highlighted */
    public record Match(Entry entry, boolean[] matched) {}

    /**
     * @param beforeCursor the file from its start up to the cursor
     * @param document     the whole file, for names it defines
     * @param files        files on the computer, for require("...")
     * @param devices      the computer's network, nearest first, for device names and their methods
     * @param viewer       the player, to say which way each device lies from them
     * @param explicit     Ctrl+Space: suggest even with nothing typed
     */
    public static Result complete(String beforeCursor, String document, List<String> files, List<NetworkDevice> devices,
                                  Viewer viewer, boolean explicit) {
        int lineStart = beforeCursor.lastIndexOf('\n') + 1;
        String line = beforeCursor.substring(lineStart);
        Matcher m;
        if ((m = SIDE_ARG.matcher(line)).find())
            return rank(line.length() - m.group(2).length(), m.group(2), values(LuaApi.SIDES, "side"));
        if ((m = DEVICE_ARG.matcher(line)).find())
            return rank(line.length() - m.group(2).length(), m.group(2), deviceNames(devices, viewer));
        if ((m = STRING_ARG.matcher(line)).find()) {
            List<Entry> values = argumentChoices(document, m.group(1), m.group(2), devices, viewer);
            if (values != null)
                return rank(line.length() - m.group(4).length(), m.group(4), values);
        }
        if ((m = REQUIRE_ARG.matcher(line)).find())
            return rank(line.length() - m.group(2).length(), m.group(2), values(modules(files), "file on this computer"));

        // nothing to suggest inside comments and strings (sides and file names were handled above)
        int[] colors = new int[line.length()];
        LuaHighlighter.State state = LuaHighlighter.State.CODE;
        if (lineStart > 0)
            for (String previous : beforeCursor.substring(0, lineStart - 1).split("\n", -1))
                state = LuaHighlighter.highlight(previous, new int[previous.length()], state);
        LuaHighlighter.highlight(line, colors, state);
        if (!line.isEmpty()) {
            int last = colors[line.length() - 1];
            if (last == LuaHighlighter.COMMENT || last == LuaHighlighter.STRING)
                return Result.NONE;
        }

        if ((m = MEMBER.matcher(line)).find()) {
            String base = m.group(1), query = m.group(3);
            List<Entry> members = LuaApi.MEMBERS.get(base);
            if (members == null)
                members = deviceMethods(document, base, devices);
            if (members == null)
                members = fieldsSeen(document, base);
            if (query.isEmpty() && members.isEmpty())
                return Result.NONE;
            return rank(line.length() - query.length(), query, members);
        }

        m = WORD.matcher(line);
        String query = m.find() ? m.group(1) : "";
        if (query.isEmpty() && !explicit)
            return Result.NONE;
        List<Entry> pool = new ArrayList<>(namesInDocument(document, query));
        pool.addAll(LuaApi.GLOBALS);
        return rank(line.length() - query.length(), query, pool);
    }

    private static List<Entry> values(List<String> names, String detail) {
        return names.stream().map(n -> new Entry(n, Kind.VALUE, n, detail, "")).toList();
    }

    /** Turns lang keys into text; the editor plugs in I18n, tests can leave it as is. */
    public static Function<String, String> translate = key -> key;

    /**
     * Where the player is and which way they face, so a device's arrow points the way they'd
     * turn to find it.
     *
     * @param yaw Minecraft's yaw: 0 faces south (+z), 90 faces west
     */
    public record Viewer(Vec3 eye, float yaw) {}

    /** Device names in distance order: rank keeps that order among equally good matches. */
    private static List<Entry> deviceNames(List<NetworkDevice> devices, Viewer viewer) {
        return devices.stream().map(d -> {
            String type = translate.apply(d.typeKey());
            List<String> doc = new ArrayList<>();
            doc.add(translate.apply(d.readable() && d.writable() ? "create_new_era.editor.device.ports_both"
                    : d.readable() ? "create_new_era.editor.device.ports_out" : "create_new_era.editor.device.ports_in"));
            if (!d.reads().isEmpty())
                doc.add(translate.apply("create_new_era.editor.device.reads") + " " + calls(d.reads()) + ".");
            if (!d.orders().isEmpty())
                doc.add(translate.apply("create_new_era.editor.device.orders") + " " + calls(d.orders()) + ".");
            String footer = "x " + d.pos().getX() + " · y " + d.pos().getY() + " · z " + d.pos().getZ();
            return new Entry(d.name(), Kind.DEVICE, d.name(), d.name() + " · " + type, String.join(" ", doc),
                    type, d.distance() + " m " + arrow(d.pos(), viewer), footer);
        }).toList();
    }

    private static String calls(List<NetworkDevice.Fn> fns) {
        return String.join(", ", fns.stream().map(f -> f.name() + "()").toList());
    }

    /**
     * Which way the device lies for someone at the viewer's eye, facing their way: ↑ straight
     * ahead, → to their right, and so on round; ⇡ or ⇣ when it's mostly straight up or down.
     */
    static String arrow(BlockPos device, Viewer viewer) {
        if (viewer == null)
            return "";
        Vec3 to = Vec3.atCenterOf(device).subtract(viewer.eye());
        double flat = Math.hypot(to.x, to.z);
        if (flat < 1 && Math.abs(to.y) >= 1)
            return to.y > 0 ? "⇡" : "⇣";
        double yaw = Math.toRadians(viewer.yaw());
        double ahead = to.x * -Math.sin(yaw) + to.z * Math.cos(yaw);
        double right = to.x * -Math.cos(yaw) + to.z * -Math.sin(yaw);
        int sector = (int) Math.round(((Math.toDegrees(Math.atan2(right, ahead)) % 360) + 360) % 360 / 45) % 8;
        return new String[] {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[sector];
    }

    /** The device a variable holds, from {@code local x = bus.get("name")} in the file, if it's on the network. */
    private static NetworkDevice deviceIn(String document, String variable, List<NetworkDevice> devices) {
        Matcher m = DEVICE_VAR.matcher(document);
        String name = null;
        while (m.find())
            if (m.group(1).equals(variable))
                name = m.group(2);
        if (name == null)
            return null;
        for (NetworkDevice d : devices)
            if (d.name().equals(name))
                return d;
        return null;
    }

    /** What a string first argument of a device's function can be: its choices, or the devices on the network. */
    private static List<Entry> argumentChoices(String document, String variable, String function, List<NetworkDevice> devices,
                                               Viewer viewer) {
        NetworkDevice d = deviceIn(document, variable, devices);
        if (d == null)
            return null;
        for (NetworkDevice.Fn fn : d.functions())
            if (fn.name().equals(function) && !fn.choices().isEmpty())
                return fn.choices().contains(com.createnewera.bus.DeviceDrivers.DEVICES) ? deviceNames(devices, viewer)
                        : fn.choices().contains(com.createnewera.block.control.PanelSpeakerBlock.SOUNDS) ? values(soundIds(), "sound")
                        : values(fn.choices(), fn.name());
        return null;
    }

    private static List<String> sounds;

    /** Every sound the game knows, sorted: the speaker's playSound(" offers them. */
    private static List<String> soundIds() {
        if (sounds == null)
            sounds = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet().stream().map(Object::toString).sorted().toList();
        return sounds;
    }

    /** What a variable holding {@code bus.get("name")} can do, or null if it isn't one. */
    private static List<Entry> deviceMethods(String document, String base, List<NetworkDevice> devices) {
        NetworkDevice d = deviceIn(document, base, devices);
        if (d == null)
            return null;
        List<Entry> out = new ArrayList<>();
        for (NetworkDevice.Fn fn : d.functions())
            out.add(new Entry(fn.name(), Kind.FUNCTION, fn.name() + (fn.params().isEmpty() ? "()" : "($0)"),
                    fn.signature(), translate.apply(fn.doc())));
        out.add(new Entry("onChange", Kind.FUNCTION, "onChange(function(value)\n  $0\nend)",
                "onChange(fn(value, device))", changeDoc(d)));
        if (d.methods().contains("isPressed"))
            out.add(new Entry("onPress", Kind.FUNCTION, "onPress(function()\n  $0\nend)", "onPress(fn(device))",
                    translate.apply("create_new_era.api.on_press")));
        if (d.methods().contains("getKey"))
            out.add(new Entry("onKey", Kind.FUNCTION, "onKey(function(key)\n  $0\nend)", "onKey(fn(key, device))",
                    translate.apply("create_new_era.api.on_key")));
        return out;
    }

    /** What onChange's function is handed for this device: the same as its read(). */
    private static String changeDoc(NetworkDevice d) {
        String value = d.read().map(f -> f.returns() + ": " + translate.apply(f.doc())).orElse("nil");
        return String.format(translate.apply("create_new_era.api.on_change"), value);
    }

    private static final Pattern CALL_OPEN = Pattern.compile("([A-Za-z_]\\w*)\\s*([.:])\\s*([A-Za-z_]\\w*)\\s*\\(([^()]*)$");
    private static final Pattern GLOBAL_CALL_OPEN = Pattern.compile("(?<![.:\\w])([A-Za-z_]\\w*)\\s*\\(([^()]*)$");

    /**
     * Signature help: inside a call's parentheses, what that function takes and does, like VS
     * Code's parameter hints. Null when the cursor isn't inside a call the editor knows.
     */
    public static Entry signature(String beforeCursor, String document, List<NetworkDevice> devices) {
        String line = beforeCursor.substring(beforeCursor.lastIndexOf('\n') + 1);
        Matcher m = CALL_OPEN.matcher(line);
        if (m.find()) {
            String base = m.group(1), method = m.group(3);
            NetworkDevice d = deviceIn(document, base, devices);
            if (d != null) {
                for (NetworkDevice.Fn fn : d.functions())
                    if (fn.name().equals(method))
                        return new Entry(fn.name(), Kind.FUNCTION, "", fn.signature(), translate.apply(fn.doc()));
                if (method.equals("onChange"))
                    return new Entry("onChange", Kind.FUNCTION, "", "onChange(fn(value, device))", changeDoc(d));
            }
            for (Entry e : LuaApi.MEMBERS.getOrDefault(base, List.of()))
                if (e.label().equals(method))
                    return e;
            return null;
        }
        m = GLOBAL_CALL_OPEN.matcher(line);
        if (m.find())
            for (Entry e : LuaApi.GLOBALS)
                if (e.label().equals(m.group(1)) && e.kind() == Kind.FUNCTION)
                    return e;
        return null;
    }


    private static List<String> modules(List<String> files) {
        return files.stream().map(f -> f.endsWith(".lua") ? f.substring(0, f.length() - 4) : f).toList();
    }

    /** Fields written after base. somewhere in the file, for tables the API doesn't know. */
    private static List<Entry> fieldsSeen(String document, String base) {
        Set<String> seen = new HashSet<>();
        List<Entry> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\b" + Pattern.quote(base) + "\\s*[.:]\\s*([A-Za-z_]\\w*)").matcher(document);
        while (m.find())
            if (seen.add(m.group(1)))
                out.add(new Entry(m.group(1), Kind.FIELD, m.group(1), base + "." + m.group(1), ""));
        return out;
    }

    /** Variables and functions the file defines or uses, except the word being typed. */
    private static List<Entry> namesInDocument(String document, String typing) {
        Map<String, Kind> found = new LinkedHashMap<>();
        Matcher f = FUNCTION_DEF.matcher(document);
        while (f.find()) {
            String name = f.group(1) != null ? f.group(1) : f.group(2);
            if (!name.contains(".") && !name.contains(":"))
                found.put(name, Kind.FUNCTION);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher m = IDENT.matcher(stripStringsAndComments(document));
        while (m.find())
            counts.merge(m.group(), 1, Integer::sum);
        Set<String> known = new HashSet<>();
        LuaApi.GLOBALS.forEach(e -> known.add(e.label()));
        counts.forEach((name, count) -> {
            if (known.contains(name) || (name.equals(typing) && count <= 1))
                return;
            found.putIfAbsent(name, Kind.VARIABLE);
        });
        List<Entry> out = new ArrayList<>();
        found.forEach((name, kind) -> out.add(new Entry(name, kind, kind == Kind.FUNCTION ? name + "($0)" : name,
                kind == Kind.FUNCTION ? "function in this file" : "in this file", "")));
        return out;
    }

    private static String stripStringsAndComments(String document) {
        StringBuilder out = new StringBuilder();
        LuaHighlighter.State state = LuaHighlighter.State.CODE;
        for (String line : document.split("\n", -1)) {
            int[] colors = new int[line.length()];
            state = LuaHighlighter.highlight(line, colors, state);
            boolean field = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                boolean code = colors[i] != LuaHighlighter.STRING && colors[i] != LuaHighlighter.COMMENT;
                // a field after a dot or colon belongs to its table, not the list of names
                if (code && (c == '.' || c == ':'))
                    field = true;
                else if (!(Character.isLetterOrDigit(c) || c == '_'))
                    field = false;
                out.append(code && !field ? c : ' ');
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static Result rank(int from, String query, List<Entry> pool) {
        List<Scored> scored = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry e : pool) {
            if (!seen.add(e.label() + e.kind()))
                continue;
            // nothing to offer for a word that's already complete
            if (e.label().equals(query) && e.kind() != Kind.SNIPPET && e.kind() != Kind.FUNCTION)
                continue;
            boolean[] matched = new boolean[e.label().length()];
            int score = score(e.label(), query, matched);
            if (score > 0)
                scored.add(new Scored(new Match(e, matched), score));
        }
        Comparator<Scored> byScore = Comparator.comparingInt((Scored s) -> -s.score)
                .thenComparingInt(s -> order(s.match.entry().kind()));
        if (pool.stream().allMatch(e -> e.kind() == Kind.VALUE || e.kind() == Kind.DEVICE)) {
            // sides and files read best in their own order (front, back, left...), devices nearest first
            scored.sort(byScore);
        } else if (query.isEmpty()) {
            scored.sort(byScore.thenComparing(s -> s.match.entry().label()));
        } else {
            // shorter first: typing "get" should offer getInput before getAnalogInput
            scored.sort(byScore.thenComparingInt((Scored s) -> s.match.entry().label().length())
                    .thenComparing(s -> s.match.entry().label()));
        }
        List<Match> items = scored.stream().limit(50).map(Scored::match).toList();
        return new Result(from, query, items);
    }

    private record Scored(Match match, int score) {}

    private static int order(Kind kind) {
        return switch (kind) {
            case VALUE, DEVICE, FIELD -> 0;
            case VARIABLE, FUNCTION, CONSTANT -> 1;
            case MODULE -> 2;
            case SNIPPET -> 3;
            case KEYWORD -> 4;
        };
    }

    /** Prefix beats case-insensitive prefix beats letters in order (like VS Code); 0 means no match. */
    static int score(String label, String query, boolean[] matched) {
        if (query.isEmpty())
            return 1;
        if (label.startsWith(query)) {
            for (int i = 0; i < query.length(); i++) matched[i] = true;
            return 4;
        }
        if (label.toLowerCase().startsWith(query.toLowerCase())) {
            for (int i = 0; i < query.length(); i++) matched[i] = true;
            return 3;
        }
        int at = 0;
        String lower = label.toLowerCase(), q = query.toLowerCase();
        if (lower.charAt(0) != q.charAt(0))
            return 0;
        for (int i = 0; i < label.length() && at < q.length(); i++) {
            if (lower.charAt(i) == q.charAt(at)) {
                matched[i] = true;
                at++;
            }
        }
        return at == q.length() ? 2 : 0;
    }

    private Completions() {}
}
