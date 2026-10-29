"""Cuts the GUI tiles the site borrows from Create's own screens (display link, schedule)
and copies the item sprites and the Monocraft font it uses.

Usage: python3 site/tools/gui.py --create-assets DIR
"""
import argparse
import shutil
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parents[2]
PUBLIC = REPO / "site" / "public"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--create-assets", required=True)
    a = ap.parse_args()
    gui = Path(a.create_assets) / "assets" / "create" / "textures" / "gui"
    out = PUBLIC / "gui"
    out.mkdir(parents=True, exist_ok=True)

    link = Image.open(gui / "display_link.png").convert("RGBA")
    schedule = Image.open(gui / "schedule.png").convert("RGBA")
    # the dithered dark panel and the wood planks beside it
    schedule.crop((40, 40, 56, 56)).save(out / "dither.png")
    link.crop((4, 18, 12, 26)).save(out / "wood.png")
    # the check button from the footer
    link.crop((201, 137, 221, 157)).save(out / "check.png")

    items = PUBLIC / "img" / "items"
    items.mkdir(parents=True, exist_ok=True)
    for f in (REPO / "website" / "img" / "items").glob("*.png"):
        shutil.copy(f, items / f.name)
    # the mod's own item sprites for the bus parts
    tex = REPO / "src" / "main" / "resources" / "assets" / "create_new_era" / "textures" / "item"
    for src, name in [("bus_port.png", "bus_port"), ("port_expansion.png", "port_expansion"),
                      ("cable_splice.png", "cable_splice"), ("cable_spool/copper_full.png", "cable_spool")]:
        shutil.copy(tex / src, items / f"create_new_era_{name}.png")
    shutil.copy(items / "create_new_era_brass_computer.png", PUBLIC / "favicon.png")

    # link preview: the hero diorama on the page's ground colour
    hero = PUBLIC / "scenes" / "hero" / "base.png"
    if hero.exists():
        og = Image.new("RGBA", (1200, 630), (21, 17, 14, 255))
        im = Image.open(hero).convert("RGBA")
        im.thumbnail((1100, 590), Image.LANCZOS)
        og.alpha_composite(im, ((1200 - im.width) // 2, (630 - im.height) // 2))
        og.convert("RGB").save(PUBLIC / "og.png", optimize=True)

    fonts = PUBLIC / "fonts"
    fonts.mkdir(parents=True, exist_ok=True)
    for f in ("Monocraft.woff2", "Monocraft-Bold.woff2", "Monocraft-OFL.txt"):
        shutil.copy(REPO / "website" / "fonts" / f, fonts / f)
    cables = PUBLIC / "img" / "cable"
    cables.mkdir(parents=True, exist_ok=True)
    for f in (REPO / "src" / "main" / "resources" / "assets" / "create_new_era" / "textures" / "block" / "cable").glob("*.png"):
        shutil.copy(f, cables / f.name)


if __name__ == "__main__":
    main()
