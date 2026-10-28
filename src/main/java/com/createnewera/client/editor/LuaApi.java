package com.createnewera.client.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the editor can suggest that doesn't come from the file itself: the globals and
 * libraries a program gets (see prelude.lua and LuaComputer), keywords and snippets.
 */
public final class LuaApi {
    public enum Kind {
        KEYWORD(0xFFF0C060, "k"), FUNCTION(0xFFC586E8, "f"), MODULE(0xFF7FB2E8, "m"), FIELD(0xFF6FC3C3, "p"),
        VARIABLE(0xFF9CDCFE, "v"), CONSTANT(0xFFEF9A6A, "c"), SNIPPET(0xFFA6E07A, "s"), VALUE(0xFFA6E07A, "\"");

        public final int color;
        public final String badge;

        Kind(int color, String badge) {
            this.color = color;
            this.badge = badge;
        }
    }

    /**
     * @param insert text put in the editor; "$0" marks where the cursor lands, newlines keep the indent
     * @param detail signature or short type, shown next to the name
     * @param doc    one or two sentences, shown beside the list
     */
    public record Entry(String label, Kind kind, String insert, String detail, String doc) {}

    public static final List<Entry> GLOBALS = new ArrayList<>();
    public static final Map<String, List<Entry>> MEMBERS = new LinkedHashMap<>();
    public static final List<String> SIDES = List.of("front", "back", "left", "right", "top", "bottom");

    private static String current;

