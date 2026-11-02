package com.createnewera.client.control;

import java.util.ArrayList;
import java.util.List;

import com.createnewera.CreateNewEra;
import com.createnewera.block.DirectionalPartBlock;
import com.createnewera.block.control.PanelControlBlockEntity;
import com.createnewera.block.control.PanelInstrumentBlock;
import com.createnewera.bus.Ships;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What moves on a panel instrument's dial, drawn over the face its model shows
 * (gen_instruments.py): needles, the artificial horizon, the compass card. Everything is in the
 * up-facing model's pixels, the dial's middle at (8, 8) and its 12 o'clock towards -z, turned
 * the way the blockstate turns the model.
 */
final class InstrumentRenderer {
    private static final ResourceLocation WHITE = CreateNewEra.asResource("textures/block/instruments/white.png");
    // gen_textures.py's palette
    private static final int MARK = 0xEEE2BF, BRASS = 0xE4B763, BRASS_DARK = 0x724731, RED = 0xF06A4A, AMBER = 0xF0B03A,
            SKY = 0x5D8FB8, GROUND = 0x7A5434;
    /** Heights above the dial, in pixels, so each layer sits over the one before */
    private static final float CARD = 4.03f, NEEDLE = 4.06f, CAP = 4.09f;
    /** The artificial horizon's ball, in pixels; the roll marks are round it */
    private static final float BALL = 3.3f;
    /** How far the horizon moves for 90 degrees of pitch, in pixels */
    private static final float PITCH_SCALE = 3.6f;

