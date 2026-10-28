/* Flip-pixel board: every pixel is a two-sided plate that turns half a revolution when its colour
   changes, the way the Brass Screen is meant to work. Only cells that change move.
   Frames are painted at 1 canvas pixel per plate, then quantised: Monocraft is drawn at 9px
   multiples so each font texel lands on exactly k plates. */
(function () {
  'use strict';

  const OFF = 0x161d21;
  const GAP = '#07090b';
  const FLIP_MS = 300;

  function hex(c) { return '#' + c.toString(16).padStart(6, '0'); }
  function shade(c, k) {
    const r = Math.min(255, ((c >> 16) & 255) * k) | 0;
    const g = Math.min(255, ((c >> 8) & 255) * k) | 0;
    const b = Math.min(255, (c & 255) * k) | 0;
    return 'rgb(' + r + ',' + g + ',' + b + ')';
  }

  // Painter helpers used by frame functions. Coordinates are in plates.
  function painter(ctx, cols) {
    return {
      rect(x, y, w, h, color) { ctx.fillStyle = color; ctx.fillRect(x, y, w, h); },
      text(str, x, baseline, k, color, align) {
        ctx.font = (9 * k) + 'px Monocraft';
        ctx.textBaseline = 'alphabetic';
        ctx.fillStyle = color;
        const w = str.length * 6 * k - k; // 6 texels advance, last column is spacing
        let px = x;
        if (align === 'center') px = Math.round((cols - w) / 2);
        if (align === 'right') px = x - w;
        ctx.fillText(str, px, baseline);
        return w;
      },
      line(points, color, width) {
        ctx.strokeStyle = color; ctx.lineWidth = width || 1; ctx.lineCap = 'square';
        ctx.beginPath(); points.forEach((p, i) => (i ? ctx.lineTo(p[0], p[1]) : ctx.moveTo(p[0], p[1]))); ctx.stroke();
      },
      arc(cx, cy, r, a0, a1, color, width) {
        ctx.strokeStyle = color; ctx.lineWidth = width; ctx.lineCap = 'butt';
        ctx.beginPath(); ctx.arc(cx, cy, r, a0, a1); ctx.stroke();
      },
      ctx
    };
  }

  function rasterize(paint, cols, rows) {
    const c = document.createElement('canvas');
    c.width = cols; c.height = rows;
    const ctx = c.getContext('2d', { willReadFrequently: true });
    ctx.imageSmoothingEnabled = false;
    paint(painter(ctx, cols), cols, rows);
    const d = ctx.getImageData(0, 0, cols, rows).data;
    const cells = new Uint32Array(cols * rows);
    for (let p = 0; p < cells.length; p++) {
      cells[p] = d[p * 4 + 3] < 128 ? OFF : (d[p * 4] << 16) | (d[p * 4 + 1] << 8) | d[p * 4 + 2];
    }
    return cells;
  }

  class FlipBoard {
    constructor(canvas, opts) {
      this.canvas = canvas;
      this.ctx = canvas.getContext('2d');
      this.cols = opts.cols; this.rows = opts.rows;
      this.painters = opts.frames;
      this.brush = opts.brush || 0xe4b763;
      this.shape = 'square';
      this.onchange = opts.onchange || function () {};
      this.reduce = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.index = 0;
      this.flips = new Map();        // cell -> {from, to, start}
      this.brushed = new Map();      // cell -> release time
      this.running = false;
      this.visible = true;
      this.cell = 4; this.tile = 3;
      this.current = new Uint32Array(this.cols * this.rows).fill(OFF);
      this._tick = this._tick.bind(this);
    }

    async start() {
      try { await document.fonts.load('18px Monocraft'); } catch (e) { /* fall back to whatever loaded */ }
      this.frames = this.painters.map((p) => rasterize(p, this.cols, this.rows));
      this.layout();
      new ResizeObserver(() => this.layout()).observe(this.canvas.parentElement);
      new IntersectionObserver((entries) => {
        this.visible = entries[0].isIntersecting;
        if (this.visible) this._wake();
      }).observe(this.canvas);
      this._bindPointer();
      this.goTo(0);
    }

    layout() {
      const dpr = window.devicePixelRatio || 1;
      const avail = Math.floor(this.canvas.parentElement.clientWidth * dpr) - 2;
      this.cell = Math.max(2, Math.floor(avail / this.cols));
      this.tile = this.cell < 3 ? this.cell : this.cell - Math.max(1, Math.round(this.cell / 6));
      this.canvas.width = this.cell * this.cols;
      this.canvas.height = this.cell * this.rows;
      this.drawAll();
    }

    setShape(shape) { this.shape = shape; this.drawAll(); }

    drawTile(x, y, color, squash, light) {
      const ctx = this.ctx, C = this.cell, T = this.tile;
      const px = x * C, py = y * C;
      ctx.fillStyle = GAP;
      ctx.fillRect(px, py, C, C);
      const h = squash === 1 ? T : Math.max(0.6, T * squash);
      const top = py + (T - h) / 2;
      ctx.fillStyle = shade(color, light);
      if (this.shape === 'disc') {
        ctx.beginPath();
        ctx.ellipse(px + T / 2, py + T / 2, T / 2, h / 2, 0, 0, Math.PI * 2);
        ctx.fill();
        return;
      }
      ctx.fillRect(px, top, T, h);
      // lit top edge and shaded bottom edge, so a resting plate reads as a raised part
      if (squash === 1 && T >= 5) {
        ctx.fillStyle = shade(color, light * 1.22);
        ctx.fillRect(px, top, T, 1);
        ctx.fillStyle = shade(color, light * 0.7);
        ctx.fillRect(px, top + h - 1, T, 1);
      }
    }

    drawAll() {
      for (let y = 0; y < this.rows; y++)
        for (let x = 0; x < this.cols; x++) this.drawTile(x, y, this.current[y * this.cols + x], 1, 1);
    }

    _target(p) { return this.brushed.has(p) ? this.brush : this.frames[this.index][p]; }

    _retarget(p, now, delay) {
      const want = this._target(p);
      const f = this.flips.get(p);
      const showing = f ? f.to : this.current[p];
      if (showing === want) return false;
      if (this.reduce.matches) {
        this.flips.delete(p);
        this.current[p] = want;
        this.drawTile(p % this.cols, (p / this.cols) | 0, want, 1, 1);
        return true;
      }
      // a plate already turning keeps turning; it just lands on the new face
      if (f && now >= f.start) { f.to = want; return true; }
      this.flips.set(p, { from: this.current[p], to: want, start: now + delay });
      return true;
    }

    goTo(i) {
      this.index = i;
      const now = performance.now();
      let changed = 0;
      for (let p = 0; p < this.current.length; p++) {
        const x = p % this.cols, y = (p / this.cols) | 0;
        // a sweep from left to right with a little jitter, like a real board catching up
        const delay = x * 6 + y * 1.4 + Math.random() * 30;
        if (this._retarget(p, now, delay)) changed++;
      }
      this.onchange({ index: i, count: this.frames.length, changed });
      this._wake();
    }

    next() { this.goTo((this.index + 1) % this.frames.length); }

    _bindPointer() {
      const c = this.canvas;
      const hit = (e) => {
        const r = c.getBoundingClientRect();
        const gx = ((e.clientX - r.left) / r.width) * this.cols;
        const gy = ((e.clientY - r.top) / r.height) * this.rows;
        const now = performance.now();
        const R = 3.2;
        for (let y = Math.floor(gy - R); y <= gy + R; y++) {
          for (let x = Math.floor(gx - R); x <= gx + R; x++) {
            if (x < 0 || y < 0 || x >= this.cols || y >= this.rows) continue;
            if (Math.hypot(x + 0.5 - gx, y + 0.5 - gy) > R) continue;
            const p = y * this.cols + x;
            const isNew = !this.brushed.has(p);
            this.brushed.set(p, now + 700);
            if (isNew) this._retarget(p, now, 0);
          }
        }
        this._wake();
      };
      c.addEventListener('pointermove', (e) => { if (e.pointerType === 'mouse' || e.buttons) hit(e); });
      c.addEventListener('pointerdown', hit);
    }

    _wake() {
      if (this.running || !this.visible) return;
      this.running = true;
      requestAnimationFrame(this._tick);
    }

    _tick(now) {
      for (const [p, until] of this.brushed) {
        if (now >= until) { this.brushed.delete(p); this._retarget(p, now, Math.random() * 60); }
      }
      for (const [p, f] of this.flips) {
        const t = (now - f.start) / FLIP_MS;
        if (t < 0) continue;
        const x = p % this.cols, y = (p / this.cols) | 0;
        if (t >= 1) {
          this.current[p] = f.to;
          this.drawTile(x, y, f.to, 1, 1);
          this.flips.delete(p);
          continue;
        }
        // half a turn, fast start and a soft landing; past 90 degrees the back face shows
        const a = Math.PI * (1 - Math.pow(1 - t, 2.4));
        const squash = Math.abs(Math.cos(a));
        this.drawTile(x, y, a < Math.PI / 2 ? f.from : f.to, squash, 0.45 + 0.55 * squash);
      }
      if ((this.flips.size || this.brushed.size) && this.visible) requestAnimationFrame(this._tick);
      else this.running = false;
    }
  }

  // ---------- frames: 132 x 84 plates, the visible area of a 3 x 2 Brass Screen ----------
  const BRASS = '#e4b763', BRASS_HI = '#ffeb8c', AMBER = '#f0b03a', COPPER = '#d67b5b',
        PARCH = '#eee2bf', ANDESITE = '#828784', DIM = '#2c383e', GREEN = '#7fd06a',
        GREEN_D = '#2f6b3a', RED = '#f06a4a';

  const FRAMES = [
    // 1. name plate
    (g, W) => {
      g.text('CREATE:', 0, 24, 2, BRASS, 'center');
      g.text('NEW ERA', 0, 52, 3, PARCH, 'center');
      g.rect(4, 59, 123, 2, COPPER);
      g.text('EARLY DEVELOPMENT', 0, 76, 1, ANDESITE, 'center');
    },
    // 2. factory dashboard (illustrative)
    (g) => {
      g.text('PRESS LINE', 0, 16, 2, PARCH, 'center');
      g.rect(4, 22, 124, 1, DIM);
      g.rect(70, 26, 1, 40, DIM);
      g.text('212', 7, 51, 3, AMBER);
      g.text('PLATES/MIN', 7, 62, 1, ANDESITE);
      const hs = [8, 10, 9, 13, 15, 14, 18, 21, 24, 27, 30];
      hs.forEach((h, i) => g.rect(75 + i * 5, 63 - h, 4, h, i === hs.length - 1 ? GREEN : GREEN_D));
      for (let x = 75; x < 129; x += 3) g.rect(x, 31, 1, 1, AMBER);
      g.rect(4, 69, 124, 1, DIM);
      g.text('64 RPM', 7, 80, 1, PARCH);
      for (let i = 0; i < 14; i++) g.rect(50 + i * 5, 74, 4, 6, i < 7 ? GREEN : i < 10 ? AMBER : DIM);
    },
    // 3. train departure board (illustrative)
    (g) => {
      g.text('DEPARTURES', 0, 16, 2, AMBER, 'center');
      g.rect(4, 22, 124, 1, DIM);
      const rows = [['IRON MINE', '0:45', GREEN], ['SMELTERY', '2:10', AMBER], ['NORTH DOCK', 'LATE', RED]];
      rows.forEach((r, i) => {
        const y = 37 + i * 17;
        g.rect(6, y - 7, 3, 7, r[2]);
        g.text(r[0], 14, y, 1, PARCH);
        g.text(r[1], 126, y, 1, r[2], 'right');
      });
      g.rect(4, 74, 124, 1, DIM);
      g.text('PLATFORM 2', 0, 83, 1, ANDESITE, 'center');
    },
    // 4. stress gauge (illustrative)
    (g) => {
      g.text('STRESS', 0, 16, 2, PARCH, 'center');
      const cx = 66, cy = 66, r = 36;
      g.arc(cx, cy, r, Math.PI, Math.PI * 1.5, GREEN, 6);
      g.arc(cx, cy, r, Math.PI * 1.5, Math.PI * 1.78, AMBER, 6);
      g.arc(cx, cy, r, Math.PI * 1.78, Math.PI * 2, RED, 6);
      const a = Math.PI * 1.36;
      g.line([[cx, cy], [cx + Math.cos(a) * (r - 8), cy + Math.sin(a) * (r - 8)]], BRASS_HI, 2);
      g.rect(cx - 2, cy - 2, 5, 5, BRASS);
      g.text('1024 SU', 0, 81, 1, AMBER, 'center');
      g.text('IN USE', 4, 81, 1, ANDESITE);
      g.text('OK', 128, 81, 1, GREEN, 'right');
    }
  ];

  window.FlipBoard = FlipBoard;
  window.FLIP_FRAMES = FRAMES;
})();
