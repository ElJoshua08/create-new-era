"""Writes the structure templates our Ponder scenes play out on.

Each scene is a list of blocks over Create's usual white concrete / snow checker plate.
The scenes themselves (what appears when, the text) live in
src/main/java/com/createnewera/ponder/NewEraPonderScenes.java; positions here must match.

Run: python3 tools/ponder/gen_ponder.py
"""
from pathlib import Path

import nbt

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/create_new_era/ponder"
DATA_VERSION = 3955  # 1.21.1

M = "create_new_era:"


def screen(facing="south", up=False, down=False, left=False, right=False):
    flags = {"up": up, "down": down, "left": left, "right": right}
    return M + "brass_screen", {"facing": facing, **{k: str(v).lower() for k, v in flags.items()}}


def part(name, facing):
    return M + name, {"facing": facing}


def casing():
    return "create:andesite_casing", {}


SCENES = {
    # Ponder's camera looks at the north and west faces, so everything faces north, towards it.
    # Seen from there, +x is on the left. The computer is driven from behind (south).
    "computer/intro": ((5, 3, 5), {
        (2, 1, 4): ("create:creative_motor", {"facing": "north"}),
        (2, 1, 3): ("create:shaft", {"axis": "z", "waterlogged": "false"}),
        (2, 1, 2): (M + "brass_computer", {"facing": "north", "running": "false"}),
        (3, 1, 2): ("minecraft:redstone_lamp", {"lit": "false"}),
        (1, 1, 2): ("minecraft:lever", {"face": "floor", "facing": "west", "powered": "false"}),
    }),
    # lone screens: the scene joins them up as they appear, like placing them would
    "screen/join": ((5, 4, 5), {
        (x, y, 2): screen("north") for x in (1, 2, 3) for y in (1, 2)
    }),
    "adapter/attach": ((5, 3, 5), {
        (2, 1, 2): ("minecraft:barrel", {"facing": "up", "open": "false"}),
        (2, 1, 1): part("universal_adapter", "north"),
        (2, 2, 2): part("universal_adapter", "up"),
    }),
    "servo/intro": ((5, 4, 5), {
        (2, 1, 2): part("bearing_servo", "up"),
        (1, 2, 2): ("minecraft:oak_planks", {}),
        (2, 2, 2): ("minecraft:oak_planks", {}),
        (3, 2, 2): ("minecraft:oak_planks", {}),
    }),
    # a desk: a counter with controls on top and on its front, a back wall with nixies along the top
    "controls/desk": ((5, 5, 5), {
        **{(x, 1, 2): casing() for x in (1, 2, 3)},
        **{(x, y, 3): casing() for x in (1, 2, 3) for y in (1, 2)},
        (3, 2, 2): part("panel_button", "up"),
        (2, 2, 2): part("panel_switch", "up"),
        (1, 2, 2): part("panel_slider", "up"),
        (3, 1, 1): part("panel_knob", "north"),
        (2, 1, 1): part("panel_lamp", "north"),
        (1, 1, 1): part("panel_keypad", "north"),
        **{(x, 3, 3): (M + "nixie_digit", {"facing": "up", "reads": "north", "lit": "false"}) for x in (1, 2, 3)},
    }),
    # a computer driven from behind, a chest on its right (west) and a barrel on its left (east)
    "bus/network": ((5, 3, 5), {
        (2, 1, 4): ("create:creative_motor", {"facing": "north"}),
        (2, 1, 3): ("create:shaft", {"axis": "z", "waterlogged": "false"}),
        (2, 1, 2): (M + "brass_computer", {"facing": "north", "running": "false"}),
        (0, 1, 2): ("minecraft:chest", {"facing": "north", "type": "single", "waterlogged": "false"}),
        (4, 1, 2): ("minecraft:barrel", {"facing": "up", "open": "false"}),
    }),
    # a flight deck: instruments along a brass counter, read from the north, a lamp in the middle
    "instruments/flight": ((5, 3, 5), {
        **{(x, 1, 2): ("create:brass_casing", {}) for x in (0, 1, 2, 3, 4)},
        (4, 2, 2): (M + "panel_altimeter", {"facing": "up", "reads": "north"}),
        (3, 2, 2): (M + "panel_gyroscope", {"facing": "up", "reads": "north"}),
        (2, 2, 2): (M + "panel_lamp", {"facing": "up", "light": "green"}),
        (1, 2, 2): (M + "panel_compass", {"facing": "up", "reads": "north"}),
        (0, 2, 2): (M + "panel_airspeed", {"facing": "up", "reads": "north"}),
    }),
}


def structure(size, blocks):
    w, _, d = size
    palette, index = [], {}

    def state(name, props):
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            entry = {"Name": name}
            if props:
                entry["Properties"] = dict(props)
            index[key] = len(palette)
            palette.append(entry)
        return index[key]

    out = []
    for x in range(w):
        for z in range(d):
            plate = "minecraft:white_concrete" if (x + z) % 2 == 0 else "minecraft:snow_block"
            out.append({"pos": nbt.NbtList(3, [nbt.Int(x), nbt.Int(0), nbt.Int(z)]), "state": nbt.Int(state(plate, {}))})
    for (x, y, z), (name, props) in sorted(blocks.items()):
        out.append({"pos": nbt.NbtList(3, [nbt.Int(x), nbt.Int(y), nbt.Int(z)]), "state": nbt.Int(state(name, props))})
    return {
        "size": nbt.NbtList(3, [nbt.Int(v) for v in size]),
        "entities": nbt.NbtList(10),
        "blocks": nbt.NbtList(10, out),
        "palette": nbt.NbtList(10, palette),
        "DataVersion": nbt.Int(DATA_VERSION),
    }


def main():
    for name, (size, blocks) in SCENES.items():
        path = OUT / f"{name}.nbt"
        path.parent.mkdir(parents=True, exist_ok=True)
        nbt.write(path, structure(size, blocks))
    print(f"{len(SCENES)} ponder structures written to {OUT}")


if __name__ == "__main__":
    main()
