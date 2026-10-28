"""Cable spool item sprites and models: an upright brass reel with the cable wound on it, one
per dye plus bare copper, full and half wound, and one empty reel. The item model picks one by
the spool's colour and how much is left (see CableRenderer.spoolModels)."""
import json
import math
from pathlib import Path

import pixel_iso as iso
from gen_textures import DYES

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/create_new_era"
TEX = ROOT / "textures/item/cable_spool"
MODELS = ROOT / "models/item"


def ellipse(cx, cy, rx, ry):
    return {(x, y) for x in range(16) for y in range(16)
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1}


def ramp_of(hexcol):
    h = hexcol.lstrip("#")
    c = tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    return [tuple(max(0, min(255, int(v * f))) for v in c) + (255,) for f in (0.42, 0.58, 0.74, 0.88, 1.0, 1.14, 1.3)]


def spool(wire, fill=1.0):
    """fill: how much cable is wound on (0 empty, 1 full)."""
    s = iso.Sprite()
    br = iso.BRASS
    cx = 8
    # bottom flange: a brass disc seen at 3/4. Its rim shows as a dark band under the front arc,
    # lighter on the left where the light comes from
    surface = ellipse(cx, 12.3, 6.5, 2.9)
    rim = ellipse(cx, 13.2, 6.5, 2.9) - surface
    for (x, y) in rim:
        s.set(x, y, br[3] if x < cx - 2 else br[2] if x < cx + 2 else br[1])
    for (x, y) in surface:
        s.set(x, y, br[5] if x < cx - 1 else br[4])
    # the wound wire: a cylinder between the flanges, wider the fuller it is. Its base follows
    # the curve of the cylinder, and it casts a shadow on the flange in front of it
    r = 1.6 + 3.0 * fill
    top_y = 5
    for x in range(16):
        across = (x + 0.5 - cx) / max(r, 0.01)  # -1 left .. 1 right
        if abs(across) > 1:
            continue
        base = 12.3 + 1.4 * math.sqrt(max(0.0, 1 - across * across))  # front arc of the cylinder's foot
        for y in range(top_y, int(base)):
            # light from the left, a bright band a third of the way across, rows of wire
            level = 4 - round(across * 2.4)
            if -0.55 < across < -0.15:
                level += 1
            if y % 2 == 0:
                level -= 1
            level = max(1, min(len(wire) - 1, level))
            s.set(x, y, wire[level] if fill > 0.05 else br[3 if across < 0 else 2])
        # the shadow it leaves on the flange, right below its foot
        if (x, int(base)) in surface:
            s.set(x, int(base), br[3] if across < 0.3 else br[2])
    # top flange, thin, with a small centre hole and a highlight on its near-left rim
    for (x, y) in ellipse(cx, 4.9, 6.5, 2.9):
        s.set(x, y, br[2])
    top = ellipse(cx, 4.2, 6.5, 2.9)
    for (x, y) in top:
        s.set(x, y, br[5])
    for (x, y) in top:
        if (x, y + 1) not in top and x < cx:
            s.set(x, y, br[6])
        if (x, y - 1) not in top:
            s.set(x, y, br[4])
    for (x, y) in ellipse(cx, 4.2, 1.5, 0.9):
        s.set(x, y, iso.SLATE[0])
    s.set(cx - 2, 3, br[7])
    # the loose end of the wire, hanging over the front edge
    if fill > 0.05:
        s.set(cx + int(r) - 1, 13, wire[3])
        s.set(cx + int(r) - 1, 14, wire[2])
    iso.outline(s, br)
    return s.img




def item_model(texture):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    (MODELS / "cable_spool").mkdir(parents=True, exist_ok=True)
    colours = [("copper", iso.COPPER)] + [(name, ramp_of(col)) for name, col in DYES.items()]
    spool(iso.COPPER, 0.0).save(TEX / "empty.png")
    write(MODELS / "cable_spool/empty.json", item_model("create_new_era:item/cable_spool/empty"))
    overrides = []
    # the last override that matches wins, so list them from the lowest predicates up
    for dye, (name, ramp) in enumerate(colours):
        overrides.append({"predicate": {"create_new_era:dye": dye, "create_new_era:fill": 0},
                          "model": "create_new_era:item/cable_spool/empty"})
        for fill, (level, amount) in enumerate([("half", 0.45), ("full", 1.0)], start=1):
            spool(ramp, amount).save(TEX / f"{name}_{level}.png")
            write(MODELS / f"cable_spool/{name}_{level}.json", item_model(f"create_new_era:item/cable_spool/{name}_{level}"))
            overrides.append({"predicate": {"create_new_era:dye": dye, "create_new_era:fill": fill},
                              "model": f"create_new_era:item/cable_spool/{name}_{level}"})
    model = item_model("create_new_era:item/cable_spool/copper_full")
    model["overrides"] = overrides
    write(MODELS / "cable_spool.json", model)
    print(f"{len(colours) * 2 + 1} spool sprites written to {TEX}")


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


if __name__ == "__main__":
    main()
