package com.createnewera.block.screen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.createnewera.CreateNewEra;

/**
 * The font screens write with: Monocraft, the editor's font, as a bitmap (gen_screen_font.py),
 * so the server can draw text into a screen's pixels itself. Six pixels a character, twelve a
 * line, from U+0020 to U+00FF; anything else shows as '?'.
 */
public final class ScreenFont {
    public static final int WIDTH = 6, HEIGHT = 12;
    private static final int FIRST = 0x20, LAST = 0xFF, COLUMNS = 16;
    private static boolean[][] glyphs;

    /** The lit pixels of a character, row by row, WIDTH by HEIGHT. */
    public static boolean[] glyph(int codePoint) {
        load();
        if (codePoint < FIRST || codePoint > LAST || (codePoint >= 0x7F && codePoint < 0xA0))
            codePoint = '?';
        return glyphs[codePoint - FIRST];
    }

    private static synchronized void load() {
        if (glyphs != null)
            return;
        glyphs = new boolean[LAST - FIRST + 1][WIDTH * HEIGHT];
        try (InputStream in = ScreenFont.class.getResourceAsStream("/data/create_new_era/screen_font.png")) {
            BufferedImage sheet = ImageIO.read(in);
            for (int i = 0; i < glyphs.length; i++) {
                int ox = (i % COLUMNS) * WIDTH, oy = (i / COLUMNS) * HEIGHT;
                for (int y = 0; y < HEIGHT; y++)
                    for (int x = 0; x < WIDTH; x++)
                        glyphs[i][y * WIDTH + x] = (sheet.getRGB(ox + x, oy + y) >>> 24) > 127;
            }
        } catch (IOException | RuntimeException e) {
            CreateNewEra.LOGGER.error("Couldn't read the screen font", e);
        }
    }

    private ScreenFont() {}
}
