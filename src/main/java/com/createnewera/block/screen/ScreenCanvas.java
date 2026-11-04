package com.createnewera.block.screen;

/**
 * Drawing into a screen's pixels: a row-major array of 0xRRGGBB, (0, 0) at the top left.
 * Everything clips at the edges, so a program can draw past them freely.
 */
public record ScreenCanvas(int[] pixels, int width, int height) {
    public void set(int x, int y, int color) {
        if (x >= 0 && y >= 0 && x < width && y < height)
            pixels[y * width + x] = color;
    }

    public int get(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height ? pixels[y * width + x] : 0;
    }

    public void fill(int color) {
        java.util.Arrays.fill(pixels, color);
    }

    public void fillRect(int x, int y, int w, int h, int color) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(width, x + w), y1 = Math.min(height, y + h);
        for (int j = y0; j < y1; j++)
            java.util.Arrays.fill(pixels, j * width + x0, j * width + Math.max(x0, x1), color);
    }

    public void drawRect(int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0)
            return;
        fillRect(x, y, w, 1, color);
        fillRect(x, y + h - 1, w, 1, color);
        fillRect(x, y, 1, h, color);
        fillRect(x + w - 1, y, 1, h, color);
    }

    /** Bresenham's line, both ends included. */
    public void line(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        for (int guard = 0; guard < 1 << 16; guard++) {
            set(x0, y0, color);
            if (x0 == x1 && y0 == y1)
                return;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** A circle round (cx, cy): its outline, or filled. */
    public void circle(int cx, int cy, int r, int color, boolean filled) {
        if (r < 0)
            return;
        for (int y = -r; y <= r; y++)
            for (int x = -r; x <= r; x++) {
                int d = x * x + y * y;
                if (filled ? d <= r * r + r : d <= r * r + r && d >= r * r - r)
                    set(cx + x, cy + y, color);
            }
    }

    /**
     * Text in the screen font, each pixel {@code scale} times over; new lines start under the
     * first. @return how wide its longest line is
     */
    public int text(int x, int y, String text, int color, int scale) {
        int cx = x, widest = 0;
        for (int cp : text.codePoints().toArray()) {
            if (cp == '\n') {
                widest = Math.max(widest, cx - x);
                cx = x;
                y += ScreenFont.HEIGHT * scale;
                continue;
            }
            boolean[] glyph = ScreenFont.glyph(cp);
            for (int gy = 0; gy < ScreenFont.HEIGHT; gy++)
                for (int gx = 0; gx < ScreenFont.WIDTH; gx++)
                    if (glyph[gy * ScreenFont.WIDTH + gx])
                        fillRect(cx + gx * scale, y + gy * scale, scale, scale, color);
            cx += ScreenFont.WIDTH * scale;
        }
        return Math.max(widest, cx - x);
    }

    public static int textWidth(String text, int scale) {
        int widest = 0, line = 0;
        for (int cp : text.codePoints().toArray()) {
            if (cp == '\n') {
                widest = Math.max(widest, line);
                line = 0;
            } else {
                line += ScreenFont.WIDTH * scale;
            }
        }
        return Math.max(widest, line);
    }
}
