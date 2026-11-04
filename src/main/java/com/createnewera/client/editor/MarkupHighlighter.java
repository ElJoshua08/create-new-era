package com.createnewera.client.editor;

/**
 * Colours a screen's markup (.screen files) one line at a time, as LuaHighlighter does Lua:
 * tag names, attributes, their values, comments, and the text in between. A comment or a tag
 * can run over several lines, so each line starts in the state the previous one ended in.
 */
public final class MarkupHighlighter {
    public static final int TAG = LuaHighlighter.BUILTIN, ATTRIBUTE = 0xFF9CDCFE, VALUE = LuaHighlighter.STRING,
            PUNCTUATION = 0xFF8F8F8F, COMMENT = LuaHighlighter.COMMENT, TEXT = LuaHighlighter.TEXT;

    /** Where a line begins: in text, inside a tag (its attributes), or inside a comment. */
    public enum State {
        TEXT, TAG, COMMENT
    }

    public static State highlight(String line, int[] colors, State start) {
        int n = line.length(), i = 0;
        State state = start;
        while (i < n) {
            switch (state) {
                case COMMENT -> {
                    int end = line.indexOf("-->", i);
                    int to = end < 0 ? n : end + 3;
                    fill(colors, i, to, COMMENT);
                    i = to;
                    if (end >= 0)
                        state = State.TEXT;
                }
                case TEXT -> {
                    if (line.startsWith("<!--", i)) {
                        state = State.COMMENT;
                        continue;
                    }
                    if (line.charAt(i) == '<') {
                        int j = i + 1;
                        if (j < n && line.charAt(j) == '/')
                            j++;
                        fill(colors, i, j, PUNCTUATION);
                        int k = j;
                        while (k < n && (Character.isLetterOrDigit(line.charAt(k)) || line.charAt(k) == '_' || line.charAt(k) == '-'))
                            k++;
                        fill(colors, j, k, TAG);
                        i = k;
                        state = State.TAG;
                    } else {
                        colors[i++] = TEXT;
                    }
                }
                case TAG -> {
                    char c = line.charAt(i);
                    if (c == '>' || (c == '/' && i + 1 < n && line.charAt(i + 1) == '>')) {
                        int to = c == '>' ? i + 1 : i + 2;
                        fill(colors, i, to, PUNCTUATION);
                        i = to;
                        state = State.TEXT;
                    } else if (c == '"' || c == '\'') {
                        int j = i + 1;
                        while (j < n && line.charAt(j) != c)
                            j++;
                        j = Math.min(n, j + 1);
                        fill(colors, i, j, VALUE);
                        i = j;
                    } else if (Character.isLetter(c)) {
                        int j = i;
                        while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_' || line.charAt(j) == '-'))
                            j++;
                        fill(colors, i, j, ATTRIBUTE);
                        i = j;
                    } else {
                        colors[i++] = PUNCTUATION;
                    }
                }
            }
        }
        return state;
    }

    private static void fill(int[] colors, int from, int to, int color) {
        for (int k = from; k < to && k < colors.length; k++)
            colors[k] = color;
    }

    private MarkupHighlighter() {}
}
