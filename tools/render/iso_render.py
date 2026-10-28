"""Isometric renderer for Minecraft block models, for reviewing models without launching the game.

Resolves parents, composite children (neoforge:composite), texture variables,
element rotations and face UV rotations, then draws with a z-buffer and the
game's directional face shading.

Usage:
  python3 tools/render/iso_render.py OUT.png NS:block/model [NS:block/model ...]
      --assets DIR [--assets DIR ...] [--size 384] [--yaw 0|90|180|270]

Each --assets DIR is a folder containing assets/<namespace>/{models,textures}.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


class Assets:
    def __init__(self, roots):
        self.roots = [Path(r) for r in roots]
        self._tex = {}

    def _find(self, kind, ref, ext):
        ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        for root in self.roots:
            p = root / "assets" / ns / kind / f"{path}.{ext}"
            if p.exists():
                return p
        raise FileNotFoundError(f"{kind} not found: {ref}")

    def model(self, ref):
        return json.loads(self._find("models", ref, "json").read_text())

    def texture(self, ref):
        if ref not in self._tex:
            img = Image.open(self._find("textures", ref, "png")).convert("RGBA")
            # animated strips: keep the first frame
            if img.height > img.width:
                img = img.crop((0, 0, img.width, img.width))
            self._tex[ref] = np.asarray(img).astype(np.float32) / 255.0
        return self._tex[ref]


def flatten(assets, ref_or_model):
    """Returns (textures, elements) with parents and composite children merged."""
    model = assets.model(ref_or_model) if isinstance(ref_or_model, str) else ref_or_model
    textures, elements = {}, None
    chain = [model]
    while "parent" in chain[-1]:
        try:
            chain.append(assets.model(chain[-1]["parent"]))
        except FileNotFoundError:
            break
    for m in reversed(chain):
        textures.update(m.get("textures", {}))
        if "elements" in m:
            elements = [(e, None) for e in m["elements"]]
    children = model.get("children")
    if children:
        elements = elements or []
        for child in children.values():
            ctex, cel = flatten(assets, child)
            local = dict(textures)
            local.update(ctex)
            elements += [(e, local) for e, _ in cel]
    return textures, elements or []


def resolve(textures, ref):
    for _ in range(16):
        if not ref.startswith("#"):
            return ref
        ref = textures.get(ref[1:], "#missing")
    return ref


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return {
        "x": np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
        "y": np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
        "z": np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]]),
    }[axis]


def face_corners(f, t, face):
    """Corners in texture order: top-left, top-right, bottom-right, bottom-left as seen from outside."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        "north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
        "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
        "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
        "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
        "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
        "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)],
    }[face]


def default_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1], "west": [z1, 16 - y2, z2, 16 - y1],
        "up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1],
    }[face]


def homography(src, dst):
    """3x3 H with dst ~ H @ src for four point pairs."""
    a = []
    for (x, y), (u, v) in zip(src, dst):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y, -u])
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y, -v])
    _, _, vt = np.linalg.svd(np.array(a, dtype=np.float64))
    return vt[-1].reshape(3, 3)


def render(assets, refs, size=384, yaw=0, pitch=30.0):
    view_yaw = math.radians(225 + yaw)
    p = math.radians(pitch)
    # camera basis: looking down at the block from the south-east by default
    fwd = np.array([math.cos(p) * math.cos(view_yaw), -math.sin(p), math.cos(p) * math.sin(view_yaw)])
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)

    color = np.zeros((size, size, 4), np.float32)
    depth = np.full((size, size), np.inf, np.float32)
    scale = size / 2.1

    def project(pt):
        q = (np.asarray(pt, dtype=np.float64) / 16.0) - 0.5
        return np.array([size / 2 + q @ right * scale, size / 2 - q @ up * scale]), q @ fwd

    for ref in refs:
        textures, elements = flatten(assets, ref)
        for el, local_tex in elements:
            tex_map = local_tex or textures
            f, t = el["from"], el["to"]
            rot = el.get("rotation")
            for face, spec in el.get("faces", {}).items():
                corners = face_corners(f, t, face)
                if rot and rot.get("angle"):
                    m = rot_matrix(rot["axis"], rot["angle"])
                    o = np.array(rot["origin"], dtype=np.float64)
                    corners = [tuple(m @ (np.array(c) - o) + o) for c in corners]
                pts, depths = zip(*(project(c) for c in corners))
                pts = np.array(pts)
                # back-face cull
                e1, e2 = pts[1] - pts[0], pts[3] - pts[0]
                if e1[0] * e2[1] - e1[1] * e2[0] < 0:
                    continue
                try:
                    tex = assets.texture(resolve(tex_map, spec["texture"]))
                except FileNotFoundError:
                    continue
                th, tw = tex.shape[:2]
                u1, v1, u2, v2 = spec.get("uv", default_uv(face, f, t))
                uvs = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
                r = (spec.get("rotation", 0) // 90) % 4
                uvs = uvs[r:] + uvs[:r]
                h = homography(pts, [(u / 16 * tw, v / 16 * th) for u, v in uvs])
                h_uv = homography(pts, [(0, 0), (1, 0), (1, 1), (0, 1)])
                x0, y0 = np.floor(pts.min(0)).astype(int).clip(0, size - 1)
                x1, y1 = np.ceil(pts.max(0)).astype(int).clip(0, size - 1)
                if x1 <= x0 or y1 <= y0:
                    continue
                ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
                pix = np.stack([xs + 0.5, ys + 0.5, np.ones_like(xs, dtype=np.float64)], -1)
                st = pix @ h_uv.T
                s, tt = st[..., 0] / st[..., 2], st[..., 1] / st[..., 2]
                inside = (s >= 0) & (s <= 1) & (tt >= 0) & (tt <= 1)
                tc = pix @ h.T
                tu = np.clip((tc[..., 0] / tc[..., 2]).astype(int), 0, tw - 1)
                tv = np.clip((tc[..., 1] / tc[..., 2]).astype(int), 0, th - 1)
                d0, d1, d3 = depths[0], depths[1], depths[3]
                z = d0 + (d1 - d0) * s + (d3 - d0) * tt
                texel = tex[tv, tu]
                mask = inside & (texel[..., 3] > 0.5) & (z < depth[y0:y1 + 1, x0:x1 + 1])
                shade = SHADE[face]
                region = color[y0:y1 + 1, x0:x1 + 1]
                region[mask, :3] = texel[mask, :3] * shade
                region[mask, 3] = 1.0
                depth[y0:y1 + 1, x0:x1 + 1][mask] = z[mask]
    return Image.fromarray((color * 255).astype(np.uint8), "RGBA")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("models", nargs="+")
    ap.add_argument("--assets", action="append", required=True)
    ap.add_argument("--size", type=int, default=384)
    ap.add_argument("--yaw", type=int, default=0)
    ap.add_argument("--labels", action="store_true")
    a = ap.parse_args()
    assets = Assets(a.assets)
    tiles = []
    for m in a.models:
        refs = m.split("+")  # "a+b" renders several models into one tile
        tiles.append((m, render(assets, refs, a.size, a.yaw)))
    pad = 24 if a.labels else 0
    sheet = Image.new("RGBA", (a.size * len(tiles), a.size + pad), (34, 30, 27, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(tiles):
        sheet.alpha_composite(img, (i * a.size, 0))
        if a.labels:
            draw.text((i * a.size + 8, a.size + 6), name.split("/")[-1], fill=(220, 210, 190, 255))
    sheet.save(a.out)


if __name__ == "__main__":
    main()
