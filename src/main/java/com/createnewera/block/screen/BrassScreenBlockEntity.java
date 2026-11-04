package com.createnewera.block.screen;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Brass Screen. The top-left screen of a pane (ScreenPane) holds the whole pane's flip dots,
 * a palette colour each; the others only point at it. Programs lay markup out on it, or draw
 * on it, on the server; once a tick, if anything changed, the dots go to the players nearby,
 * compressed, and each player's screen turns them over (BrassScreenRenderer).
 */
public class BrassScreenBlockEntity extends BlockEntity {
    /** The pane's dots, palette colours (ScreenPalette), or null before anything was shown */
    private byte @Nullable [] dots;
    private int width, height;
    private boolean dirty;
    /** Whether the last change was a layout: the dots turn in a ripple, not all at once */
    private boolean ripple;

    /** The markup shown, if any, and the values the program has given its elements since */
    private @Nullable String markup;
    /** The file the markup came from and the computer it's on, so saving the file shows it again */
    private @Nullable String markupFile;
    private @Nullable BlockPos markupComputer;
    private final Map<String, String> values = new LinkedHashMap<>();
    private List<ScreenMarkup.Button> buttons = List.of();

    private int touchX, touchY;
    private String touchedBy = "", clicked = "", clickedBy = "";
    private long touches, clicks;

    /** Client only: bumped when new dots arrive, so the renderer starts turning them */
    public int version;

    public BrassScreenBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The dots to draw on, sized to the pane now, kept as they were where the pane still covers them. */
    public ScreenCanvas canvas(ScreenPane pane) {
        int w = pane.width(), h = pane.height();
        if (dots == null || width != w || height != h) {
            byte[] resized = new byte[w * h];
            if (dots != null)
                for (int y = 0; y < Math.min(h, height); y++)
                    System.arraycopy(dots, y * width, resized, y * w, Math.min(w, width));
            dots = resized;
            width = w;
            height = h;
            if (markup != null)
                redraw(new ScreenCanvas(dots, width, height));
            dirty = true;
        }
        return new ScreenCanvas(dots, width, height);
    }

    public @Nullable ScreenCanvas shown() {
        return dots == null ? null : new ScreenCanvas(dots, width, height);
    }

    /** A program drew straight on the dots: they turn at once. */
    public void drawn() {
        dirty = true;
        ripple = false;
    }

    /** Lays new markup out over the whole pane; from a computer's file if {@code file} isn't null. */
    public void show(ScreenPane pane, String source, @Nullable String file, @Nullable BlockPos computer) throws ScreenMarkup.MarkupException {
        ScreenMarkup.parse(source); // a mistake throws before anything changes
        markup = source;
        markupFile = file;
        markupComputer = computer == null ? null : computer.immutable();
        values.clear();
        relayout(pane);
        if (file != null)
            SHOWING_FILES.add(this);
    }

