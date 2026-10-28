"""Generates Create: New Era's detail atlas.

Block bodies reuse Create's own casing textures (referenced from the models, never
copied); this atlas only holds the parts Create doesn't have: screens, dials,
LEDs, ports. Palettes are sampled from Create's casings so the parts sit
naturally on them. 1 px here = 1/16 of a block, matching Create's density.

Run: python3 tools/textures/gen_textures.py
"""
from pathlib import Path

from PIL import Image, ImageColor

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/create_new_era/textures/block"

BRASS = ["#3a2411", "#724731", "#9e6947", "#cea05a", "#e4b763", "#f7cb6c", "#ffeb8c"]
COPPER = ["#5e2f1f", "#904931", "#9a5038", "#b26247", "#c87456", "#d67b5b", "#e3826c"]
SLATE = ["#1d1f26", "#2a2d36", "#353a47", "#424a5c", "#5b6478", "#7d87a0"]
GLASS = ["#0b1416", "#0f1d20", "#132a2e", "#1a383d", "#24494f"]
PHOSPHOR = ["#2f6b3a", "#4f9e4a", "#7fd06a", "#c4f5a8"]
PARCHMENT = ["#8a7a5c", "#b8a77f", "#d9caa0", "#eee2bf"]
RED = ["#5a1414", "#8e2020", "#c43a2a", "#f06a4a"]
AMBER = ["#6b3d0c", "#b8721a", "#f0b03a", "#ffe08a"]
ANDESITE = ["#3e403c", "#505351", "#60635f", "#707570", "#828784", "#9aa49d", "#b4b9b5"]
ITEM_OUT = OUT.parent / "item"

# Region name -> (x, y, w, h) in pixels; models address these as uv = px / 2.
REGIONS = {
    "screen": (0, 0, 12, 9),
    "keys": (0, 9, 12, 3),
    "bezel": (16, 0, 16, 2),
    "brass_bar": (16, 2, 16, 2),
    "copper_bar": (16, 4, 16, 2),
    "led_green": (16, 6, 2, 2),
    "led_amber": (18, 6, 2, 2),
    "led_red": (20, 6, 2, 2),
    "dial": (24, 6, 8, 8),
    "rotor": (0, 12, 12, 12),
    "adapter_face": (12, 12, 10, 10),
    "port": (22, 14, 4, 4),
    "slate": (16, 24, 16, 2),
    "copper_coil": (16, 26, 16, 4),
    "brass_panel": (22, 18, 10, 6),
}


# Second atlas: parts for the buildable control-panel blocks.
PART_REGIONS = {
    "cap_red": (0, 0, 4, 4),
    "cap_red_side": (4, 0, 4, 2),
    "cap_green_side": (4, 2, 4, 2),
    "cap_green": (0, 4, 4, 4),
    "bezel": (8, 0, 6, 6),
    "knob_top": (14, 0, 6, 6),
    "knob_side": (20, 0, 12, 2),
    "slate_side": (20, 2, 12, 2),
    "track": (0, 8, 12, 4),
    "handle_top": (12, 8, 4, 3),
    "handle_side": (16, 8, 4, 2),
    "lamp_amber": (0, 12, 4, 4),
    "lamp_amber_side": (4, 12, 4, 3),
    "lamp_green": (8, 12, 4, 4),
    "lamp_green_side": (12, 12, 4, 3),
    "lever_bar": (16, 10, 2, 8),
    "lever_knob": (18, 10, 2, 2),
    "nixie_face": (20, 6, 6, 10),
    "nixie_side": (26, 6, 4, 10),
    "nixie_top": (20, 16, 6, 4),
    "keypad": (0, 18, 10, 12),
    "gauge_face": (10, 18, 10, 10),
    "screen_off": (20, 20, 12, 12),
}


