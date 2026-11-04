package com.createnewera.ponder;

import java.util.List;

import com.createnewera.bus.BusPort;
import com.createnewera.bus.BusSpan;
import com.createnewera.client.bus.BusPartRenderer;
import com.createnewera.client.bus.CableRenderer;
import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Ports and cables in a Ponder scene. They aren't blocks, so a scene's world doesn't draw them:
 * this does, with the game's own renderers, fading in like a section of blocks.
 */
public class BusElement extends AnimatedSceneElementBase {
    private final List<BusPort> parts;
    private final List<BusSpan> spans;

    public BusElement(List<BusPort> parts, List<BusSpan> spans) {
        this.parts = parts;
        this.spans = spans;
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        applyFade(pose, fade);
        var consumer = buffer.getBuffer(RenderType.cutout());
        for (BusPort part : parts) {
            var outside = part.pos().relative(part.face());
            pose.pushPose();
            pose.translate(outside.getX(), outside.getY(), outside.getZ());
            BusPartRenderer.drawPart(pose, consumer, part, false, lightCoordsFromFade(fade));
            pose.popPose();
        }
        for (BusSpan span : spans)
            CableRenderer.drawSpan(pose, consumer, world, span);
        pose.popPose();
    }
}
