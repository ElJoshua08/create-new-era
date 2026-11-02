package com.createnewera.client.control;

import com.createnewera.CreateNewEra;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The knob's dial while it's dragged: Create's value bar and brass frame (value_settings.png,
 * the same pixels and colours) bent into an arc open at the bottom, lit brass up to where the
 * knob is, with Create's milestones marking each notch when there are few enough to tell apart.
 * Drawn pixel by pixel into a texture, again only when the knob moves.
 */
final class DialTexture {
    static final int SIZE = 64;
    static final ResourceLocation ID = CreateNewEra.asResource("dynamic/dial");

    /** Up to this many notches get a milestone each; finer ranges read as one sweep */
    static final int MILESTONES = 24;

    private static final float CENTER = SIZE / 2f;
    /** Radius where the inner brass frame starts; then 3 px of frame, 8 of bar, 3 of frame */
    private static final float INNER = 17;
    /** Half the arc, in degrees: it runs from -135 (bottom left) through the top to 135 */
    private static final double SWEEP = 135;

    // value_settings.png: the bar, its milestones, the brass frame
    private static final int EDGE = 0x0E0000, RIB_DARK = 0x1E0A05;
    private static final int[] RIBS = {0x392A25, 0x2B1A14, 0x23120C, 0x23120C, 0x2B1A14, 0x392A25};
    private static final int[] MILESTONE = {0x463831, 0x362821, 0x362821, 0x362821, 0x362821, 0x463831};
    private static final int FRAME_OUT = 0x704630, FRAME_HIGHLIGHT = 0xFBE88A, FRAME_MID = 0xCB9E59, FRAME_IN = 0x9C6846,
            FRAME_SPECK = 0xF4C86A;
    // the part it has turned past: the same bar, glowing brass
    private static final int LIT_DARK = 0x5A3A18;
    private static final int[] LIT_RIBS = {0xB8893F, 0x946A2E, 0x7A5524, 0x7A5524, 0x946A2E, 0xB8893F};
    private static final int[] LIT_MILESTONE = {0xF4C86A, 0xCB9E59, 0xCB9E59, 0xCB9E59, 0xCB9E59, 0xF4C86A};

    private static DynamicTexture texture;
    private static int drawnNotches = -1, drawnStep = -1;

    /** Makes sure the texture shows this position, and returns it to draw. */
    static ResourceLocation at(int notches, int step) {
        if (texture == null) {
            texture = new DynamicTexture(SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(ID, texture);
        }
        if (notches != drawnNotches || step != drawnStep) {
            paint(texture.getPixels(), notches, step);
            texture.upload();
            drawnNotches = notches;
            drawnStep = step;
        }
        return ID;
    }

    private static void paint(NativeImage image, int notches, int step) {
        double at = (double) step / notches;
        double mid = INNER + 7; // the bar's middle radius: where lengths along it are measured
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixelRGBA(x, y, 0);
                double dx = x + 0.5 - CENTER, dy = y + 0.5 - CENTER;
                double r = Math.hypot(dx, dy);
                int band = (int) Math.floor(r - INNER); // 0-2 inner frame, 3-10 bar, 11-13 outer frame
                if (band < 0 || band > 13)
                    continue;
                double angle = Math.toDegrees(Math.atan2(dx, -dy)); // 0 straight up, clockwise
                double over = Math.abs(angle) - SWEEP, cap = 3 / Math.toRadians(r);
                if (over > cap)
                    continue;
                if (over > 0) { // the frame closing off each end of the bar
                    set(image, x, y, over > cap * 0.66 || band == 0 || band == 13 ? FRAME_OUT : over > cap * 0.33 ? FRAME_MID : FRAME_IN);
                    continue;
                }
                if (band <= 2 || band >= 11) {
                    int fromBar = band >= 11 ? band - 11 : 2 - band;
                    double light = (-dx - dy) / r; // lit from the top left, like Create's frames
                    int color = fromBar == 2 ? FRAME_OUT : fromBar == 0 ? FRAME_IN
                            : light > 0.35 ? FRAME_HIGHLIGHT : light > -0.2 && (int) (angle * 7) % 5 == 0 ? FRAME_SPECK : FRAME_MID;
                    set(image, x, y, color);
                    continue;
                }
                int row = band - 3;
                if (row == 0 || row == 7) {
                    set(image, x, y, EDGE);
                    continue;
                }
                double fraction = (angle + SWEEP) / (2 * SWEEP);
                boolean lit = fraction <= at + 1e-6;
                boolean milestone = false;
                if (notches <= MILESTONES) {
                    double width = notches <= 4 ? 2.5 : 1; // two or three positions get wide ones
                    for (int i = 0; i <= notches && !milestone; i++)
                        milestone = Math.abs(fraction - (double) i / notches) * Math.toRadians(2 * SWEEP) * mid < width;
                }
                int color;
                if (milestone)
                    color = (lit ? LIT_MILESTONE : MILESTONE)[row - 1];
                else if (Math.floorMod((int) Math.floor(Math.toRadians(angle) * mid), 2) == 0)
                    color = lit ? LIT_DARK : RIB_DARK;
                else
                    color = (lit ? LIT_RIBS : RIBS)[row - 1];
                set(image, x, y, color);
            }
        }
        // where it is: a brass needle across the bar
        double a = Math.toRadians(-SWEEP + 2 * SWEEP * at);
        for (double r = INNER + 2; r < INNER + 14; r += 0.5) {
            for (double side : new double[] {-1.2, -0.4, 0.4, 1.2}) {
                int x = (int) (CENTER + r * Math.sin(a) + side * Math.cos(a)), y = (int) (CENTER - r * Math.cos(a) + side * Math.sin(a));
                if (x >= 0 && x < SIZE && y >= 0 && y < SIZE)
                    set(image, x, y, Math.abs(side) > 1 ? FRAME_OUT : FRAME_HIGHLIGHT);
            }
        }
    }

    /** NativeImage stores ABGR. */
    private static void set(NativeImage image, int x, int y, int rgb) {
        image.setPixelRGBA(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
    }

    private DialTexture() {}
}