class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def px(self, x, y, color):
        self.img.putpixel((x, y), ImageColor.getrgb(color) + (255,))

    def rect(self, x, y, w, h, color):
        for j in range(h):
            for i in range(w):
                self.px(x + i, y + j, color)

    def hbar(self, x, y, w, ramp):
        """Horizontal bar shaded top-light to bottom-dark, one ramp step per row."""
        for j, c in enumerate(ramp):
            self.rect(x, y + j, w, 1, c)

    def bevel(self, x, y, w, h, light, dark, corner=None):
        self.rect(x, y, w, 1, light)
        self.rect(x, y, 1, h, light)
        self.rect(x, y + h - 1, w, 1, dark)
        self.rect(x + w - 1, y, 1, h, dark)
        if corner:
            self.px(x + w - 1, y, corner)
            self.px(x, y + h - 1, corner)

    def disc(self, cx, cy, r, ramp):
        """Filled disc lit from the top-left; ramp goes dark -> light."""
        n = len(ramp)
        for j in range(int(cy - r - 1), int(cy + r + 2)):
            for i in range(int(cx - r - 1), int(cx + r + 2)):
                dx, dy = i + 0.5 - cx, j + 0.5 - cy
                d = (dx * dx + dy * dy) ** 0.5
                if d > r:
                    continue
                light = 0.5 - (dx + dy) / (2.8 * r)
                k = min(n - 2, max(1, int(light * n)))
                if d > r - 1:
                    k = n - 1 if light >= 0.5 else 0
                self.px(i, j, ramp[k])


def draw_screen(c, x, y):
    w, h = 12, 9
    c.rect(x, y, w, h, SLATE[2])
    c.bevel(x, y, w, h, SLATE[4], SLATE[1], SLATE[3])
    for j in range(1, h - 1):
        c.rect(x + 1, y + j, w - 2, 1, GLASS[3] if j == 1 else GLASS[1 + (j % 2)])
    c.px(x + w - 3, y + 1, GLASS[4])
    c.px(x + w - 2, y + 1, "#2f5a60")
    c.px(x + w - 2, y + 2, GLASS[4])
    c.px(x + 2, y + 2, PHOSPHOR[2])
    for i, col in zip(range(4, 8), [PHOSPHOR[3], PHOSPHOR[2], PHOSPHOR[2], PHOSPHOR[1]]):
        c.px(x + i, y + 2, col)
    for i in range(3, 9):
        c.px(x + i, y + 4, PHOSPHOR[1] if i % 3 == 0 else PHOSPHOR[2])
    for i in range(3, 6):
        c.px(x + i, y + 5, PHOSPHOR[1])
    c.px(x + 2, y + 6, PHOSPHOR[2])
    c.rect(x + 4, y + 6, 2, 1, PHOSPHOR[3])


def draw_keys(c, x, y):
    c.hbar(x, y, 12, [BRASS[5], BRASS[3], BRASS[1]])
    for i in range(1, 11, 2):
        c.px(x + i, y + 1, SLATE[4])
        c.px(x + i, y + 2, SLATE[2])
        c.px(x + i + 1, y + 2, BRASS[2])
    c.px(x + 10, y + 1, AMBER[2])


def draw_dial(c, x, y):
    c.rect(x, y, 8, 8, BRASS[3])
    c.bevel(x, y, 8, 8, BRASS[6], BRASS[1], BRASS[2])
    c.rect(x + 1, y + 1, 6, 6, PARCHMENT[2])
    c.bevel(x + 1, y + 1, 6, 6, PARCHMENT[1], PARCHMENT[3])
    for tx, ty in [(2, 2), (3, 2), (5, 2), (2, 4)]:
        c.px(x + tx, y + ty, PARCHMENT[0])
    c.px(x + 4, y + 3, RED[2])
    c.px(x + 5, y + 2, RED[3])
    c.px(x + 3, y + 4, BRASS[1])
    c.px(x + 4, y + 4, BRASS[0])


def draw_rotor(c, x, y):
    c.rect(x, y, 12, 12, BRASS[4])
    c.bevel(x, y, 12, 12, BRASS[6], BRASS[1], BRASS[2])
    c.bevel(x + 1, y + 1, 10, 10, BRASS[5], BRASS[3])
    # turned groove: lit on the lower-right inner edge, shadowed top-left
    for j in range(12):
        for i in range(12):
            dx, dy = i + 0.5 - 6, j + 0.5 - 6
            if 3.3 < (dx * dx + dy * dy) ** 0.5 <= 4.3:
                c.px(x + i, y + j, BRASS[2] if dx + dy < 0 else BRASS[5])
    c.rect(x + 4, y + 4, 4, 4, SLATE[2])
    c.bevel(x + 4, y + 4, 4, 4, SLATE[1], SLATE[4])
    c.rect(x + 5, y + 5, 2, 2, SLATE[0])
    for bx, by in [(2, 2), (9, 2), (2, 9), (9, 9)]:
        c.px(x + bx, y + by, BRASS[1])
    c.rect(x + 5, y + 2, 2, 1, RED[2])


