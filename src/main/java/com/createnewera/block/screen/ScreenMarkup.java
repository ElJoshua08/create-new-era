package com.createnewera.block.screen;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Screens laid out from a few tags, the way a page is from HTML, but with the look built in:
 * no stylesheet, just the tags, so every screen keeps Create's style. A program shows some
 * markup once and then changes the elements it gave an id, and the screen lays itself out
 * again and flips only the dots that changed.
 *
 * <pre>
 * &lt;screen&gt;
 *   &lt;title&gt;IRON FARM&lt;/title&gt;
 *   &lt;row&gt;&lt;label&gt;IRON&lt;/label&gt;&lt;value id="iron" align="right"&gt;0&lt;/value&gt;&lt;/row&gt;
 *   &lt;bar id="load" value="0.7"/&gt;
 *   &lt;row&gt;&lt;button id="start"&gt;START&lt;/button&gt;&lt;button id="stop"&gt;STOP&lt;/button&gt;&lt;/row&gt;
 * &lt;/screen&gt;
 * </pre>
 */
public final class ScreenMarkup {
    /** The tags, and what each may carry besides id, color, align and size */
    public static final Map<String, Set<String>> TAGS = new LinkedHashMap<>();

    static {
        TAGS.put("screen", Set.of("background"));
        TAGS.put("title", Set.of());
        TAGS.put("row", Set.of());
        TAGS.put("column", Set.of());
        TAGS.put("label", Set.of());
        TAGS.put("value", Set.of());
        TAGS.put("text", Set.of());
        TAGS.put("bar", Set.of("value", "max"));
        TAGS.put("button", Set.of());
        TAGS.put("divider", Set.of());
        TAGS.put("spacer", Set.of("height"));
    }

    private static final Set<String> COMMON = Set.of("id", "color", "align", "size");
    /** Space round the screen's edge, and between what's stacked or side by side */
    private static final int PADDING = 2, GAP = 2, ROW_GAP = 4;

    /** A part of the screen a player can press, in dots. */
    public record Button(String id, int x, int y, int width, int height) {
        boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }

    /** Markup that doesn't make sense, said in a way a program's author can fix. */
    public static final class MarkupException extends Exception {
        /** The line it's on, from 1, when the reader knows it */
        public final int line;
        /** The text that's wrong, for the editor to find and underline when the line isn't known */
        public final String token;

        MarkupException(String message) {
            this(message, -1, "");
        }

        MarkupException(String message, int line, String token) {
            super(message);
            this.line = line;
            this.token = token;
        }

        /** The line of {@code source} this is about, from 1, or 1 if it can't tell. */
        public int lineIn(String source) {
            if (line > 0)
                return line;
            if (!token.isEmpty()) {
                String[] lines = source.split("\n", -1);
                for (int i = 0; i < lines.length; i++)
                    if (lines[i].contains(token))
                        return i + 1;
            }
            return 1;
        }
    }

