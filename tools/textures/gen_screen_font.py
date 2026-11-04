"""The bitmap font Brass Screens draw text with: Monocraft (the editor's font, OFL, bundled in
assets/create_new_era/font) at its own pixel size, where it renders crisp, one cell per
character from U+0020 to U+00FF (ASCII and Latin-1: accents, ñ, ¿, ¡). The server rasterises
programs' text into a screen's pixels with it (ScreenFont), so it lives in data/, not assets/.

Run: python3 tools/textures/gen_screen_font.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
TTF = ROOT / "src/main/resources/assets/create_new_era/font/monocraft.ttf"
OUT = ROOT / "src/main/resources/data/create_new_era/screen_font.png"
CELL_W, CELL_H = 6, 12
TOP = -2  # accents over capitals reach two pixels above the font's own top
FIRST, LAST, COLUMNS = 0x20, 0xFF, 16


def main():
    # 9 px: one font unit a pixel, no grey edges; the basic layout draws one character as it is
    font = ImageFont.truetype(str(TTF), 9, layout_engine=ImageFont.Layout.BASIC)
    count = LAST - FIRST + 1
    rows = (count + COLUMNS - 1) // COLUMNS
    sheet = Image.new("LA", (COLUMNS * CELL_W, rows * CELL_H), (0, 0))
    draw = ImageDraw.Draw(sheet)
    for i in range(count):
        x, y = (i % COLUMNS) * CELL_W, (i // COLUMNS) * CELL_H
        draw.text((x, y - TOP), chr(FIRST + i), font=font, fill=(255, 255))
    # hard pixels only: a screen pixel is lit or not
    sheet = sheet.point(lambda v: 255 if v >= 128 else 0)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"{count} glyphs, {CELL_W}x{CELL_H}, written to {OUT}")


if __name__ == "__main__":
    main()
