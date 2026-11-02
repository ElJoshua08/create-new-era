package com.createnewera.client.control;


import org.jetbrains.annotations.Nullable;

import com.createnewera.block.control.PanelDialBlock;
import com.createnewera.block.control.PanelControlBlockEntity;
import com.createnewera.network.DialPayload;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Setting a knob or slider like Aeronautics' throttle lever: hold use on it and drag the mouse
 * sideways. The camera stays put; the control clicks from notch to notch, no faster than a set
 * speed however fast the mouse goes, and shows where it is under the crosshair: a ring of
 * notches for the knob, the throttle lever's bar for the slider.
 */
public final class DialDrag {
    /** Mouse pixels to go across its whole range */
    private static final double FULL_SWEEP = 420;
    /** However fast the mouse goes, the control moves at most this much of its range a second */
    private static final double MAX_SPEED = 1.1;
    /** With Ctrl held, how much of the usual distance and speed the mouse moves it */
    private static final double FINE = 0.15;
    /** The most marks the ring and bar draw: a range finer than this gets as many as fit */
    private static final int MAX_MARKS = 31;
    /** How far past a notch's edge the mouse has to go before it clicks over to the next one, in steps */
    private static final float DETENT = 0.2f;

    private static @Nullable BlockPos pos;
    /** where the mouse has taken it, 0 to 1, between notches */
    private static float raw;
    /** the notch it rests in, 0 to {@link #notches} */
    private static int step;
    /** its range's last notch: 15 unless a program set a range */
    private static int notches = 15;
    /** the value at each notch, for the label */
    private static @Nullable PanelControlBlockEntity dialAt;
    private static long lastClick;
    private static double lastX;
    private static long lastFrame;

    public static void register(IEventBus gameBus) {
        gameBus.addListener(DialDrag::grab);
        gameBus.addListener(DialDrag::holdCamera);
        gameBus.addListener(DialDrag::follow);
        gameBus.addListener(DialDrag::tick);
        gameBus.addListener(DialDrag::drawBar);
    }

