package com.createnewera.client.editor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lwjgl.glfw.GLFW;

import com.createnewera.CreateNewEra;
import com.createnewera.computer.BrassComputerBlockEntity;
import com.createnewera.computer.ComputerSide;
import com.createnewera.computer.ConsoleLine;
import com.createnewera.network.ComputerSnapshot;
import com.createnewera.network.EditorActionPayload;
import com.createnewera.network.EditorActionPayload.Action;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The brass computer's editor, after the Terminal artboard in the design canvas: brass bar on
 * top, code on a dark checkered plate, console underneath, a plank side panel with the
 * redstone on each side, and the toolbar along the bottom.
 */
public class ComputerEditorScreen extends Screen {
    private static final ResourceLocation CHECKER = CreateNewEra.asResource("textures/gui/editor_checker.png");
    private static final Pattern ERROR_LINE = Pattern.compile("program:(\\d+):");

    private static final int BAR = 4, TOOLBAR = 18, SIDE = 86, CONSOLE = 42;
    private static final int BRASS = 0xFFE8B75A, BRASS_LIGHT = 0xFFFBDC93, BRASS_DARK = 0xFFA1712C;
    private static final int PLANK = 0xFF6B4429, PLANK_GAP = 0xFF5C3A22, PLANK_EDGE = 0xFF3D2614;
    private static final int LABEL = 0xFFF3E6C4, LABEL_SHADOW = 0xFF2A1A0D;
    private static final int STONE = 0xFFC6C6C6, STONE_LIGHT = 0xFFFFFFFF, STONE_DARK = 0xFF555555;
    private static final int REDSTONE = 0xFFE0402A;

    private final BlockPos pos;
    private final CodeEditor editor;
    private ComputerSnapshot snapshot;
    private int consoleScroll;
    private long lastChange = -1;
    private long savedFlash = -1;

    private int left, top, w, h;

    public ComputerEditorScreen(BlockPos pos, String source, ComputerSnapshot snapshot) {
        super(Component.translatable("block.create_new_era.brass_computer"));
        this.pos = pos;
        this.snapshot = snapshot;
        this.editor = new CodeEditor(net.minecraft.client.Minecraft.getInstance().font, BrassComputerBlockEntity.MAX_SOURCE);
        editor.setText(source);
        editor.setOnChange(() -> lastChange = Util.getMillis());
        updateErrorLine();
    }

    public BlockPos getPos() {
        return pos;
    }

    public void update(ComputerSnapshot snapshot) {
        boolean grew = snapshot.console().size() != this.snapshot.console().size();
        this.snapshot = snapshot;
        if (grew)
            consoleScroll = 0;
        updateErrorLine();
    }

    @Override
    protected void init() {
        w = Math.min(352, width - 8);
        h = Math.min(200, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        int bodyTop = top + BAR;
        int bodyH = h - BAR - TOOLBAR;
        editor.setBounds(left + 1, bodyTop + 1, w - SIDE - 2, bodyH - CONSOLE - 2);
    }

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
        if (lastChange > 0 && Util.getMillis() - lastChange > 2000) {
            send(Action.SAVE);
            lastChange = -1;
        }
    }

    @Override
    public void onClose() {
        send(Action.CLOSE);
        super.onClose();
    }

    private void send(Action action) {
        PacketDistributor.sendToServer(new EditorActionPayload(pos, action, editor.getText()));
    }

    private void run() {
        send(Action.RUN);
        lastChange = -1;
    }

    // ---- input ----

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (hasControlDown()) {
            if (key == GLFW.GLFW_KEY_R) {
                run();
                return true;
            }
            if (key == GLFW.GLFW_KEY_S) {
                send(Action.SAVE);
                lastChange = -1;
                savedFlash = Util.getMillis();
                return true;
            }
            if (key == GLFW.GLFW_KEY_T) {
                send(Action.STOP);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE)
            return super.keyPressed(key, scan, modifiers);
        return editor.keyPressed(key, modifiers) || super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return editor.charTyped(ch);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int y = top + h - TOOLBAR + 2;
        if (button == 0 && inside(mx, my, left + 4, y, 14, 14)) {
            run();
            return true;
        }
        if (button == 0 && inside(mx, my, left + 21, y, 14, 14)) {
            send(Action.STOP);
            return true;
        }
        return editor.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return editor.mouseDragged(mx, my, button) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        editor.mouseReleased();
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (editor.mouseScrolled(mx, my, scrollY))
            return true;
        int consoleTop = top + h - TOOLBAR - CONSOLE;
        if (inside(mx, my, left, consoleTop, w - SIDE, CONSOLE)) {
            consoleScroll = Mth.clamp(consoleScroll + (int) Math.signum(scrollY),
                    0, Math.max(0, snapshot.console().size() - consoleRows()));
            return true;
        }
        return false;
    }

