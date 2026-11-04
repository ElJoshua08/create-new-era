package com.createnewera.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.createnewera.CreateNewEra;
import com.createnewera.block.HorizontalMachineBlock;
import com.createnewera.block.screen.BrassScreenBlockEntity;
import com.createnewera.block.screen.ScreenCanvas;
import com.createnewera.block.screen.ScreenPalette;
import com.createnewera.block.screen.ScreenPane;
import com.createnewera.client.control.ShaderGlow;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * A pane of Brass Screens as players see it: flip dots, each a little plate that turns over
 * to show a new colour, the way the screens on the website do. When a layout changes the turn
 * ripples across from the left; dots a program draws turn at once. Each screen draws its own
 * share of the pane's picture on its glass, at full brightness.
 */
public class BrassScreenRenderer implements BlockEntityRenderer<BrassScreenBlockEntity> {
    /** Just in front of the glass, which sits 1.5 pixels into the block from its front */
    private static final float DEPTH = 1.5f / 16 - 0.002f;

    public BrassScreenRenderer(BlockEntityRendererProvider.Context context) {
        BrassScreenBlockEntity.clientRemoved = Flips::release;
    }

    @Override
    public void render(BrassScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var level = screen.getLevel();
        if (level == null)
            return;
        ScreenPane pane = ScreenPane.of(level, screen.getBlockPos());
        if (pane == null || !(level.getBlockEntity(pane.origin()) instanceof BrassScreenBlockEntity controller))
            return;
        ScreenCanvas canvas = controller.shown();
        if (canvas == null || canvas.width() != pane.width() || canvas.height() != pane.height())
            return;
        ResourceLocation texture = Flips.of(controller, canvas);

        // this block's share of the glass, in the pane's own pixels (16 a block), left to right and top down
        int[] cell = pane.cell(screen.getBlockPos());
        int paneW = pane.columns() * 16, paneH = pane.rows() * 16, frame = ScreenPane.FRAME;
        float u0 = Math.max(cell[0] * 16, frame), u1 = Math.min(cell[0] * 16 + 16, paneW - frame);
        float v0 = Math.max(cell[1] * 16, frame), v1 = Math.min(cell[1] * 16 + 16, paneH - frame);
        if (u1 <= u0 || v1 <= v0)
            return;

        Direction facing = screen.getBlockState().getValue(HorizontalMachineBlock.FACING);
        pose.pushPose();
        // the turn the blockstate gives the model, authored facing north with +x on the viewer's left
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(BlockModelRotation.by(0, ((int) facing.toYRot() + 180) % 360).getRotation().getLeftRotation());
        pose.translate(-0.5, -0.5, -0.5);
        float x0 = (16 - (u0 - cell[0] * 16)) / 16, x1 = (16 - (u1 - cell[0] * 16)) / 16;
        float y0 = (16 - (v0 - cell[1] * 16)) / 16, y1 = (16 - (v1 - cell[1] * 16)) / 16;
        float s0 = (u0 - frame) / (paneW - 2 * frame), s1 = (u1 - frame) / (paneW - 2 * frame);
        float t0 = (v0 - frame) / (paneH - 2 * frame), t1 = (v1 - frame) / (paneH - 2 * frame);
        var last = pose.last();
        ShaderGlow.draw(() -> {
            VertexConsumer out = buffers.getBuffer(RenderType.text(texture));
            // both windings: it shows from the front whatever the render type culls
            vertex(out, last, x0, y0, s0, t0);
            vertex(out, last, x0, y1, s0, t1);
            vertex(out, last, x1, y1, s1, t1);
            vertex(out, last, x1, y0, s1, t0);
            vertex(out, last, x1, y0, s1, t0);
            vertex(out, last, x1, y1, s1, t1);
            vertex(out, last, x0, y1, s0, t1);
            vertex(out, last, x0, y0, s0, t0);
        });
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v) {
        out.addVertex(pose, x, y, DEPTH).setColor(255, 255, 255, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    @Override
    public boolean shouldRenderOffScreen(BrassScreenBlockEntity screen) {
        return true;
    }

    /**
     * Each pane's dots as they're turning, and the texture they're painted into: six texels a
     * dot, a five-texel plate and the gap beside it.
     */
    public static final class Flips {
        private static final int CELL = 6, PLATE = 5;
        /** A turn takes about 140 ms, as on the website */
        private static final float TURN_SECONDS = 0.14f;
        /** A layout's ripple takes this long to cross the pane, plus a little randomness per dot */
        private static final float RIPPLE_SECONDS = 0.35f, JITTER_SECONDS = 0.06f;
        private static final Random RANDOM = new Random();
        private static final Map<BlockPos, Flips> BY_PANE = new HashMap<>();

        private final int width, height;
        private final DynamicTexture texture;
        private final ResourceLocation id;
        /** The colour showing, the one it's turning to, how far through the turn (0 to 1), and the wait before it starts */
        private final byte[] from, to;
        private final float[] turn, wait;
        private int version = Integer.MIN_VALUE;
        private long lastNanos;
        private boolean painted;

        private Flips(BlockPos at, int width, int height) {
            this.width = width;
            this.height = height;
            texture = new DynamicTexture(width * CELL, height * CELL, false);
            id = CreateNewEra.asResource("screen/" + at.getX() + "_" + at.getY() + "_" + at.getZ() + "_" + System.nanoTime());
            Minecraft.getInstance().getTextureManager().register(id, texture);
            from = new byte[width * height];
            to = new byte[width * height];
            turn = new float[width * height];
            wait = new float[width * height];
            java.util.Arrays.fill(turn, 1);
        }

        static ResourceLocation of(BrassScreenBlockEntity controller, ScreenCanvas canvas) {
            BlockPos at = controller.getBlockPos();
            Flips flips = BY_PANE.get(at);
            if (flips == null || flips.width != canvas.width() || flips.height != canvas.height()) {
                if (flips != null)
                    flips.close();
                flips = new Flips(at, canvas.width(), canvas.height());
                BY_PANE.put(at, flips);
                // a screen coming into view shows what it shows, without turning everything over
                System.arraycopy(canvas.dots(), 0, flips.from, 0, flips.from.length);
                System.arraycopy(canvas.dots(), 0, flips.to, 0, flips.to.length);
                flips.version = controller.version;
            }
            flips.update(controller, canvas);
            return flips.id;
        }

        private void update(BrassScreenBlockEntity controller, ScreenCanvas canvas) {
            long now = System.nanoTime();
            // every screen of the pane asks each frame: turn the dots once a frame
            if (painted && now - lastNanos < 2_000_000L)
                return;
            float dt = lastNanos == 0 ? 0 : Math.min(0.1f, (now - lastNanos) / 1e9f);
            lastNanos = now;
            if (controller.version != version) {
                version = controller.version;
                boolean ripple = controller.ripples();
                byte[] dots = canvas.dots();
                for (int i = 0; i < dots.length; i++) {
                    if (dots[i] == to[i])
                        continue;
                    // halfway through a turn, it carries on from the colour it shows
                    if (turn[i] >= 0.5f && turn[i] < 1)
                        from[i] = to[i];
                    to[i] = dots[i];
                    turn[i] = 0;
                    wait[i] = ripple ? (float) (i % width) / width * RIPPLE_SECONDS + RANDOM.nextFloat() * JITTER_SECONDS : 0;
                }
            }
            NativeImage image = texture.getPixels();
            boolean changed = false;
            if (!painted) {
                image.fillRect(0, 0, width * CELL, height * CELL, abgr(ScreenPalette.GAP_RGB));
                for (int i = 0; i < from.length; i++)
                    paint(image, i, 1);
                painted = changed = true;
            }
            for (int i = 0; i < turn.length; i++) {
                if (turn[i] >= 1)
                    continue;
                if (wait[i] > 0) {
                    wait[i] -= dt;
                    continue;
                }
                turn[i] = Math.min(1, turn[i] + dt / TURN_SECONDS);
                if (turn[i] >= 1)
                    from[i] = to[i];
                paint(image, i, turn[i]);
                changed = true;
            }
            if (changed)
                texture.upload();
        }

        /** One dot partway through its turn: narrowing as it turns edge-on, then the other face. */
        private void paint(NativeImage image, int i, float progress) {
            int x = (i % width) * CELL, y = (i / width) * CELL;
            int gap = abgr(ScreenPalette.GAP_RGB);
            image.fillRect(x, y, CELL, CELL, gap);
            float w = Math.abs((float) Math.cos(progress * Math.PI)) * PLATE;
            int plate = Math.round(w);
            if (plate <= 0)
                return;
            int color = abgr(ScreenPalette.rgb(progress < 0.5f ? from[i] : to[i]));
            image.fillRect(x + (CELL - plate) / 2, y, plate, PLATE, color);
        }

        /** NativeImage keeps ABGR. */
        private static int abgr(int rgb) {
            return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
        }

        private void close() {
            Minecraft.getInstance().getTextureManager().release(id);
        }

        /** A screen went away: if it held a pane, its picture goes too. */
        public static void release(BlockPos controller) {
            Flips flips = BY_PANE.remove(controller);
            if (flips != null)
                flips.close();
        }
    }
}
