package com.createnewera.gametest;

import java.util.List;
import java.util.Map;

import com.createnewera.CreateNewEra;
import com.createnewera.client.editor.Completions;
import com.createnewera.client.editor.LuaApi.Entry;
import com.createnewera.client.editor.LuaHighlighter;

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