def draw_adapter_face(c, x, y):
    c.rect(x, y, 10, 10, BRASS[3])
    c.bevel(x, y, 10, 10, BRASS[6], BRASS[1], BRASS[2])
    c.bevel(x + 1, y + 1, 8, 8, BRASS[5], BRASS[2])
    c.rect(x + 2, y + 2, 6, 6, SLATE[2])
    c.bevel(x + 2, y + 2, 6, 6, SLATE[1], SLATE[4])
    c.rect(x + 3, y + 3, 4, 4, SLATE[3])
    c.px(x + 7, y + 8, PHOSPHOR[2])


def draw_port(c, x, y):
    c.rect(x, y, 4, 4, COPPER[4])
    c.bevel(x, y, 4, 4, COPPER[6], COPPER[1], COPPER[3])
    c.rect(x + 1, y + 1, 2, 2, SLATE[0])


def draw_brass_panel(c, x, y):
    c.rect(x, y, 10, 6, BRASS[4])
    c.rect(x, y + 3, 10, 1, BRASS[3])
    c.hbar(x, y, 10, [BRASS[6], BRASS[5]])
    c.hbar(x, y + 4, 10, [BRASS[2], BRASS[1]])
    c.rect(x, y + 1, 1, 4, BRASS[5])
    c.rect(x + 9, y + 1, 1, 4, BRASS[2])
    for rx in (2, 7):
        c.px(x + rx, y + 2, BRASS[1])
        c.px(x + rx, y + 3, BRASS[2])


def draw_coil(c, x, y):
    for i in range(16):
        ramp = [COPPER[5], COPPER[4], COPPER[2], COPPER[1]] if i % 2 == 0 else [COPPER[6], COPPER[5], COPPER[3], COPPER[2]]
        for j, col in enumerate(ramp):
            c.px(x + i, y + j, col)


def draw_cap(c, x, y, ramp):
    c.rect(x, y, 4, 4, ramp[2])
    c.px(x + 1, y + 1, ramp[3])
    c.px(x, y + 1, ramp[2])
    c.rect(x + 1, y, 2, 1, ramp[2])
    c.rect(x + 3, y + 1, 1, 3, ramp[1])
    c.rect(x, y + 3, 4, 1, ramp[1])
    c.px(x, y, ramp[1])
    c.px(x + 3, y, ramp[1])
    c.px(x, y + 3, ramp[0])
    c.px(x + 3, y + 3, ramp[0])


