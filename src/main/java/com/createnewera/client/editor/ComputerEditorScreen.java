package com.createnewera.client.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.createnewera.CreateNewEra;
import com.createnewera.client.bus.DeviceHighlight;
import com.createnewera.computer.ComputerFiles;
import com.createnewera.computer.ComputerSide;
import com.createnewera.computer.ConsoleLine;
import com.createnewera.network.ComputerSnapshot;
import com.createnewera.network.EditorActionPayload;
import com.createnewera.network.EditorActionPayload.Action;
import com.createnewera.network.OpenEditorPayload;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The brass computer's editor, laid out like VS Code in Create's materials: a brass title bar,
 * file tabs over the code, the console underneath, a side panel (files, redstone, flows) picked
 * from the activity bar, and a status bar. The window, both splitters and the panel are draggable,
 * and where you leave them is remembered.
 */
public class ComputerEditorScreen extends Screen {
    private static final ResourceLocation CHECKER = CreateNewEra.asResource("textures/gui/editor_checker.png");
    private static final Pattern ERROR_AT = Pattern.compile("([\\w./\\-]+):(\\d+):");

    private static final int TITLE = 12, TABS = 12, TOOLBAR = 16, ACTIVITY = 16, DIVIDER = 3, EDGE = 3, ROW = 11;
    private static final int MIN_W = 240, MIN_H = 140;

    // palette: brass, wood and stone from the design canvas; dark chrome around the code
    private static final int BRASS = 0xFFE8B75A, BRASS_LIGHT = 0xFFFBDC93, BRASS_DARK = 0xFFA1712C, BRASS_TEXT = 0xFF3A2411;
    private static final int PLANK = 0xFF6B4429, PLANK_GAP = 0xFF5C3A22, PLANK_EDGE = 0xFF3D2614, WOOD_DARK = 0xFF2E1D10;
    private static final int LABEL = 0xFFF3E6C4, LABEL_DIM = 0xFFC9B083, LABEL_SHADOW = 0xFF2A1A0D;
    private static final int STONE = 0xFFC6C6C6, STONE_LIGHT = 0xFFFFFFFF, STONE_DARK = 0xFF555555;
    private static final int TAB_BAR = 0xFF262626, TAB_ACTIVE = 0xFF383838, TAB_TEXT = 0xFF9A9A9A;
    private static final int REDSTONE = 0xFFE0402A, ERROR = 0xFFFF6A4A;

    private enum Panel { FILES, REDSTONE, FLOWS }

    private enum Drag { NONE, MOVE, RESIZE, CONSOLE, SIDE, TEXT }

    private record Rect(int x, int y, int w, int h) {
        static final Rect EMPTY = new Rect(0, 0, 0, 0);

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int right() {
            return x + w;
        }

        int bottom() {
            return y + h;
        }
    }

    private final BlockPos pos;
    private final EditorLayout layout = EditorLayout.load();
    private final List<String> fileOrder;
    private final Map<String, String> disk;
    private final Map<String, CodeEditor> open = new LinkedHashMap<>();
    private String active;
    private String entry;
    private ComputerSnapshot snapshot;

    private Rect win = Rect.EMPTY, title, tabs, code = Rect.EMPTY, consoleDivider, console, sideDivider, side, activity, toolbar;
    private Drag drag = Drag.NONE;
    private int resizeEdges; // 1 left, 2 right, 4 top, 8 bottom
    private double dragFromX, dragFromY;
    private Rect dragStart = Rect.EMPTY;
    private int dragStartValue;
    private long lastTitleClick;

    private Completions.Result completion = Completions.Result.NONE;
    private int selected, listTop;

    private long lastChange = -1;
    private int consoleScroll;
    private @Nullable String nameDraft;
    private @Nullable String renaming;
    private @Nullable String pendingDelete;
    private long pendingDeleteAt;
    private @Nullable String status;
    private long statusAt;
    private final Map<Integer, ConsoleLine> consoleRows = new HashMap<>();
    private long cursorShape;

    public ComputerEditorScreen(OpenEditorPayload payload) {
        super(Component.translatable("block.create_new_era.brass_computer"));
        this.pos = payload.pos();
        this.fileOrder = new ArrayList<>(payload.order());
        this.disk = new HashMap<>(payload.files());
        this.entry = payload.entry();
        this.snapshot = payload.snapshot();
        openFile(fileOrder.contains(entry) ? entry : fileOrder.isEmpty() ? newDefaultFile() : fileOrder.get(0));
        updateErrorLines();
    }

