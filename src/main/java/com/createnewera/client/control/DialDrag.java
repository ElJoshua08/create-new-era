package com.createnewera.client.control;

import org.jetbrains.annotations.Nullable;

import com.createnewera.block.control.PanelDialBlock;
import com.createnewera.block.control.PanelDialBlockEntity;
import com.createnewera.network.DialPayload;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * sideways. The camera stays put while the control follows the mouse, and a bar in Create's
 * value-setting style shows the exact value under the crosshair.
 */
public final class DialDrag {
    /** Mouse pixels to go from 0 to 15 */
    private static final double FULL_SWEEP = 240;

    private static @Nullable BlockPos pos;
    private static float value;
    private static int sent;
    private static double lastX;

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
        sent = state.getValue(PanelDialBlock.VALUE);
        value = sent / 15f;
        lastX = mc.mouseHandler.xpos();
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
        value = Mth.clamp(value + (float) ((x - lastX) / FULL_SWEEP), 0, 1);
        lastX = x;
        if (mc.level.getBlockEntity(pos) instanceof PanelDialBlockEntity dial)
            dial.dragged = value;
        int step = Math.round(value * 15);
        if (step != sent) {
            sent = step;
            PacketDistributor.sendToServer(new DialPayload(pos, step));
        }
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
        if (pos == null || mc.level == null)
            return;
        GuiGraphics g = event.getGuiGraphics();
        int steps = 16, stepW = 8, barW = (steps - 1) * stepW, barH = 14;
        int x = g.guiWidth() / 2 - barW / 2, y = g.guiHeight() / 2 + 18;

        // brass frame round a dark field, like Create's value settings
        brassFrame(g, x - 6, y - 5, barW + 12, barH + 10);
        UIRenderHelper.drawStretched(g, x - 3, y - 2, barW + 6, barH + 4, 0, AllGuiTextures.VALUE_SETTINGS_OUTER_BG);
        UIRenderHelper.drawStretched(g, x, y + 1, barW, barH - 2, 0, AllGuiTextures.VALUE_SETTINGS_BAR_BG);
        AllGuiTextures milestone = AllGuiTextures.VALUE_SETTINGS_MILESTONE;
        for (int i = 0; i < steps; i++)
            milestone.render(g, x + i * stepW - milestone.getWidth() / 2, y + 1);

        // the cursor, on the value being dragged to, with the number in it
        int step = Math.round(value * 15);
        String label = String.valueOf(step);
        int labelW = mc.font.width(label) + 6;
        int cx = x + Math.round(value * barW) - labelW / 2;
        AllGuiTextures left = AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT, right = AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT;
        left.render(g, cx - left.getWidth(), y);
        UIRenderHelper.drawStretched(g, cx, y, labelW, barH, 0, AllGuiTextures.VALUE_SETTINGS_CURSOR);
        right.render(g, cx + labelW, y);
        g.drawString(mc.font, label, cx + 3, y + 3, 0xFFFFFF, false);

        Component name = mc.level.getBlockState(pos).getBlock().getName();
        g.drawString(mc.font, name, g.guiWidth() / 2 - mc.font.width(name) / 2, y - 16, 0xFFEEDDAA, true);
    }

    private static void brassFrame(GuiGraphics g, int x, int y, int w, int h) {
        AllGuiTextures tl = AllGuiTextures.BRASS_FRAME_TL, br = AllGuiTextures.BRASS_FRAME_BR;
        int cw = tl.getWidth(), ch = tl.getHeight();
        UIRenderHelper.drawStretched(g, x + cw, y, w - 2 * cw, ch, 0, AllGuiTextures.BRASS_FRAME_TOP);
        UIRenderHelper.drawStretched(g, x + cw, y + h - ch, w - 2 * cw, ch, 0, AllGuiTextures.BRASS_FRAME_BOTTOM);
        UIRenderHelper.drawStretched(g, x, y + ch, cw, h - 2 * ch, 0, AllGuiTextures.BRASS_FRAME_LEFT);
        UIRenderHelper.drawStretched(g, x + w - cw, y + ch, cw, h - 2 * ch, 0, AllGuiTextures.BRASS_FRAME_RIGHT);
        tl.render(g, x, y);
        AllGuiTextures.BRASS_FRAME_TR.render(g, x + w - cw, y);
        AllGuiTextures.BRASS_FRAME_BL.render(g, x, y + h - ch);
        br.render(g, x + w - cw, y + h - ch);
    }

    private DialDrag() {}
}
