package com.createnewera.client.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;

/**
 * The code area of the computer editor. Text sits on a fixed 6 px grid so columns line up
 * while still using Minecraft's own font.
 */
public class CodeEditor {
    public static final int CELL = 6;
    /** The code's font: Monocraft (assets/create_new_era/font/editor.json), 6 px a character like CELL */
    public static final Style MONO = Style.EMPTY.withFont(com.createnewera.CreateNewEra.asResource("editor"));
    public static final int LINE = 10;
    private static final int GUTTER = 22;
    private static final int PAD = 4;
    private static final String INDENT = "  ";
    private static final int UNDO_LIMIT = 200;

    private static final int GUTTER_BG = 0x38000000;
    private static final int GUTTER_TEXT = 0xFF777777;
    private static final int GUTTER_CURRENT = 0xFFE8E8E8;
    private static final int CURRENT_LINE = 0x29E8B75A;
    private static final int SELECTION = 0x8C3A6EA5;
    private static final int ERROR_MARK = 0xFFFF6A4A;

    private final Font font;
    private final int maxLength;
    private List<String> lines = new ArrayList<>(List.of(""));
    private int row, col;
    /** Other end of the selection, or -1 when nothing is selected. */
    private int anchorRow = -1, anchorCol;
    /** Column the cursor wants to be in while moving up and down through shorter lines. */
    private int wantedCol;
    private int scrollRow, scrollCol;
    private int x, y, width, height;
    private int errorLine = -1;
    private static final List<String> CLOSERS = List.of("end", "else", "until");
    /** Row the editor pulled back after a closing keyword, so it can undo that if the word keeps going. */
    private int dedentedRow = -1;

    private final Deque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private EditKind lastEdit = EditKind.NONE;
    private long lastEditTime;
    private long lastClick;
    private boolean dragging;
    private Runnable onChange = () -> {};

    private record Snapshot(String text, int row, int col) {}

    private enum EditKind { NONE, TYPE, DELETE, OTHER }

