package com.createnewera.block.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.world.item.DyeColor;

/**
 * The colours a flip dot can be painted: its dark face, the amber of Create's screens, and
 * one for each dye. A dot holds one of these by number, 0 being dark.
 */
public final class ScreenPalette {
    public static final int DARK = 0, AMBER = 1;
    /** The dark face, and the gaps between dots, from the screens on the website */
    public static final int DARK_RGB = 0x0F1B1B, GAP_RGB = 0x071010;

    private static final List<String> NAMES = new ArrayList<>();
    private static final List<Integer> RGB = new ArrayList<>();

    static {
        add("dark", DARK_RGB);
        add("amber", 0xF2CF7A);
        for (DyeColor dye : DyeColor.values())
            // paint on a dot: the dye's own colour (as on wool) a little brighter, soft beside the
            // amber; black lifted so it still shows against the dark face
            add(dye.getSerializedName(), dye == DyeColor.BLACK ? 0x3A4444 : brighter(dye.getTextureDiffuseColor()));
    }

    private static int brighter(int rgb) {
        int r = Math.min(255, (rgb >> 16 & 255) * 13 / 10), g = Math.min(255, (rgb >> 8 & 255) * 13 / 10), b = Math.min(255, (rgb & 255) * 13 / 10);
        return r << 16 | g << 8 | b;
    }

    private static void add(String name, int rgb) {
        NAMES.add(name);
        RGB.add(rgb);
    }

    public static int count() {
        return NAMES.size();
    }

    public static int rgb(int index) {
        return RGB.get(index >= 0 && index < RGB.size() ? index : DARK);
    }

    public static List<String> names() {
        return List.copyOf(NAMES);
    }

    /** A colour by name, "#RRGGBB" or 0xRRGGBB to the nearest dot paint; -1 if it's none of those. */
    public static int of(String name) {
        String n = name.toLowerCase(Locale.ROOT).strip();
        if (n.equals("off") || n.equals("none"))
            return DARK;
        int i = NAMES.indexOf(n);
        if (i >= 0)
            return i;
        if (n.startsWith("#")) {
            String hex = n.substring(1);
            if (hex.length() == 3)
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
            if (hex.matches("[0-9a-f]{6}"))
                return nearest(Integer.parseInt(hex, 16));
        }
        return -1;
    }

    public static int nearest(int rgb) {
        int best = DARK;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < RGB.size(); i++) {
            int c = RGB.get(i);
            long dr = (c >> 16 & 255) - (rgb >> 16 & 255), dg = (c >> 8 & 255) - (rgb >> 8 & 255), db = (c & 255) - (rgb & 255);
            long d = 2 * dr * dr + 4 * dg * dg + 3 * db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    private ScreenPalette() {}
}
