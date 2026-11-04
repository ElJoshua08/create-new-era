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
    /** {@code local press = bus.get("press")}: the variable holds that device */
    private static final Pattern DEVICE_VAR = Pattern.compile("([A-Za-z_]\\w*)\\s*=\\s*bus\\s*[.:]\\s*get\\s*\\(\\s*[\"'](\\w+)[\"']\\s*\\)");
    private static final Pattern MEMBER = Pattern.compile("([A-Za-z_]\\w*)\\s*([.:])\\s*(\\w*)$");
    private static final Pattern WORD = Pattern.compile("([A-Za-z_]\\w*)$");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_]\\w*");
    /** {@code local terminal = require("terminal")}: the variable holds what that file returns */
    private static final Pattern MODULE_VAR = Pattern.compile("([A-Za-z_]\\w*)\\s*=\\s*require\\s*\\(?\\s*[\"']([\\w./\\-]+)[\"']");
    private static final Pattern RETURNED = Pattern.compile("(?m)^return\\s+([A-Za-z_]\\w*)\\s*$");

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
     * @param source       a file's text by name (what's in its tab if it's open), to read the modules it requires
     * @param devices      the computer's network, nearest first, for device names and their methods
     * @param viewer       the player, to say which way each device lies from them
     * @param explicit     Ctrl+Space: suggest even with nothing typed
     */
    public static Result complete(String beforeCursor, String document, List<String> files, Function<String, String> source,
                                  List<NetworkDevice> devices, Viewer viewer, boolean explicit) {
        int lineStart = beforeCursor.lastIndexOf('\n') + 1;
        String line = beforeCursor.substring(lineStart);
        Result argument = argument(line, document, files, devices, viewer);
        if (argument != null)
            return argument;
        Matcher m;

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
                members = moduleMembers(document, base, files, source);
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
        List<Entry> pool = new ArrayList<>(namesInDocument(document, query, devices));
        Matcher r = MODULE_VAR.matcher(document);
        while (r.find()) {
            String file = file(r.group(2), files);
            pool.add(0, new Entry(r.group(1), Kind.MODULE, r.group(1), "require(\"" + r.group(2) + "\")", file == null
                    ? translate.apply("create_new_era.editor.module_missing")
                    : String.format(translate.apply("create_new_era.editor.module"), file)));
        }
        pool.addAll(LuaApi.GLOBALS);
        return rank(line.length() - query.length(), query, pool);
    }

    /** Plain values: no detail pane, it would only cover the signature with "sound" or "side". */
    private static List<Entry> values(List<String> names) {
        return names.stream().map(n -> new Entry(n, Kind.VALUE, n, "", "")).toList();
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
        return devices.stream().map(d -> deviceEntry(d, viewer)).toList();
    }

    /** A device as a suggestion: its kind of block, what it reads and does, where it is. */
    private static Entry deviceEntry(NetworkDevice d, Viewer viewer) {
        {
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
        }
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

    /** A call still open at the cursor: {@code base.method(args so far} or {@code function(args so far} */
    private static final Pattern OPEN_CALL = Pattern.compile("(?:([A-Za-z_]\\w*)\\s*[.:]\\s*)?([A-Za-z_]\\w*)\\s*\\(([^()]*)$");

    /**
     * Inside a call's parentheses, what the argument under the cursor can be: a lamp's colours,
     * a sound, a device, a side, a file... With the quote typed or not: without it, picking a value
     * writes the quotes too. Null when the cursor isn't on an argument the editor knows values for.
     */
    private static Result argument(String line, String document, List<String> files, List<NetworkDevice> devices, Viewer viewer) {
        Matcher m = OPEN_CALL.matcher(line);
        if (!m.find())
            return null;
        String base = m.group(1), function = m.group(2), args = m.group(3);
        // which argument, and where it starts: commas inside strings don't count
        int index = 0, start = 0;
        char quote = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (quote != 0) {
                if (c == '\\')
                    i++;
                else if (c == quote)
                    quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == ',') {
                index++;
                start = i + 1;
            } else if (c == '{') {
                return null;
            }
        }
        String current = args.substring(start).stripLeading();
        boolean quoted = !current.isEmpty() && (current.charAt(0) == '"' || current.charAt(0) == '\'');
        String query = quoted ? current.substring(1) : current;
        // an argument already finished ("red" then a space), or an expression rather than a value
        if (quoted ? query.indexOf(current.charAt(0)) >= 0 : !query.matches("\\w*"))
            return null;
        if (quoted && !query.matches("[\\w:./\\- ]*"))
            return null;
        List<Entry> values = choicesFor(base, function, index, document, files, devices, viewer);
        if (values == null)
            return null;
        if (!quoted) {
            // unquoted: a variable fits too, and picking a string value writes its quotes
            List<Entry> pool = new ArrayList<>();
            for (Entry e : values)
                pool.add(e.kind() == Kind.CONSTANT ? e : new Entry(e.label(), e.kind(), "\"" + e.insert() + "\"", e.detail(), e.doc(),
                        e.rowType(), e.rowHint(), e.footer()));
            if (!query.isEmpty())
                pool.addAll(namesInDocument(document, query, devices));
            return rank(line.length() - query.length(), query, pool);
        }
        values = values.stream().filter(e -> e.kind() != Kind.CONSTANT).toList();
        return values.isEmpty() ? null : rank(line.length() - query.length(), query, values);
    }

    /** What argument {@code index} of base.function can be, or null if the editor doesn't know. */
    private static List<Entry> choicesFor(String base, String function, int index, String document, List<String> files,
                                          List<NetworkDevice> devices, Viewer viewer) {
        if (base == null)
            return function.equals("require") && index == 0 ? values(modules(files)) : null;
        if ((base.equals("redstone") || base.equals("rs")) && index == 0)
            return LuaApi.MEMBERS.get(base).stream().anyMatch(e -> e.label().equals(function) && e.detail().contains("side"))
                    ? values(LuaApi.SIDES) : null;
        if (base.equals("bus") && (function.equals("get") || function.equals("info")) && index == 0)
            return deviceNames(devices, viewer);
        NetworkDevice d = deviceIn(document, base, devices);
        if (d == null)
            return null;
        for (NetworkDevice.Fn fn : d.functions())
            if (fn.name().equals(function))
                return expand(argChoices(fn.choices(), index), fn, devices, viewer);
        return null;
    }

    /** The part of a function's flat choices list for one argument (see DeviceDrivers.Api.choices). */
    private static List<String> argChoices(List<String> all, int index) {
        List<String> out = new ArrayList<>();
        int arg = 0;
        for (String c : all) {
            if (c.startsWith(com.createnewera.bus.DeviceDrivers.ARG))
                arg = Integer.parseInt(c.substring(com.createnewera.bus.DeviceDrivers.ARG.length()));
            else if (arg == index)
                out.add(c);
        }
        return out;
    }

    /** Choices to suggestions, the editor filling in the lists only it has: sounds, items, devices... */
    private static List<Entry> expand(List<String> choices, NetworkDevice.Fn fn, List<NetworkDevice> devices, Viewer viewer) {
        if (choices.isEmpty())
            return null;
        List<Entry> out = new ArrayList<>();
        for (String c : choices) {
            switch (c) {
                case com.createnewera.bus.DeviceDrivers.DEVICES -> out.addAll(deviceNames(devices, viewer));
                case com.createnewera.block.control.PanelSpeakerBlock.SOUNDS -> out.addAll(values(soundIds()));
                case com.createnewera.bus.DeviceDrivers.ITEMS -> out.addAll(named(net.minecraft.core.registries.BuiltInRegistries.ITEM));
                case com.createnewera.bus.DeviceDrivers.FLUIDS -> out.addAll(named(net.minecraft.core.registries.BuiltInRegistries.FLUID));
                case com.createnewera.bus.DeviceDrivers.PLAYERS -> out.addAll(values(players.get()));
                case com.createnewera.bus.DeviceDrivers.BOOLEAN -> {
                    out.add(new Entry("true", Kind.CONSTANT, "true", "boolean", ""));
                    out.add(new Entry("false", Kind.CONSTANT, "false", "boolean", ""));
                }
                default -> out.add(new Entry(c, Kind.VALUE, c, "", ""));
            }
        }
        return out;
    }

    /** The players online, for tell(" ; the editor plugs in the client's list. */
    public static java.util.function.Supplier<List<String>> players = List::of;

    private static final Map<Object, List<Entry>> NAMED = new java.util.HashMap<>();

    /** Every id in an item or fluid registry, each with the name players know it by beside it. */
    private static List<Entry> named(net.minecraft.core.Registry<?> registry) {
        return NAMED.computeIfAbsent(registry, r -> registry(registry).stream().map(id -> {
            Object thing = registry.get(net.minecraft.resources.ResourceLocation.parse(id));
            String name = thing instanceof net.minecraft.world.item.Item item ? item.getDescription().getString()
                    : thing instanceof net.minecraft.world.level.material.Fluid fluid
                            ? fluid.getFluidType().getDescription().getString() : "";
            return new Entry(id, Kind.VALUE, id, "", "", name, "", "");
        }).toList());
    }

    private static final Map<Object, List<String>> REGISTRY_IDS = new java.util.HashMap<>();

    private static List<String> registry(net.minecraft.core.Registry<?> registry) {
        return REGISTRY_IDS.computeIfAbsent(registry, r -> registry.keySet().stream()
                .filter(id -> !id.getPath().equals("air") && !id.getPath().equals("empty"))
                .map(Object::toString).sorted().toList());
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
        out.addAll(sugar(d));
        return out;
    }

    /** The prelude's on* helpers this device gets; mirrors the conditions in prelude.lua. */
    private static List<Entry> sugar(NetworkDevice d) {
        List<Entry> out = new ArrayList<>();
        boolean typewriter = d.methods().contains("getKeyEvents");
        if (d.methods().contains("isPressed") && !typewriter)
            out.add(new Entry("onPress", Kind.FUNCTION, "onPress(function()\n  $0\nend)", "onPress(fn(device))",
                    translate.apply("create_new_era.api.on_press")));
        if (typewriter) {
            out.add(new Entry("onKeyDown", Kind.FUNCTION, "onKeyDown(function(key)\n  $0\nend)", "onKeyDown(fn(key, device))",
                    translate.apply("create_new_era.api.on_key_down")));
            out.add(new Entry("onKeyUp", Kind.FUNCTION, "onKeyUp(function(key)\n  $0\nend)", "onKeyUp(fn(key, device))",
                    translate.apply("create_new_era.api.on_key_up")));
            out.add(new Entry("onKey", Kind.FUNCTION, "onKey(function(key, down)\n  $0\nend)", "onKey(fn(key, down, device))",
                    translate.apply("create_new_era.api.on_key_any")));
        } else if (d.methods().contains("getKey"))
            out.add(new Entry("onKey", Kind.FUNCTION, "onKey(function(key)\n  $0\nend)", "onKey(fn(key, device))",
                    translate.apply("create_new_era.api.on_key")));
        if (d.methods().contains("getLastTouch"))
            out.add(new Entry("onTouch", Kind.FUNCTION, "onTouch(function(x, y, player)\n  $0\nend)", "onTouch(fn(x, y, player, device))",
                    translate.apply("create_new_era.api.on_touch")));
        if (d.methods().contains("getLast"))
            out.add(new Entry("onMessage", Kind.FUNCTION, "onMessage(function(message)\n  $0\nend)", "onMessage(fn(message, device))",
                    translate.apply("create_new_era.api.on_message")));
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
    public static Entry signature(String beforeCursor, String document, List<String> files, Function<String, String> source,
                                  List<NetworkDevice> devices) {
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
                for (Entry e : sugar(d))
                    if (e.label().equals(method))
                        return e;
            }
            List<Entry> members = LuaApi.MEMBERS.get(base);
            if (members == null)
                members = moduleMembers(document, base, files, source);
            for (Entry e : members == null ? List.<Entry>of() : members)
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


    /** The file require(module) loads, resolved the way the computer does: as named, or dots to slashes plus .lua. */
    private static String file(String module, List<String> files) {
        if (files.contains(module))
            return module;
        String withExtension = module.replace('.', '/') + ".lua";
        return files.contains(withExtension) ? withExtension : null;
    }

    /**
     * What a variable holding {@code require("x")} offers: the functions and fields x.lua puts on
     * the table it returns, each with the comment written above it as its doc. Null if the
     * variable isn't a required module.
     */
    private static List<Entry> moduleMembers(String document, String variable, List<String> files, Function<String, String> source) {
        Matcher m = MODULE_VAR.matcher(document);
        String module = null;
        while (m.find())
            if (m.group(1).equals(variable))
                module = m.group(2);
        if (module == null)
            return null;
        String name = file(module, files);
        String text = name == null ? null : source.apply(name);
        if (text == null)
            return List.of();
        Matcher r = RETURNED.matcher(text);
        String table = null;
        while (r.find())
            table = r.group(1);
        if (table == null)
            return List.of();
        String t = Pattern.quote(table);
        Pattern def = Pattern.compile("(?m)^[ \\t]*(?:function\\s+" + t + "\\s*[.:]\\s*([A-Za-z_]\\w*)\\s*\\(([^)]*)\\)"
                + "|" + t + "\\s*\\.\\s*([A-Za-z_]\\w*)\\s*=\\s*(function\\s*\\(([^)]*)\\))?)");
        List<Entry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher d = def.matcher(text);
        while (d.find()) {
            boolean fn = d.group(1) != null || d.group(4) != null;
            String field = d.group(1) != null ? d.group(1) : d.group(3);
            if (!seen.add(field))
                continue;
            String doc = commentAbove(text, d.start());
            if (fn) {
                String params = (d.group(1) != null ? d.group(2) : d.group(5)).trim().replaceAll("\\s*,\\s*", ", ");
                out.add(new Entry(field, Kind.FUNCTION, field + (params.isEmpty() ? "()" : "($0)"),
                        variable + "." + field + "(" + params + ")", doc, "", name, ""));
            } else {
                out.add(new Entry(field, Kind.FIELD, field, variable + "." + field, doc, "", name, ""));
            }
        }
        return out;
    }

    /** The -- comment lines right above a definition, joined, as its doc. */
    private static String commentAbove(String text, int at) {
        List<String> lines = new ArrayList<>();
        int end = text.lastIndexOf('\n', Math.max(0, at - 1));
        while (end > 0) {
            int start = text.lastIndexOf('\n', end - 1) + 1;
            String line = text.substring(start, end).trim();
            if (!line.startsWith("--") || line.startsWith("--[["))
                break;
            lines.add(0, line.replaceFirst("^-+\\s?", ""));
            end = start - 1;
        }
        return String.join(" ", lines).trim();
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

    private static final Pattern FUNCTION_SIGNATURE = Pattern.compile("(?m)^[ \\t]*(?:local\\s+)?function\\s+([A-Za-z_]\\w*)\\s*\\(([^)]*)\\)"
            + "|(?m)^[ \\t]*(?:local\\s+)?([A-Za-z_]\\w*)\\s*=\\s*function\\s*\\(([^)]*)\\)");

    /**
     * Variables and functions the file defines or uses, except the word being typed. Each says
     * what it is: a function its parameters and the comment above it, a device what it is and
     * does, any other name the line that gives it its value.
     */
    private static List<Entry> namesInDocument(String document, String typing, List<NetworkDevice> devices) {
        Map<String, Entry> found = new LinkedHashMap<>();
        Matcher f = FUNCTION_SIGNATURE.matcher(document);
        while (f.find()) {
            String name = f.group(1) != null ? f.group(1) : f.group(3);
            String params = (f.group(1) != null ? f.group(2) : f.group(4)).trim().replaceAll("\\s*,\\s*", ", ");
            found.putIfAbsent(name, new Entry(name, Kind.FUNCTION, name + (params.isEmpty() ? "()" : "($0)"),
                    "function " + name + "(" + params + ")", commentAbove(document, f.start())));
        }
        Set<String> skip = new HashSet<>();
        LuaApi.GLOBALS.forEach(e -> skip.add(e.label()));
        Matcher r = MODULE_VAR.matcher(document);
        while (r.find())
            skip.add(r.group(1)); // offered as modules
        Map<String, Integer> counts = new LinkedHashMap<>();
        Matcher m = IDENT.matcher(stripStringsAndComments(document));
        while (m.find())
            counts.merge(m.group(), 1, Integer::sum);
        counts.forEach((name, count) -> {
            if (skip.contains(name) || found.containsKey(name) || (name.equals(typing) && count <= 1))
                return;
            NetworkDevice d = deviceIn(document, name, devices);
            if (d != null) {
                Entry e = deviceEntry(d, null);
                found.put(name, new Entry(name, Kind.VARIABLE, name, e.detail(), e.doc(), e.rowType(), "", e.footer()));
            } else {
                found.put(name, new Entry(name, Kind.VARIABLE, name, definition(document, name), ""));
            }
        });
        return new ArrayList<>(found.values());
    }

    /** The line that first gives a name its value (or else first uses it), trimmed: what it holds. */
    private static String definition(String document, String name) {
        String n = Pattern.quote(name);
        Pattern assigned = Pattern.compile("(?m)^[ \\t]*(.*(?:\\blocal\\b[^=\\n]*\\b" + n + "\\b|(?<![.:\\w])" + n
                + "\\s*=(?!=)|\\bfor\\b[^\\n]*\\b" + n + "\\b|\\bfunction\\b[^\\n]*\\([^)]*\\b" + n + "\\b).*)$");
        Matcher m = assigned.matcher(document);
        String line = m.find() ? m.group(1) : null;
        if (line == null) {
            Matcher used = Pattern.compile("(?m)^[ \\t]*(.*(?<![.:\\w])" + n + "\\b.*)$").matcher(document);
            line = used.find() ? used.group(1) : name;
        }
        line = line.strip();
        return line.length() > 60 ? line.substring(0, 59) + "…" : line;
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
        // anywhere in it: "bell" finds minecraft:block.bell.use, "kn" panel_knob_2
        int inside = lower.indexOf(q);
        if (inside > 0) {
            for (int i = 0; i < q.length(); i++) matched[inside + i] = true;
            return 2;
        }
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