    static void render(PanelControlBlockEntity be, PanelInstrumentBlock instrument, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        if (level == null)
            return;
        BlockPos pos = be.getBlockPos();
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(rotation(state).getRotation().getLeftRotation());
        // on a wall the model turns its dial back round (gen_models.py); so do the needles
        if (state.getValue(DirectionalPartBlock.FACING).getAxis().isHorizontal())
            pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(-0.5, -0.5, -0.5);
        Draw draw = new Draw(buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), pose.last(), light);
        switch (instrument.kind()) {
            case ALTIMETER -> {
                double y = Ships.position(level, pos).y;
                needle(draw, Math.floorMod((long) Math.floor(y * 10), 10000) / 10000.0, 2.3f, 0.9f, BRASS);
                needle(draw, Math.floorMod((long) Math.floor(y * 100), 10000) / 10000.0, 3.7f, 0.55f, MARK);
                cap(draw);
            }
            case GYROSCOPE -> {
                double[] a = PanelInstrumentBlock.attitude(level, pos, partialTick);
                horizon(draw, a[0], a[1]);
                // the little aircraft, fixed: wings and a dot
                draw.rect(8 - 2.4f, 8 - 0.22f, 8 - 0.75f, 8 + 0.22f, NEEDLE, AMBER);
                draw.rect(8 + 0.75f, 8 - 0.22f, 8 + 2.4f, 8 + 0.22f, NEEDLE, AMBER);
                draw.rect(8 - 0.35f, 8 - 0.35f, 8 + 0.35f, 8 + 0.35f, NEEDLE, AMBER);
            }
            case COMPASS -> {
                double heading = PanelInstrumentBlock.attitude(level, pos, partialTick)[2];
                // the card turns against the heading, so north on it stays north
                for (int i = 0; i < 16; i++) {
                    double f = i / 16.0 - heading / 360;
                    float inner = i % 4 == 0 ? 2.9f : i % 2 == 0 ? 3.4f : 3.8f;
                    tick(draw, f, inner, 4.35f, i == 0 ? 0.55f : 0.35f, i == 0 ? RED : MARK);
                }
                cap(draw);
            }
            case AIRSPEED -> {
                double speed = clientSpeed(be, level, pos);
                double top = be.max > 0 ? be.max : PanelInstrumentBlock.TOP_SPEED;
                needle(draw, -0.375 + 0.75 * Math.min(speed / top, 1.04), 3.7f, 0.55f, MARK);
                cap(draw);
            }
        }
        pose.popPose();
    }

    /** The ship's speed as this client sees it move, smoothed: Sable only knows it on the server. */
    private static double clientSpeed(PanelControlBlockEntity be, Level level, BlockPos pos) {
        Vec3 at = Ships.position(level, pos);
        long now = System.nanoTime();
        if (be.lastSeenAt != null) {
            double seconds = (now - be.lastSeenNanos) / 1e9;
            if (seconds < 0.03)
                return be.shownSpeed;
            double measured = seconds > 1 ? 0 : at.distanceTo(be.lastSeenAt) / seconds;
            be.shownSpeed += (measured - be.shownSpeed) * Math.min(1, seconds * 3);
        }
        be.lastSeenAt = at;
        be.lastSeenNanos = now;
        return be.shownSpeed;
    }

    /** The turn the blockstate gives the model (ModBlocks.instrument). */
    static BlockModelRotation rotation(BlockState state) {
        Direction facing = state.getValue(DirectionalPartBlock.FACING);
        int reads = (int) state.getValue(PanelInstrumentBlock.READS).toYRot();
        return switch (facing) {
            case UP -> BlockModelRotation.by(0, reads % 360);
            case DOWN -> BlockModelRotation.by(180, (reads + 180) % 360);
            default -> BlockModelRotation.by(90, ((int) facing.toYRot() + 180) % 360);
        };
    }

    /** A needle from just behind the middle out to {@code length}, pointing a fraction of a turn round from 12. */
    private static void needle(Draw draw, double turn, float length, float width, int color) {
        double a = 2 * Math.PI * turn;
        float s = (float) Math.sin(a), c = (float) Math.cos(a);
        // along the needle (s, -c), across it (c, s)
        float tailX = 8 - 0.9f * s, tailZ = 8 + 0.9f * c, tipX = 8 + length * s, tipZ = 8 - length * c;
        float w = width / 2, tip = w * 0.3f;
        draw.poly(NEEDLE, color, new float[] {tailX - w * c, tailZ - w * s, tipX - tip * c, tipZ - tip * s,
                tipX + tip * c, tipZ + tip * s, tailX + w * c, tailZ + w * s});
    }

    private static void tick(Draw draw, double turn, float from, float to, float width, int color) {
        double a = 2 * Math.PI * turn;
        float s = (float) Math.sin(a), c = (float) Math.cos(a), w = width / 2;
        float x0 = 8 + from * s, z0 = 8 - from * c, x1 = 8 + to * s, z1 = 8 - to * c;
        draw.poly(CARD, color, new float[] {x0 - w * c, z0 - w * s, x1 - w * c, z1 - w * s, x1 + w * c, z1 + w * s, x0 + w * c, z0 + w * s});
    }

    /** The brass boss the needles turn on. */
    private static void cap(Draw draw) {
        draw.rect(8 - 0.8f, 8 - 0.8f, 8 + 0.8f, 8 + 0.8f, CAP, BRASS_DARK);
        draw.rect(8 - 0.55f, 8 - 0.55f, 8 + 0.55f, 8 + 0.55f, CAP + 0.01f, BRASS);
    }

    /**
     * The artificial horizon: sky over ground inside the ball, the line between them moved by
     * pitch and tilted against the roll, with short pitch marks every 10 degrees.
     */
    private static void horizon(Draw draw, double pitch, double roll) {
        double r = Math.toRadians(-roll);
        float nx = (float) -Math.sin(r), nz = (float) Math.cos(r); // points from the sky into the ground
        float offset = (float) (Math.clamp(pitch, -90, 90) / 90 * PITCH_SCALE);
        float line = nx * 8 + nz * 8 + offset;
        List<float[]> ball = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            double a = 2 * Math.PI * i / 32;
            ball.add(new float[] {8 + BALL * (float) Math.sin(a), 8 - BALL * (float) Math.cos(a)});
        }
        draw.convex(CARD, SKY, clip(ball, nx, nz, line));
        draw.convex(CARD, GROUND, clip(ball, -nx, -nz, -line));
        // the horizon and pitch marks, parallel to it, kept inside the ball
        float[][] marks = {{0, 2.8f}, {10, 0.55f}, {-10, 0.55f}, {20, 1.0f}, {-20, 1.0f}};
        for (float[] m : marks) {
            float o = offset - m[0] / 90 * PITCH_SCALE;
            float cx = 8 + nx * o, cz = 8 + nz * o, half = m[1];
            if (Math.hypot(cx - 8, cz - 8) + 0.2 > BALL)
                continue;
            float w = 0.18f;
            draw.poly(CARD + 0.01f, MARK, new float[] {cx - nz * half - nx * w, cz + nx * half - nz * w, cx + nz * half - nx * w,
                    cz - nx * half - nz * w, cx + nz * half + nx * w, cz - nx * half + nz * w, cx - nz * half + nx * w, cz + nx * half + nz * w});
        }
    }

    /** The part of a convex polygon where (nx, nz) . p <= limit. */
    private static List<float[]> clip(List<float[]> polygon, float nx, float nz, float limit) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            float[] p = polygon.get(i), q = polygon.get((i + 1) % polygon.size());
            float dp = nx * p[0] + nz * p[1] - limit, dq = nx * q[0] + nz * q[1] - limit;
            if (dp <= 0)
                out.add(p);
            if ((dp < 0) != (dq < 0)) {
                float t = dp / (dp - dq);
                out.add(new float[] {p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])});
            }
        }
        return out;
    }

    /** Flat coloured shapes on the dial, facing up, in the model's pixels. */
    private record Draw(VertexConsumer out, PoseStack.Pose pose, int light) {
        void rect(float x0, float z0, float x1, float z1, float y, int color) {
            poly(y, color, new float[] {x0, z0, x0, z1, x1, z1, x1, z0});
        }

        /** A quad given as four corners {x, z, ...}, either winding. */
        void poly(float y, int color, float[] c) {
            for (int i = 0; i < 8; i += 2)
                vertex(c[i], y, c[i + 1], color);
        }

        /** A convex polygon, as a fan of quads (the last of each a repeat when it's a triangle). */
        void convex(float y, int color, List<float[]> points) {
            for (int i = 1; i + 1 < points.size(); i += 2) {
                float[] a = points.get(0), b = points.get(i), c = points.get(i + 1), d = points.get(Math.min(i + 2, points.size() - 1));
                vertex(a[0], y, a[1], color);
                vertex(b[0], y, b[1], color);
                vertex(c[0], y, c[1], color);
                vertex(d[0], y, d[1], color);
            }
        }

        private void vertex(float x, float y, float z, int color) {
            out.addVertex(pose, x / 16, y / 16, z / 16).setColor(color >> 16 & 255, color >> 8 & 255, color & 255, 255)
                    .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
        }
    }

    private InstrumentRenderer() {}
}