    private String newDefaultFile() {
        fileOrder.add(ComputerFiles.STARTUP);
        disk.put(ComputerFiles.STARTUP, "");
        return ComputerFiles.STARTUP;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void update(ComputerSnapshot snapshot) {
        if (snapshot.console().size() != this.snapshot.console().size())
            consoleScroll = 0;
        this.snapshot = snapshot;
        updateErrorLines();
    }

    private CodeEditor editor() {
        return open.get(active);
    }

    // ---- files ----

    private void openFile(String name) {
        if (!open.containsKey(name)) {
            CodeEditor editor = new CodeEditor(Minecraft.getInstance().font, ComputerFiles.MAX_FILE);
            editor.setText(disk.getOrDefault(name, ""));
            editor.setOnChange(() -> lastChange = Util.getMillis());
            open.put(name, editor);
        }
        active = name;
        closeCompletion();
        code = Rect.EMPTY; // forces the new editor to get its bounds
        updateErrorLines();
    }

    private void closeTab(String name) {
        CodeEditor editor = open.get(name);
        if (editor == null)
            return;
        save(name, editor);
        open.remove(name);
        if (open.isEmpty())
            openFile(name);
        else if (name.equals(active))
            openFile(open.keySet().iterator().next());
    }

    private void save(String name, CodeEditor editor) {
        if (!editor.isDirty())
            return;
        disk.put(name, editor.getText());
        send(Action.SAVE, name, editor.getText());
        editor.markSaved();
    }

    private void saveAll() {
        open.forEach(this::save);
        lastChange = -1;
    }

    private void createFile(String name) {
        if (!name.contains("."))
            name += ".lua";
        if (!ComputerFiles.isValidName(name)) {
            flash(I18n.get("create_new_era.editor.bad_name"));
            return;
        }
        if (fileOrder.contains(name)) {
            flash(I18n.get("create_new_era.editor.file_exists", name));
            return;
        }
        fileOrder.add(name);
        disk.put(name, "");
        send(Action.SAVE, name, "");
        openFile(name);
    }

    private void renameFile(String from, String newName) {
        String to = newName.contains(".") ? newName : newName + ".lua";
        if (to.equals(from))
            return;
        if (!ComputerFiles.isValidName(to) || fileOrder.contains(to)) {
            flash(I18n.get(fileOrder.contains(to) ? "create_new_era.editor.file_exists" : "create_new_era.editor.bad_name", to));
            return;
        }
        CodeEditor editor = open.get(from);
        if (editor != null)
            save(from, editor);
        fileOrder.set(fileOrder.indexOf(from), to);
        disk.put(to, disk.remove(from));
        // keep the tab where it was
        Map<String, CodeEditor> reordered = new LinkedHashMap<>();
        open.forEach((k, v) -> reordered.put(k.equals(from) ? to : k, v));
        open.clear();
        open.putAll(reordered);
        if (from.equals(active))
            active = to;
        if (from.equals(entry))
            entry = to;
        send(Action.RENAME, from, to);
    }

    private void deleteFile(String name) {
        fileOrder.remove(name);
        disk.remove(name);
        open.remove(name);
        send(Action.DELETE, name, "");
        if (fileOrder.isEmpty())
            createFile(ComputerFiles.STARTUP);
        if (open.isEmpty())
            openFile(fileOrder.get(0));
        else if (name.equals(active))
            openFile(open.keySet().iterator().next());
    }

    private void send(Action action, String name, String argument) {
        PacketDistributor.sendToServer(new EditorActionPayload(pos, action, name, argument));
    }

    private void run() {
        saveAll();
        entry = active;
        send(Action.RUN, active, "");
    }

    private void flash(String message) {
        status = message;
        statusAt = Util.getMillis();
    }

    // ---- lifecycle ----

    @Override
    public boolean isPauseScreen() {
        // programs keep running while you edit
        return false;
    }

    @Override
    public void tick() {
        var player = minecraft.player;
        if (player == null || player.distanceToSqr(pos.getCenter()) > 64)
            onClose();
        // autosave a moment after typing stops, so nothing is lost if the game closes
        if (lastChange > 0 && Util.getMillis() - lastChange > 1500)
            saveAll();
        if (pendingDelete != null && Util.getMillis() - pendingDeleteAt > 3000)
            pendingDelete = null;
    }

    @Override
    public void onClose() {
        saveAll();
        send(Action.CLOSE, "", "");
        super.onClose();
    }

    @Override
    public void removed() {
        DeviceHighlight.startCountdown();
        layout.save();
        setCursorShape(0);
        super.removed();
    }

    @Override
    protected void init() {
        Completions.translate = I18n::get;
        code = Rect.EMPTY;
        computeLayout();
    }

    // ---- layout ----

    private void computeLayout() {
        if (layout.width == 0) {
            layout.width = Math.min(width - 16, 460);
            layout.height = Math.min(height - 16, 260);
            layout.x = (width - layout.width) / 2;
            layout.y = (height - layout.height) / 2;
        }
        if (layout.maximized) {
            win = new Rect(2, 2, width - 4, height - 4);
        } else {
            int w = Mth.clamp(layout.width, Math.min(MIN_W, width - 4), width - 4);
            int h = Mth.clamp(layout.height, Math.min(MIN_H, height - 4), height - 4);
            int x = Mth.clamp(layout.x, 2, width - w - 2);
            int y = Mth.clamp(layout.y, 2, height - h - 2);
            win = new Rect(x, y, w, h);
        }
        title = new Rect(win.x, win.y, win.w, TITLE);
        toolbar = new Rect(win.x, win.bottom() - TOOLBAR, win.w, TOOLBAR);
        int bodyTop = title.bottom(), bodyH = toolbar.y - bodyTop;
        activity = new Rect(win.right() - ACTIVITY, bodyTop, ACTIVITY, bodyH);
        boolean panelOpen = !layout.panel.isEmpty();
        int sideW = panelOpen ? Mth.clamp(layout.sideWidth, 70, Math.max(70, (int) (win.w * 0.6))) : 0;
        side = new Rect(activity.x - sideW, bodyTop, sideW, bodyH);
        sideDivider = panelOpen ? new Rect(side.x - DIVIDER, bodyTop, DIVIDER, bodyH) : Rect.EMPTY;
        int mainRight = panelOpen ? sideDivider.x : activity.x;
        int mainW = mainRight - win.x;
        tabs = new Rect(win.x, bodyTop, mainW, TABS);
        int consoleH = Mth.clamp(layout.consoleHeight, 18, Math.max(18, bodyH - TABS - DIVIDER - 30));
        console = new Rect(win.x, toolbar.y - consoleH, mainW, consoleH);
        consoleDivider = new Rect(win.x, console.y - DIVIDER, mainW, DIVIDER);
        Rect newCode = new Rect(win.x, tabs.bottom(), mainW, consoleDivider.y - tabs.bottom());
        if (!newCode.equals(code)) {
            code = newCode;
            editor().setBounds(code.x + 1, code.y + 1, code.w - 2, code.h - 2);
        }
    }

    private @Nullable Panel panel() {
        return layout.panel.isEmpty() ? null : Panel.valueOf(layout.panel);
    }

    // ---- input ----

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (nameDraft != null)
            return nameKey(key);
        if (completionOpen()) {
            switch (key) {
                case GLFW.GLFW_KEY_DOWN -> { moveSelection(1); return true; }
                case GLFW.GLFW_KEY_UP -> { moveSelection(-1); return true; }
                case GLFW.GLFW_KEY_PAGE_DOWN -> { moveSelection(8); return true; }
                case GLFW.GLFW_KEY_PAGE_UP -> { moveSelection(-8); return true; }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> { acceptCompletion(); return true; }
                case GLFW.GLFW_KEY_ESCAPE -> { closeCompletion(); return true; }
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> closeCompletion();
                default -> {}
            }
        }
        if (hasControlDown()) {
            switch (key) {
                case GLFW.GLFW_KEY_R -> { run(); return true; }
                case GLFW.GLFW_KEY_S -> { saveAll(); flash(I18n.get("create_new_era.editor.saved")); return true; }
                case GLFW.GLFW_KEY_T -> { send(Action.STOP, "", ""); return true; }
                case GLFW.GLFW_KEY_SPACE -> { refreshCompletion(true); return true; }
                case GLFW.GLFW_KEY_W -> { closeTab(active); return true; }
                case GLFW.GLFW_KEY_N -> { startNaming(null); return true; }
                case GLFW.GLFW_KEY_B -> { togglePanel(panel() == null ? Panel.FILES : panel()); return true; }
                case GLFW.GLFW_KEY_TAB -> { cycleTab(hasShiftDown() ? -1 : 1); return true; }
                default -> {}
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(key, scan, modifiers);
        boolean handled = editor().keyPressed(key, modifiers);
        if (completionOpen() && (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE))
            refreshCompletion(false);
        else if (handled && completionOpen() && key != GLFW.GLFW_KEY_LEFT_SHIFT && key != GLFW.GLFW_KEY_RIGHT_SHIFT)
            closeCompletion();
        return handled || super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (nameDraft != null) {
            if (StringUtil.isAllowedChatCharacter(ch) && nameDraft.length() < 48)
                nameDraft += ch;
            return true;
        }
        boolean typed = editor().charTyped(ch);
        if (typed) {
            // keep suggesting while a word, a member access, a string argument or a path is being typed
            if (Character.isLetterOrDigit(ch) || "_.:\"'/(".indexOf(ch) >= 0)
                refreshCompletion(false);
            else
                closeCompletion();
        }
        return typed;
    }

    private void cycleTab(int dir) {
        List<String> names = new ArrayList<>(open.keySet());
        openFile(names.get(Math.floorMod(names.indexOf(active) + dir, names.size())));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (completionOpen() && clickCompletion(mx, my))
            return true;
        closeCompletion();
        if (nameDraft != null && !side.contains(mx, my))
            commitName();

        // title bar buttons, then window edges, then the bar itself
        if (button == 0 && closeButton().contains(mx, my)) {
            onClose();
            return true;
        }
        if (button == 0 && maximizeButton().contains(mx, my)) {
            layout.maximized = !layout.maximized;
            code = Rect.EMPTY;
            return true;
        }
        int edges = edgesAt(mx, my);
        if (button == 0 && edges != 0) {
            startDrag(Drag.RESIZE, mx, my, 0);
            resizeEdges = edges;
            return true;
        }
        if (button == 0 && title.contains(mx, my)) {
            long now = Util.getMillis();
            if (now - lastTitleClick < 300) {
                layout.maximized = !layout.maximized;
                code = Rect.EMPTY;
            } else if (!layout.maximized) {
                startDrag(Drag.MOVE, mx, my, 0);
            }
            lastTitleClick = now;
            return true;
        }
        if (button == 0 && grow(consoleDivider, 1).contains(mx, my)) {
            startDrag(Drag.CONSOLE, mx, my, console.h);
            return true;
        }
        if (button == 0 && grow(sideDivider, 1).contains(mx, my)) {
            startDrag(Drag.SIDE, mx, my, side.w);
            return true;
        }
        if (activity.contains(mx, my)) {
            Panel[] panels = Panel.values();
            int i = (int) ((my - activity.y - 2) / 18);
            if (i >= 0 && i < panels.length)
                togglePanel(panels[i]);
            return true;
        }
        if (side.contains(mx, my))
            return clickSidePanel(mx, my, button);
        if (tabs.contains(mx, my))
            return clickTabs(mx, my, button);
        if (toolbar.contains(mx, my)) {
            if (button == 0 && runButton().contains(mx, my))
                run();
            else if (button == 0 && stopButton().contains(mx, my))
                send(Action.STOP, "", "");
            return true;
        }
        if (console.contains(mx, my)) {
            ConsoleLine line = consoleRows.get((int) ((my - console.y - 3) / 9));
            if (line != null && line.kind() == ConsoleLine.Kind.ERROR)
                jumpToError(line.text());
            return true;
        }
        if (button == 0 && editor().mouseClicked(mx, my, button)) {
            drag = Drag.TEXT;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void startDrag(Drag kind, double mx, double my, int value) {
        drag = kind;
        dragFromX = mx;
        dragFromY = my;
        dragStart = win;
        dragStartValue = value;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int ddx = (int) (mx - dragFromX), ddy = (int) (my - dragFromY);
        switch (drag) {
            case MOVE -> {
                layout.x = dragStart.x + ddx;
                layout.y = dragStart.y + ddy;
                layout.width = dragStart.w;
                layout.height = dragStart.h;
            }
            case RESIZE -> {
                int x = dragStart.x, y = dragStart.y, w = dragStart.w, h = dragStart.h;
                if ((resizeEdges & 1) != 0) { x = Math.min(x + ddx, dragStart.right() - MIN_W); w = dragStart.right() - x; }
                if ((resizeEdges & 2) != 0) w = Math.max(MIN_W, w + ddx);
                if ((resizeEdges & 4) != 0) { y = Math.min(y + ddy, dragStart.bottom() - MIN_H); h = dragStart.bottom() - y; }
                if ((resizeEdges & 8) != 0) h = Math.max(MIN_H, h + ddy);
                layout.x = x;
                layout.y = y;
                layout.width = w;
                layout.height = h;
            }
            case CONSOLE -> layout.consoleHeight = dragStartValue - ddy;
            case SIDE -> layout.sideWidth = dragStartValue - ddx;
            case TEXT -> editor().mouseDragged(mx, my, button);
            case NONE -> {
                return super.mouseDragged(mx, my, button, dx, dy);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag == Drag.TEXT)
            editor().mouseReleased();
        if (drag != Drag.NONE && drag != Drag.TEXT)
            layout.save();
        drag = Drag.NONE;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (completionOpen() && completionRect().contains(mx, my)) {
            listTop = Mth.clamp(listTop - (int) Math.signum(scrollY), 0, Math.max(0, completion.items().size() - visibleItems()));
            return true;
        }
        if (editor().mouseScrolled(mx, my, scrollY)) {
            closeCompletion();
            return true;
        }
        if (console.contains(mx, my)) {
            consoleScroll = Mth.clamp(consoleScroll + (int) Math.signum(scrollY), 0,
                    Math.max(0, snapshot.console().size() - consoleRowsVisible()));
            return true;
        }
        return false;
    }

    /** Which window edges the mouse is on, as resize flags. */
    private int edgesAt(double mx, double my) {
        if (layout.maximized || !grow(win, EDGE).contains(mx, my))
            return 0;
        int edges = 0;
        if (mx < win.x + EDGE) edges |= 1;
        if (mx >= win.right() - EDGE) edges |= 2;
        if (my < win.y + 2) edges |= 4;
        if (my >= win.bottom() - EDGE) edges |= 8;
        // the grip in the status bar's corner
        if (mx >= win.right() - 8 && my >= win.bottom() - 8) edges = 2 | 8;
        return edges;
    }

    private static Rect grow(Rect r, int by) {
        return r.w == 0 ? r : new Rect(r.x - by, r.y - by, r.w + by * 2, r.h + by * 2);
    }

    private void togglePanel(Panel panel) {
        layout.panel = panel() == panel ? "" : panel.name();
        layout.save();
    }

    // ---- completion ----

    private boolean completionOpen() {
        return !completion.items().isEmpty();
    }

    private void closeCompletion() {
        completion = Completions.Result.NONE;
    }

    private void refreshCompletion(boolean explicit) {
        CodeEditor editor = editor();
        var player = Minecraft.getInstance().player;
        var viewer = player == null ? new Completions.Viewer(Vec3.atCenterOf(pos), 0)
                : new Completions.Viewer(player.getEyePosition(), player.getYRot());
        completion = Completions.complete(editor.textBeforeCursor(), editor.getText(), fileOrder, snapshot.devices(), viewer, explicit);
        selected = 0;
        listTop = 0;
    }

    private void moveSelection(int by) {
        int n = completion.items().size();
        selected = by == 1 || by == -1 ? Math.floorMod(selected + by, n) : Mth.clamp(selected + by, 0, n - 1);
        if (selected < listTop)
            listTop = selected;
        else if (selected >= listTop + visibleItems())
            listTop = selected - visibleItems() + 1;
    }

    private void acceptCompletion() {
        var item = completion.items().get(selected).entry();
        editor().applyCompletion(completion.from(), item.insert());
        closeCompletion();
        // a device picked by name: show where it is once the editor closes
        if (item.kind() == LuaApi.Kind.DEVICE)
            snapshot.devices().stream().filter(d -> d.name().equals(item.label())).findFirst()
                    .ifPresent(d -> DeviceHighlight.show(d.pos()));
        // straight into the next level: "redstone" + "." shows its functions
        if (item.kind() == LuaApi.Kind.MODULE)
            refreshCompletion(false);
    }

    private int visibleItems() {
        return Math.min(8, completion.items().size());
    }

    private static final int POPUP_W = 150, DOC_W = 140;
    /** Device rows carry their kind of block and how far away it is, so they need more room. */
    private static final int DEVICE_POPUP_W = 230, DEVICE_DOC_W = 170;

    private boolean devicesShown() {
        return !completion.items().isEmpty() && completion.items().get(0).entry().kind() == LuaApi.Kind.DEVICE;
    }

    private Rect completionRect() {
        int[] at = editor().cursorScreenPos();
        int h = visibleItems() * 10 + 2;
        // line the labels up with the start of the word being completed (badge sits to its left)
        int wordX = at[0] - (editor().getCol() - completion.from()) * CodeEditor.CELL;
        int w = devicesShown() ? DEVICE_POPUP_W : POPUP_W;
        int x = Mth.clamp(wordX - 11, 2, width - w - 2);
        int y = at[1] + CodeEditor.LINE;
        if (y + h > height - 2)
            y = at[1] - h - 1;
        return new Rect(x, y, w, h);
    }

    private boolean clickCompletion(double mx, double my) {
        Rect r = completionRect();
        if (!r.contains(mx, my))
            return false;
        selected = listTop + (int) ((my - r.y - 1) / 10);
        if (selected < completion.items().size())
            acceptCompletion();
        return true;
    }

    private void renderCompletion(GuiGraphics g, int mouseX, int mouseY) {
        Rect r = completionRect();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(r.x - 1, r.y - 1, r.right() + 1, r.bottom() + 1, 0xFF454545);
        g.fill(r.x, r.y, r.right(), r.bottom(), 0xFF252526);
        for (int i = 0; i < visibleItems(); i++) {
            int index = listTop + i;
            var match = completion.items().get(index);
            var entry = match.entry();
            int rowY = r.y + 1 + i * 10;
            boolean hovered = r.contains(mouseX, mouseY) && (mouseY - r.y - 1) / 10 == i;
            if (index == selected)
                g.fill(r.x, rowY, r.right(), rowY + 10, 0xFF094771);
            else if (hovered)
                g.fill(r.x, rowY, r.right(), rowY + 10, 0xFF2A2D2E);
            // kind badge
            g.fill(r.x + 2, rowY + 2, r.x + 8, rowY + 8, entry.kind().color);
            g.drawString(font, entry.kind().badge, r.x + 5 - font.width(entry.kind().badge) / 2, rowY + 1, 0xFF1E1E1E, false);
            int tx = r.x + 11;
            String label = entry.label();
            for (int c = 0; c < label.length(); c++) {
                Component ch = Component.literal(String.valueOf(label.charAt(c))).withStyle(CodeEditor.MONO);
                g.drawString(font, ch, tx, rowY + 1, match.matched()[c] ? 0xFF2AAAFF : 0xFFD4D4D4, false);
                tx += font.width(ch);
            }
            String detail = entry.kind() == LuaApi.Kind.KEYWORD ? "keyword" : entry.kind() == LuaApi.Kind.SNIPPET ? "snippet" : "";
            int right = r.right() - 3;
            // a device: how far and which way at the end, its kind of block before that
            if (!entry.rowHint().isEmpty()) {
                int hintW = 34;
                g.drawString(font, entry.rowHint(), right - font.width(entry.rowHint()), rowY + 1,
                        index == selected ? 0xFFFFD75E : 0xFF8A8A8A, false);
                right -= hintW + 4;
            }
            if (!entry.rowType().isEmpty()) {
                String type = font.plainSubstrByWidth(entry.rowType(), Math.max(0, right - tx - 6));
                if (!type.isEmpty())
                    g.drawString(font, type, right - font.width(type), rowY + 1, index == selected ? 0xFFC8C8C8 : 0xFF8A8A8A, false);
            }
            if (!detail.isEmpty() && tx + 6 + font.width(detail) < right)
                g.drawString(font, detail, right - font.width(detail), rowY + 1, 0xFF8A8A8A, false);
        }
        if (completion.items().size() > visibleItems()) {
            int track = r.h - 2, thumb = Math.max(6, track * visibleItems() / completion.items().size());
            int top = r.y + 1 + (track - thumb) * listTop / Math.max(1, completion.items().size() - visibleItems());
            g.fill(r.right() - 2, top, r.right(), top + thumb, 0x80FFFFFF);
        }

        // details of the selected suggestion, like VS Code's side pane
        var entry = completion.items().get(selected).entry();
        if (!entry.detail().isEmpty() || !entry.doc().isEmpty()) {
            int docW = devicesShown() ? DEVICE_DOC_W : DOC_W;
            List<FormattedCharSequence> doc = entry.doc().isEmpty() ? List.of()
                    : font.split(Component.literal(entry.doc()), docW - 8);
            List<FormattedCharSequence> sig = font.split(Component.literal(entry.detail()), docW - 8);
            List<FormattedCharSequence> footer = entry.footer().isEmpty() ? List.of()
                    : font.split(Component.literal(entry.footer()), docW - 8);
            int dh = 4 + sig.size() * 9 + (doc.isEmpty() ? 0 : 3 + doc.size() * 9)
                    + (footer.isEmpty() ? 0 : 3 + footer.size() * 9) + 2;
            int dx = r.right() + 2 + docW > width ? r.x - docW - 2 : r.right() + 2;
            g.fill(dx - 1, r.y - 1, dx + docW + 1, r.y + dh + 1, 0xFF454545);
            g.fill(dx, r.y, dx + docW, r.y + dh, 0xFF252526);
            int ty = r.y + 3;
            for (FormattedCharSequence line : sig) {
                g.drawString(font, line, dx + 4, ty, 0xFFDCDCAA, false);
                ty += 9;
            }
            if (!doc.isEmpty()) {
                ty += 3;
                for (FormattedCharSequence line : doc) {
                    g.drawString(font, line, dx + 4, ty, 0xFFC8C8C8, false);
                    ty += 9;
                }
            }
            if (!footer.isEmpty()) {
                ty += 3;
                for (FormattedCharSequence line : footer) {
                    g.drawString(font, line, dx + 4, ty, 0xFF8A8A8A, false);
                    ty += 9;
                }
            }
        }
        g.pose().popPose();
    }

    // ---- side panel ----

    private boolean clickSidePanel(double mx, double my, int button) {
        Panel panel = panel();
        if (panel != Panel.FILES)
            return true;
        if (newFileButton().contains(mx, my)) {
            startNaming(null);
            return true;
        }
        int index = (int) ((my - side.y - 16) / ROW);
        if (index < 0 || index >= fileOrder.size())
            return true;
        String name = fileOrder.get(index);
        int rowY = side.y + 16 + index * ROW;
        if (new Rect(side.right() - 11, rowY, 10, ROW).contains(mx, my)) {
            // first click arms, second click within three seconds deletes
            if (name.equals(pendingDelete)) {
                deleteFile(name);
                pendingDelete = null;
            } else {
                pendingDelete = name;
                pendingDeleteAt = Util.getMillis();
            }
        } else if (new Rect(side.right() - 22, rowY, 10, ROW).contains(mx, my)) {
            startNaming(name);
        } else {
            openFile(name);
        }
        return true;
    }

    private Rect newFileButton() {
        return new Rect(side.right() - 12, side.y + 3, 10, 10);
    }

    private void startNaming(@Nullable String file) {
        if (panel() != Panel.FILES)
            togglePanel(Panel.FILES);
        renaming = file;
        nameDraft = file == null ? "" : file;
    }

    private boolean nameKey(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commitName();
            case GLFW.GLFW_KEY_ESCAPE -> nameDraft = null;
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!nameDraft.isEmpty())
                    nameDraft = nameDraft.substring(0, nameDraft.length() - 1);
            }
            default -> {}
        }
        return true;
    }

    private void commitName() {
        String draft = nameDraft == null ? "" : nameDraft.strip();
        nameDraft = null;
        if (draft.isEmpty())
            return;
        if (renaming == null)
            createFile(draft);
        else
            renameFile(renaming, draft);
    }

    private void renderSidePanel(GuiGraphics g, int mouseX, int mouseY) {
        for (int py = side.y; py < side.bottom(); py += 8) {
            g.fill(side.x, py, side.right(), Math.min(py + 7, side.bottom()), PLANK);
            g.fill(side.x, Math.min(py + 7, side.bottom()), side.right(), Math.min(py + 8, side.bottom()), PLANK_GAP);
        }
        g.enableScissor(side.x, side.y, side.right(), side.bottom());
        switch (panel()) {
            case FILES -> renderFiles(g, mouseX, mouseY);
            case REDSTONE -> renderRedstone(g);
            case FLOWS -> renderFlows(g);
            case null -> {}
        }
        g.disableScissor();
    }

    private void renderFiles(GuiGraphics g, int mouseX, int mouseY) {
        label(g, I18n.get("create_new_era.editor.files"), side.x + 5, side.y + 4);
        Rect plus = newFileButton();
        boolean plusHover = plus.contains(mouseX, mouseY);
        g.fill(plus.x + 4, plus.y + 1, plus.x + 6, plus.y + 9, plusHover ? 0xFFFFFFFF : LABEL);
        g.fill(plus.x + 1, plus.y + 4, plus.x + 9, plus.y + 6, plusHover ? 0xFFFFFFFF : LABEL);

        int rowY = side.y + 16;
        for (String name : fileOrder) {
            boolean hovered = new Rect(side.x, rowY, side.w, ROW).contains(mouseX, mouseY);
            if (name.equals(active))
                g.fill(side.x, rowY, side.right(), rowY + ROW, 0x50000000);
            else if (hovered)
                g.fill(side.x, rowY, side.right(), rowY + ROW, 0x28000000);
            if (name.equals(pendingDelete))
                g.fill(side.x, rowY, side.right(), rowY + ROW, 0x80A02010);
            if (name.equals(entry) && snapshot.running())
                g.fill(side.x + 4, rowY + 3, side.x + 7, rowY + 8, 0xFF7BE05A); // running: little green bar
            if (nameDraft != null && name.equals(renaming)) {
                nameInput(g, side.x + 9, rowY);
            } else {
                String shown = name.equals(pendingDelete) ? I18n.get("create_new_era.editor.delete_confirm") : name;
                g.drawString(font, ellipsize(shown, side.w - (hovered ? 34 : 14)), side.x + 9, rowY + 2,
                        name.equals(active) ? 0xFFFFFFFF : LABEL, false);
            }
            if (hovered || name.equals(pendingDelete)) {
                drawPencil(g, side.right() - 21, rowY + 2);
                drawCross(g, side.right() - 10, rowY + 2, name.equals(pendingDelete) ? 0xFFFFFFFF : LABEL_DIM);
            }
            rowY += ROW;
        }
        if (nameDraft != null && renaming == null)
            nameInput(g, side.x + 9, rowY);
        if (status != null && Util.getMillis() - statusAt < 3000) {
            List<FormattedCharSequence> lines = font.split(Component.literal(status), side.w - 10);
            int y = rowY + ROW + 4;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, side.x + 5, y, ERROR, false);
                y += 9;
            }
        }
    }