    static {
        fn("print", "print(...)", "Writes its arguments to the console, separated by tabs.");
        fn("sleep", "sleep(seconds)", "Waits in game time. sleep(0) waits one tick.");
        fn("require", "require(module)", "Runs another file on this computer once and returns what it returned. require(\"lib\") finds lib or lib.lua.");
        module("redstone", "Read and send redstone on each side of the computer.");
        module("rs", "Short for redstone.");
        module("os", "Time and waiting.");
        module("string", "Text functions. Also callable as methods: s:upper().");
        module("table", "List helpers: insert, remove, sort, concat.");
        module("math", "Numbers: floor, max, random, pi...");
        module("coroutine", "Run functions side by side and switch between them.");
        module("utf8", "Text as Unicode code points.");
        module("bit32", "Bitwise operations on 32-bit numbers.");
        for (String[] f : new String[][] {
                {"pairs", "pairs(t)", "Iterates every key and value of a table: for k, v in pairs(t) do."},
                {"ipairs", "ipairs(t)", "Iterates t[1], t[2]... in order: for i, v in ipairs(t) do."},
                {"next", "next(t, key)", "The key after key in table t."},
                {"select", "select(n, ...)", "The arguments from the nth on, or their count with select(\"#\", ...)."},
                {"type", "type(value)", "\"nil\", \"number\", \"string\", \"boolean\", \"table\" or \"function\"."},
                {"tostring", "tostring(value)", "The value as text."},
                {"tonumber", "tonumber(text, base)", "Text as a number, or nil if it isn't one."},
                {"error", "error(message, level)", "Stops with an error. Level 2 blames the caller."},
                {"assert", "assert(value, message)", "Errors with message if value is false or nil."},
                {"pcall", "pcall(f, ...)", "Calls f and catches errors: returns true and the results, or false and the error."},
                {"xpcall", "xpcall(f, handler, ...)", "Like pcall, passing errors through handler."},
                {"setmetatable", "setmetatable(t, mt)", "Gives table t the metatable mt."},
                {"getmetatable", "getmetatable(t)", "The metatable of t."},
                {"rawget", "rawget(t, k)", "t[k] without metamethods."},
                {"rawset", "rawset(t, k, v)", "t[k] = v without metamethods."},
                {"rawequal", "rawequal(a, b)", "a == b without metamethods."},
                {"rawlen", "rawlen(t)", "#t without metamethods."},
                {"load", "load(source, name)", "Compiles text into a function, or returns nil and the error."}})
            fn(f[0], f[1], f[2]);

        String side = "side is front, back, left, right, top or bottom.";
        for (String lib : new String[] {"redstone", "rs"}) {
            current = lib;
            member("getInput", "getInput(side) → boolean", "Whether any signal comes in on that side. " + side);
            member("getAnalogInput", "getAnalogInput(side) → 0-15", "Strength of the signal coming in. " + side);
            member("setOutput", "setOutput(side, on)", "Sends a full signal out of that side, or turns it off. " + side);
            member("setAnalogOutput", "setAnalogOutput(side, strength)", "Sends a signal of 0 to 15 out of that side.");
            member("getOutput", "getOutput(side) → boolean", "Whether this computer is sending anything out of that side.");
            member("getAnalogOutput", "getAnalogOutput(side) → 0-15", "Strength this computer sends out of that side.");
            member("getSides", "getSides() → table", "The six side names.");
        }
        current = "os";
        member("clock", "clock() → seconds", "Seconds of game time since the program started.");
        member("sleep", "sleep(seconds)", "Same as sleep.");
        current = "string";
        for (String[] f : new String[][] {
                {"byte", "byte(s, i, j)", "Character codes of s from i to j."}, {"char", "char(...)", "Text from character codes."},
                {"find", "find(s, pattern, init, plain)", "Where pattern first appears in s: start and end."},
                {"format", "format(fmt, ...)", "Formats values: string.format(\"%d RPM\", 64)."},
                {"gmatch", "gmatch(s, pattern)", "Iterates every match of pattern in s."},
                {"gsub", "gsub(s, pattern, repl, n)", "Replaces matches of pattern; returns the text and the count."},
                {"len", "len(s)", "Length of s, same as #s."}, {"lower", "lower(s)", "s in lower case."},
                {"match", "match(s, pattern, init)", "The first match of pattern in s, or nil."},
                {"rep", "rep(s, n, sep)", "s repeated n times."}, {"reverse", "reverse(s)", "s backwards."},
                {"sub", "sub(s, i, j)", "The part of s from i to j. Negative counts from the end."},
                {"upper", "upper(s)", "s in upper case."}})
            member(f[0], f[1], f[2]);
        current = "table";
        for (String[] f : new String[][] {
                {"concat", "concat(t, sep, i, j)", "Joins the items of a list into text."},
                {"insert", "insert(t, pos, value)", "Adds value to a list, at the end or at pos."},
                {"pack", "pack(...)", "The arguments as a list, with n set to their count."},
                {"remove", "remove(t, pos)", "Removes and returns the item at pos, or the last one."},
                {"sort", "sort(t, less)", "Sorts a list in place, optionally with your own comparison."},
                {"unpack", "unpack(t, i, j)", "The items of a list as separate values."}})
            member(f[0], f[1], f[2]);
        current = "math";
        for (String[] f : new String[][] {
                {"abs", "abs(x)", "Absolute value."}, {"ceil", "ceil(x)", "Rounds up."}, {"floor", "floor(x)", "Rounds down."},
                {"max", "max(x, ...)", "The largest argument."}, {"min", "min(x, ...)", "The smallest argument."},
                {"sqrt", "sqrt(x)", "Square root."}, {"random", "random(m, n)", "A random number: [0,1), 1 to m, or m to n."},
                {"randomseed", "randomseed(x)", "Seeds random."}, {"fmod", "fmod(x, y)", "Remainder of x / y."},
                {"sin", "sin(x)", "Sine, x in radians."}, {"cos", "cos(x)", "Cosine, x in radians."}, {"tan", "tan(x)", "Tangent."},
                {"asin", "asin(x)", "Arc sine."}, {"acos", "acos(x)", "Arc cosine."}, {"atan", "atan(y, x)", "Arc tangent."},
                {"deg", "deg(x)", "Radians to degrees."}, {"rad", "rad(x)", "Degrees to radians."},
                {"exp", "exp(x)", "e to the x."}, {"log", "log(x, base)", "Logarithm."}})
            member(f[0], f[1], f[2]);
        constant("pi", "math.pi", "3.14159...");
        constant("huge", "math.huge", "Infinity.");
        current = "coroutine";
        for (String[] f : new String[][] {
                {"create", "create(f)", "A new coroutine running f."}, {"resume", "resume(co, ...)", "Runs co until it yields or ends."},
                {"yield", "yield(...)", "Pauses the current coroutine."}, {"status", "status(co)", "\"running\", \"suspended\", \"normal\" or \"dead\"."},
                {"wrap", "wrap(f)", "A function that resumes a coroutine running f."}, {"running", "running()", "The running coroutine."}})
            member(f[0], f[1], f[2]);
        current = "utf8";
        for (String[] f : new String[][] {
                {"char", "char(...)", "Text from code points."}, {"codepoint", "codepoint(s, i, j)", "Code points of s."},
                {"len", "len(s)", "Number of characters in s."}, {"codes", "codes(s)", "Iterates positions and code points."},
                {"offset", "offset(s, n, i)", "Byte position of the nth character."}})
            member(f[0], f[1], f[2]);
        current = "bit32";
        for (String[] f : new String[][] {
                {"band", "band(...)", "Bitwise and."}, {"bor", "bor(...)", "Bitwise or."}, {"bxor", "bxor(...)", "Bitwise xor."},
                {"bnot", "bnot(x)", "Bitwise not."}, {"lshift", "lshift(x, n)", "Shift left."}, {"rshift", "rshift(x, n)", "Shift right."},
                {"arshift", "arshift(x, n)", "Arithmetic shift right."}, {"btest", "btest(...)", "Whether band is not zero."},
                {"extract", "extract(x, field, width)", "Reads bits from x."}, {"replace", "replace(x, v, field, width)", "Writes bits into x."}})
            member(f[0], f[1], f[2]);

        for (String kw : new String[] {"and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if",
                "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"})
            GLOBALS.add(new Entry(kw, kw.equals("true") || kw.equals("false") || kw.equals("nil") ? Kind.CONSTANT : Kind.KEYWORD,
                    kw, "", ""));

        snippet("for", "for i = 1, 10 do ... end", "for i = 1, $0 do\n  \nend", "Counts from 1 to the number you type.");
        snippet("forp", "for k, v in pairs(t) do ... end", "for k, v in pairs($0) do\n  \nend", "Every key and value of a table.");
        snippet("fori", "for i, v in ipairs(t) do ... end", "for i, v in ipairs($0) do\n  \nend", "Every item of a list, in order.");
        snippet("while", "while ... do ... end", "while $0 do\n  \nend", "Repeats while the condition holds.");
        snippet("loop", "while true do ... sleep(0) end", "while true do\n  $0\n  sleep(0)\nend", "Runs forever, once a tick.");
        snippet("if", "if ... then ... end", "if $0 then\n  \nend", "Runs code when a condition holds.");
        snippet("ife", "if ... then ... else ... end", "if $0 then\n  \nelse\n  \nend", "One branch or the other.");
        snippet("fn", "function name(...) ... end", "function $0()\n  \nend", "A new function.");
        snippet("lfn", "local function name(...) ... end", "local function $0()\n  \nend", "A function only this file sees.");
    }

    private static void fn(String name, String signature, String doc) {
        GLOBALS.add(new Entry(name, Kind.FUNCTION, name + "($0)", signature, doc));
    }

    private static void module(String name, String doc) {
        GLOBALS.add(new Entry(name, Kind.MODULE, name, "library", doc));
    }

    private static void member(String name, String signature, String doc) {
        MEMBERS.computeIfAbsent(current, k -> new ArrayList<>()).add(new Entry(name, Kind.FUNCTION, name + "($0)", signature, doc));
    }

    private static void constant(String name, String signature, String doc) {
        MEMBERS.computeIfAbsent(current, k -> new ArrayList<>()).add(new Entry(name, Kind.CONSTANT, name, signature, doc));
    }

    private static void snippet(String label, String detail, String insert, String doc) {
        GLOBALS.add(new Entry(label, Kind.SNIPPET, insert, detail, doc));
    }

    private LuaApi() {}
}
