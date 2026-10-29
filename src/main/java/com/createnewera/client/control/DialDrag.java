package com.createnewera.client.control;

import org.jetbrains.annotations.Nullable;

import com.createnewera.block.control.PanelDialBlock;
import com.createnewera.block.control.PanelDialBlockEntity;
import com.createnewera.network.DialPayload;
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
    /** Mouse pixels to go from 0 to 15 */
    private static final double FULL_SWEEP = 420;
    /** However fast the mouse goes, the control moves at most this much of its range a second */
    private static final double MAX_SPEED = 1.1;
    /** How far past a notch's edge the mouse has to go before it clicks over to the next one, in steps */
    private static final float DETENT = 0.2f;

    private static @Nullable BlockPos pos;
    /** where the mouse has taken it, 0 to 1, between notches */
    private static float raw;
    /** the notch it rests in, 0 to 15 */
    private static int step;
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
        if (!(state.getBlock() instanceof PanelDialBlock))
            return;
        pos = hit.getBlockPos().immutable();
        step = state.getValue(PanelDialBlock.VALUE);
        raw = step / 15f;
        lastX = mc.mouseHandler.xpos();
        lastFrame = System.nanoTime();
        event.setCanceled(true);
        event.setSwingHand(false);
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
        double most = MAX_SPEED * (now - lastFrame) / 1e9;
        raw = Mth.clamp(raw + (float) Mth.clamp((x - lastX) / FULL_SWEEP, -most, most), 0, 1);
        lastX = x;
        lastFrame = now;
        // it sits in a notch until the mouse has gone a little past its edge, like a geared dial
        float at = raw * 15;
        int notch = step;
        if (at > step + 0.5f + DETENT)
            notch = Math.min(15, Math.round(at));
        else if (at < step - 0.5f - DETENT)
            notch = Math.max(0, Math.round(at));
        if (notch != step) {
            step = notch;
            PacketDistributor.sendToServer(new DialPayload(pos, step));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.6f + step / 30f, 0.25f));
        }
        if (mc.level.getBlockEntity(pos) instanceof PanelDialBlockEntity dial)
            dial.dragged = step / 15f;
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
        if (mc.level != null && pos != null && mc.level.getBlockEntity(pos) instanceof PanelDialBlockEntity dial)
            dial.dragged = -1;
        pos = null;
    }

    private static void drawBar(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc.level == null || !(mc.level.getBlockState(pos).getBlock() instanceof PanelDialBlock dial))
            return;
        GuiGraphics g = event.getGuiGraphics();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        if (dial.style() == PanelDialBlock.Style.KNOB)
            drawDial(g, mc, cx, cy);
        else
            drawThrottle(g, mc, cx, cy);
        Component name = dial.getName();
        g.drawString(mc.font, name, cx - mc.font.width(name) / 2, cy - 44, 0xFFEEDDAA, true);
    }

    /**
     * The knob: its sixteen notches in a ring round the crosshair, from -135 to +135 degrees as
     * the knob turns, lit up to where it's set, the number in the middle.
     */
    private static void drawDial(GuiGraphics g, Minecraft mc, int cx, int cy) {
        int radius = 24;
        for (int i = 0; i <= 15; i++) {
            double a = Math.toRadians(-135 + 270 * i / 15.0);
            int x = cx + (int) Math.round(radius * Math.sin(a)), y = cy - (int) Math.round(radius * Math.cos(a));
            boolean lit = i <= step, here = i == step;
            int size = here ? 3 : 2;
            g.fill(x - size, y - size, x + size, y + size, 0xFF3A2411);
            g.fill(x - size + 1, y - size + 1, x + size - 1, y + size - 1,
                    here ? 0xFFFFEB8C : lit ? 0xFFE4B763 : 0xFF5E4A36);
        }
        String label = String.valueOf(step);
        g.drawString(mc.font, label, cx - mc.font.width(label) / 2, cy + 10, 0xFFFFEB8C, true);
    }

    /**
     * The slider: Aeronautics' throttle lever bar, lying down: a brass frame round Create's
     * value-settings bar, filled up to where the handle is, the number beside it.
     */
    private static void drawThrottle(GuiGraphics g, Minecraft mc, int cx, int cy) {
        AllGuiTextures bar = AllGuiTextures.VALUE_SETTINGS_BAR;
        int w = bar.getWidth(), h = bar.getHeight();
        int x = cx - w / 2, y = cy + 20;
        brassFrame(g, x - 4, y - 4, w + 8, h + 8);
        UIRenderHelper.drawStretched(g, x, y, w, h, 0, AllGuiTextures.VALUE_SETTINGS_BAR_BG);
        int filled = Math.round(w * step / 15f);
        if (filled > 0)
            UIRenderHelper.drawCropped(g, x, y, filled, h, 0, bar);
        String label = step + " / 15";
        g.drawString(mc.font, label, cx - mc.font.width(label) / 2, y + h + 7, 0xFFFFEB8C, true);
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