def draw_parts():
    c = Canvas(32, 32)
    R = PART_REGIONS
    draw_cap(c, *R["cap_red"][:2], RED)
    draw_cap(c, *R["cap_green"][:2], PHOSPHOR)
    c.hbar(*R["cap_red_side"][:3], [RED[2], RED[1]])
    c.hbar(*R["cap_green_side"][:3], [PHOSPHOR[2], PHOSPHOR[1]])
    x, y = R["bezel"][:2]
    c.rect(x, y, 6, 6, BRASS[4])
    c.bevel(x, y, 6, 6, BRASS[6], BRASS[1], BRASS[3])
    c.rect(x + 1, y + 1, 4, 4, SLATE[0])
    x, y = R["knob_top"][:2]
    c.rect(x, y, 6, 6, SLATE[2])
    c.bevel(x, y, 6, 6, SLATE[4], SLATE[1])
    for px_, py_ in [(0, 0), (5, 0), (0, 5), (5, 5)]:
        c.px(x + px_, y + py_, SLATE[1])
    c.rect(x + 2, y + 1, 2, 3, BRASS[5])
    c.px(x + 2, y + 1, BRASS[6])
    x, y = R["knob_side"][:2]
    for i in range(12):
        c.px(x + i, y, SLATE[4] if i % 2 == 0 else SLATE[2])
        c.px(x + i, y + 1, SLATE[3] if i % 2 == 0 else SLATE[1])
    c.hbar(*R["slate_side"][:3], [SLATE[3], SLATE[1]])
    x, y = R["track"][:2]
    c.rect(x, y, 12, 4, SLATE[2])
    c.bevel(x, y, 12, 4, SLATE[1], SLATE[4])
    c.rect(x + 1, y + 1, 10, 2, SLATE[0])
    for i in range(1, 12, 2):
        c.px(x + i, y, SLATE[4])
    x, y = R["handle_top"][:2]
    c.rect(x, y, 4, 3, BRASS[4])
    c.bevel(x, y, 4, 3, BRASS[6], BRASS[1])
    c.rect(x + 1, y + 1, 2, 1, BRASS[2])
    c.hbar(*R["handle_side"][:3], [BRASS[4], BRASS[1]])
    for name, ramp in [("lamp_amber", AMBER), ("lamp_green", PHOSPHOR)]:
        x, y = R[name][:2]
        draw_cap(c, x, y, ramp)
        c.px(x + 1, y + 1, "#ffffff")
        x, y = R[name + "_side"][:2]
        c.rect(x, y, 4, 2, ramp[2])
        c.px(x, y, ramp[3])
        c.rect(x, y + 2, 4, 1, BRASS[2])
    x, y = R["lever_bar"][:2]
    c.rect(x, y, 2, 8, SLATE[4])
    c.rect(x + 1, y, 1, 8, SLATE[3])
    x, y = R["lever_knob"][:2]
    c.rect(x, y, 2, 2, BRASS[5])
    c.px(x + 1, y + 1, BRASS[2])
    # nixie: amber digit with a dim halo, glass edges catching light
    glass, glass_dk, glass_hi = "#1e1612", "#2e231c", "#6a5546"
    x, y = R["nixie_face"][:2]
    c.rect(x, y, 6, 10, glass)
    c.rect(x, y, 1, 10, glass_hi)
    c.rect(x + 5, y, 1, 10, glass_dk)
    c.rect(x, y, 6, 1, "#4a3a2e")
    digit = ["###", "..#", "###", "#..", "###"]
    lit = {(1 + i, 2 + j) for j, row in enumerate(digit) for i, ch in enumerate(row) if ch == "#"}
    for (i, j) in list(lit):
        for di, dj in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            n = (i + di, j + dj)
            if n not in lit and 1 <= n[0] <= 4 and 1 <= n[1] <= 8:
                c.px(x + n[0], y + n[1], AMBER[0])
    for (i, j) in lit:
        c.px(x + i, y + j, AMBER[3] if j == 2 else AMBER[2])
    x, y = R["nixie_side"][:2]
    c.rect(x, y, 4, 10, glass)
    c.rect(x, y, 1, 10, glass_hi)
    c.rect(x + 3, y, 1, 10, glass_dk)
    for j in range(2, 9, 2):
        c.rect(x + 1, y + j, 2, 1, "#2a1f19")
    c.rect(x, y, 4, 1, "#4a3a2e")
    x, y = R["nixie_top"][:2]
    c.rect(x, y, 6, 4, "#3a2d24")
    c.bevel(x, y, 6, 4, glass_hi, glass_dk)
    x, y = R["keypad"][:2]
    c.rect(x, y, 10, 12, SLATE[2])
    c.bevel(x, y, 10, 12, SLATE[1], SLATE[4])
    for kj in range(4):
        for ki in range(3):
            kx, ky = x + 1 + ki * 3, y + 1 + kj * 3 - (1 if kj else 0) + (1 if kj else 0)
            col = PARCHMENT[2] if not (kj == 3 and ki != 1) else (RED[2] if ki == 0 else PHOSPHOR[2])
            c.rect(kx, ky, 2, 2, col)
            c.px(kx + 1, ky + 1, PARCHMENT[0] if col == PARCHMENT[2] else SLATE[1])
    x, y = R["gauge_face"][:2]
    c.rect(x, y, 10, 10, BRASS[3])
    c.bevel(x, y, 10, 10, BRASS[6], BRASS[1], BRASS[2])
    c.rect(x + 1, y + 1, 8, 8, PARCHMENT[3])
    c.bevel(x + 1, y + 1, 8, 8, PARCHMENT[1], PARCHMENT[2])
    for tx, ty, col in [(2, 6, PHOSPHOR[1]), (2, 4, PHOSPHOR[1]), (3, 2, AMBER[1]), (5, 2, AMBER[1]), (7, 3, RED[1]), (7, 5, RED[1])]:
        c.px(x + tx, y + ty, col)
    c.px(x + 5, y + 6, BRASS[0])
    c.px(x + 4, y + 5, RED[2])
    c.px(x + 4, y + 4, RED[2])
    c.px(x + 3, y + 3, RED[3])
    # unpowered screen: dark glass, the flip-plate grid just visible
    x, y = R["screen_off"][:2]
    c.rect(x, y, 12, 12, GLASS[1])
    for j in range(12):
        for i in range(12):
            if i % 2 == 1 or j % 2 == 1:
                c.px(x + i, y + j, GLASS[0])
    c.rect(x, y, 12, 1, GLASS[3])
    c.px(x + 10, y + 1, GLASS[4])
    c.px(x + 11, y + 1, GLASS[4])
    c.img.save(OUT / "interface_parts.png")