    private static void grab(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || mc.level == null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return;
        if (pos != null) { // already dragging: a repeat of the held key
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        var state = mc.level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof PanelDialBlock) || holdingTool(mc))
            return;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof PanelControlBlockEntity dial))
            return;
        pos = hit.getBlockPos().immutable();
        dialAt = dial;
        notches = dial.notches();
        step = dial.position();
        raw = (float) step / notches;
        lastX = mc.mouseHandler.xpos();
        lastFrame = System.nanoTime();
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /** A cable, a port or a wrench in hand is for wiring the control, not turning it. */
    private static boolean holdingTool(Minecraft mc) {
        var held = mc.player == null ? net.minecraft.world.item.ItemStack.EMPTY : mc.player.getMainHandItem();
        return held.getItem() instanceof com.createnewera.cable.CableSpoolItem || held.getItem() instanceof com.createnewera.bus.BusPartItem
                || AllItems.WRENCH.isIn(held);
    }

    /** While dragging, the mouse moves the control, not the view. */
    private static void holdCamera(CalculatePlayerTurnEvent event) {
        if (pos != null)
            event.setMouseSensitivity(-1 / 3.0); // the game's sensitivity curve gives no turn at all there
    }

    private static void follow(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc.level == null)
            return;
        double x = mc.mouseHandler.xpos();
        long now = System.nanoTime();
        // Ctrl for fine control: the same mouse moves it a fraction as far, and slower
        double scale = net.minecraft.client.gui.screens.Screen.hasControlDown() ? FINE : 1;
        double most = MAX_SPEED * scale * (now - lastFrame) / 1e9;
        raw = Mth.clamp(raw + (float) Mth.clamp((x - lastX) / FULL_SWEEP * scale, -most, most), 0, 1);
        lastX = x;
        lastFrame = now;
        // it sits in a notch until the mouse has gone a little past its edge, like a geared dial
        float at = raw * notches;
        int notch = step;
        if (at > step + 0.5f + DETENT)
            notch = Math.min(notches, Math.round(at));
        else if (at < step - 0.5f - DETENT)
            notch = Math.max(0, Math.round(at));
        if (notch != step) {
            step = notch;
            PacketDistributor.sendToServer(new DialPayload(pos, step));
            // a fine range would rattle: one click at most every few frames
            long ms = System.currentTimeMillis();
            if (ms - lastClick > 45) {
                lastClick = ms;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.6f + 0.5f * step / notches, 0.25f));
            }
        }
        if (mc.level.getBlockEntity(pos) instanceof PanelControlBlockEntity dial)
            dial.dragged = (float) step / notches;
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null)
            return;
        boolean gone = mc.level == null || mc.player == null || !(mc.level.getBlockState(pos).getBlock() instanceof PanelDialBlock)
                || !mc.player.canInteractWithBlock(pos, 1) || mc.screen != null;
        if (gone || !mc.options.keyUse.isDown())
            release();
    }

    private static void release() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && pos != null && mc.level.getBlockEntity(pos) instanceof PanelControlBlockEntity dial)
            dial.dragged = -1;
        pos = null;
        dialAt = null;
    }

    private static void drawBar(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc.level == null || !(mc.level.getBlockState(pos).getBlock() instanceof PanelDialBlock dial))
            return;
        GuiGraphics g = event.getGuiGraphics();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        if (dial.style() == PanelDialBlock.Style.KNOB)
            drawRing(g, mc, cx, cy);
        else
            drawThrottle(g, mc, cx, cy);

        // the bar or ring already says the value: just how to use it, below
        Component hint = Component.translatable("create_new_era.dial.hint");
        g.drawString(mc.font, hint, cx - mc.font.width(hint) / 2, cy + 52, 0xFFFFFFFF, true);
    }

    /** The value at the notch it's at, as a program would read it: 7, 0.35, -12.5 */
    private static String label() {
        double v = dialAt == null ? step : dialAt.valueAt(step);
        if (v == Math.rint(v))
            return String.valueOf((long) v);
        String s = String.format(java.util.Locale.ROOT, "%.3f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** The cursor with the number in it, centred on x: Create's value-settings cursor. */
    private static void cursor(GuiGraphics g, Minecraft mc, int x, int y) {
        AllGuiTextures left = AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT, right = AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT;
        int w = Math.max(14, mc.font.width(label()) + 4);
        int x0 = x - w / 2;
        left.render(g, x0 - left.getWidth(), y);
        UIRenderHelper.drawCropped(g, x0, y, w, 14, 0, AllGuiTextures.VALUE_SETTINGS_CURSOR);
        right.render(g, x0 + w, y);
        String label = label();
        g.drawString(mc.font, label, x - mc.font.width(label) / 2 + 1, y + 3, CURSOR_TEXT, false);
    }

    /** The colour of the number on the cursor: the brown Aeronautics uses */
    private static final int CURSOR_TEXT = 0xFF5B3A1D;

    /**
     * The slider: Aeronautics' throttle lever bar lying down under the crosshair: a brass frame
     * round Create's value bar, filled to where the handle is, the cursor riding along it.
     */
    private static void drawThrottle(GuiGraphics g, Minecraft mc, int cx, int cy) {
        int w = 104, h = 14;
        int x = cx - w / 2, y = cy + 16;
        brassFrame(g, x, y, w, h);
        // the value bar fills the frame's inside: Create's texture is shorter, so it repeats
        int length = w - 6;
        int bar = AllGuiTextures.VALUE_SETTINGS_BAR.getWidth();
        for (int done = 0; done < length; done += bar)
            UIRenderHelper.drawCropped(g, x + 3 + done, y + 3, Math.min(bar, length - done), 8, 0, AllGuiTextures.VALUE_SETTINGS_BAR);
        // a tick under the bar for each notch, or evenly spread ones for a fine range
        int marks = Math.min(notches, MAX_MARKS - 1);
        for (int i = 0; i <= marks; i++) {
            int tx = x + 3 + 7 + Math.round((length - 14) * ((float) i / marks));
            g.fill(tx, y + h + 1, tx + 1, y + h + (i == 0 || i == marks ? 4 : 3), 0xFF3A2411);
        }
        int at = x + 3 + 7 + Math.round((length - 14) * ((float) step / notches));
        cursor(g, mc, at, y);
    }

    /** The knob's dial: an arc, open at the bottom, a slider bent round the crosshair */
    private static final int ARC_RADIUS = 24;
    private static final int ARC_EDGE = 0xFF2A1A0C, ARC_EMPTY = 0xFF5A4030, ARC_LIT = 0xFFE4B763;
    /** Up to this many notches get a mark each round the arc; finer ranges read as one smooth sweep */
    private static final int ARC_MARKS = 24;

    /** Where on the arc a fraction of the range sits: 0 at the bottom left, 1 at the bottom right. */
    private static int[] onArc(int cx, int cy, double fraction, double radius) {
        double a = Math.toRadians(-135 + 270 * fraction);
        return new int[] {cx + (int) Math.round(radius * Math.sin(a)), cy - (int) Math.round(radius * Math.cos(a))};
    }

    /**
     * The knob: a track bent into an arc round the crosshair, lit brass up to where it is, like
     * the slider's bar. A range of a few notches marks each one outside the arc, so two or
     * three positions read as positions; thousands read as one sweep. The value sits in the gap.
     */
    private static void drawRing(GuiGraphics g, Minecraft mc, int cx, int cy) {
        float at = (float) step / notches;
        int samples = 360;
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i <= samples; i++) {
                float f = (float) i / samples;
                int[] p = onArc(cx, cy, f, ARC_RADIUS);
                if (pass == 0)
                    g.fill(p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3, ARC_EDGE);
                else
                    g.fill(p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2, f <= at + 1e-4 ? ARC_LIT : ARC_EMPTY);
            }
        if (notches <= ARC_MARKS) {
            int size = notches <= 4 ? 2 : 1; // two or three positions get bigger marks
            for (int i = 0; i <= notches; i++) {
                int[] p = onArc(cx, cy, (double) i / notches, ARC_RADIUS + 6 + size);
                g.fill(p[0] - size - 1, p[1] - size - 1, p[0] + size + 1, p[1] + size + 1, ARC_EDGE);
                g.fill(p[0] - size, p[1] - size, p[0] + size, p[1] + size, i <= step ? ARC_LIT : ARC_EMPTY);
            }
        }
        // where it is: a bright stud on the track
        int[] p = onArc(cx, cy, at, ARC_RADIUS);
        g.fill(p[0] - 4, p[1] - 4, p[0] + 4, p[1] + 4, ARC_EDGE);
        g.fill(p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3, 0xFFFFF0C0);
        cursor(g, mc, cx, cy + ARC_RADIUS - 4);
    }

    private static void brassFrame(GuiGraphics g, int x, int y, int w, int h) {
        AllGuiTextures tl = AllGuiTextures.BRASS_FRAME_TL;
        int cw = tl.getWidth(), ch = tl.getHeight();
        UIRenderHelper.drawStretched(g, x, y + ch, cw, h - 2 * ch, 0, AllGuiTextures.BRASS_FRAME_LEFT);
        UIRenderHelper.drawStretched(g, x + w - cw, y + ch, cw, h - 2 * ch, 0, AllGuiTextures.BRASS_FRAME_RIGHT);
        UIRenderHelper.drawCropped(g, x + cw, y, w - 2 * cw, ch, 0, AllGuiTextures.BRASS_FRAME_TOP);
        UIRenderHelper.drawCropped(g, x + cw, y + h - ch, w - 2 * cw, ch, 0, AllGuiTextures.BRASS_FRAME_BOTTOM);
        tl.render(g, x, y);
        AllGuiTextures.BRASS_FRAME_TR.render(g, x + w - cw, y);
        AllGuiTextures.BRASS_FRAME_BL.render(g, x, y + h - ch);
        AllGuiTextures.BRASS_FRAME_BR.render(g, x + w - cw, y + h - ch);
    }

    private DialDrag() {}
}
