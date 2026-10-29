package com.createnewera.client.bus;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Outlines a device picked from the editor's suggestions, pulsing in brass, so the player can
 * find it once the editor closes. It lasts a few seconds from the moment they're back in the world.
 */
public final class DeviceHighlight {
    private static final long SHOWN_FOR_MS = 10_000;
    private static @Nullable BlockPos pos;
    private static long until;

    public static void register(IEventBus gameBus) {
        gameBus.addListener(DeviceHighlight::render);
    }

    public static void show(BlockPos device) {
        pos = device.immutable();
        until = Long.MAX_VALUE; // the clock starts when the editor closes
    }

    /** Called as the editor closes: the highlight counts down from now. */
    public static void startCountdown() {
        if (pos != null)
            until = System.currentTimeMillis() + SHOWN_FOR_MS;
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || pos == null)
            return;
        long now = System.currentTimeMillis();
        if (now > until) {
            pos = null;
            return;
        }
        if (Minecraft.getInstance().screen != null)
            return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        // a slow breath, and fading out over the last second
        float pulse = 0.65f + 0.35f * (float) Math.sin(now / 160.0);
        float fade = Math.min(1, (until - now) / 1000f);
        AABB box = new AABB(pos).inflate(0.02).move(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 0.98f, 0.8f, 0.4f, pulse * fade);
        buffers.endBatch(RenderType.lines());
    }

    private DeviceHighlight() {}
}