    private void nameInput(GuiGraphics g, int x, int y) {
        int w = side.right() - x - 4;
        g.fill(x - 2, y, x + w, y + ROW, 0xFF1E1E1E);
        g.fill(x - 2, y + ROW - 1, x + w, y + ROW, BRASS);
        String text = nameDraft.isEmpty() && renaming == null ? "" : nameDraft;
        g.drawString(font, ellipsizeStart(text, w - 6), x, y + 2, 0xFFFFFFFF, false);
        if (nameDraft.isEmpty() && renaming == null)
            g.drawString(font, I18n.get("create_new_era.editor.new_file"), x, y + 2, 0xFF6A6A6A, false);
        if ((Util.getMillis() / 500) % 2 == 0) {
            int cx = x + Math.min(font.width(text), w - 6);
            g.fill(cx, y + 1, cx + 1, y + ROW - 2, 0xFFFFFFFF);
        }
    }

    private void renderRedstone(GuiGraphics g) {
        label(g, I18n.get("create_new_era.editor.redstone"), side.x + 5, side.y + 4);
        int meterW = Math.max(14, Math.min(22, (side.w - 50) / 2));
        int outX = side.right() - 5 - meterW, inX = outX - 3 - meterW;
        g.drawString(font, I18n.get("create_new_era.editor.in"), inX + meterW / 2 - font.width(I18n.get("create_new_era.editor.in")) / 2, side.y + 16, LABEL_DIM, false);
        g.drawString(font, I18n.get("create_new_era.editor.out"), outX + meterW / 2 - font.width(I18n.get("create_new_era.editor.out")) / 2, side.y + 16, LABEL_DIM, false);
        for (ComputerSide s : ComputerSide.values()) {
            int rowY = side.y + 28 + s.ordinal() * 13;
            g.drawString(font, ellipsize(s.luaName(), inX - side.x - 8), side.x + 5, rowY + 1, LABEL, false);
            meter(g, inX, rowY, meterW, snapshot.inputs().get(s.ordinal()));
            meter(g, outX, rowY, meterW, snapshot.outputs().get(s.ordinal()));
        }
    }

