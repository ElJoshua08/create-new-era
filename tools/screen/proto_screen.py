"""Prototype of the monitor's pixel look: rasterizes a dashboard at a given
pixels-per-block resolution, then shows each screen pixel as a lit cell with a
dark gap (the "mechanical" grid) plus a soft glow.

Used to pick the monitor resolution before writing the in-game renderer.

Run: python3 tools/screen/proto_screen.py OUT_DIR FONT.ttf [--res 32 48 64] [--blocks 3 2]
"""
import argparse
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

BG = (14, 20, 24)
PANEL = (22, 30, 35)
PANEL_HI = (38, 52, 60)
INK = (243, 230, 196)
MUTED = (143, 163, 173)
BRASS = (232, 183, 90)
BRASS_DK = (138, 94, 34)
GREEN = (127, 208, 106)
AMBER = (240, 176, 58)
RED = (224, 83, 58)
NIXIE = (255, 170, 80)
NIXIE_OFF = (60, 38, 24)
WATER = (58, 127, 208)
TILE = (42, 45, 54)
TILE_DK = (29, 31, 38)


class Screen:
    def __init__(self, w, h, font_path):
        self.img = Image.new("RGB", (w, h), BG)
        self.glow = Image.new("RGB", (w, h), (0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.g = ImageDraw.Draw(self.glow)
        self.d.fontmode = self.g.fontmode = "1"
        # Monocraft is pixel-exact at 9 px (6 px advance) and its multiples
        self.font = ImageFont.truetype(font_path, 9)
        self.big = ImageFont.truetype(font_path, 18)

    def text(self, x, y, s, color=INK, big=False, glow=False):
        f = self.big if big else self.font
        self.d.text((x, y - 2 * (2 if big else 1)), s, font=f, fill=color)
        if glow:
            self.g.text((x, y - 2 * (2 if big else 1)), s, font=f, fill=color)

    def panel(self, x, y, w, h):
        self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=PANEL)
        self.d.line([x, y + h - 1, x + w - 1, y + h - 1], fill=PANEL_HI)
        self.d.line([x + w - 1, y, x + w - 1, y + h - 1], fill=PANEL_HI)

    def flap(self, x, y, w, s):
        n = max(1, (w + 1) // 8)
        s = s[:n]
        x0 = x + (w - (len(s) * 8 - 1)) // 2
        for i, ch in enumerate(s):
            tx = x0 + i * 8
            if ch == " ":
                continue
            self.d.rectangle([tx, y, tx + 6, y + 5], fill=TILE)
            self.d.rectangle([tx, y + 6, tx + 6, y + 10], fill=TILE_DK)
            self.text(tx + 1, y + 2, ch)

    def gauge(self, x, y, w, h, frac, value, unit):
        r = min(w // 2 - 2, h - 12)
        cx, cy = x + w // 2, y + r + 2
        t = max(2, r // 5)
        for a0, a1, col in [(180, 240, GREEN), (240, 300, AMBER), (300, 360, RED)]:
            self.d.arc([cx - r, cy - r, cx + r, cy + r], a0, a1, fill=col, width=t)
        ang = math.radians(180 + 180 * frac)
        self.d.line([cx, cy, cx + int((r - t - 1) * math.cos(ang)), cy + int((r - t - 1) * math.sin(ang))], fill=INK)
        self.d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=BRASS)
        label = f"{value} {unit}"
        self.text(cx - len(label) * 3, cy + 3, label)

    def nixie(self, x, y, w, h, digits):
        n = len(digits)
        big = h >= 26
        tw = min((w - (n - 1) * 2) // n, 14 if big else 8)
        th = min(h, 26 if big else 14)
        x0 = x + (w - (n * tw + (n - 1) * 2)) // 2
        lit = False
        for i, ch in enumerate(digits):
            tx = x0 + i * (tw + 2)
            self.d.rectangle([tx, y, tx + tw - 1, y + th - 3], fill=(30, 22, 18))
            self.d.rectangle([tx, y + th - 2, tx + tw - 1, y + th - 1], fill=BRASS)
            lit = lit or ch != "0"
            col = NIXIE if lit else NIXIE_OFF
            gx = tx + (tw - (12 if big else 6)) // 2 + (1 if big else 0)
            self.text(gx, y + (th - 2 - (16 if big else 8)) // 2 + 1, ch, col, big=big, glow=lit)

    def tank(self, x, y, w, h, frac):
        self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=BRASS)
        self.d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=(10, 14, 16))
        fill_h = int((h - 4) * frac)
        self.d.rectangle([x + 2, y + h - 2 - fill_h, x + w - 3, y + h - 3], fill=WATER)
        for yy in range(y + h - 2 - fill_h, y + h - 2, 3):
            self.d.line([x + 2, yy, x + w - 3, yy], fill=(52, 111, 191))

    def chart(self, x, y, w, h, series, target):
        lo, hi = 0, 280
        py = lambda v: y + h - 1 - int((v - lo) / (hi - lo) * (h - 1))
        for xx in range(x, x + w, 3):
            self.d.point((xx, py(target)), fill=BRASS)
        pts = [(x + int(i * (w - 1) / (len(series) - 1)), py(v)) for i, v in enumerate(series)]
        for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
            for xx in range(x1, x2 + 1):
                yy = y1 + (y2 - y1) * (xx - x1) // max(1, x2 - x1)
                self.d.line([xx, yy + 1, xx, y + h - 1], fill=(30, 52, 34))
        self.d.line(pts, fill=GREEN)
        self.g.line(pts, fill=(60, 110, 50))

    def lever(self, x, y, w, h, on):
        bx, by = x + w // 2 - 5, y + h - 5
        self.d.rectangle([bx, by, bx + 9, by + 4], fill=(139, 139, 139))
        self.d.line([bx, by, bx + 9, by], fill=(198, 198, 198))
        self.d.line([bx + 5, by, bx + 8, by - 8], fill=(107, 68, 41), width=2)
        self.d.rectangle([bx + 7, by - 11, bx + 10, by - 8], fill=BRASS)
        self.d.point((bx + 8, by + 2), fill=GREEN if on else RED)
        self.g.point((bx + 8, by + 2), fill=GREEN if on else RED)


def dashboard(w, h, font, rpm=64, count=212, series=None, lever=True, title="FABRICA HIERRO"):
    s = Screen(w, h, font)
    pad = max(2, w // 48)
    s.flap(pad, pad, w - 2 * pad, title)
    top = pad + 11 + pad
    row_h = (h - top - pad) * 3 // 5
    cols = [0.34, 0.44, 0.22]
    xs, x = [], pad
    inner = w - 2 * pad - 2 * pad
    for c in cols:
        xs.append((x, int(inner * c)))
        x += int(inner * c) + pad
    for bx, bw in xs:
        s.panel(bx, top, bw, row_h)
    (gx, gw), (nx, nw), (tx, tw) = xs
    s.gauge(gx + 2, top + 2, gw - 4, row_h - 4, rpm / 256, rpm, "RPM")
    s.nixie(nx + 2, top + 3, nw - 4, row_h - 12, f"{count:03d}" if nw < 70 else f"{count:04d}")
    lbl = "Placas" if nw > 44 else ""
    s.text(nx + (nw - len(lbl) * 6) // 2, top + row_h - 9, lbl, MUTED)
    s.tank(tx + 3, top + 3, max(6, tw // 3), row_h - 6, 0.75)
    pct = "75%"
    if tw > 30:
        s.text(tx + max(6, tw // 3) + 6, top + 3, pct)
    bottom = top + row_h + pad
    bh = h - bottom - pad
    lw = max(16, w // 6)
    s.panel(pad, bottom, w - 3 * pad - lw, bh)
    s.panel(w - pad - lw, bottom, lw, bh)
    series = series or [40, 62, 90, 118, 140, 165, 185, 205, 220, 210, 225, 238, 232]
    s.chart(pad + 2, bottom + 2, w - 3 * pad - lw - 4, bh - 4, series, 256)
    s.lever(w - pad - lw, bottom, lw, bh, lever)
    return s


def mechanical(screen, cell=6, gap=1):
    """Each screen pixel becomes a cell: lit body, darker gap, then a soft bloom."""
    w, h = screen.img.size
    big = screen.img.resize((w * cell, h * cell), Image.Resampling.NEAREST)
    mask = Image.new("L", (cell, cell), 255)
    md = ImageDraw.Draw(mask)
    md.rectangle([cell - gap, 0, cell - 1, cell - 1], fill=90)
    md.rectangle([0, cell - gap, cell - 1, cell - 1], fill=90)
    tiled = Image.new("L", big.size)
    for yy in range(0, big.height, cell):
        for xx in range(0, big.width, cell):
            tiled.paste(mask, (xx, yy))
    lit = Image.composite(big, Image.new("RGB", big.size, (0, 0, 0)), tiled)
    lit = ImageChops.blend(Image.new("RGB", big.size, (0, 0, 0)), big, 0.35)
    lit = Image.composite(big, lit, tiled.point(lambda v: 255 if v == 255 else 0))
    glow = screen.glow.resize(big.size, Image.Resampling.NEAREST).filter(ImageFilter.GaussianBlur(cell * 1.6))
    return ImageChops.add(lit, glow)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("font")
    ap.add_argument("--res", type=int, nargs="+", default=[32, 48, 64])
    ap.add_argument("--blocks", type=int, nargs=2, default=[3, 2])
    a = ap.parse_args()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    bw, bh = a.blocks
    for r in a.res:
        s = dashboard(bw * r, bh * r, a.font)
        s.img.save(out / f"raw_{r}.png")
        # same physical size on screen for every resolution: 384 display px per block
        cell = 384 // r
        mechanical(s, cell=cell, gap=max(1, cell // 6)).save(out / f"screen_{r}.png")


if __name__ == "__main__":
    main()
