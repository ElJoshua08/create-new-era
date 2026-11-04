package com.createnewera.block.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaValue;

import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;

/**
 * What programs draw on a pane of Brass Screens with, through any of its screens: pixels,
 * rectangles, lines, circles and text, in any colour, (1, 1) at the top left. Everything clips
 * at the edges; what's drawn shows at the end of the tick.
 */
public final class ScreenDriver {
    /** The colours a colour argument can be named by: the dyes. */
    public static final List<String> COLORS = Arrays.stream(DyeColor.values()).map(DyeColor::getSerializedName).toList();

    public static void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.family("screen");
        api.read("getSize", "", "{width, height}", args -> {
            ScreenPane pane = pane(level, pos);
            return Map.of("width", pane.width(), "height", pane.height());
        });
        api.read("getWidth", "", "pixels", args -> pane(level, pos).width());
        api.read("getHeight", "", "pixels", args -> pane(level, pos).height());
        api.read("getCharSize", "", "{width, height}", args -> Map.of("width", ScreenFont.WIDTH, "height", ScreenFont.HEIGHT));
        api.read("getPixel", "x: number, y: number", "colour 0xRRGGBB", args -> {
            Drawing d = drawing(level, pos);
            return d.canvas.get(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1);
        });
        api.read("getLastTouch", "", "{x, y, player, count} | nil", args -> {
            BrassScreenBlockEntity controller = drawing(level, pos).controller;
            return controller.touches() == 0 ? null : Map.of("x", controller.touchX() + 1, "y", controller.touchY() + 1,
                    "player", controller.touchedBy(), "count", controller.touches());
        });
        api.read("getTextWidth", "text: string, scale?: number", "pixels",
                args -> ScreenCanvas.textWidth(args.arg(1).checkString(), scale(args.arg(2))));

        api.order("clear", "colour?: name | 0xRRGGBB", "", args -> {
            Drawing d = drawing(level, pos);
            d.canvas.fill(args.arg(1).isNil() ? 0 : color(args.arg(1)));
            d.done();
            return null;
        });
        api.order("setPixel", "x: number, y: number, colour", "", args -> {
            Drawing d = drawing(level, pos);
            d.canvas.set(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1, color(args.arg(3)));
            d.done();
            return null;
        });
        api.order("fillRect", "x: number, y: number, width: number, height: number, colour", "", args -> {
            Drawing d = drawing(level, pos);
            d.canvas.fillRect(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1, args.arg(3).checkInteger(),
                    args.arg(4).checkInteger(), color(args.arg(5)));
            d.done();
            return null;
        });
        api.order("drawRect", "x: number, y: number, width: number, height: number, colour", "", args -> {
            Drawing d = drawing(level, pos);
            d.canvas.drawRect(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1, args.arg(3).checkInteger(),
                    args.arg(4).checkInteger(), color(args.arg(5)));
            d.done();
            return null;
        });
        api.order("drawLine", "x1: number, y1: number, x2: number, y2: number, colour", "", args -> {
            Drawing d = drawing(level, pos);
            d.canvas.line(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1, args.arg(3).checkInteger() - 1,
                    args.arg(4).checkInteger() - 1, color(args.arg(5)));
            d.done();
            return null;
        });
        api.order("drawCircle", "x: number, y: number, radius: number, colour", "", args -> {
            Drawing d = drawing(level, pos);
            d.canvas.circle(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1, args.arg(3).checkInteger(), color(args.arg(4)), false);
            d.done();
            return null;
        });
        api.order("fillCircle", "x: number, y: number, radius: number, colour", "", args -> {
            Drawing d = drawing(level, pos);
            d.canvas.circle(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1, args.arg(3).checkInteger(), color(args.arg(4)), true);
            d.done();
            return null;
        });
        api.order("drawText", "x: number, y: number, text: string, colour?, scale?: number", "width in pixels", args -> {
            Drawing d = drawing(level, pos);
            int width = d.canvas.text(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1, args.arg(3).checkString(),
                    args.arg(4).isNil() ? 0xFFFFFF : color(args.arg(4)), scale(args.arg(5)));
            d.done();
            return width;
        });
        // where a colour goes, the editor offers the dyes
        api.choices("clear", COLORS);
        api.choices("setPixel", 2, COLORS);
        for (String fn : List.of("fillRect", "drawRect", "drawLine"))
            api.choices(fn, 4, COLORS);
        for (String fn : List.of("drawCircle", "fillCircle", "drawText"))
            api.choices(fn, 3, COLORS);
    }

    /** What onChange notices: a touch. */
    public static Object snapshot(ServerLevel level, BlockPos pos) {
        ScreenPane pane = ScreenPane.of(level, pos);
        return pane != null && level.getBlockEntity(pane.origin()) instanceof BrassScreenBlockEntity c ? c.touches() : 0L;
    }

    private record Drawing(BrassScreenBlockEntity controller, ScreenCanvas canvas) {
        void done() {
            controller.changed();
        }
    }

    private static ScreenPane pane(ServerLevel level, BlockPos pos) throws LuaError {
        ScreenPane pane = ScreenPane.of(level, pos);
        if (pane == null)
            throw new LuaError("the screen is gone");
        return pane;
    }

    private static Drawing drawing(ServerLevel level, BlockPos pos) throws LuaError {
        ScreenPane pane = pane(level, pos);
        if (!(level.getBlockEntity(pane.origin()) instanceof BrassScreenBlockEntity controller))
            throw new LuaError("the screen is gone");
        return new Drawing(controller, controller.canvas(pane));
    }

    private static int scale(LuaValue value) throws LuaError {
        return value.isNil() ? 1 : Math.clamp(value.checkInteger(), 1, 16);
    }

    /** A colour: 0xRRGGBB, "#RRGGBB" or "#RGB", or a dye's name. */
    static int color(LuaValue value) throws LuaError {
        if (value.type() == Constants.TNUMBER)
            return value.checkInteger() & 0xFFFFFF;
        String name = value.checkString().toLowerCase(java.util.Locale.ROOT).strip();
        if (name.startsWith("#")) {
            String hex = name.substring(1);
            if (hex.length() == 3)
                hex = new String(new char[] {hex.charAt(0), hex.charAt(0), hex.charAt(1), hex.charAt(1), hex.charAt(2), hex.charAt(2)});
            if (hex.matches("[0-9a-f]{6}"))
                return Integer.parseInt(hex, 16);
        }
        DyeColor dye = DyeColor.byName(name, null);
        if (dye != null)
            return dye.getTextColor();
        throw new LuaError("bad colour \"" + value.checkString() + "\" (a dye's name, \"#RRGGBB\" or 0xRRGGBB expected)");
    }

    private ScreenDriver() {}
}