    private void renderFlows(GuiGraphics g) {
        label(g, I18n.get("create_new_era.editor.flows"), side.x + 5, side.y + 4);
        // a little picture of what this panel will show: machines feeding a computer
        int cx = side.x + side.w / 2, top = side.y + 22;
        int[][] nodes = {{cx - 30, top}, {cx + 14, top}, {cx - 8, top + 26}};
        g.fill(cx - 20, top + 5, cx + 18, top + 6, 0xFFA1712C);
        g.fill(cx - 21, top + 5, cx - 20, top + 30, 0xFFA1712C);
        g.fill(cx + 22, top + 5, cx + 23, top + 30, 0xFFA1712C);
        g.fill(cx - 21, top + 30, cx - 8, top + 31, 0xFFA1712C);
        g.fill(cx + 8, top + 30, cx + 23, top + 31, 0xFFA1712C);
        for (int[] n : nodes) {
            g.fill(n[0], n[1], n[0] + 16, n[1] + 10, 0xFF8B8B8B);
            g.fill(n[0], n[1], n[0] + 16, n[1] + 1, 0xFFB5B5B5);
            g.fill(n[0] + 2, n[1] + 3, n[0] + 6, n[1] + 7, BRASS);
        }
        List<FormattedCharSequence> text = font.split(Component.translatable("create_new_era.editor.flows_soon"), side.w - 10);
        int y = top + 46;
        for (FormattedCharSequence line : text) {
            g.drawString(font, line, side.x + 5, y, LABEL_DIM, false);
            y += 9;
        }
    }

