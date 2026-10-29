// A brass screen's flip pixels: each one is a little plate that turns over when it changes.

const GLYPHS: Record<string, string> = {
  '0': '01110100011001110101110011000101110',
  '1': '00100011000010000100001000010001110',
  '2': '01110100010000100010001000100011111',
  '3': '11110000010000101110000010000111110',
  '4': '00010001100101010010111110001000010',
  '5': '11111100001111000001000011000101110',
  '6': '00110010001000011110100011000101110',
  '7': '11111000010001000100010000100001000',
  '8': '01110100011000101110100011000101110',
  '9': '01110100011000101111000010001001100',
  A: '01110100011000111111100011000110001',
  C: '01110100011000010000100001000101110',
  E: '11111100001000011110100001000011111',
  H: '10001100011000111111100011000110001',
  I: '01110001000010000100001000010001110',
  L: '10000100001000010000100001000011111',
  M: '10001110111010110101100011000110001',
  N: '10001100011100110101100111000110001',
  O: '01110100011000110001100011000101110',
  P: '11110100011000111110100001000010000',
  R: '11110100011000111110101001001010001',
  S: '01111100001000001110000010000111110',
  T: '11111001000010000100001000010000100',
  W: '10001100011000110101101011010101010',
  ' ': '00000000000000000000000000000000000',
};

export class FlipScreen {
  readonly target: Uint8Array;
  /** 0 = dark side up, 1 = bright side up, in between = turning */
  readonly turn: Float32Array;
  private delay: Float32Array;
  readonly canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private dirty = true;
  private readonly cell = 4;

  constructor(
    readonly cols: number,
    readonly rows: number,
    private on = '#f2cf7a',
    private off = '#0f1b1b',
    private gap = '#071010',
  ) {
    this.target = new Uint8Array(cols * rows);
    this.turn = new Float32Array(cols * rows);
    this.delay = new Float32Array(cols * rows);
    this.canvas = document.createElement('canvas');
    this.canvas.width = cols * this.cell;
    this.canvas.height = rows * this.cell;
    this.ctx = this.canvas.getContext('2d')!;
  }

  clear() {
    this.target.fill(0);
  }

  set(x: number, y: number, v = 1) {
    if (x >= 0 && y >= 0 && x < this.cols && y < this.rows) this.target[y * this.cols + x] = v;
  }

  get(x: number, y: number) {
    return this.target[y * this.cols + x];
  }

  rect(x: number, y: number, w: number, h: number, v = 1) {
    for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) this.set(x + i, y + j, v);
  }

  text(s: string, x: number, y: number, scale = 1) {
    let cx = x;
    for (const ch of s.toUpperCase()) {
      const g = GLYPHS[ch] ?? GLYPHS[' '];
      for (let j = 0; j < 7; j++)
        for (let i = 0; i < 5; i++) if (g[j * 5 + i] === '1') this.rect(cx + i * scale, y + j * scale, scale, scale);
      cx += 6 * scale;
    }
    return cx;
  }

  /** Everything flips to its target now, with a short ripple from left to right. */
  commit(ripple = 0.35) {
    for (let i = 0; i < this.target.length; i++) {
      if (this.target[i] !== (this.turn[i] >= 0.5 ? 1 : 0)) {
        const x = i % this.cols;
        this.delay[i] = (x / this.cols) * ripple + Math.random() * 0.06;
      }
    }
  }

  /** Jumps straight to the target, for reduced motion. */
  snap() {
    for (let i = 0; i < this.target.length; i++) this.turn[i] = this.target[i];
    this.dirty = true;
  }

  update(dt: number) {
    const speed = 7; // a flip takes about 140 ms
    let moving = false;
    for (let i = 0; i < this.target.length; i++) {
      const goal = this.target[i];
      if (this.turn[i] === goal) continue;
      if (this.delay[i] > 0) {
        this.delay[i] -= dt;
        moving = true;
        continue;
      }
      const step = speed * dt;
      this.turn[i] = goal ? Math.min(1, this.turn[i] + step) : Math.max(0, this.turn[i] - step);
      moving = true;
    }
    if (moving) this.dirty = true;
    return moving;
  }

  /** Redraws the pixel canvas if anything moved. */
  paint() {
    if (!this.dirty) return;
    this.dirty = false;
    const { ctx, cell } = this;
    ctx.fillStyle = this.gap;
    ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);
    for (let y = 0; y < this.rows; y++) {
      for (let x = 0; x < this.cols; x++) {
        const t = this.turn[y * this.cols + x];
        const w = Math.abs(Math.cos(t * Math.PI)) * (cell - 1);
        if (w < 0.2) continue;
        ctx.fillStyle = t >= 0.5 ? this.on : this.off;
        ctx.fillRect(x * cell + (cell - w) / 2, y * cell + 0.5, w, cell - 1);
      }
    }
  }
}