    /** Reads markup, checking every tag and attribute: a mistake is caught here, not drawn wrong. */
    public static Element parse(String markup) throws MarkupException {
        String source = markup.strip();
        if (!source.startsWith("<screen"))
            source = "<screen>" + source + "</screen>";
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // a screen's markup is text from a program: no doctypes, no outside files
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            doc = builder.parse(new InputSource(new StringReader(source)));
        } catch (SAXParseException e) {
            throw new MarkupException("markup line " + e.getLineNumber() + ": " + e.getMessage(), e.getLineNumber(), "");
        } catch (Exception e) {
            throw new MarkupException("markup: " + e.getMessage());
        }
        check(doc.getDocumentElement());
        return doc.getDocumentElement();
    }

    private static void check(Element e) throws MarkupException {
        Set<String> allowed = TAGS.get(e.getTagName());
        if (allowed == null)
            throw new MarkupException("unknown tag <" + e.getTagName() + ">: use " + String.join(", ", TAGS.keySet()), -1, "<" + e.getTagName());
        var attributes = e.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.item(i).getNodeName();
            if (!COMMON.contains(name) && !allowed.contains(name))
                throw new MarkupException("<" + e.getTagName() + "> has no attribute \"" + name + "\"", -1, name + "=");
            String value = attributes.item(i).getNodeValue();
            if ((name.equals("color") || name.equals("background")) && ScreenPalette.of(value) < 0)
                throw new MarkupException("unknown colour \"" + value + "\" on <" + e.getTagName() + ">", -1, value);
            if (name.equals("align") && !Set.of("left", "center", "right").contains(value))
                throw new MarkupException("align is left, center or right, not \"" + value + "\"", -1, value);
            if (name.equals("size") && !Set.of("small", "big").contains(value))
                throw new MarkupException("size is small or big, not \"" + value + "\"", -1, value);
        }
        for (Element child : children(e))
            check(child);
    }

    /** The element with this id, for a program to change. */
    public static Element byId(Element root, String id) {
        if (id.equals(root.getAttribute("id")))
            return root;
        for (Element child : children(root)) {
            Element found = byId(child, id);
            if (found != null)
                return found;
        }
        return null;
    }

    /** Sets an element to a program's value: a bar's fill, anything else's text. */
    public static void set(Element e, String value) {
        if (e.getTagName().equals("bar"))
            e.setAttribute("value", value);
        else
            e.setTextContent(value);
    }

    /** Lays the markup out on the canvas and draws it. @return where its buttons are */
    public static List<Button> draw(Element root, ScreenCanvas canvas) {
        int background = color(root, "background", ScreenPalette.DARK);
        canvas.fill(background);
        List<Button> buttons = new ArrayList<>();
        Context ctx = new Context(canvas, buttons, background);
        stack(root, ctx, PADDING, PADDING, canvas.width() - 2 * PADDING, color(root, "color", ScreenPalette.AMBER));
        return buttons;
    }

    private record Context(ScreenCanvas canvas, List<Button> buttons, int background) {}

    /** Children one under another, as the screen and a column lay them. @return the height used */
    private static int stack(Element parent, Context ctx, int x, int y, int width, int ink) {
        int top = y;
        for (Element child : children(parent)) {
            int h = block(child, ctx, x, y, width, color(child, "color", ink));
            y += h + GAP;
        }
        return Math.max(0, y - top - GAP);
    }

    /** One element given a whole line's width. @return its height */
    private static int block(Element e, Context ctx, int x, int y, int width, int ink) {
        ScreenCanvas c = ctx.canvas;
        int scale = scale(e);
        String text = e.getTextContent().strip();
        switch (e.getTagName()) {
            case "title" -> {
                // a band of the ink colour, the text cut out of it: the screen's heading
                int h = ScreenCanvas.lineHeight(scale) + 2;
                c.fillRect(x - PADDING, y - PADDING, width + 2 * PADDING, h + PADDING, ink);
                c.text(aligned(e, x + 1, width - 2, text, scale), y, text, ctx.background, scale);
                return h - 1;
            }
            case "row" -> {
                return row(e, ctx, x, y, width, ink);
            }
            case "column" -> {
                return stack(e, ctx, x, y, width, ink);
            }
            case "bar" -> {
                return bar(e, c, x, y, width, ink);
            }
            case "button" -> {
                return button(e, ctx, x, y, width, ink);
            }
            case "divider" -> {
                c.fillRect(x, y + 1, width, 1, ink);
                return 3;
            }
            case "spacer" -> {
                return intAttribute(e, "height", 4);
            }
            default -> { // label, value, text
                c.text(aligned(e, x, width, text, scale), y, text, ink, scale);
                return ScreenCanvas.lineHeight(scale) - scale;
            }
        }
    }

    /**
     * Side by side: text takes what it needs; buttons, bars, columns and spacers share what's
     * left. Anything aligned right goes to the right end, in order. @return the height used
     */
    private static int row(Element row, Context ctx, int x, int y, int width, int ink) {
        List<Element> items = children(row);
        int fixed = 0, flexible = 0;
        for (Element item : items) {
            if (flexible(item))
                flexible++;
            else
                fixed += natural(item);
        }
        int gaps = ROW_GAP * Math.max(0, items.size() - 1);
        int share = flexible == 0 ? 0 : Math.max(0, (width - fixed - gaps) / flexible);
        // the right-aligned ones are laid from the right edge inwards
        int left = x, right = x + width, height = 0;
        List<Element> rightSide = new ArrayList<>();
        for (Element item : items) {
            if ("right".equals(item.getAttribute("align")) && !flexible(item)) {
                rightSide.add(0, item);
                continue;
            }
            int w = flexible(item) ? share : natural(item);
            height = Math.max(height, inline(item, ctx, left, y, w, color(item, "color", ink)));
            left += w + ROW_GAP;
        }
        for (Element item : rightSide) {
            int w = natural(item);
            right -= w;
            height = Math.max(height, inline(item, ctx, right, y, w, color(item, "color", ink)));
            right -= ROW_GAP;
        }
        return height;
    }

    private static int inline(Element e, Context ctx, int x, int y, int width, int ink) {
        if (e.getTagName().equals("label") || e.getTagName().equals("value") || e.getTagName().equals("text")) {
            int scale = scale(e);
            String text = e.getTextContent().strip();
            ctx.canvas.text(x, y, text, ink, scale);
            return ScreenCanvas.lineHeight(scale) - scale;
        }
        return block(e, ctx, x, y, width, ink);
    }

    private static boolean flexible(Element e) {
        return switch (e.getTagName()) {
            case "button", "bar", "column", "spacer", "divider" -> true;
            default -> false;
        };
    }

    /** How wide some text is on its own. */
    private static int natural(Element e) {
        return ScreenCanvas.textWidth(e.getTextContent().strip(), scale(e));
    }

    /** A frame with a gap inside it, filled as far as its value goes. */
    private static int bar(Element e, ScreenCanvas c, int x, int y, int width, int ink) {
        double max = doubleAttribute(e, "max", 1), value = doubleAttribute(e, "value", 0);
        double fraction = max <= 0 ? 0 : Math.clamp(value / max, 0, 1);
        int h = 7;
        c.drawRect(x, y, width, h, ink);
        c.fillRect(x + 2, y + 2, (int) Math.round((width - 4) * fraction), h - 4, ink);
        return h;
    }

    /** An outlined box, its label in the middle: pressing it is a click a program hears. */
    private static int button(Element e, Context ctx, int x, int y, int width, int ink) {
        int scale = scale(e);
        String text = e.getTextContent().strip();
        int h = ScreenCanvas.lineHeight(scale) + 3;
        ctx.canvas.drawRect(x, y, width, h, ink);
        ctx.canvas.text(x + (width - ScreenCanvas.textWidth(text, scale)) / 2 + 1, y + 2, text, ink, scale);
        String id = e.getAttribute("id");
        if (!id.isEmpty())
            ctx.buttons.add(new Button(id, x, y, width, h));
        return h;
    }

    private static int aligned(Element e, int x, int width, String text, int scale) {
        int w = ScreenCanvas.textWidth(text, scale);
        return switch (e.getAttribute("align")) {
            case "right" -> x + width - w;
            case "center" -> x + (width - w) / 2;
            default -> x;
        };
    }

    private static int scale(Element e) {
        return "big".equals(e.getAttribute("size")) ? 2 : 1;
    }

    private static int color(Element e, String attribute, int otherwise) {
        String value = e.getAttribute(attribute);
        int c = value.isEmpty() ? -1 : ScreenPalette.of(value);
        return c < 0 ? otherwise : c;
    }

    private static double doubleAttribute(Element e, String name, double otherwise) {
        try {
            return e.hasAttribute(name) ? Double.parseDouble(e.getAttribute(name)) : otherwise;
        } catch (NumberFormatException ex) {
            return otherwise;
        }
    }

    private static int intAttribute(Element e, String name, int otherwise) {
        return (int) Math.round(doubleAttribute(e, name, otherwise));
    }

    static List<Element> children(Element e) {
        List<Element> out = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element child)
                out.add(child);
        return out;
    }

    private ScreenMarkup() {}
}