    /** A recessed slot that glows redstone red with the signal strength. */
    private void meter(GuiGraphics g, int x, int y, int w, int strength) {
        g.fill(x, y, x + w, y + 10, WOOD_DARK);
        g.fill(x, y, x + w, y + 1, 0xFF1A0F07);
        if (strength > 0) {
            int alpha = 0x60 + strength * 0x9F / 15;
            g.fill(x + 1, y + 1, x + w - 1, y + 10, (alpha << 24) | (REDSTONE & 0xFFFFFF));
        }
        String s = Integer.toString(strength);
        g.drawString(font, s, x + w / 2 - font.width(s) / 2 + 1, y + 1, strength > 0 ? 0xFFFFFFFF : 0xFF6B4520, false);
    }

    private void renderActivityBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(activity.x, activity.y, activity.right(), activity.bottom(), WOOD_DARK);
        g.fill(activity.x, activity.y, activity.x + 1, activity.bottom(), PLANK_EDGE);
        Panel current = panel();
        for (Panel p : Panel.values()) {
            int y = activity.y + 2 + p.ordinal() * 18;
            boolean on = p == current, hover = new Rect(activity.x, y, ACTIVITY, 18).contains(mouseX, mouseY);
            if (on)
                g.fill(activity.right() - 2, y + 1, activity.right(), y + 17, BRASS);
            int color = on ? LABEL : hover ? 0xFFE0D2B0 : 0xFF8A7558;
            int ix = activity.x + 3, iy = y + 4;
            switch (p) {
                case FILES -> {
                    g.fill(ix + 1, iy, ix + 8, iy + 1, color);
                    g.fill(ix + 1, iy, ix + 2, iy + 10, color);
                    g.fill(ix + 1, iy + 9, ix + 10, iy + 10, color);
                    g.fill(ix + 9, iy + 2, ix + 10, iy + 10, color);
                    g.fill(ix + 3, iy + 4, ix + 8, iy + 5, color);
                    g.fill(ix + 3, iy + 6, ix + 8, iy + 7, color);
                }
                case REDSTONE -> {
                    int red = on || hover ? REDSTONE : 0xFF9A3020;
                    g.fill(ix + 4, iy, ix + 6, iy + 2, red);
                    g.fill(ix + 3, iy + 2, ix + 7, iy + 5, red);
                    g.fill(ix + 4, iy + 5, ix + 6, iy + 10, color);
                }
                case FLOWS -> {
                    g.fill(ix, iy, ix + 4, iy + 3, color);
                    g.fill(ix + 6, iy + 7, ix + 10, iy + 10, color);
                    g.fill(ix + 2, iy + 3, ix + 3, iy + 8, color);
                    g.fill(ix + 2, iy + 8, ix + 6, iy + 9, color);
                }
            }
            if (hover)
                g.renderTooltip(font, Component.translatable("create_new_era.editor.panel." + p.name().toLowerCase()), mouseX, mouseY);
        }
    }

    // ---- tabs ----

    private boolean clickTabs(double mx, double my, int button) {
        int x = tabs.x;
        for (String name : List.copyOf(open.keySet())) {
            int w = tabWidth(name);
            if (mx >= x && mx < x + w) {
                if (button == 2 || (button == 0 && mx >= x + w - 11))
                    closeTab(name);
                else
                    openFile(name);
                return true;
            }
            x += w;
        }
        return true;
    }

    private int tabWidth(String name) {
        return font.width(name) + 20;
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(tabs.x, tabs.y, tabs.right(), tabs.bottom(), TAB_BAR);
        g.enableScissor(tabs.x, tabs.y, tabs.right(), tabs.bottom());
        int x = tabs.x;
        for (var e : open.entrySet()) {
            String name = e.getKey();
            int w = tabWidth(name);
            boolean on = name.equals(active), hover = new Rect(x, tabs.y, w, TABS).contains(mouseX, mouseY);
            if (on) {
                g.fill(x, tabs.y, x + w, tabs.bottom(), TAB_ACTIVE);
                g.fill(x, tabs.y, x + w, tabs.y + 1, BRASS);
            }
            g.fill(x + w - 1, tabs.y + 2, x + w, tabs.bottom() - 2, 0xFF1A1A1A);
            g.drawString(font, name, x + 6, tabs.y + 2, on ? 0xFFFFFFFF : TAB_TEXT, false);
            // a dot for unsaved changes that turns into a close cross under the mouse
            int bx = x + w - 10;
            if (hover)
                drawCross(g, bx, tabs.y + 2, 0xFFD0D0D0);
            else if (e.getValue().isDirty())
                g.fill(bx + 1, tabs.y + 4, bx + 5, tabs.y + 8, BRASS);
            x += w;
        }
        g.disableScissor();
    }

    // ---- rendering ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        computeLayout();
        super.render(g, mouseX, mouseY, partialTick);

        g.fill(win.x - 1, win.y - 1, win.right() + 1, win.bottom() + 1, 0xFF120E0A);
        renderTitle(g, mouseX, mouseY);
        renderTabs(g, mouseX, mouseY);

        g.blit(CHECKER, code.x, code.y, 0, 0, code.w, code.h, 2, 2);
        bevel(g, code, 0xFF222222, 0xFF4E4E4E);
        editor().render(g, mouseX, mouseY, nameDraft == null);

        boolean consoleHot = drag == Drag.CONSOLE || grow(consoleDivider, 1).contains(mouseX, mouseY);
        g.fill(consoleDivider.x, consoleDivider.y, consoleDivider.right(), consoleDivider.bottom(), consoleHot ? BRASS_DARK : 0xFF1A1A1A);
        renderConsole(g, mouseX, mouseY);

        if (panel() != null) {
            boolean sideHot = drag == Drag.SIDE || grow(sideDivider, 1).contains(mouseX, mouseY);
            g.fill(sideDivider.x, sideDivider.y, sideDivider.right(), sideDivider.bottom(), sideHot ? BRASS_DARK : PLANK_EDGE);
            renderSidePanel(g, mouseX, mouseY);
        }
        renderActivityBar(g, mouseX, mouseY);
        renderToolbar(g, mouseX, mouseY);

        renderSignature(g);
        if (completionOpen())
            renderCompletion(g, mouseX, mouseY);
        updateCursorShape(mouseX, mouseY);
    }

    private static final int SIGNATURE_W = 220;

    /** Inside a call's parentheses: what the function takes and does, above the line being written. */
    private void renderSignature(GuiGraphics g) {
        CodeEditor editor = editor();
        if (editor == null || nameDraft != null)
            return;
        var help = Completions.signature(editor.textBeforeCursor(), editor.getText(), snapshot.devices());
        if (help == null)
            return;
        List<FormattedCharSequence> sig = font.split(Component.literal(help.detail()), SIGNATURE_W - 8);
        List<FormattedCharSequence> doc = help.doc().isEmpty() ? List.of() : font.split(Component.literal(help.doc()), SIGNATURE_W - 8);
        int h = 4 + sig.size() * 9 + (doc.isEmpty() ? 0 : 2 + doc.size() * 9) + 2;
        int[] at = editor.cursorScreenPos();
        int x = Mth.clamp(at[0] - 12, 2, width - SIGNATURE_W - 2);
        int y = at[1] - h - 2;
        if (y < 2)
            y = at[1] + CodeEditor.LINE + 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 390);
        g.fill(x - 1, y - 1, x + SIGNATURE_W + 1, y + h + 1, 0xFF454545);
        g.fill(x, y, x + SIGNATURE_W, y + h, 0xFF252526);
        int ty = y + 3;
        for (FormattedCharSequence line : sig) {
            g.drawString(font, line, x + 4, ty, 0xFFDCDCAA, false);
            ty += 9;
        }
        if (!doc.isEmpty()) {
            ty += 2;
            for (FormattedCharSequence line : doc) {
                g.drawString(font, line, x + 4, ty, 0xFFC8C8C8, false);
                ty += 9;
            }
        }
        g.pose().popPose();
    }

    private Rect closeButton() {
        return new Rect(title.right() - 12, title.y + 1, 10, 10);
    }

    private Rect maximizeButton() {
        return new Rect(title.right() - 24, title.y + 1, 10, 10);
    }

    private void renderTitle(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(title.x, title.y, title.right(), title.bottom(), BRASS);
        g.fill(title.x, title.y, title.right(), title.y + 1, BRASS_LIGHT);
        g.fill(title.x, title.bottom() - 1, title.right(), title.bottom(), BRASS_DARK);
        String text = I18n.get("block.create_new_era.brass_computer") + " — " + active;
        g.drawString(font, ellipsize(text, title.w - 34), title.x + 5, title.y + 2, BRASS_TEXT, false);

        Rect max = maximizeButton(), close = closeButton();
        if (max.contains(mouseX, mouseY))
            g.fill(max.x, max.y, max.right(), max.bottom(), BRASS_DARK);
        int c = BRASS_TEXT;
        g.fill(max.x + 2, max.y + 2, max.x + 8, max.y + 3, c);
        g.fill(max.x + 2, max.y + 2, max.x + 3, max.y + 8, c);
        g.fill(max.x + 7, max.y + 2, max.x + 8, max.y + 8, c);
        g.fill(max.x + 2, max.y + 7, max.x + 8, max.y + 8, c);
        if (close.contains(mouseX, mouseY))
            g.fill(close.x, close.y, close.right(), close.bottom(), 0xFFC0402A);
        drawCross(g, close.x + 1, close.y + 1, close.contains(mouseX, mouseY) ? 0xFFFFFFFF : c);
    }

    private void renderConsole(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(console.x, console.y, console.right(), console.bottom(), 0xFF262626);
        bevel(g, console, 0xFF161616, 0xFF3A3A3A);
        List<ConsoleLine> lines = snapshot.console();
        int rows = consoleRowsVisible();
        int end = lines.size() - consoleScroll, start = Math.max(0, end - rows);
        consoleRows.clear();
        g.enableScissor(console.x + 2, console.y + 2, console.right() - 2, console.bottom() - 2);
        for (int i = start; i < end; i++) {
            ConsoleLine line = lines.get(i);
            int row = i - start, y = console.y + 3 + row * 9;
            consoleRows.put(row, line);
            // system lines carry a lang key, so each player reads them in their own language
            String text = line.kind() == ConsoleLine.Kind.SYSTEM ? "> " + I18n.get(line.text()) : line.text();
            int color = switch (line.kind()) {
                case OUTPUT -> 0xFFC8C8C8;
                case ERROR -> 0xFFFF6A4A;
                case SYSTEM -> 0xFF8F8F8F;
            };
            boolean link = line.kind() == ConsoleLine.Kind.ERROR && ERROR_AT.matcher(line.text()).find()
                    && new Rect(console.x, y, console.w, 9).contains(mouseX, mouseY);
            g.drawString(font, text, console.x + 5, y, color, false);
            if (link)
                g.fill(console.x + 5, y + 8, console.x + 5 + font.width(text), y + 9, color);
        }
        g.disableScissor();
        if (lines.isEmpty())
            g.drawString(font, I18n.get("create_new_era.editor.console_empty"), console.x + 5, console.y + 3, 0xFF5A5A5A, false);
    }

    private int consoleRowsVisible() {
        return Math.max(1, (console.h - 4) / 9);
    }

    private Rect runButton() {
        return new Rect(toolbar.x + 3, toolbar.y + 2, 12, 12);
    }

    private Rect stopButton() {
        return new Rect(toolbar.x + 17, toolbar.y + 2, 12, 12);
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(toolbar.x, toolbar.y, toolbar.right(), toolbar.bottom(), STONE);
        bevel(g, toolbar, STONE_LIGHT, STONE_DARK);

        Rect run = runButton(), stop = stopButton();
        button(g, run, run.contains(mouseX, mouseY));
        for (int i = 0; i < 4; i++)
            g.fill(run.x + 4 + i, run.y + 2 + i, run.x + 5 + i, run.y + 10 - i, 0xFF4FB33A);
        button(g, stop, stop.contains(mouseX, mouseY));
        g.fill(stop.x + 3, stop.y + 3, stop.x + 9, stop.y + 9, 0xFFD8402A);

        boolean running = snapshot.running();
        // a running program with no rotation is paused, not stopped: amber, with the reason
        boolean paused = running && (snapshot.rpm() == 0 || snapshot.overstressed());
        int x = stop.right() + 6;
        g.fill(x, toolbar.y + 6, x + 4, toolbar.y + 10, paused ? 0xFFE0A030 : running ? 0xFF4FB33A : 0xFF7A7A7A);
        String state;
        if (paused)
            state = I18n.get(snapshot.overstressed() ? "create_new_era.editor.paused_stress" : "create_new_era.editor.paused_rotation");
        else if (running)
            state = I18n.get("create_new_era.editor.running") + ": " + entry + " · " + I18n.get("create_new_era.editor.rpm", Math.abs(snapshot.rpm()));
        else
            state = I18n.get("create_new_era.editor.stopped");
        if (status != null && Util.getMillis() - statusAt < 1500 && panel() != Panel.FILES)
            state = status;
        g.drawString(font, state, x + 7, toolbar.y + 4, 0xFF2A2A2A, false);

        CodeEditor editor = editor();
        String where = I18n.get("create_new_era.editor.line_col", editor.getRow() + 1, editor.getCol() + 1);
        g.drawString(font, where, toolbar.right() - 12 - font.width(where), toolbar.y + 4, 0xFF4A4A4A, false);

        // resize grip
        if (!layout.maximized)
            for (int i = 0; i < 3; i++)
                for (int j = 0; j <= i; j++)
                    g.fill(toolbar.right() - 3 - j * 2, toolbar.bottom() - 7 + i * 2, toolbar.right() - 2 - j * 2, toolbar.bottom() - 6 + i * 2, 0xFF7A7A7A);

        if (run.contains(mouseX, mouseY))
            g.renderTooltip(font, Component.translatable("create_new_era.editor.run"), mouseX, mouseY);
        else if (stop.contains(mouseX, mouseY))
            g.renderTooltip(font, Component.translatable("create_new_era.editor.stop"), mouseX, mouseY);
    }

    private void button(GuiGraphics g, Rect r, boolean hovered) {
        g.fill(r.x, r.y, r.right(), r.bottom(), hovered ? 0xFF9B9B9B : 0xFF8B8B8B);
        bevel(g, r, 0xFFB5B5B5, 0xFF4A4A4A);
    }

    private void label(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x + 1, y + 1, LABEL_SHADOW, false);
        g.drawString(font, text, x, y, LABEL, false);
    }

    private static void drawCross(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + 1 + i, y + 1 + i, x + 2 + i, y + 2 + i, color);
            g.fill(x + 6 - i, y + 1 + i, x + 7 - i, y + 2 + i, color);
        }
    }

    private static void drawPencil(GuiGraphics g, int x, int y) {
        for (int i = 0; i < 5; i++)
            g.fill(x + 2 + i, y + 6 - i, x + 4 + i, y + 7 - i, LABEL_DIM);
        g.fill(x + 1, y + 7, x + 2, y + 8, LABEL_DIM);
    }

    private static void bevel(GuiGraphics g, Rect r, int light, int dark) {
        g.fill(r.x, r.y, r.right(), r.y + 1, light);
        g.fill(r.x, r.y, r.x + 1, r.bottom(), light);
        g.fill(r.x, r.bottom() - 1, r.right(), r.bottom(), dark);
        g.fill(r.right() - 1, r.y, r.right(), r.bottom(), dark);
    }

    private String ellipsize(String text, int maxWidth) {
        if (font.width(text) <= maxWidth)
            return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private String ellipsizeStart(String text, int maxWidth) {
        while (font.width(text) > maxWidth && !text.isEmpty())
            text = text.substring(1);
        return text;
    }

    // ---- mouse cursor shape ----

    private static long hResize, vResize, hand, crosshair;

    private void updateCursorShape(int mouseX, int mouseY) {
        if (hResize == 0) {
            hResize = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);
            vResize = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
            hand = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
            crosshair = GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR);
        }
        int edges = drag == Drag.RESIZE ? resizeEdges : drag == Drag.NONE ? edgesAt(mouseX, mouseY) : 0;
        long shape = 0;
        if (drag == Drag.SIDE || (drag == Drag.NONE && grow(sideDivider, 1).contains(mouseX, mouseY)) || edges == 1 || edges == 2)
            shape = hResize;
        else if (drag == Drag.CONSOLE || (drag == Drag.NONE && grow(consoleDivider, 1).contains(mouseX, mouseY)) || edges == 4 || edges == 8)
            shape = vResize;
        else if (edges != 0)
            shape = crosshair;
        else if (drag == Drag.NONE && (activity.contains(mouseX, mouseY) || runButton().contains(mouseX, mouseY)
                || stopButton().contains(mouseX, mouseY) || tabs.contains(mouseX, mouseY)))
            shape = hand;
        setCursorShape(shape);
    }

    private void setCursorShape(long shape) {
        if (shape != cursorShape) {
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), shape);
            cursorShape = shape;
        }
    }

    // ---- errors ----

    /** Marks the line of the latest error since the last start in whichever open file it points at. */
    private void updateErrorLines() {
        String file = null;
        int line = -1;
        for (ConsoleLine l : snapshot.console()) {
            if (l.kind() == ConsoleLine.Kind.SYSTEM) {
                file = null;
                line = -1;
            } else if (l.kind() == ConsoleLine.Kind.ERROR) {
                Matcher m = ERROR_AT.matcher(l.text());
                if (m.find()) {
                    file = m.group(1);
                    line = Integer.parseInt(m.group(2));
                }
            }
        }
        for (var e : open.entrySet())
            e.getValue().setErrorLine(e.getKey().equals(file) ? line : -1);
    }

    private void jumpToError(String text) {
        Matcher m = ERROR_AT.matcher(text);
        if (!m.find() || !fileOrder.contains(m.group(1)))
            return;
        openFile(m.group(1));
        editor().goToLine(Integer.parseInt(m.group(2)));
    }
}
