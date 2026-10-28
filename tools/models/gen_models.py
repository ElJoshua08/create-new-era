"""Generates block models from compact element lists.

Bodies use Create's casings by reference; details use our atlas (see
tools/textures/gen_textures.py for its regions). Output is plain vanilla
JSON, so Blockbench opens and edits it directly.

Run: python3 tools/models/gen_models.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/textures"))
from gen_textures import PART_REGIONS, REGIONS  # noqa: E402

OUT = ROOT / "src/main/resources/assets/create_new_era/models/block"
FACES = ["north", "south", "east", "west", "up", "down"]


def region(name, sub=None):
    """Atlas region as model uv (the atlas is 32 px, model uv spans 16)."""
    x, y, w, h = REGIONS[name]
    if sub:
        sx, sy, sw, sh = sub
        x, y, w, h = x + sx, y + sy, sw, sh
    return [x / 2, y / 2, (x + w) / 2, (y + h) / 2]


def part(name, sub=None):
    x, y, w, h = PART_REGIONS[name]
    if sub:
        sx, sy, sw, sh = sub
        x, y, w, h = x + sx, y + sy, sw, sh
    return [x / 2, y / 2, (x + w) / 2, (y + h) / 2]


def box(name, frm, to, faces, rotation=None, cull=False):
    """faces: {face: (texture_var, uv or None)}; 'sides' and 'all' expand.
    cull: faces lying on the block boundary get a cullface, so joined blocks hide them."""
    expanded = {}
    # group keys first so a specific face always overrides them
    groups = ("all", "sides", "caps")
    ordered = sorted(faces.items(), key=lambda kv: kv[0] not in groups)
    for key, val in ordered:
        targets = {"all": FACES, "sides": ["north", "south", "east", "west"], "caps": ["up", "down"]}.get(key, [key])
        for f in targets:
            expanded[f] = val
    out = {}
    for f, (tex, uv) in expanded.items():
        spec = {"texture": "#" + tex}
        if uv is not None:
            spec["uv"] = uv
        if cull and on_boundary(f, frm, to):
            spec["cullface"] = f
        out[f] = spec
    el = {"name": name, "from": frm, "to": to, "faces": out}
    if rotation:
        el["rotation"] = rotation
    return el


def on_boundary(face, frm, to):
    axis, value = {"north": (2, frm[2] == 0), "south": (2, to[2] == 16), "west": (0, frm[0] == 0),
                   "east": (0, to[0] == 16), "down": (1, frm[1] == 0), "up": (1, to[1] == 16)}[face]
    return value


def model(textures, elements):
    return {"credit": "Create: New Era", "parent": "block/block", "textures": textures, "elements": elements}


DETAILS = "create_new_era:block/computing_details"
PARTS = "create_new_era:block/interface_parts"
PANEL_TEX = {"casing": "create:block/andesite_casing", "parts": PARTS, "details": DETAILS, "particle": "create:block/andesite_casing"}


def panel(*elements):
    """Control-panel block: a small andesite plate on the attached face (down), parts on top."""
    base = box("base", [2, 0, 2], [14, 2, 14], {
        "up": ("casing", [2, 2, 14, 14]), "down": ("casing", [2, 2, 14, 14]),
        "sides": ("casing", [2, 14, 14, 16]),
    })
    return model(PANEL_TEX, [base, *elements])


def bezel(lo, hi):
    return box("bezel", [lo, 2, lo], [hi, 3, hi], {
        "up": ("parts", part("bezel")), "sides": ("details", region("brass_bar", (0, 0, hi - lo, 1))),
    })

MODELS = {
    "brass_computer": model(
        {"casing": "create:block/brass_casing", "details": DETAILS, "particle": "create:block/brass_casing"},
        [
            # frame pieces keep default uv so the casing texture lines up across them
            # body, then a back plate framing a 6x6 hole: the shaft driving the computer turns inside it
            box("body", [0, 0, 2], [16, 16, 12], {"all": ("casing", None)}),
            box("back_top", [0, 11, 12], [16, 16, 16], {"all": ("casing", None)}),
            box("back_bottom", [0, 0, 12], [16, 5, 16], {"all": ("casing", None)}),
            box("back_left", [0, 5, 12], [5, 11, 16], {"all": ("casing", None)}),
            box("back_right", [11, 5, 12], [16, 11, 16], {"all": ("casing", None)}),
            box("frame_top", [0, 13, 0], [16, 16, 2], {"all": ("casing", None)}),
            box("frame_bottom", [0, 0, 0], [16, 4, 2], {"all": ("casing", None)}),
            box("frame_left", [14, 4, 0], [16, 13, 2], {"all": ("casing", None)}),
            box("frame_right", [0, 4, 0], [2, 13, 2], {"all": ("casing", None)}),
            box("screen", [2, 4, 1], [14, 13, 2], {"north": ("details", region("screen"))}),
            box("led_power", [3, 1.5, -0.5], [5, 2.5, 0], {"all": ("details", region("led_green", (0, 0, 2, 1)))}),
            box("led_bus", [6, 1.5, -0.5], [8, 2.5, 0], {"all": ("details", region("led_amber", (0, 0, 2, 1)))}),
            box("port", [16, 6, 6], [16.5, 10, 10], {
                "east": ("details", region("port")),
                "sides": ("details", region("copper_bar", (0, 0, 1, 2))),
                "caps": ("details", region("copper_bar", (0, 0, 1, 2))),
            }),
        ],
    ),
    "bearing_servo": model(
        {"casing": "create:block/andesite_casing", "details": DETAILS, "particle": "create:block/andesite_casing"},
        [
            box("casing", [0, 0, 0.5], [16, 14, 16], {
                "sides": ("casing", None), "north": ("casing", [0, 2, 16, 16]), "south": ("casing", [0, 2, 16, 16]),
                "caps": ("casing", [0, 0, 16, 16]),
            }),
            box("rotor", [2, 14, 2], [14, 16, 14], {
                "up": ("details", region("rotor")),
                "sides": ("details", region("brass_bar", (0, 0, 12, 2))),
            }),
            box("dial", [4, 4, 0], [12, 12, 0.5], {
                "north": ("details", region("dial")),
                "sides": ("details", region("bezel", (0, 0, 8, 1))),
                "caps": ("details", region("bezel", (0, 0, 8, 1))),
            }),
        ],
    ),
    # points up: the plate sits on the face it was placed against
    "universal_adapter": model(
        {"plate": "create:block/andesite_casing", "details": DETAILS, "particle": "create:block/andesite_casing"},
        [
            box("plate", [1, 0, 1], [15, 3, 15], {
                "up": ("plate", [1, 1, 15, 15]), "down": ("plate", [1, 1, 15, 15]),
                "sides": ("plate", [1, 13, 15, 16]),
            }),
            box("body", [3, 3, 3], [13, 9, 13], {
                "up": ("details", region("adapter_face")),
                "sides": ("details", region("brass_panel")),
            }),
            box("port", [6, 9, 6], [10, 10, 10], {
                "up": ("details", region("port")),
                "sides": ("details", region("copper_bar", (0, 0, 4, 1))),
            }),
        ],
    ),
    "panel_button": panel(
        bezel(5, 11),
        box("cap", [6, 3, 6], [10, 5, 10], {"up": ("parts", part("cap_red")), "sides": ("parts", part("cap_red_side"))}),
    ),
    "panel_switch": panel(
        bezel(6, 10),
        box("lever", [7.5, 2.5, 7.5], [8.5, 10, 8.5], {"all": ("parts", part("lever_bar", (0, 0, 1, 8)))},
            {"angle": 22.5, "axis": "x", "origin": [8, 2.5, 8]}),
        box("tip", [7, 9.5, 7], [9, 11.5, 9], {"all": ("parts", part("lever_knob"))},
            {"angle": 22.5, "axis": "x", "origin": [8, 2.5, 8]}),
    ),
    "panel_knob": panel(
        box("knob", [5, 2, 5], [11, 5, 11], {"up": ("parts", part("knob_top")), "sides": ("parts", part("knob_side", (0, 0, 6, 2)))}),
    ),
    "panel_slider": panel(
        box("track", [2.5, 2, 6], [13.5, 2.5, 10], {"up": ("parts", part("track")), "sides": ("parts", part("slate_side", (0, 0, 11, 1)))}),
        box("handle", [7, 2.5, 6.5], [10, 5, 9.5], {"up": ("parts", part("handle_top")), "sides": ("parts", part("handle_side"))}),
    ),
    "panel_lamp": panel(
        bezel(5, 11),
        box("lamp", [6, 3, 6], [10, 6, 10], {"up": ("parts", part("lamp_amber")), "sides": ("parts", part("lamp_amber_side"))}),
    ),
    "nixie_digit": panel(
        box("socket", [4, 2, 5], [12, 3, 11], {"up": ("parts", part("bezel")), "sides": ("details", region("brass_bar", (0, 0, 8, 1)))}),
        box("tube", [5, 3, 6], [11, 13, 10], {
            "north": ("parts", part("nixie_face")), "south": ("parts", part("nixie_face")),
            "east": ("parts", part("nixie_side")), "west": ("parts", part("nixie_side")),
            "up": ("parts", part("nixie_top")),
        }),
        box("dome", [6, 13, 7], [10, 14, 9], {"all": ("parts", part("nixie_top", (1, 1, 4, 2)))}),
    ),
    "panel_gauge": panel(
        box("gauge", [3, 2, 3], [13, 4, 13], {"up": ("parts", part("gauge_face")), "sides": ("details", region("brass_bar", (0, 0, 10, 2)))}),
    ),
    "panel_keypad": panel(
        box("keys", [3, 2, 2], [13, 3, 14], {"up": ("parts", part("keypad")), "sides": ("parts", part("slate_side", (0, 0, 10, 1)))}),
    ),
}


# Brass screen, front facing north. Screens next to each other (same facing) join:
# a side with a neighbour swaps its brass frame strip for glass, so the wall reads as one pane.
# The blockstate (datagen, ModBlocks) picks the pieces. Seen from the front, +x is the
# screen's left. Corners: frame unless both adjoining sides are joined.
SCREEN_TEX = {"casing": "create:block/brass_casing", "glass": "create_new_era:block/screen_glass",
              "particle": "create:block/brass_casing"}
SCREEN_EDGES = {
    "up": ([2, 14, 0], [14, 16, 2]),
    "down": ([2, 0, 0], [14, 2, 2]),
    "left": ([14, 2, 0], [16, 14, 2]),
    "right": ([0, 2, 0], [2, 14, 2]),
}
SCREEN_CORNERS = {
    "up_left": ([14, 14, 0], [16, 16, 2]),
    "up_right": ([0, 14, 0], [2, 16, 2]),
    "down_left": ([14, 0, 0], [16, 2, 2]),
    "down_right": ([0, 0, 0], [2, 2, 2]),
}


def screen_frame(name, frm, to):
    # default uv keeps the casing texture lined up across pieces
    return box(name, frm, to, {"all": ("casing", None)}, cull=True)


def screen_glass(name, frm, to):
    return box(name, [frm[0], frm[1], 1.5], [to[0], to[1], 1.5], {"north": ("glass", None)})


def screen_models():
    core = [box("back", [0, 0, 2], [16, 16, 16], {"all": ("casing", None)}, cull=True),
            screen_glass("glass", [2, 2], [14, 14])]
    models = {"brass_screen_core": model(SCREEN_TEX, core)}
    for side, (frm, to) in {**SCREEN_EDGES, **SCREEN_CORNERS}.items():
        models[f"brass_screen_frame_{side}"] = model(SCREEN_TEX, [screen_frame(side, frm, to)])
        models[f"brass_screen_glass_{side}"] = model(SCREEN_TEX, [screen_glass(side, frm, to)])
    # a lone screen, for the item and anything that wants the whole model
    lone = core + [screen_frame(side, frm, to) for side, (frm, to) in {**SCREEN_EDGES, **SCREEN_CORNERS}.items()]
    models["brass_screen"] = model(SCREEN_TEX, lone)
    return models


MODELS.update(screen_models())


def computer_off():
    """The computer with no program running: same body, dark screen glass."""
    m = json.loads(json.dumps(MODELS["brass_computer"]))
    m["textures"]["parts"] = PARTS
    for el in m["elements"]:
        if el["name"] == "screen":
            el["faces"]["north"] = {"texture": "#parts", "uv": part("screen_off", (0, 0, 12, 9))}
        if el["name"] == "led_bus":
            el["faces"] = {f: {"texture": "#details", "uv": region("led_red", (0, 0, 2, 1))} for f in el["faces"]}
    return m


MODELS["brass_computer_off"] = computer_off()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, m in MODELS.items():
        (OUT / f"{name}.json").write_text(json.dumps(m, indent=2) + "\n")
    print(f"{len(MODELS)} models written to {OUT}")


if __name__ == "__main__":
    main()
