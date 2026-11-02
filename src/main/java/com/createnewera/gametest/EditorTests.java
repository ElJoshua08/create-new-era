package com.createnewera.gametest;

import java.util.List;
import java.util.Map;

import com.createnewera.CreateNewEra;
import com.createnewera.block.control.PanelSpeakerBlock;
import com.createnewera.bus.DeviceDrivers;
import com.createnewera.network.NetworkDevice;
import com.createnewera.client.editor.Completions;
import com.createnewera.client.editor.LuaApi.Entry;
import com.createnewera.client.editor.LuaHighlighter;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The editor's understanding of code: pure text, so these need no blocks. */
@GameTestHolder(CreateNewEra.MOD_ID)
@PrefixGameTestTemplate(false)
public class EditorTests {
    private static final String EMPTY = "empty3";

    private static final Map<String, String> DISK = Map.of(
            "startup.lua", "",
            "terminal.lua", """
                    local T = {}

                    -- Writes key to the console.
                    function T.printToTerminal(key)
                      print(key)
                    end

                    T.prefix = "> "
                    T.clear = function(a,  b) end

                    return T
                    """);

    private static NetworkDevice device(String name, NetworkDevice.Fn... fns) {
        return new NetworkDevice(name, "test", "", BlockPos.ZERO, 2, true, true, List.of(fns));
    }

    private static NetworkDevice.Fn fn(String name, String params, String... choices) {
        return new NetworkDevice.Fn(name, params, "", "", false, List.of(choices));
    }

    /** What the editor offers with the cursor at the end of {@code code}, as the text each would insert. */
    private static List<String> offered(String code, List<NetworkDevice> devices) {
        return Completions.complete(code, code, List.of("startup.lua"), n -> null, devices, null, false)
                .items().stream().map(m -> m.entry().insert()).toList();
    }

    @GameTest(template = EMPTY)
    public static void argumentsOfferTheirValues(GameTestHelper helper) {
        List<NetworkDevice> devices = List.of(
                device("speaker", fn("playSound", "sound: id", PanelSpeakerBlock.SOUNDS)),
                device("lamp", fn("set", "light", "amber", "green", "red", "off")),
                device("chest", fn("pushItems", "to: device, count?: number, item?: id",
                        DeviceDrivers.DEVICES, DeviceDrivers.ARG + 2, DeviceDrivers.ITEMS)),
                device("switch", fn("setOn", "on: boolean", DeviceDrivers.BOOLEAN)));
        String vars = "local speaker = bus.get('speaker')\nlocal lamp = bus.get('lamp')\nlocal chest = bus.get('chest')\n"
                + "local sw = bus.get('switch')\n";
        check(helper, offered(vars + "speaker.playSound(minecr", devices), "\"minecraft:", "no quote typed: values come quoted");
        check(helper, offered(vars + "speaker.playSound(\"bell", devices), "minecraft:block.bell", "part of an id finds it");
        check(helper, offered(vars + "lamp.set(", devices), "\"amber\"", "right after the bracket");
        check(helper, offered(vars + "lamp.set('gr", devices), "green", "inside the quotes");
        check(helper, offered(vars + "chest.pushItems(", devices), "\"lamp\"", "a device to send to");
        check(helper, offered(vars + "chest.pushItems(\"lamp\", 4, \"iron_ing", devices), "minecraft:iron_ingot", "third argument");
        helper.assertTrue(offered(vars + "chest.pushItems(\"lamp\", ", devices).isEmpty(), "a count has no values to offer");
        check(helper, offered(vars + "sw.setOn(", devices), "true", "booleans, bare");
        check(helper, offered("redstone.setOutput(", devices), "\"front\"", "sides");
        check(helper, offered("bus.get(", devices), "\"speaker\"", "devices by name");
        check(helper, offered("bus.get(\"wit", devices), "switch", "a name found by its middle");
        // a variable says what it is: a device what it does, anything else the line that sets it
        var speaker = Completions.complete(vars + "spe", vars + "spe", List.of(), n -> null, devices, null, true).items().stream()
                .map(m -> m.entry()).filter(e -> e.label().equals("speaker")).findFirst().orElse(null);
        helper.assertTrue(speaker != null && speaker.detail().startsWith("speaker"), "speaker said " + (speaker == null ? null : speaker.detail()));
        String code = "local count = 0\n-- Adds one.\nlocal function bump(n) count = count + n end\nco";
        var names = Completions.complete(code, code, List.of(), n -> null, List.of(), null, true).items().stream().map(m -> m.entry()).toList();
        helper.assertTrue(names.stream().anyMatch(e -> e.label().equals("count") && e.detail().equals("local count = 0")), "count: " + names);
        String fnCode = "local count = 0\n-- Adds one.\nlocal function bump(n) count = count + n end\nbu";
        var bump = Completions.complete(fnCode, fnCode, List.of(), n -> null, List.of(), null, true).items().get(0).entry();
        helper.assertTrue(bump.detail().equals("function bump(n)") && bump.doc().equals("Adds one."), "bump: " + bump.detail() + " / " + bump.doc());
        helper.succeed();
    }

    private static void check(GameTestHelper helper, List<String> offered, String expected, String what) {
        helper.assertTrue(offered.stream().anyMatch(o -> o.startsWith(expected)), what + ": expected " + expected + " in " + offered);
    }

    @GameTest(template = EMPTY)
    public static void modulesOfferTheirFunctions(GameTestHelper helper) {
        String doc = "local terminal = require(\"terminal\")\nterminal.";
        var result = Completions.complete(doc, doc, List.of("startup.lua", "terminal.lua"), DISK::get, List.of(), null, false);
        List<String> labels = result.items().stream().map(m -> m.entry().label()).toList();
        helper.assertTrue(labels.containsAll(List.of("printToTerminal", "prefix", "clear")), "members were " + labels);

        String call = "local terminal = require(\"terminal\")\nterminal.printToTerminal(";
        Entry help = Completions.signature(call, call, List.of("startup.lua", "terminal.lua"), DISK::get, List.of());
        helper.assertTrue(help != null && help.detail().equals("terminal.printToTerminal(key)")
                && help.doc().equals("Writes key to the console."), "signature was " + (help == null ? null : help.detail() + " / " + help.doc()));

        Entry clear = Completions.signature("local terminal = require('terminal')\nterminal.clear(",
                "local terminal = require('terminal')\nterminal.clear(", List.of("terminal.lua"), DISK::get, List.of());
        helper.assertTrue(clear != null && clear.detail().equals("terminal.clear(a, b)"), "clear was " + (clear == null ? null : clear.detail()));

        String line = "terminal.go(key)";
        int[] colors = new int[line.length()];
        LuaHighlighter.highlight(line, colors, LuaHighlighter.State.CODE, java.util.Set.of("terminal"));
        helper.assertTrue(colors[0] == LuaHighlighter.BUILTIN && colors[9] == LuaHighlighter.CALL, "module and call colours");
        helper.succeed();
    }
}
