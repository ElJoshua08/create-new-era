"""Isometric pixel art for item sprites, in Create's style (see the canvas page "Estilo de pixel art"):
objects in 3/4 view on a 2:1 grid, light from the top left, no black outline (the material's own
darkest tone instead), long colour ramps sampled from Create's own sprites, and material grain."""
from PIL import Image


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


# ramps sampled from Create's copper_sheet, brass_sheet and andesite_alloy, dark to light
COPPER = [hexc(c) for c in ['#6d3421', '#8a4129', '#9c4529', '#c15a36', '#e77c56', '#fc9982', '#fbc3b6']]
BRASS = [hexc(c) for c in ['#602f24', '#76462d', '#995a3d', '#b57849', '#d3a155', '#fbcc68', '#ffeb94', '#fff9c7']]
ANDESITE = [hexc(c) for c in ['#2b3635', '#374141', '#4a5451', '#5e6963', '#6d7d73', '#829789', '#a9afa1', '#c9caba']]
PCB = [hexc(c) for c in ['#10261a', '#173a24', '#1f5230', '#2b6b3c', '#3d8a4c', '#5aa860', '#8ccf7e']]
SLATE = [hexc(c) for c in ['#0f1830', '#182853', '#28375e', '#3f4675', '#515887']]
AMBER = [hexc(c) for c in ['#8a4a12', '#e08a1e', '#ffc847', '#fff2b0']]
GREEN = [hexc(c) for c in ['#1f5a24', '#3fae3a', '#8cf07a', '#e6ffd6']]
INK = hexc('#1d1410')


class Sprite:
    def __init__(self, size=16):
        self.size = size
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.face = {}  # (x, y) -> face name, for outlining

    def set(self, x, y, color, face=None):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.img.putpixel((x, y), color)
            if face:
                self.face[(x, y)] = face

    def get(self, x, y):
        if 0 <= x < self.size and 0 <= y < self.size:
            return self.img.getpixel((x, y))
        return (0, 0, 0, 0)


def inside(poly, x, y):
    hit = False
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            hit = not hit
    return hit


def box(s, cx, y0, a, b, h, top, side, name="box", top_index=None):
    """An iso box: a cells along the right-down axis, b along the left-down axis, h pixels tall.
    top/side are colour ramps (dark to light). Returns the top face polygon."""
    T = (cx, y0)
    R = (cx + 2 * a, y0 + a)
    B = (cx + 2 * a - 2 * b, y0 + a + b)
    L = (cx - 2 * b, y0 + b)
    faces = [
        ("left", [L, B, (B[0], B[1] + h), (L[0], L[1] + h)]),
        ("right", [B, R, (R[0], R[1] + h), (B[0], B[1] + h)]),
        ("top", [T, R, B, L]),
    ]
    ti = len(top) - 3 if top_index is None else top_index
    for fname, poly in faces:
        for y in range(s.size):
            for x in range(s.size):
                if inside(poly, x + 0.5, y + 0.5):
                    if fname == "top":
                        col = top[ti]
                    elif fname == "left":
                        col = side[len(side) // 2]
                    else:
                        col = side[max(1, len(side) // 2 - 2)]
                    s.set(x, y, col, f"{name}:{fname}")
    return [T, R, B, L]


def on_top(cx, y0, s_, t_):
    """Pixel for face cell (s, t) on a top face starting at (cx, y0): s runs right-down, t left-down."""
    return (int(cx + 2 * s_ - 2 * t_), int(y0 + s_ + t_))


def shade_top_edges(s, name, ramp, light=-1):
    """Brighten the top face's front-left rim, darken its back-right, Create style."""
    for (x, y), f in list(s.face.items()):
        if f != f"{name}:top":
            continue
        below = s.face.get((x, y + 1), "")
        if below.endswith(":left"):
            s.set(x, y, ramp[light], f)
        elif below.endswith(":right"):
            s.set(x, y, ramp[-2], f)


def outline(s, ramp, mode="selective"):
    """Selective outline: the material's own dark tone, darkest on the bottom and right.
    mode 'ink' draws a uniform dark outline outside the shape instead."""
    filled = {(x, y) for x in range(s.size) for y in range(s.size) if s.get(x, y)[3] > 0}
    if mode == "ink":
        for x in range(s.size):
            for y in range(s.size):
                if (x, y) in filled:
                    continue
                if any((x + dx, y + dy) in filled for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    s.set(x, y, INK)
        return
    for (x, y) in filled:
        edge_b = (x, y + 1) not in filled
        edge_r = (x + 1, y) not in filled
        edge_l = (x - 1, y) not in filled
        edge_t = (x, y - 1) not in filled
        if edge_b or edge_r:
            s.set(x, y, ramp[0], s.face.get((x, y)))
        elif edge_l or edge_t:
            s.set(x, y, ramp[1], s.face.get((x, y)))


def speckle(s, name, ramp, seed, amount=0.18, face="top"):
    """Material grain: a few pixels one step up or down the ramp."""
    import random
    rnd = random.Random(seed)
    for (x, y), f in list(s.face.items()):
        if f == f"{name}:{face}" and rnd.random() < amount:
            c = s.get(x, y)
            if c in ramp:
                i = ramp.index(c) + rnd.choice((-1, 1))
                if 0 < i < len(ramp) - 1:
                    s.set(x, y, ramp[i], f)
