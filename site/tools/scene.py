"""Renders the hero diorama for the landing page from the mod's and Create's real block models.

The scene is drawn once as a still (base.png) and again for every pose of each moving part
(the cogwheel, the computer's screen, the motor shaft, the press). Only the pixels that change
inside a group are kept in its sprite sheet, so the page can lay the frames over the still in
any combination without one group painting over another.

Usage:
  python3 site/tools/scene.py --create-assets DIR --vanilla-assets DIR [--out site/public/hero]
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "tools" / "render"))
from iso_render import Assets, default_uv, face_corners, flatten, homography, resolve, rot_matrix  # noqa: E402

SS = 2  # supersampling


def shade_for(normal):
    n = normal / (np.linalg.norm(normal) + 1e-9)
    x, y, z = n * n
    up = 1.0 if n[1] > 0 else 0.5
    return x * 0.6 + z * 0.8 + y * up


class Scene:
    def __init__(self, assets):
        self.assets = assets
        self.quads = []  # (corners[4][3] world px, texture ref or array, uvs[4][2] in 0..16, group)

    def model(self, ref, pos, pre=None, group=None, origin=(8, 8, 8), cull=None):
        """Adds a model at block pos. pre is a 3x3 matrix applied about origin (block centre)."""
        textures, elements = flatten(self.assets, ref)
        o = np.array(origin, float)
        base = np.array(pos, float) * 16
        for el, local in elements:
            tex_map = local or textures
            f, t = el["from"], el["to"]
            rot = el.get("rotation")
            for face, spec in el.get("faces", {}).items():
                if cull and face in cull:
                    continue
                corners = [np.array(c, float) for c in face_corners(f, t, face)]
                if rot and rot.get("angle"):
                    m = rot_matrix(rot["axis"], rot["angle"])
                    ro = np.array(rot["origin"], float)
                    corners = [m @ (c - ro) + ro for c in corners]
                if pre is not None:
                    corners = [pre @ (c - o) + o for c in corners]
                corners = [c + base for c in corners]
                ref_t = resolve(tex_map, spec["texture"])
                u1, v1, u2, v2 = spec.get("uv", default_uv(face, f, t))
                uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
                r = (spec.get("rotation", 0) // 90) % 4
                uvs = uvs[r:] + uvs[:r]
                self.quads.append((corners, ref_t, uvs, group))

    def cube(self, tex, pos, faces=("up", "north", "south", "east", "west", "down"), group=None):
        for face in faces:
            corners = [np.array(c, float) + np.array(pos, float) * 16 for c in face_corners((0, 0, 0), (16, 16, 16), face)]
            self.quads.append((corners, tex, [(0, 0), (16, 0), (16, 16), (0, 16)], group))

    def quad(self, corners, tex, uvs=((0, 0), (16, 0), (16, 16), (0, 16)), group=None):
        self.quads.append(([np.array(c, float) for c in corners], tex, list(uvs), group))

    def tube(self, points, radius, tex, group=None):
        """A square tube along a polyline of world-pixel points, the way the cable renderer draws them."""
        pts = [np.array(p, float) for p in points]
        along = 0.0
        for a, b in zip(pts, pts[1:]):
            d = b - a
            length = np.linalg.norm(d)
            if length < 1e-6:
                continue
            d /= length
            side = np.cross(d, [0, 1, 0])
            if np.linalg.norm(side) < 1e-6:
                side = np.array([1.0, 0, 0])
            side /= np.linalg.norm(side)
            up = np.cross(side, d)
            v0 = along % 16
            if v0 + length > 16:
                v0 = 0
            v1 = v0 + min(length, 16)
            along += length
            ring = [side * radius + up * radius, -side * radius + up * radius,
                    -side * radius - up * radius, side * radius - up * radius]
            for i in range(4):
                p, q = ring[i], ring[(i + 1) % 4]
                u = i * 4
                # outward winding: start corner, next corner, along the segment
                self.quads.append(([a + p, a + q, b + q, b + p], tex, [(u, v0), (u + 4, v0), (u + 4, v1), (u, v1)], group))


class Camera:
    def __init__(self, pitch=30.0, yaw=225.0, scale=1.0):
        vy = math.radians(yaw)
        p = math.radians(pitch)
        fwd = np.array([math.cos(p) * math.cos(vy), -math.sin(p), math.cos(p) * math.sin(vy)])
        right = np.cross(fwd, [0, 1, 0])
        right /= np.linalg.norm(right)
        self.fwd, self.right, self.up = fwd, right, np.cross(right, fwd)
        self.scale = scale
        self.offset = np.zeros(2)

    def project(self, pt):
        q = np.asarray(pt, float)
        return np.array([q @ self.right * self.scale, -(q @ self.up) * self.scale]) + self.offset, q @ self.fwd


def render(scene, cam, size, assets, groups_state=None):
    w, h = size
    color = np.zeros((h, w, 4), np.float32)
    depth = np.full((h, w), np.inf, np.float32)
    for corners, tex_ref, uvs, group in scene.quads:
        if isinstance(tex_ref, np.ndarray):
            tex = tex_ref
        else:
            try:
                tex = assets.texture(tex_ref)
            except FileNotFoundError:
                continue
        proj = [cam.project(c) for c in corners]
        pts = np.array([p for p, _ in proj])
        depths = [d for _, d in proj]
        e1, e2 = pts[1] - pts[0], pts[3] - pts[0]
        if e1[0] * e2[1] - e1[1] * e2[0] < 1e-6:
            continue
        normal = np.cross(corners[1] - corners[0], corners[3] - corners[0])
        shade = shade_for(-normal)
        th, tw = tex.shape[:2]
        hm = homography(pts, [(u / 16 * tw, v / 16 * th) for u, v in uvs])
        h_uv = homography(pts, [(0, 0), (1, 0), (1, 1), (0, 1)])
        x0, y0 = np.floor(pts.min(0)).astype(int).clip(0, [w - 1, h - 1])
        x1, y1 = np.ceil(pts.max(0)).astype(int).clip(0, [w - 1, h - 1])
        if x1 <= x0 or y1 <= y0:
            continue
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        pix = np.stack([xs + 0.5, ys + 0.5, np.ones_like(xs, dtype=np.float64)], -1)
        st = pix @ h_uv.T
        s, tt = st[..., 0] / st[..., 2], st[..., 1] / st[..., 2]
        inside = (s >= 0) & (s <= 1) & (tt >= 0) & (tt <= 1)
        tc = pix @ hm.T
        tu = np.clip(np.floor(tc[..., 0] / tc[..., 2]).astype(int), 0, tw - 1)
        tv = np.clip(np.floor(tc[..., 1] / tc[..., 2]).astype(int), 0, th - 1)
        z = depths[0] + (depths[1] - depths[0]) * s + (depths[3] - depths[0]) * tt
        texel = tex[tv, tu]
        mask = inside & (texel[..., 3] > 0.5) & (z < depth[y0:y1 + 1, x0:x1 + 1])
        region = color[y0:y1 + 1, x0:x1 + 1]
        region[mask, :3] = texel[mask, :3] * shade
        region[mask, 3] = 1.0
        depth[y0:y1 + 1, x0:x1 + 1][mask] = z[mask]
    img = Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8), "RGBA")
    return img.reduce(SS) if SS > 1 else img


# --- the scene -------------------------------------------------------------------------------

def rot_y_state(deg):
    """Blockstate y rotation: y=90 turns north to east."""
    return rot_matrix("y", -deg)


AXIS_Z = rot_matrix("x", 90)   # a model authored along y, turned to lie along z
AXIS_X = rot_matrix("z", -90)  # ... along x

FACE_ROT = {"up": np.eye(3), "south": rot_matrix("x", 90), "east": rot_matrix("z", -90),
            "north": rot_matrix("x", -90), "west": rot_matrix("z", 90)}
FACE_DIR = {"up": (0, 1, 0), "south": (0, 0, 1), "east": (1, 0, 0), "north": (0, 0, -1), "west": (-1, 0, 0)}


def port(scene, ref, block, face, u, v, group=None):
    """A data port (authored pointing up, 4x4) on a face of block. Returns its cable anchor in world px."""
    outside = np.array(block) + np.array(FACE_DIR[face])
    m = FACE_ROT[face]
    textures, elements = flatten(scene.assets, ref)
    pre = m
    # shift the authored part to its spot, then turn it onto the face about the block centre
    shift = np.array([u, 0, v], float)
    o = np.array([8, 8, 8], float)
    for el, local in elements:
        tex_map = local or textures
        f, t = el["from"], el["to"]
        for face_name, spec in el.get("faces", {}).items():
            corners = [np.array(c, float) + shift for c in face_corners(f, t, face_name)]
            # authored with its base at y=0 of the block outside the face
            corners = [pre @ (c - o) + o + outside * 16 for c in corners]
            ref_t = resolve(tex_map, spec["texture"])
            u1, v1, u2, v2 = spec.get("uv", default_uv(face_name, f, t))
            uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            r = (spec.get("rotation", 0) // 90) % 4
            scene.quads.append((corners, ref_t, uvs[r:] + uvs[:r], group))
    centre = np.array([u + 2, 1.0, v + 2], float)
    return pre @ (centre - o) + o + outside * 16


def sag_line(a, b, n=24):
    a, b = np.array(a, float), np.array(b, float)
    length = np.linalg.norm(b - a) / 16
    flat = math.hypot(*(b - a)[[0, 2]]) / 16
    sag = min(0.15 + length * 0.05, 1.4) * (flat / length if length > 1e-4 else 0) * 16
    return [a + (b - a) * (i / n) - np.array([0, sag * 4 * (i / n) * (1 - i / n), 0]) for i in range(n + 1)]


CUT = "create:block/palettes/stone_types/cut/andesite_cut"
POLISHED = "create:block/palettes/stone_types/polished/andesite_cut_polished"
COPPER = "create_new_era:block/cable/copper"
SPIN_Z = lambda deg: rot_matrix("z", deg) @ AXIS_Z  # noqa: E731
SPIN_X = lambda deg: rot_matrix("x", deg) @ AXIS_X  # noqa: E731


def floor(s, w, d, pit=()):
    pit = set(pit)
    for x in range(w):
        for z in range(d):
            if (x, z) in pit:
                continue
            faces = ["up"]
            if z == d - 1 or (x, z + 1) in pit:
                faces.append("south")
            if x == w - 1 or (x + 1, z) in pit:
                faces.append("east")
            if (x - 1, z) in pit:
                faces.append("west")
            s.cube(POLISHED if (x + z) % 2 else CUT, (x, 0, z), faces)
    for x, z in pit:
        s.cube(CUT, (x, -1, z), ["up"])


def screen_wall(s, x0, y0, z, w, h, joined=True, group=None):
    """Brass screens facing south. Joined, the frames between them give way to glass."""
    for i in range(w):
        for j in range(h):
            left, right = joined and i > 0, joined and i < w - 1
            up, down = joined and j < h - 1, joined and j > 0
            parts = ["brass_screen_core",
                     f"brass_screen_{'glass' if left else 'frame'}_left",
                     f"brass_screen_{'glass' if right else 'frame'}_right",
                     f"brass_screen_{'glass' if up else 'frame'}_up",
                     f"brass_screen_{'glass' if down else 'frame'}_down"]
            for corner, (cu, cl) in {"up_left": (up, left), "up_right": (up, right),
                                     "down_left": (down, left), "down_right": (down, right)}.items():
                parts.append(f"brass_screen_{'glass' if (cu and cl) else 'frame'}_{corner}")
            for p in parts:
                s.model("create_new_era:block/" + p, (x0 + i, y0 + j, z), rot_y_state(180), group=group)


def screen_face(x0, y0, z, w, h):
    zf = (z + 1) * 16 - 1.5
    a, b = x0 * 16 + 2, (x0 + w) * 16 - 2
    c, d = y0 * 16 + 2, (y0 + h) * 16 - 2
    return {"tl": (a, d, zf), "tr": (b, d, zf), "bl": (a, c, zf)}


def cog_and_computer(s, state, cog_at, computer_at):
    a = state["cog"]
    s.model("create:block/large_cogwheel", cog_at, SPIN_Z(a), group="cog")
    for z in range(cog_at[2] + 1, computer_at[2]):
        s.model("create:block/shaft", (cog_at[0], cog_at[1], z), SPIN_Z(-a), group="cog")
    computer = "create_new_era:block/brass_computer" if state["on"] else "create_new_era:block/brass_computer_off"
    s.model(computer, computer_at, rot_y_state(180), group="computer")


# --- scenes ----------------------------------------------------------------------------------
# Each returns (scene, anchors, cables, faces): anchors are named points, cables named polylines
# and faces named screen surfaces, all in world pixels, for the page to animate over.

def hero(assets, st):
    s = Scene(assets)
    floor(s, 7, 4, pit=[(0, 0), (1, 0), (2, 0)])
    cog_and_computer(s, st, (1, 1, 0), (1, 1, 2))
    screen_wall(s, 3, 2, 0, 3, 2)
    for x in range(3, 6):
        s.model("create:block/andesite_casing", (x, 1, 0))

    # press line along the right edge: a creative motor on a casing pillar at the back,
    # a shaft forward to the press, a depot under it
    s.model("create:block/depot/block", (6, 1, 3))
    press = (6, 3, 3)
    s.model("create:block/mechanical_press/block", press)
    drop = st["press"] * (16 + 3)
    s.model("create:block/mechanical_press/head", (press[0], press[1] - drop / 16, press[2]), group="press")
    for z in (1, 2, 3):
        s.model("create:block/shaft", (6, 3, z), SPIN_Z(st["motor"]), group="motor")
    for y in (1, 2):
        s.model("create:block/andesite_casing", (6, y, 0))
    s.model("create:block/creative_motor/block", (6, 3, 0), rot_y_state(180))
    item = "create:item/iron_sheet" if st["item"] else "minecraft:item/iron_ingot"
    top, cx, cz = 16 + 13.01, 6 * 16 + 8, 3 * 16 + 8
    s.quad([(cx - 5, top, cz - 5), (cx + 5, top, cz - 5), (cx + 5, top, cz + 5), (cx - 5, top, cz + 5)], item, group="press")

    s.model("create_new_era:block/nixie_digit", (0, 1, 3), rot_y_state(180))
    s.model("create_new_era:block/nixie_digit", (1, 1, 3), rot_y_state(180))
    s.model("create_new_era:block/panel_lamp", (2, 1, 3), rot_y_state(180))
    s.model("create:block/item_vault", (3, 1, 2))

    an = {
        "computer_top": port(s, "create_new_era:block/bus_port_out", (1, 1, 2), "up", 6, 6),
        "computer_east": port(s, "create_new_era:block/bus_port_out", (1, 1, 2), "east", 6, 5),
        "screen_top": port(s, "create_new_era:block/bus_port_in", (3, 3, 0), "up", 6, 9),
        "motor_top": port(s, "create_new_era:block/bus_port_in", (6, 3, 0), "up", 6, 6),
        "vault_top": port(s, "create_new_era:block/bus_port_out", (3, 1, 2), "up", 6, 6),
    }
    r = 1.0
    va, ce = an["vault_top"], an["computer_east"]
    lift = r + 0.05
    cables = {
        "screen": sag_line(an["computer_top"], an["screen_top"]),
        "motor": sag_line(an["computer_east"], an["motor_top"], 32),
        # flat over the vault, down its side, along the floor and up to the computer's port
        "vault": [va, np.array([3 * 16 - lift, va[1], va[2]]), np.array([3 * 16 - lift, 16 + lift, va[2]]),
                  np.array([ce[0], 16 + lift, va[2]]), ce],
    }
    for pts in cables.values():
        s.tube(pts, r, COPPER)
    an["press_head"] = np.array([6 * 16 + 8, 2 * 16 + 8, 3 * 16 + 8])
    an["depot_top"] = np.array([cx, top, cz])
    return s, an, cables, {"screen": screen_face(3, 2, 0, 3, 2)}


HERO_REST = {"cog": 0.0, "on": False, "motor": 0.0, "press": 0.0, "item": 0}
HERO_POSES = {
    "cog": [dict(HERO_REST, cog=i * 90 / 12) for i in range(12)],
    "motor": [dict(HERO_REST, motor=i * 90 / 8) for i in range(8)],
    "computer": [dict(HERO_REST, on=False), dict(HERO_REST, on=True)],
    "press": [dict(HERO_REST, press=e / 7, item=it) for it in (0, 1) for e in range(8)],
}


def power(assets, st):
    """The computer and the cogwheel that drives it, for the speed demo."""
    s = Scene(assets)
    floor(s, 3, 3, pit=[(0, 0), (1, 0), (2, 0)])
    cog_and_computer(s, st, (1, 1, 0), (1, 1, 2))
    return s, {}, {}, {}


POWER_REST = {"cog": 0.0, "on": True}
POWER_POSES = {
    "cog": [dict(POWER_REST, cog=i * 90 / 12) for i in range(12)],
    "computer": [dict(POWER_REST, on=False), dict(POWER_REST, on=True)],
}

DYES = ["copper", "orange", "light_blue", "lime", "magenta", "white", "red"]


def bus(assets, st):
    """Ports on Create machines: copper flat along the floor, a dyed network hanging overhead."""
    s = Scene(assets)
    floor(s, 5, 3)
    s.model("create_new_era:block/brass_computer", (0, 1, 1), rot_y_state(180))
    s.model("create:block/fluid_tank/block_single_window", (2, 1, 0))
    s.model("create:block/item_vault", (4, 1, 0))
    s.model("create:block/encased_fan/block", (4, 1, 2), rot_y_state(180))
    exp = port(s, "create_new_era:block/port_expansion", (0, 1, 1), "up", 5, 5)
    an = {
        "computer_east": port(s, "create_new_era:block/bus_port_out", (0, 1, 1), "east", 6, 5),
        "tank_south": port(s, "create_new_era:block/bus_port_out", (2, 1, 0), "south", 6, 6),
        "vault_top": port(s, "create_new_era:block/bus_port_out", (4, 1, 0), "up", 6, 6),
        "fan_top": port(s, "create_new_era:block/bus_port_in", (4, 1, 2), "up", 6, 6),
        "expansion": exp + np.array([1.0, 0, 1.0]),
    }
    dye = "create_new_era:block/cable/" + DYES[st["dye"]]
    r = 1.0
    lift = r + 0.05
    ce, tk = an["computer_east"], an["tank_south"]
    cables = {
        "tank": [ce, np.array([ce[0], 16 + lift, ce[2]]), np.array([tk[0], 16 + lift, ce[2]]),
                 np.array([tk[0], 16 + lift, tk[2]]), tk],
        "fan": sag_line(an["expansion"], an["fan_top"], 32),
        "vault": sag_line(an["fan_top"], an["vault_top"]),
    }
    s.tube(cables["tank"], r, COPPER)
    s.tube(cables["vault"], r, dye, group="dye")
    s.tube(cables["fan"], r, dye, group="dye")
    return s, an, cables, {}


BUS_REST = {"dye": 0}
BUS_POSES = {"dye": [dict(BUS_REST, dye=i) for i in range(len(DYES))]}


def encased(assets, st):
    """A cable run through brass, andesite and copper casing."""
    s = Scene(assets)
    floor(s, 4, 1)
    pts = [np.array([x, 16 + 2.05, 8.0]) for x in (0.0, 64.0)]
    s.tube([pts[0], np.array([21.0, 18.05, 8])], 2.0, "create:block/andesite_casing")
    s.tube([np.array([21.0, 18.05, 8]), np.array([43.0, 18.05, 8])], 2.0, "create:block/brass_casing")
    s.tube([np.array([43.0, 18.05, 8]), pts[1]], 2.0, "create:block/copper_casing")
    return s, {}, {}, {}


def desk(assets, st):
    """A control desk: every panel control on a row of casings."""
    s = Scene(assets)
    controls = ["panel_button", "panel_switch", "panel_knob", "panel_slider", "panel_lamp",
                "nixie_digit", "nixie_digit", "panel_gauge", "panel_keypad"]
    for x in range(5):
        for z in range(2):
            s.model("create:block/brass_casing" if z else "create:block/andesite_casing", (x, 0, z))
    for k, c in enumerate(controls):
        x, z = k % 5, k // 5
        s.model("create_new_era:block/" + c, (x, 1, z), rot_y_state(180))
    return s, {}, {}, {}


def wall(assets, st):
    """A big pane of screens; the pose shows them apart and joined."""
    s = Scene(assets)
    screen_wall(s, 0, 0, 0, 4, 3, joined=st["joined"], group="join")
    return s, {}, {}, {"screen": screen_face(0, 0, 0, 4, 3)}


SCENES = {
    "hero": (hero, HERO_REST, HERO_POSES),
    "power": (power, POWER_REST, POWER_POSES),
    "bus": (bus, BUS_REST, BUS_POSES),
    "encased": (encased, {}, {}),
    "desk": (desk, {}, {}),
    "wall": (wall, {"joined": True}, {"join": [{"joined": False}, {"joined": True}]}),
}


def make(name, assets, out, scale, still=False):
    build_fn, rest, poses = SCENES[name]
    scene, anchors, cables, faces = build_fn(assets, rest)
    cam = Camera(scale=scale * SS)
    pts = np.array([cam.project(c)[0] for q in scene.quads for c in q[0]])
    lo, hi = pts.min(0), pts.max(0)
    pad = 8 * SS
    cam.offset = -lo + pad
    size = tuple((np.ceil(hi - lo) + 2 * pad).astype(int))
    size = (size[0] + (-size[0]) % (2 * SS), size[1] + (-size[1]) % (2 * SS))

    out = out / name
    out.mkdir(parents=True, exist_ok=True)
    base = render(scene, cam, size, assets)
    base.save(out / "base.png", optimize=True)
    if still:
        print(name, "still", base.size)
        return

    def proj(p):
        return [round(float(v) / SS, 2) for v in cam.project(p)[0]]

    meta = {"width": base.width, "height": base.height, "groups": {},
            "cables": {k: [proj(p) for p in v] for k, v in cables.items()},
            "anchors": {k: proj(p) for k, p in anchors.items()},
            "faces": {k: {c: proj(p) for c, p in f.items()} for k, f in faces.items()}}

    masks, frames = {}, {}
    base_arr = np.asarray(base).astype(np.int16)
    for g, states in poses.items():
        arrs = [np.asarray(render(build_fn(assets, st)[0], cam, size, assets)).astype(np.int16) for st in states]
        mask = np.zeros(arrs[0].shape[:2], bool)
        for arr in arrs:
            mask |= np.abs(arr - base_arr).sum(-1) > 0
        masks[g], frames[g] = mask, arrs
        print(name, g, len(states), int(mask.sum()))
    names = list(masks)
    for i, g in enumerate(names):
        for h in names[i + 1:]:
            if (both := int((masks[g] & masks[h]).sum())):
                print(f"warning: {name}: {g} and {h} share {both} pixels")

    for g, arrs in frames.items():
        ys, xs = np.nonzero(masks[g])
        if not len(xs):
            continue
        bx0, bx1, by0, by1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
        fw, fh = bx1 - bx0, by1 - by0
        sheet = np.zeros((fh, fw * len(arrs), 4), np.uint8)
        for k, arr in enumerate(arrs):
            crop = arr[by0:by1, bx0:bx1].astype(np.uint8).copy()
            crop[~masks[g][by0:by1, bx0:bx1]] = 0
            sheet[:, k * fw:(k + 1) * fw] = crop
        Image.fromarray(sheet, "RGBA").save(out / f"{g}.png", optimize=True)
        meta["groups"][g] = {"x": int(bx0), "y": int(by0), "w": int(fw), "h": int(fh), "frames": len(arrs)}
    (out / "scene.json").write_text(json.dumps(meta))
    print(name, "done", base.size)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--create-assets", required=True)
    ap.add_argument("--vanilla-assets", required=True)
    ap.add_argument("--out", default=str(REPO / "site" / "public" / "scenes"))
    ap.add_argument("--scale", type=float, default=8.0, help="screen pixels per model pixel")
    ap.add_argument("--only", nargs="*", help="scene names")
    ap.add_argument("--still", action="store_true", help="only the stills, for checking the layout")
    a = ap.parse_args()
    assets = Assets([REPO / "src" / "main" / "resources", REPO / "src" / "generated" / "resources",
                     a.create_assets, a.vanilla_assets])
    for name in a.only or SCENES:
        make(name, assets, Path(a.out), a.scale, a.still)


if __name__ == "__main__":
    main()
