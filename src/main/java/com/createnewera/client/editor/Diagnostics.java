package com.createnewera.client.editor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;

import com.createnewera.block.screen.ScreenMarkup;
import com.createnewera.network.NetworkDevice;

/**
 * What's wrong with a file while it's being written, for the editor to underline and say at the
 * end of the line: Lua that won't compile (the computer's own compiler), devices asked for
 * something they don't have (Completions.diagnose), markup a screen won't take.
 */
public final class Diagnostics {
    /**
     * @param line    from 1
     * @param from    the first column underlined, from 0
     * @param to      just past the last
     * @param error   an error, or else a warning
     */
    public record Diagnostic(int line, int from, int to, String message, boolean error) {}

    private static final Pattern AT_LINE = Pattern.compile("^[^:]*:(\\d+): (.*)$", Pattern.DOTALL);
    private static final Pattern NEAR = Pattern.compile("near '([^']*)'");

    public static List<Diagnostic> of(String file, String text, List<String> files, java.util.function.Function<String, String> source,
                                      List<NetworkDevice> devices) {
        List<Diagnostic> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        if (file.endsWith(com.createnewera.block.screen.ScreenDriver.EXTENSION)) {
            try {
                ScreenMarkup.parse(text);
            } catch (ScreenMarkup.MarkupException e) {
                int line = Math.min(e.lineIn(text), lines.length);
                out.add(underline(lines, line, e.token, e.getMessage().replaceFirst("^markup line \\d+: ", ""), true));
            }
            return out;
        }
        String syntax = syntax(file, text);
        if (syntax != null) {
            Matcher m = AT_LINE.matcher(syntax);
            int line = m.find() ? Math.min(Integer.parseInt(m.group(1)), lines.length) : 1;
            String message = m.matches() ? m.group(2) : syntax;
            Matcher near = NEAR.matcher(message);
            out.add(underline(lines, line, near.find() ? near.group(1) : "", message, true));
        }
        out.addAll(Completions.diagnose(text, files, source, devices));
        return out;
    }

    /** The compiler's complaint, "file:line: message", or null if it compiles. */
    private static String syntax(String file, String text) {
        try {
            LoadState.load(LuaState.builder().build(), new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "=" + file, new LuaTable());
            return null;
        } catch (CompileException | LuaError e) {
            return e.getMessage();
        } catch (RuntimeException e) {
            return null; // the compiler tripped on something it isn't for us to report
        }
    }

    /** A diagnostic on a line, underlining {@code token} if it's there, else the line's text. */
    static Diagnostic underline(String[] lines, int line, String token, String message, boolean error) {
        String text = lines[Math.max(0, line - 1)];
        int at = token.isEmpty() || token.equals("<eof>") ? -1 : text.indexOf(token);
        if (at >= 0)
            return new Diagnostic(line, at, at + token.length(), message, error);
        int start = text.length() - text.stripLeading().length();
        return new Diagnostic(line, start, Math.max(start + 1, text.stripTrailing().length()), message, error);
    }

    private Diagnostics() {}
}