    private static boolean inside(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    // ---- rendering ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // outer dark rim
        g.fill(left - 1, top - 1, left + w + 1, top + h + 1, 0xFF120E0A);

        // brass bar
        g.fill(left, top, left + w, top + BAR, BRASS);
        g.fill(left, top, left + w, top + 1, BRASS_LIGHT);
        g.fill(left, top + BAR - 1, left + w, top + BAR, BRASS_DARK);

        int bodyTop = top + BAR, bodyBottom = top + h - TOOLBAR;
        int codeRight = left + w - SIDE;

        // code plate
        g.blit(CHECKER, left, bodyTop, 0, 0, codeRight - left, bodyBottom - CONSOLE - bodyTop, 2, 2);
        bevel(g, left, bodyTop, codeRight - left, bodyBottom - CONSOLE - bodyTop, 0xFF222222, 0xFF4E4E4E);
        editor.render(g, mouseX, mouseY, true);

        renderConsole(g, left, bodyBottom - CONSOLE, codeRight - left, CONSOLE);
        renderSidePanel(g, codeRight, bodyTop, SIDE, bodyBottom - bodyTop);
        renderToolbar(g, mouseX, mouseY, left, bodyBottom, w, TOOLBAR);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    private void renderConsole(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, 0xFF262626);
        bevel(g, x, y, width, height, 0xFF161616, 0xFF3A3A3A);
        List<ConsoleLine> lines = snapshot.console();
        int rows = consoleRows();
        int end = lines.size() - consoleScroll;
        int start = Math.max(0, end - rows);
        g.enableScissor(x + 2, y + 2, x + width - 2, y + height - 2);
        for (int i = start; i < end; i++) {
            ConsoleLine line = lines.get(i);
            // system lines carry a lang key, so each player reads them in their own language
            String text = line.kind() == ConsoleLine.Kind.SYSTEM ? "> " + I18n.get(line.text()) : line.text();
            int color = switch (line.kind()) {
                case OUTPUT -> 0xFFC8C8C8;
                case ERROR -> 0xFFFF6A4A;
                case SYSTEM -> 0xFF8F8F8F;
            };
            g.drawString(font, text, x + 5, y + 4 + (i - start) * 9, color, false);
        }
        g.disableScissor();
        if (lines.isEmpty())
            g.drawString(font, I18n.get("create_new_era.editor.console_empty"), x + 5, y + 4, 0xFF5A5A5A, false);
    }

    private int consoleRows() {
        return (CONSOLE - 6) / 9;
    }

    private void renderSidePanel(GuiGraphics g, int x, int y, int width, int height) {
        // planks, like the design's wood side panels
        for (int py = y; py < y + height; py += 8) {
            g.fill(x, py, x + width, Math.min(py + 7, y + height), PLANK);
            g.fill(x, Math.min(py + 7, y + height), x + width, Math.min(py + 8, y + height), PLANK_GAP);
        }
        g.fill(x, y, x + 1, y + height, PLANK_EDGE);

        label(g, I18n.get("create_new_era.editor.redstone"), x + 6, y + 6);
        g.drawString(font, I18n.get("create_new_era.editor.in"), x + 44, y + 18, 0xFFC9B083, false);
        g.drawString(font, I18n.get("create_new_era.editor.out"), x + 63, y + 18, 0xFFC9B083, false);
        for (ComputerSide side : ComputerSide.values()) {
            int rowY = y + 30 + side.ordinal() * 13;
            g.drawString(font, side.luaName(), x + 6, rowY + 1, LABEL, false);
            meter(g, x + 42, rowY, snapshot.inputs().get(side.ordinal()));
            meter(g, x + 62, rowY, snapshot.outputs().get(side.ordinal()));
        }
    }