    /** Screens showing a computer's file, so saving it in the editor shows it again */
    private static final java.util.Set<BrassScreenBlockEntity> SHOWING_FILES =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /**
     * A computer's file was saved: every screen showing it lays the new markup out, keeping
     * the values its program set. @return what's wrong with the markup, if anything
     */
    public static @Nullable String fileSaved(Level level, BlockPos computer, String file, String text) {
        String problem = null;
        for (BrassScreenBlockEntity screen : java.util.List.copyOf(SHOWING_FILES)) {
            if (screen.isRemoved() || screen.level != level || !file.equals(screen.markupFile) || !computer.equals(screen.markupComputer))
                continue;
            ScreenPane pane = ScreenPane.of(level, screen.worldPosition);
            if (pane == null || !pane.origin().equals(screen.worldPosition))
                continue;
            try {
                ScreenMarkup.parse(text);
                screen.markup = text;
                screen.relayout(pane);
            } catch (ScreenMarkup.MarkupException e) {
                problem = e.getMessage();
            }
        }
        return problem;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && markupFile != null)
            SHOWING_FILES.add(this);
    }

    /** Changes one element of the markup shown, by id, and lays it out again. */
    public void set(ScreenPane pane, String id, String value) throws ScreenMarkup.MarkupException {
        if (markup == null)
            throw new ScreenMarkup.MarkupException("nothing is shown: show some markup first");
        if (ScreenMarkup.byId(ScreenMarkup.parse(markup), id) == null)
            throw new ScreenMarkup.MarkupException("no element with id \"" + id + "\" in what's shown");
        values.put(id, value);
        relayout(pane);
    }

    /** What an element of the markup shown says now: its text, or a bar's value. */
    public @Nullable String get(String id) throws ScreenMarkup.MarkupException {
        if (markup == null)
            return null;
        Element e = ScreenMarkup.byId(element(), id);
        if (e == null)
            return null;
        return e.getTagName().equals("bar") ? e.getAttribute("value") : e.getTextContent().strip();
    }

    /** Forgets the markup: what a program draws next is all there is. */
    public void forgetMarkup() {
        markup = null;
        markupFile = null;
        markupComputer = null;
        values.clear();
        buttons = List.of();
    }

    private void relayout(ScreenPane pane) {
        redraw(canvas(pane));
        dirty = true;
        ripple = true;
    }

    private void redraw(ScreenCanvas canvas) {
        try {
            buttons = ScreenMarkup.draw(element(), canvas);
        } catch (ScreenMarkup.MarkupException e) {
            buttons = List.of();
        }
    }

    /** The markup with the program's values put in. */
    private Element element() throws ScreenMarkup.MarkupException {
        Element root = ScreenMarkup.parse(markup);
        for (var value : values.entrySet()) {
            Element e = ScreenMarkup.byId(root, value.getKey());
            if (e != null)
                ScreenMarkup.set(e, value.getValue());
        }
        return root;
    }

    /** A player pressed the glass at this dot: a touch, and a click if it's on a button. */
    public void touched(int x, int y, String player) {
        touchX = x;
        touchY = y;
        touchedBy = player;
        touches++;
        if (markup != null && buttons.isEmpty() && dots != null)
            redraw(new ScreenCanvas(dots, width, height)); // after a load: find the buttons again
        for (ScreenMarkup.Button button : buttons)
            if (button.contains(x, y)) {
                clicked = button.id();
                clickedBy = player;
                clicks++;
            }
        setChanged();
    }

    public int touchX() {
        return touchX;
    }

    public int touchY() {
        return touchY;
    }

    public String touchedBy() {
        return touchedBy;
    }

    public long touches() {
        return touches;
    }

    public String clicked() {
        return clicked;
    }

    public String clickedBy() {
        return clickedBy;
    }

    public long clicks() {
        return clicks;
    }

    /** Client: whether the dots that just arrived turn in a ripple */
    public boolean ripples() {
        return ripple;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BrassScreenBlockEntity screen) {
        if (!screen.dirty)
            return;
        screen.dirty = false;
        screen.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /** Client: lets go of the textures a pane drew with when a screen goes (BrassScreenRenderer plugs it in). */
    public static Consumer<BlockPos> clientRemoved = pos -> {};

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide)
            clientRemoved.accept(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeDots(tag);
        if (markup != null) {
            tag.putString("Markup", markup);
            if (markupFile != null && markupComputer != null) {
                tag.putString("MarkupFile", markupFile);
                tag.putLong("MarkupComputer", markupComputer.asLong());
            }
            CompoundTag set = new CompoundTag();
            values.forEach(set::putString);
            tag.put("Values", set);
        }
        tag.putLong("Touches", touches);
        tag.putInt("TouchX", touchX);
        tag.putInt("TouchY", touchY);
        tag.putString("TouchedBy", touchedBy);
        tag.putLong("Clicks", clicks);
        tag.putString("Clicked", clicked);
        tag.putString("ClickedBy", clickedBy);
    }

    private void writeDots(CompoundTag tag) {
        if (dots == null)
            return;
        tag.putInt("Width", width);
        tag.putInt("Height", height);
        tag.putByteArray("Dots", pack(dots));
        tag.putBoolean("Ripple", ripple);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Dots")) {
            int w = tag.getInt("Width"), h = tag.getInt("Height");
            byte[] read = unpack(tag.getByteArray("Dots"), w * h);
            if (read != null) {
                dots = read;
                width = w;
                height = h;
            }
            ripple = tag.getBoolean("Ripple");
        } else {
            dots = null;
        }
        markup = tag.contains("Markup") ? tag.getString("Markup") : null;
        markupFile = tag.contains("MarkupFile") ? tag.getString("MarkupFile") : null;
        markupComputer = tag.contains("MarkupComputer") ? BlockPos.of(tag.getLong("MarkupComputer")) : null;
        values.clear();
        CompoundTag set = tag.getCompound("Values");
        for (String key : set.getAllKeys())
            values.put(key, set.getString(key));
        buttons = List.of();
        touches = tag.getLong("Touches");
        touchX = tag.getInt("TouchX");
        touchY = tag.getInt("TouchY");
        touchedBy = tag.getString("TouchedBy");
        clicks = tag.getLong("Clicks");
        clicked = tag.getString("Clicked");
        clickedBy = tag.getString("ClickedBy");
        version++;
    }

    /** Players only need the dots, not the markup behind them. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeDots(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Deflated: a screen is mostly runs of the same colour, so it packs small. */
    private static byte[] pack(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 8 + 64);
        byte[] chunk = new byte[8192];
        while (!deflater.finished())
            out.write(chunk, 0, deflater.deflate(chunk));
        deflater.end();
        return out.toByteArray();
    }

    private static byte @Nullable [] unpack(byte[] packed, int count) {
        byte[] raw = new byte[count];
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        try {
            int read = 0;
            while (read < raw.length && !inflater.finished()) {
                int n = inflater.inflate(raw, read, raw.length - read);
                if (n == 0 && inflater.needsInput())
                    break;
                read += n;
            }
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
        return raw;
    }
}
