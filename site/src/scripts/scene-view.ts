// Draws a diorama rendered by tools/scene.py: the still, then the current pose of every moving
// group on top. Each group's sprite sheet only holds the pixels that group changes, so poses of
// different groups can be mixed freely.

export type Pt = [number, number];

export interface Face {
  tl: Pt;
  tr: Pt;
  bl: Pt;
}

export interface SceneMeta {
  width: number;
  height: number;
  groups: Record<string, { x: number; y: number; w: number; h: number; frames: number }>;
  cables: Record<string, Pt[]>;
  anchors: Record<string, Pt>;
  faces: Record<string, Face>;
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.decoding = 'async';
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

export class SceneView {
  readonly ctx: CanvasRenderingContext2D;
  readonly frames: Record<string, number> = {};
  scale = 1;

  private constructor(
    readonly canvas: HTMLCanvasElement,
    readonly meta: SceneMeta,
    private base: HTMLImageElement,
    private sprites: Record<string, HTMLImageElement>,
  ) {
    this.ctx = canvas.getContext('2d')!;
    for (const g of Object.keys(meta.groups)) this.frames[g] = 0;
    this.resize();
  }

  static async load(canvas: HTMLCanvasElement, dir: string): Promise<SceneView> {
    const meta: SceneMeta = await (await fetch(`${dir}/scene.json`)).json();
    const [base, ...sheets] = await Promise.all([
      loadImage(`${dir}/base.png`),
      ...Object.keys(meta.groups).map((g) => loadImage(`${dir}/${g}.png`)),
    ]);
    const sprites: Record<string, HTMLImageElement> = {};
    Object.keys(meta.groups).forEach((g, i) => (sprites[g] = sheets[i]));
    return new SceneView(canvas, meta, base, sprites);
  }

  resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = Math.max(1, Math.round(this.canvas.clientWidth * dpr));
    const h = Math.round((w * this.meta.height) / this.meta.width);
    if (this.canvas.width !== w || this.canvas.height !== h) {
      this.canvas.width = w;
      this.canvas.height = h;
    }
    this.scale = w / this.meta.width;
  }

  /** Puts the context in scene pixels. */
  sceneSpace() {
    this.ctx.setTransform(this.scale, 0, 0, this.scale, 0, 0);
  }

  draw(over?: (ctx: CanvasRenderingContext2D) => void) {
    const { ctx } = this;
    this.sceneSpace();
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.clearRect(0, 0, this.meta.width, this.meta.height);
    ctx.drawImage(this.base, 0, 0);
    for (const [g, sheet] of Object.entries(this.sprites)) {
      const m = this.meta.groups[g];
      const f = ((Math.floor(this.frames[g]) % m.frames) + m.frames) % m.frames;
      ctx.drawImage(sheet, f * m.w, 0, m.w, m.h, m.x, m.y, m.w, m.h);
    }
    if (over) over(ctx);
  }

  /** Transform that maps a cols x rows grid onto a face of the scene. */
  faceTransform(face: Face, cols: number, rows: number) {
    const { tl, tr, bl } = face;
    this.ctx.setTransform(this.scale, 0, 0, this.scale, 0, 0);
    this.ctx.transform((tr[0] - tl[0]) / cols, (tr[1] - tl[1]) / cols, (bl[0] - tl[0]) / rows, (bl[1] - tl[1]) / rows, tl[0], tl[1]);
  }
}

/** A point at distance d along a polyline, and the polyline's length. */
export function along(line: Pt[], d: number): Pt {
  let left = d;
  for (let i = 1; i < line.length; i++) {
    const [ax, ay] = line[i - 1];
    const [bx, by] = line[i];
    const seg = Math.hypot(bx - ax, by - ay);
    if (left <= seg) {
      const k = seg ? left / seg : 0;
      return [ax + (bx - ax) * k, ay + (by - ay) * k];
    }
    left -= seg;
  }
  return line[line.length - 1];
}

export function lineLength(line: Pt[]) {
  let n = 0;
  for (let i = 1; i < line.length; i++) n += Math.hypot(line[i][0] - line[i - 1][0], line[i][1] - line[i - 1][1]);
  return n;
}

export const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Runs fn every frame while el is on screen and the tab is visible. */
export function whileVisible(el: Element, fn: (dt: number) => void) {
  let raf = 0;
  let last = 0;
  let onScreen = false;
  const tick = (now: number) => {
    const dt = last ? Math.min(0.05, (now - last) / 1000) : 0;
    last = now;
    fn(dt);
    raf = requestAnimationFrame(tick);
  };
  const update = () => {
    const run = onScreen && !document.hidden;
    if (run && !raf) {
      last = 0;
      raf = requestAnimationFrame(tick);
    } else if (!run && raf) {
      cancelAnimationFrame(raf);
      raf = 0;
    }
  };
  new IntersectionObserver((entries) => {
    onScreen = entries.some((e) => e.isIntersecting);
    update();
  }).observe(el);
  document.addEventListener('visibilitychange', update);
}
