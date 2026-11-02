"""Faces of the panel instruments: altimeter, attitude indicator, compass, airspeed.

Each is the top of a 12x12 brass housing filling the panel plate: a one-pixel brass bezel lit
from the top left round a dark round dial with its fixed markings. The dial is drawn at twice
Create's density (32 px a block), fine enough for an instrument's marks; the bezel keeps
Create's chunky pixel. What moves (needles, the horizon, the compass card) is drawn over it
by PanelInstrumentRenderer.

Run: python3 tools/textures/gen_instruments.py [preview.png]
"""
import math
import sys
from pathlib import Path

from gen_textures import AMBER, BRASS, Canvas, PARCHMENT, PHOSPHOR, RED, SLATE

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/create_new_era/textures/block/instruments"
FACE = [SLATE[0], "#15161b"]
MARK = PARCHMENT[3]
SIZE = 24  # the 12x12 top at 2 texels a pixel
CENTER = SIZE / 2
DIAL = 10  # the dial's radius, in texels


def housing():
    c = Canvas(SIZE, SIZE)
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - CENTER, y + 0.5 - CENTER
            d = math.hypot(dx, dy)
            if x < 2 or y < 2 or x >= SIZE - 2 or y >= SIZE - 2:
                # the bezel, two texels (one of Create's pixels), lit from the top left
                lit = x < 2 or y < 2
                dark = x >= SIZE - 2 or y >= SIZE - 2
                c.px(x, y, BRASS[3] if lit and dark else BRASS[5] if lit else BRASS[1])
            elif d > DIAL:
                c.px(x, y, BRASS[3] if dx + dy < 0 else BRASS[2])  # the brass round the round dial
            elif d > DIAL - 1:
                c.px(x, y, BRASS[1] if dx + dy < 0 else BRASS[0])  # its rim, in shadow at the top
            else:
                c.px(x, y, FACE[0] if (x + y) % 7 else FACE[1])
    return c


def mark(c, f, r0, r1, color, width=1):
    """A mark from radius r0 out to r1 at a fraction of a turn, 0 at the top, clockwise."""
    a = 2 * math.pi * f
    steps = int((r1 - r0) * 3) + 1
    for i in range(steps):
        r = r0 + (r1 - r0) * i / max(1, steps - 1)
        x, y = CENTER + r * math.sin(a), CENTER - r * math.cos(a)
        c.px(int(x), int(y), color)
        if width > 1:
            c.px(int(x + math.cos(a) * 0.9), int(y + math.sin(a) * 0.9), color)


def altimeter():
    c = housing()
    # a hundred blocks a turn of the long needle: a mark every ten, a short one between
    for i in range(20):
        f = i / 20
        if i % 2 == 0:
            mark(c, f, DIAL - 3.4, DIAL - 1.2, AMBER[3] if i == 0 else MARK, 2 if i == 0 else 1)
        else:
            mark(c, f, DIAL - 2.0, DIAL - 1.2, PARCHMENT[1])
    return c


def attitude():
    c = housing()
    # roll marks round the top: level, 10, 20, 30 and 60 degrees each way
    for deg in (-60, -30, -20, -10, 10, 20, 30, 60):
        long = abs(deg) in (30, 60)
        mark(c, deg / 360, DIAL - (3.2 if long else 2.2), DIAL - 1.2, MARK)
    mark(c, 0, DIAL - 3.2, DIAL - 1.2, AMBER[3], 2)
    return c


def compass():
    c = housing()
    mark(c, 0, DIAL - 3.5, DIAL - 1.2, AMBER[3], 2)  # the lubber line: the way the ship faces
    return c


def airspeed():
    c = housing()
    # three quarters of a turn, in Create's gauge colours: green, amber, then red at the top
    for i in range(13):
        f = -0.375 + 0.75 * i / 12
        col = PHOSPHOR[2] if i < 8 else AMBER[2] if i < 11 else RED[3]
        mark(c, f, DIAL - (3.4 if i % 3 == 0 else 2.0), DIAL - 1.2, col, 1)
    return c


FACES = {"altimeter": altimeter, "attitude": attitude, "compass": compass, "airspeed": airspeed}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, draw in FACES.items():
        draw().img.save(OUT / f"{name}.png")
    print(f"{len(FACES)} instrument faces written to {OUT}")


if __name__ == "__main__":
    main()
