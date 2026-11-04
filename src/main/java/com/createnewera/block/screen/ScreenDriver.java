package com.createnewera.block.screen;

import java.util.List;
import java.util.Map;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaValue;

import com.createnewera.bus.DeviceDrivers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * What programs do with a pane of Brass Screens, through any of its screens. Mostly: show some
 * markup (ScreenMarkup) and then set the elements it gave an id, and hear its buttons. For what
 * markup can't lay out, a radar say, they can draw on its flip dots straight: dots, rectangles,
 * lines, circles, text, (1, 1) at the top left, in the dots' colours (ScreenPalette).
 */
public final class ScreenDriver {
    public static void methods(DeviceDrivers.Api api, ServerLevel level, BlockPos pos) {
        api.family("screen");

        // ---- markup ----
        api.order("show", "markup: string", "", args -> {
            Drawing d = drawing(level, pos);
            try {
                d.controller.show(d.pane, args.arg(1).checkString());
            } catch (ScreenMarkup.MarkupException e) {
                throw new LuaError(e.getMessage());
            }
            return null;
        });
        api.order("set", "id: string, value", "", args -> {
            Drawing d = drawing(level, pos);
            LuaValue value = args.arg(2);
            String text = value.type() == Constants.TNUMBER ? number(value.checkDouble()) : value.toString();
            try {
                d.controller.set(d.pane, args.arg(1).checkString(), text);
            } catch (ScreenMarkup.MarkupException e) {
                throw new LuaError(e.getMessage());
            }
            return null;
        });
        api.read("get", "id: string", "string | nil", args -> {
            try {
                return drawing(level, pos).controller.get(args.arg(1).checkString());
            } catch (ScreenMarkup.MarkupException e) {
                throw new LuaError(e.getMessage());
            }
        });
        api.read("getLastClick", "", "{id, player, count} | nil", args -> {
            BrassScreenBlockEntity c = drawing(level, pos).controller;
            return c.clicks() == 0 ? null : Map.of("id", c.clicked(), "player", c.clickedBy(), "count", c.clicks());
        });
        api.read("getLastTouch", "", "{x, y, player, count} | nil", args -> {
            BrassScreenBlockEntity c = drawing(level, pos).controller;
            return c.touches() == 0 ? null : Map.of("x", c.touchX() + 1, "y", c.touchY() + 1, "player", c.touchedBy(), "count", c.touches());
        });

        // ---- size ----
        api.read("getSize", "", "{width, height}", args -> {
            ScreenPane pane = pane(level, pos);
            return Map.of("width", pane.width(), "height", pane.height());
        });
        api.read("getWidth", "", "dots", args -> pane(level, pos).width());
        api.read("getHeight", "", "dots", args -> pane(level, pos).height());
        api.read("getTextWidth", "text: string, scale?: number", "dots",
                args -> ScreenCanvas.textWidth(args.arg(1).checkString(), scale(args.arg(2))));
        api.read("getColors", "", "{\"amber\", \"red\", ...}", args -> ScreenPalette.names());

        // ---- drawing on the dots ----
        api.read("getDot", "x: number, y: number", "colour name", args -> {
            Drawing d = drawing(level, pos);
            return ScreenPalette.names().get(d.canvas.get(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1));
        });
        api.order("clear", "colour?", "", args -> {
            Drawing d = drawing(level, pos);
            d.controller.forgetMarkup();
            d.canvas.fill(args.arg(1).isNil() ? ScreenPalette.DARK : color(args.arg(1)));
            d.done();
            return null;
        });
        api.order("setDot", "x: number, y: number, colour", "", args -> {
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
        api.order("drawText", "x: number, y: number, text: string, colour?, scale?: number", "width in dots", args -> {
            Drawing d = drawing(level, pos);
            int width = d.canvas.text(args.arg(1).checkInteger() - 1, args.arg(2).checkInteger() - 1, args.arg(3).checkString(),
                    args.arg(4).isNil() ? ScreenPalette.AMBER : color(args.arg(4)), scale(args.arg(5)));
            d.done();
            return width;
        });

        List<String> colors = ScreenPalette.names();
        api.choices("clear", colors);
        api.choices("setDot", 2, colors);
        for (String fn : List.of("fillRect", "drawRect", "drawLine"))
            api.choices(fn, 4, colors);
        for (String fn : List.of("drawCircle", "fillCircle", "drawText"))
            api.choices(fn, 3, colors);
    }

    /** What onChange notices: a touch, or a click on a button. */
    public static Object snapshot(ServerLevel level, BlockPos pos) {
        ScreenPane pane = ScreenPane.of(level, pos);
        return pane != null && level.getBlockEntity(pane.origin()) instanceof BrassScreenBlockEntity c
                ? List.of(c.touches(), c.clicks()) : List.of();
    }

    private record Drawing(ScreenPane pane, BrassScreenBlockEntity controller, ScreenCanvas canvas) {
        void done() {
            controller.drawn();
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
        return new Drawing(pane, controller, controller.canvas(pane));
    }

    private static int scale(LuaValue value) throws LuaError {
        return value.isNil() ? 1 : Math.clamp(value.checkInteger(), 1, 8);
    }

    /** A number as a program would print it. */
    private static String number(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long) v) : Double.toString(v);
    }

    /** A dot colour: a name ("amber", "red"...), "#RRGGBB" or 0xRRGGBB to the nearest. */
    static int color(LuaValue value) throws LuaError {
        if (value.type() == Constants.TNUMBER)
            return ScreenPalette.nearest(value.checkInteger() & 0xFFFFFF);
        int c = ScreenPalette.of(value.checkString());
        if (c < 0)
            throw new LuaError("bad colour \"" + value.checkString() + "\" (" + String.join(", ", ScreenPalette.names()) + ", or \"#RRGGBB\")");
        return c;
    }

    private ScreenDriver() {}
}
