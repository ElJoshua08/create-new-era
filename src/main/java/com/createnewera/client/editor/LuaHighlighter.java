package com.createnewera.client.editor;

import java.util.Set;

/**
 * Colours Lua one line at a time. Long comments and long strings can span lines, so each
 * line starts in the state the previous one ended in.
 */
public final class LuaHighlighter {
    // palette from the editor design (design canvas, Terminal artboard)
    public static final int TEXT = 0xFFE8E8E8;
    public static final int KEYWORD = 0xFFF0C060;
    public static final int STRING = 0xFFA6E07A;
    public static final int NUMBER = 0xFFEF9A6A;
    public static final int COMMENT = 0xFF8F8F8F;
    public static final int BUILTIN = 0xFF7FB2E8;

    private static final Set<String> KEYWORDS = Set.of(
            "and", "break", "do", "else", "elseif", "end", "for", "function", "goto", "if", "in",
            "local", "not", "or", "repeat", "return", "then", "until", "while");
    private static final Set<String> CONSTANTS = Set.of("true", "false", "nil");
    private static final Set<String> BUILTINS = Set.of(
            "print", "sleep", "redstone", "rs", "os", "string", "table", "math", "coroutine", "bit32", "utf8",
            "pairs", "ipairs", "next", "select", "type", "tostring", "tonumber", "error", "assert",
            "pcall", "xpcall", "setmetatable", "getmetatable", "rawget", "rawset", "rawequal", "rawlen", "load");

    /** Where a line begins: in normal code, or inside a long bracket of the given level. */
    public record State(boolean inComment, boolean inString, int level) {
        public static final State CODE = new State(false, false, 0);
    }

    /** Colours each char of {@code line} into {@code colors}; returns the state for the next line. */
    public static State highlight(String line, int[] colors, State start) {
        int n = line.length();
        int i = 0;
        if (start.inComment || start.inString) {
            int close = findLongClose(line, 0, start.level);
            int until = close < 0 ? n : close;
            fill(colors, 0, until, start.inComment ? COMMENT : STRING);
            if (close < 0)
                return start;
            i = close;
        }
        while (i < n) {
            char c = line.charAt(i);
            if (c == '-' && i + 1 < n && line.charAt(i + 1) == '-') {
                int level = longOpenLevel(line, i + 2);
                if (level >= 0) {
                    int close = findLongClose(line, i + 2 + level + 2, level);
                    fill(colors, i, close < 0 ? n : close, COMMENT);
                    if (close < 0)
                        return new State(true, false, level);
                    i = close;
                } else {
                    fill(colors, i, n, COMMENT);
                    return State.CODE;
                }
            } else if (c == '[' && longOpenLevel(line, i) >= 0) {
                int level = longOpenLevel(line, i);
                int close = findLongClose(line, i + level + 2, level);
                fill(colors, i, close < 0 ? n : close, STRING);
                if (close < 0)
                    return new State(false, true, level);
                i = close;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && line.charAt(j) != c)
                    j += line.charAt(j) == '\\' ? 2 : 1;
                j = Math.min(n, j + 1);
                fill(colors, i, j, STRING);
                i = j;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(line.charAt(i + 1)))) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '.'
                        || ((line.charAt(j) == '-' || line.charAt(j) == '+') && "eEpP".indexOf(line.charAt(j - 1)) >= 0)))
                    j++;
                fill(colors, i, j, NUMBER);
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_'))
                    j++;
                String word = line.substring(i, j);
                // a field like foo.print isn't the builtin
                boolean field = i > 0 && (line.charAt(i - 1) == '.' || line.charAt(i - 1) == ':');
                int color = KEYWORDS.contains(word) ? KEYWORD
                        : CONSTANTS.contains(word) ? NUMBER
                        : !field && BUILTINS.contains(word) ? BUILTIN
                        : TEXT;
                fill(colors, i, j, color);
                i = j;
            } else {
                colors[i++] = TEXT;
            }
        }
        return State.CODE;
    }

    /** Level of a long bracket opening at {@code i} ([[ is 0, [=[ is 1), or -1. */
    private static int longOpenLevel(String line, int i) {
        if (i >= line.length() || line.charAt(i) != '[')
            return -1;
        int j = i + 1, level = 0;
        while (j < line.length() && line.charAt(j) == '=') {
            level++;
            j++;
        }
        return j < line.length() && line.charAt(j) == '[' ? level : -1;
    }

    /** Index just past the closing ]=*] of the given level, or -1 if it's not on this line. */
    private static int findLongClose(String line, int from, int level) {
        String close = "]" + "=".repeat(level) + "]";
        int at = line.indexOf(close, Math.min(from, line.length()));
        return at < 0 ? -1 : at + close.length();
    }

    private static void fill(int[] colors, int from, int to, int color) {
        for (int k = from; k < to; k++)
            colors[k] = color;
    }

    private LuaHighlighter() {}
}