def draw_screen_glass():
    """Unpowered screen glass for joined screens: tiles seamlessly, so a wall of
    screens reads as one pane. Same flip-plate grid as screen_off, no highlight."""
    c = Canvas(16, 16)
    c.rect(0, 0, 16, 16, GLASS[1])
    for j in range(16):
        for i in range(16):
            if i % 2 == 1 or j % 2 == 1:
                c.px(i, j, GLASS[0])
    c.img.save(OUT / "screen_glass.png")


def draw_editor_checker():
    """Computer editor background: the design's dark checker, tiled by the GUI."""
    c = Canvas(2, 2)
    c.rect(0, 0, 2, 2, "#383838")
    c.px(1, 0, "#3c3c3c")
    c.px(0, 1, "#3c3c3c")
    gui = OUT.parent / "gui"
    gui.mkdir(exist_ok=True)
    c.img.save(gui / "editor_checker.png")


def draw_control_plate_item():
    """Inventory sprite: the andesite plate every control is built on, seen slightly from above."""
    c = Canvas(16, 16)
    c.rect(1, 5, 14, 6, ANDESITE[4])
    c.rect(1, 5, 14, 1, ANDESITE[6])
    c.rect(1, 5, 1, 6, ANDESITE[5])
    c.rect(14, 5, 1, 6, ANDESITE[2])
    c.rect(1, 11, 14, 2, ANDESITE[1])
    c.rect(1, 13, 14, 1, ANDESITE[0])
    c.rect(3, 7, 10, 2, ANDESITE[3])
    for bx in (3, 12):
        c.px(bx, 6, BRASS[5])
        c.px(bx, 9, BRASS[3])
    c.rect(6, 7, 4, 2, BRASS[4])
    c.px(6, 7, BRASS[6])
    ITEM_OUT.mkdir(parents=True, exist_ok=True)
    c.img.save(ITEM_OUT / "control_plate.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.png"):
        old.unlink()
    c = Canvas(32, 32)
    draw_screen(c, *REGIONS["screen"][:2])
    draw_keys(c, *REGIONS["keys"][:2])
    c.hbar(16, 0, 16, [SLATE[4], SLATE[2]])
    c.hbar(16, 2, 16, [BRASS[5], BRASS[2]])
    c.hbar(16, 4, 16, [COPPER[5], COPPER[2]])
    for name, ramp in [("led_green", PHOSPHOR), ("led_amber", AMBER), ("led_red", RED)]:
        x, y = REGIONS[name][:2]
        c.px(x, y, ramp[3])
        c.px(x + 1, y, ramp[2])
        c.px(x, y + 1, ramp[2])
        c.px(x + 1, y + 1, ramp[1])
    draw_dial(c, *REGIONS["dial"][:2])
    draw_rotor(c, *REGIONS["rotor"][:2])
    draw_adapter_face(c, *REGIONS["adapter_face"][:2])
    draw_port(c, *REGIONS["port"][:2])
    c.hbar(16, 24, 16, [SLATE[3], SLATE[1]])
    draw_coil(c, *REGIONS["copper_coil"][:2])
    draw_brass_panel(c, *REGIONS["brass_panel"][:2])
    c.img.save(OUT / "computing_details.png")
    draw_parts()
    draw_screen_glass()
    draw_editor_checker()
    draw_control_plate_item()
    print(f"atlas written to {OUT / 'computing_details.png'}")


if __name__ == "__main__":
    main()