    public CodeEditor(Font font, int maxLength) {
        this.font = font;
        this.maxLength = maxLength;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        scrollToCursor();
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public String getText() {
        return String.join("\n", lines);
    }

    public void setText(String text) {
        lines = new ArrayList<>(List.of(text.replace("\t", INDENT).replace("\r", "").split("\n", -1)));
        row = col = 0;
        anchorRow = -1;
        undo.clear();
        redo.clear();
        savedText = getText();
    }

    /** Puts the cursor at the start of a line (1-based) and scrolls it into view. */
    public void goToLine(int line) {
        anchorRow = -1;
        row = Mth.clamp(line - 1, 0, lines.size() - 1);
        col = indentOf(lines.get(row)).length();
        wantedCol = col;
        scrollRow = Math.max(0, row - visibleRows() / 2);
    }

    /** Marks a line (1-based) with a red square in the gutter, or clears it with -1. */
    public void setErrorLine(int line) {
        errorLine = line;
    }

    // ---- rendering ----

    public void render(GuiGraphics g, int mouseX, int mouseY, boolean focused) {
        g.fill(x, y, x + GUTTER, y + height, GUTTER_BG);
        g.enableScissor(x, y, x + width, y + height);

        int visible = visibleRows();
        LuaHighlighter.State state = LuaHighlighter.State.CODE;
        // highlighting state flows from the top, so run through the lines above the view too
        for (int r = 0; r < scrollRow && r < lines.size(); r++)
            state = LuaHighlighter.highlight(lines.get(r), new int[lines.get(r).length()], state);

        int textX = x + GUTTER + PAD;
        for (int i = 0; i <= visible && scrollRow + i < lines.size(); i++) {
            int r = scrollRow + i;
            int lineY = y + PAD + i * LINE;
            String line = lines.get(r);
            if (r == row)
                g.fill(x + GUTTER, lineY - 1, x + width, lineY + LINE - 1, CURRENT_LINE);
            drawSelection(g, r, line, textX, lineY);

            String number = Integer.toString(r + 1);
            int numberColor = r == row ? GUTTER_CURRENT : GUTTER_TEXT;
            if (r + 1 == errorLine) {
                g.fill(x + 2, lineY + 1, x + 6, lineY + 5, ERROR_MARK);
                numberColor = ERROR_MARK;
            }
            g.drawString(font, number, x + GUTTER - 3 - font.width(number), lineY, numberColor, false);

            int[] colors = new int[line.length()];
            state = LuaHighlighter.highlight(line, colors, state);
            int last = Math.min(line.length(), scrollCol + columns() + 1);
            for (int c = scrollCol; c < last; c++) {
                char ch = line.charAt(c);
                if (ch == ' ')
                    continue;
                int cellX = textX + (c - scrollCol) * CELL;
                // Monocraft: monospaced and drawn like Minecraft's own font, so an i fills its cell
                // as an m does, instead of a 1 px stroke floating in a 6 px gap
                Component glyph = Component.literal(String.valueOf(ch)).withStyle(MONO);
                g.drawString(font, glyph, cellX + (CELL - font.width(glyph)) / 2, lineY, colors[c], false);
            }
        }

        if (focused && (Util.getMillis() / 500) % 2 == 0 && row >= scrollRow && row <= scrollRow + visible) {
            int cx = textX + (col - scrollCol) * CELL;
            int cy = y + PAD + (row - scrollRow) * LINE;
            g.fill(cx, cy - 1, cx + 1, cy + LINE - 2, 0xFFFFFFFF);
        }
        g.disableScissor();
        drawScrollbar(g);
    }

    private void drawSelection(GuiGraphics g, int r, String line, int textX, int lineY) {
        if (!hasSelection())
            return;
        int[] s = selectionStart(), e = selectionEnd();
        if (r < s[0] || r > e[0])
            return;
        int from = r == s[0] ? s[1] : 0;
        int to = r == e[0] ? e[1] : line.length() + 1; // +1 shows the selected line break
        int x1 = textX + (from - scrollCol) * CELL, x2 = textX + (to - scrollCol) * CELL;
        g.fill(Math.max(x1, x + GUTTER), lineY - 1, x2, lineY + LINE - 1, SELECTION);
    }

    private void drawScrollbar(GuiGraphics g) {
        int visible = visibleRows();
        if (lines.size() <= visible)
            return;
        int track = height - 4;
        int thumb = Math.max(8, track * visible / lines.size());
        int top = y + 2 + (track - thumb) * scrollRow / Math.max(1, lines.size() - visible);
        g.fill(x + width - 3, top, x + width - 1, top + thumb, 0x80E8B75A);
    }

    private int visibleRows() {
        return Math.max(1, (height - PAD * 2) / LINE);
    }

    private int columns() {
        return Math.max(1, (width - GUTTER - PAD * 2) / CELL);
    }

    private void scrollToCursor() {
        int visible = visibleRows();
        if (row < scrollRow)
            scrollRow = row;
        else if (row >= scrollRow + visible)
            scrollRow = row - visible + 1;
        int cols = columns();
        if (col < scrollCol)
            scrollCol = Math.max(0, col - 4);
        else if (col >= scrollCol + cols)
            scrollCol = col - cols + 4;
        scrollRow = Mth.clamp(scrollRow, 0, Math.max(0, lines.size() - 1));
    }

    // ---- mouse ----

    public boolean isMouseOver(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!isMouseOver(mx, my) || button != 0)
            return false;
        int[] at = positionAt(mx, my);
        long now = Util.getMillis();
        if (now - lastClick < 250 && at[0] == row && at[1] == col) {
            selectWord();
        } else {
            if (Screen.hasShiftDown()) {
                if (!hasSelection())
                    setAnchor();
            } else {
                anchorRow = -1;
            }
            moveTo(at[0], at[1]);
        }
        lastClick = now;
        dragging = true;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button) {
        if (!dragging || button != 0)
            return false;
        if (!hasSelection())
            setAnchor();
        int[] at = positionAt(mx, my);
        moveTo(at[0], at[1]);
        scrollToCursor();
        return true;
    }