    /** A recessed slot that glows redstone red with the signal strength. */
    private void meter(GuiGraphics g, int x, int y, int strength) {
        g.fill(x, y, x + 17, y + 10, 0xFF2A1A0D);
        g.fill(x, y, x + 17, y + 1, 0xFF1A0F07);
        if (strength > 0) {
            int alpha = 0x60 + strength * 0x9F / 15;
            g.fill(x + 1, y + 1, x + 16, y + 10, (alpha << 24) | (REDSTONE & 0xFFFFFF));
        }
        String s = Integer.toString(strength);
        g.drawString(font, s, x + 9 - font.width(s) / 2, y + 1, strength > 0 ? 0xFFFFFFFF : 0xFF6B4520, false);
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, STONE);
        bevel(g, x, y, width, height, STONE_LIGHT, STONE_DARK);

        int by = y + 2;
        button(g, x + 4, by, inside(mouseX, mouseY, x + 4, by, 14, 14), (bx, byy) -> {
            // play triangle, pixel steps like the design
            for (int i = 0; i < 4; i++)
                g.fill(bx + 5 + i, byy + 3 + i, bx + 6 + i, byy + 11 - i, 0xFF7BE05A);
        });
        button(g, x + 21, by, inside(mouseX, mouseY, x + 21, by, 14, 14), (bx, byy) ->
                g.fill(bx + 4, byy + 4, bx + 10, byy + 10, 0xFFFF6A4A));

        boolean running = snapshot.running();
        int dot = running ? 0xFF4FB33A : 0xFF6A6A6A;
        g.fill(x + 42, y + 7, x + 46, y + 11, dot);
        String status = I18n.get(running ? "create_new_era.editor.running" : "create_new_era.editor.stopped");
        if (savedFlash > 0 && Util.getMillis() - savedFlash < 1500)
            status = I18n.get("create_new_era.editor.saved");
        g.drawString(font, status, x + 50, y + 5, 0xFF2A2A2A, false);

        String hints = I18n.get("create_new_era.editor.hints");
        g.drawString(font, hints, x + width - 6 - font.width(hints), y + 5, 0xFF6A6A6A, false);

        if (inside(mouseX, mouseY, x + 4, by, 14, 14))
            g.renderTooltip(font, Component.translatable("create_new_era.editor.run"), mouseX, mouseY);
        else if (inside(mouseX, mouseY, x + 21, by, 14, 14))
            g.renderTooltip(font, Component.translatable("create_new_era.editor.stop"), mouseX, mouseY);
    }

    private interface Icon {
        void draw(int x, int y);
    }

    private void button(GuiGraphics g, int x, int y, boolean hovered, Icon icon) {
        g.fill(x, y, x + 14, y + 14, hovered ? 0xFF9B9B9B : 0xFF8B8B8B);
        bevel(g, x, y, 14, 14, 0xFFB5B5B5, 0xFF4A4A4A);
        icon.draw(x, y);
    }

    private void label(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x + 1, y + 1, LABEL_SHADOW, false);
        g.drawString(font, text, x, y, LABEL, false);
    }

    private static void bevel(GuiGraphics g, int x, int y, int width, int height, int light, int dark) {
        g.fill(x, y, x + width, y + 1, light);
        g.fill(x, y, x + 1, y + height, light);
        g.fill(x, y + height - 1, x + width, y + height, dark);
        g.fill(x + width - 1, y, x + width, y + height, dark);
    }

    /** Marks the line of the most recent error since the last start, if it came from the program. */
    private void updateErrorLine() {
        int line = -1;
        for (ConsoleLine l : snapshot.console()) {
            if (l.kind() == ConsoleLine.Kind.SYSTEM) {
                line = -1;
            } else if (l.kind() == ConsoleLine.Kind.ERROR) {
                Matcher m = ERROR_LINE.matcher(l.text());
                if (m.find())
                    line = Integer.parseInt(m.group(1));
            }
        }
        editor.setErrorLine(line);
    }
}