    public void mouseReleased() {
        dragging = false;
        if (hasSelection() && anchorRow == row && anchorCol == col)
            anchorRow = -1;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!isMouseOver(mx, my))
            return false;
        if (Screen.hasShiftDown())
            scrollCol = Math.max(0, scrollCol - (int) Math.signum(amount) * 6);
        else
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(amount) * 3, 0, Math.max(0, lines.size() - visibleRows()));
        return true;
    }

    private int[] positionAt(double mx, double my) {
        int r = Mth.clamp(scrollRow + (int) Math.floor((my - y - PAD + 1) / LINE), 0, lines.size() - 1);
        int c = scrollCol + (int) Math.round((mx - x - GUTTER - PAD) / CELL);
        return new int[] {r, Mth.clamp(c, 0, lines.get(r).length())};
    }

    // ---- keyboard ----

    public boolean charTyped(char ch) {
        if (!StringUtil.isAllowedChatCharacter(ch))
            return false;
        insert(String.valueOf(ch), EditKind.TYPE);
        String line = lines.get(row);
        boolean closer = col == line.length() && CLOSERS.contains(line.strip());
        if (closer && dedentedRow != row && line.startsWith(INDENT)) {
            // typing a closing keyword pulls the line back one level
            lines.set(row, line.substring(INDENT.length()));
            col -= INDENT.length();
            dedentedRow = row;
        } else if (!closer && dedentedRow == row && !"elseif".startsWith(line.strip())) {
            // it was the start of a longer word (endpoint, untilDone): put the indent back
            lines.set(row, INDENT + line);
            col += INDENT.length();
            dedentedRow = -1;
        }
        wantedCol = col;
        return true;
    }

    public boolean keyPressed(int key, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = Screen.hasControlDown();

        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    anchorRow = 0;
                    anchorCol = 0;
                    row = lines.size() - 1;
                    col = lines.get(row).length();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    if (hasSelection())
                        Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (hasSelection()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
                        insert("", EditKind.OTHER);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    insert(Minecraft.getInstance().keyboardHandler.getClipboard().replace("\r", "").replace("\t", INDENT), EditKind.OTHER);
                    return true;
                }
                case GLFW.GLFW_KEY_Z -> {
                    if (shift) redo(); else undo();
                    return true;
                }
                case GLFW.GLFW_KEY_Y -> {
                    redo();
                    return true;
                }
                case GLFW.GLFW_KEY_D -> {
                    duplicateLine();
                    return true;
                }
                case GLFW.GLFW_KEY_SLASH -> {
                    toggleComment();
                    return true;
                }
                default -> {}
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                newLine();
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (shift) dedentLines(); else if (hasSelection() && selectionStart()[0] != selectionEnd()[0]) indentLines(); else insert(INDENT, EditKind.TYPE);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!hasSelection()) {
                    if (row == 0 && col == 0)
                        return true;
                    setAnchor();
                    String before = lines.get(row).substring(0, col);
                    // backspace in leading whitespace removes a whole indent step
                    if (!before.isEmpty() && before.isBlank() && before.length() % INDENT.length() == 0)
                        col -= INDENT.length();
                    else if (ctrl)
                        moveWord(-1);
                    else
                        stepLeft();
                }
                insert("", EditKind.DELETE);
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (!hasSelection()) {
                    setAnchor();
                    if (ctrl) moveWord(1); else stepRight();
                }
                insert("", EditKind.DELETE);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN,
                 GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                navigate(key, shift, ctrl);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void navigate(int key, boolean shift, boolean ctrl) {
        if (shift) {
            if (!hasSelection())
                setAnchor();
        } else if (hasSelection() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            // collapse the selection to the side we're moving towards
            int[] to = key == GLFW.GLFW_KEY_LEFT ? selectionStart() : selectionEnd();
            anchorRow = -1;
            moveTo(to[0], to[1]);
            return;
        } else {
            anchorRow = -1;
        }
        int page = visibleRows() - 1;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> { if (ctrl) moveWord(-1); else stepLeft(); wantedCol = col; }
            case GLFW.GLFW_KEY_RIGHT -> { if (ctrl) moveWord(1); else stepRight(); wantedCol = col; }
            case GLFW.GLFW_KEY_UP -> moveVertical(-1);
            case GLFW.GLFW_KEY_DOWN -> moveVertical(1);
            case GLFW.GLFW_KEY_PAGE_UP -> moveVertical(-page);
            case GLFW.GLFW_KEY_PAGE_DOWN -> moveVertical(page);
            case GLFW.GLFW_KEY_HOME -> {
                if (ctrl) {
                    row = 0;
                    col = 0;
                } else {
                    // first press goes to the code, second to the very start
                    int indent = indentOf(lines.get(row)).length();
                    col = col == indent ? 0 : indent;
                }
                wantedCol = col;
            }
            case GLFW.GLFW_KEY_END -> {
                if (ctrl)
                    row = lines.size() - 1;
                col = lines.get(row).length();
                wantedCol = col;
            }
            default -> {}
        }
        scrollToCursor();
    }

    // ---- editing ----

    private void insert(String text, EditKind kind) {
        long now = Util.getMillis();
        // runs of typing or deleting undo together, like any other editor
        if (kind != lastEdit || kind == EditKind.OTHER || now - lastEditTime > 1000 || text.contains("\n"))
            pushUndo();
        lastEdit = kind;
        lastEditTime = now;

        rawInsert(text);
        scrollToCursor();
        onChange.run();
    }

    /** Inserts at the cursor, replacing any selection, with no undo bookkeeping. */
    private void rawInsert(String text) {
        if (hasSelection())
            deleteSelection();
        String current = getText();
        if (current.length() + text.length() > maxLength)
            text = text.substring(0, Math.max(0, maxLength - current.length()));
        if (!text.isEmpty()) {
            String line = lines.get(row);
            String[] parts = (line.substring(0, col) + text + line.substring(col)).split("\n", -1);
            String tail = line.substring(col);
            lines.set(row, parts[0]);
            for (int i = 1; i < parts.length; i++)
                lines.add(row + i, parts[i]);
            row += parts.length - 1;
            col = lines.get(row).length() - tail.length();
        }
        wantedCol = col;
    }

    // ---- completion support ----

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    /** The file from its start up to the cursor. */
    public String textBeforeCursor() {
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < row; r++)
            out.append(lines.get(r)).append('\n');
        return out.append(lines.get(row), 0, col).toString();
    }

    /** Top-left of the cursor's cell on screen. */
    public int[] cursorScreenPos() {
        return new int[] {x + GUTTER + PAD + (col - scrollCol) * CELL, y + PAD + (row - scrollRow) * LINE};
    }

    /**
     * Replaces the cursor line from column {@code from} up to the cursor with a suggestion.
     * "$0" in it marks where the cursor ends up; new lines keep the current indent.
     */
    public void applyCompletion(int from, String insert) {
        pushUndo();
        lastEdit = EditKind.OTHER;
        anchorRow = -1;
        String line = lines.get(row);
        lines.set(row, line.substring(0, Math.min(from, col)) + line.substring(col));
        col = Math.min(from, col);
        String text = insert.replace("\n", "\n" + indentOf(line));
        // calling a function that already has its brackets: just the name
        if (text.endsWith("($0)") && lines.get(row).substring(col).startsWith("("))
            text = text.substring(0, text.length() - 4);
        int caret = text.indexOf("$0");
        if (caret < 0) {
            rawInsert(text);
        } else {
            rawInsert(text.substring(0, caret));
            int caretRow = row, caretCol = col;
            rawInsert(text.substring(caret + 2));
            row = caretRow;
            col = caretCol;
            wantedCol = col;
        }
        dedentedRow = -1;
        scrollToCursor();
        onChange.run();
    }

    // ---- saved state ----

    private String savedText = "";

    public boolean isDirty() {
        return !getText().equals(savedText);
    }

    public void markSaved() {
        savedText = getText();
    }

    private void newLine() {
        dedentedRow = -1;
        String line = lines.get(row);
        String indent = indentOf(line);
        String before = line.substring(0, col).strip();
        boolean opens = before.endsWith(" then") || before.equals("then") || before.endsWith(" do") || before.equals("do")
                || before.equals("else") || before.equals("repeat") || before.endsWith("{")
                || before.endsWith("(") || before.matches(".*\\bfunction\\b.*\\)\\s*$");
        String inner = opens ? indent + INDENT : indent;
        insert("\n" + inner, EditKind.OTHER);
    }

    private void deleteSelection() {
        int[] s = selectionStart(), e = selectionEnd();
        String merged = lines.get(s[0]).substring(0, s[1]) + lines.get(e[0]).substring(e[1]);
        lines.subList(s[0] + 1, e[0] + 1).clear();
        lines.set(s[0], merged);
        row = s[0];
        col = s[1];
        anchorRow = -1;
    }

    private void indentLines() {
        pushUndo();
        int[] s = selectionStart(), e = selectionEnd();
        for (int r = s[0]; r <= e[0]; r++)
            lines.set(r, INDENT + lines.get(r));
        anchorCol = anchorRow == s[0] ? s[1] + INDENT.length() : anchorCol + INDENT.length();
        col += INDENT.length();
        onChange.run();
    }

    private void dedentLines() {
        pushUndo();
        int from = hasSelection() ? selectionStart()[0] : row, to = hasSelection() ? selectionEnd()[0] : row;
        for (int r = from; r <= to; r++) {
            String line = lines.get(r);
            int remove = Math.min(INDENT.length(), indentOf(line).length());
            lines.set(r, line.substring(remove));
            if (r == row)
                col = Math.max(0, col - remove);
            if (r == anchorRow)
                anchorCol = Math.max(0, anchorCol - remove);
        }
        onChange.run();
    }

    private void duplicateLine() {
        pushUndo();
        lines.add(row + 1, lines.get(row));
        row++;
        scrollToCursor();
        onChange.run();
    }

    private void toggleComment() {
        pushUndo();
        int from = hasSelection() ? selectionStart()[0] : row, to = hasSelection() ? selectionEnd()[0] : row;
        boolean allCommented = true;
        for (int r = from; r <= to; r++)
            if (!lines.get(r).isBlank() && !lines.get(r).strip().startsWith("--"))
                allCommented = false;
        for (int r = from; r <= to; r++) {
            String line = lines.get(r);
            String indent = indentOf(line);
            if (line.isBlank())
                continue;
            String body = line.substring(indent.length());
            if (allCommented)
                body = body.startsWith("-- ") ? body.substring(3) : body.substring(2);
            else
                body = "-- " + body;
            lines.set(r, indent + body);
        }
        col = Math.min(col, lines.get(row).length());
        onChange.run();
    }

    private void pushUndo() {
        undo.push(new Snapshot(getText(), row, col));
        while (undo.size() > UNDO_LIMIT)
            undo.removeLast();
        redo.clear();
    }

    private void undo() {
        if (undo.isEmpty())
            return;
        redo.push(new Snapshot(getText(), row, col));
        restore(undo.pop());
    }

    private void redo() {
        if (redo.isEmpty())
            return;
        undo.push(new Snapshot(getText(), row, col));
        restore(redo.pop());
    }

    private void restore(Snapshot snapshot) {
        lines = new ArrayList<>(List.of(snapshot.text().split("\n", -1)));
        row = Math.min(snapshot.row(), lines.size() - 1);
        col = Math.min(snapshot.col(), lines.get(row).length());
        anchorRow = -1;
        lastEdit = EditKind.NONE;
        scrollToCursor();
        onChange.run();
    }

    // ---- cursor helpers ----

    private void moveTo(int r, int c) {
        row = r;
        col = c;
        wantedCol = c;
    }

    private void stepLeft() {
        if (col > 0) {
            col--;
        } else if (row > 0) {
            row--;
            col = lines.get(row).length();
        }
    }

    private void stepRight() {
        if (col < lines.get(row).length()) {
            col++;
        } else if (row < lines.size() - 1) {
            row++;
            col = 0;
        }
    }

    private void moveVertical(int delta) {
        row = Mth.clamp(row + delta, 0, lines.size() - 1);
        col = Math.min(wantedCol, lines.get(row).length());
    }

    private void moveWord(int dir) {
        String line = lines.get(row);
        if (dir < 0) {
            if (col == 0) { stepLeft(); return; }
            int c = col;
            while (c > 0 && line.charAt(c - 1) == ' ') c--;
            boolean word = c > 0 && isWordChar(line.charAt(c - 1));
            while (c > 0 && line.charAt(c - 1) != ' ' && isWordChar(line.charAt(c - 1)) == word) c--;
            col = c;
        } else {
            if (col == line.length()) { stepRight(); return; }
            int c = col;
            boolean word = isWordChar(line.charAt(c));
            while (c < line.length() && line.charAt(c) != ' ' && isWordChar(line.charAt(c)) == word) c++;
            while (c < line.length() && line.charAt(c) == ' ') c++;
            col = c;
        }
    }

    private void selectWord() {
        String line = lines.get(row);
        int start = col, end = col;
        while (start > 0 && isWordChar(line.charAt(start - 1))) start--;
        while (end < line.length() && isWordChar(line.charAt(end))) end++;
        anchorRow = row;
        anchorCol = start;
        col = end;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static String indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return line.substring(0, i);
    }

    private void setAnchor() {
        anchorRow = row;
        anchorCol = col;
    }

    private boolean hasSelection() {
        return anchorRow >= 0 && (anchorRow != row || anchorCol != col);
    }

    private int[] selectionStart() {
        return anchorRow < row || (anchorRow == row && anchorCol < col) ? new int[] {anchorRow, anchorCol} : new int[] {row, col};
    }

    private int[] selectionEnd() {
        return anchorRow < row || (anchorRow == row && anchorCol < col) ? new int[] {row, col} : new int[] {anchorRow, anchorCol};
    }

    private String selectedText() {
        int[] s = selectionStart(), e = selectionEnd();
        if (s[0] == e[0])
            return lines.get(s[0]).substring(s[1], e[1]);
        StringBuilder out = new StringBuilder(lines.get(s[0]).substring(s[1]));
        for (int r = s[0] + 1; r < e[0]; r++)
            out.append('\n').append(lines.get(r));
        return out.append('\n').append(lines.get(e[0]), 0, e[1]).toString();
    }
}
