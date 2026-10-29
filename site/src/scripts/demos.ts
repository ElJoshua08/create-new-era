import { SceneView, along, lineLength, reducedMotion, whileVisible } from './scene-view';
import { FlipScreen } from './flip';

// --- the computer's speed: drag the shaft speed, watch the cog and the time slice -----------
async function power(root: HTMLElement) {
  const canvas = root.querySelector<HTMLCanvasElement>('canvas')!;
  const still = root.querySelector<HTMLImageElement>('img');
  const input = root.querySelector<HTMLInputElement>('input[type=range]')!;
  const rpmOut = root.querySelector('[data-rpm]')!;
  const msOut = root.querySelector('[data-ms]')!;
  const suOut = root.querySelector('[data-su]')!;
  const state = root.querySelector<HTMLElement>('[data-state]')!;
  const slice = root.querySelector<HTMLElement>('[data-slice]')!;
  const view = await SceneView.load(canvas, '/scenes/power');
  canvas.hidden = false;
  if (still) still.style.visibility = 'hidden';

  let shown = Number(input.value);
  let angle = 0;
  const read = () => {
    const rpm = Number(input.value);
    rpmOut.textContent = String(rpm);
    msOut.textContent = ((rpm / 256) * 2).toFixed(2);
    suOut.textContent = String(rpm * 2);
    state.dataset.on = rpm > 0 ? 'true' : 'false';
    state.textContent = rpm > 0 ? state.dataset.running! : state.dataset.paused!;
    slice.style.setProperty('--k', String(rpm / 256));
    input.style.setProperty('--k', String(rpm / 256));
  };
  input.addEventListener('input', read);
  read();

  const draw = () => {
    view.frames.cog = angle / 7.5;
    view.frames.computer = shown > 0.5 ? 1 : 0;
    view.draw();
  };
  new ResizeObserver(() => {
    view.resize();
    draw();
  }).observe(canvas);
  whileVisible(canvas, (dt) => {
    const target = Number(input.value);
    // the gears ease toward the new speed like a real network spinning up
    shown += (target - shown) * Math.min(1, dt * 6);
    if (!reducedMotion()) angle += shown * 6 * dt * 0.5;
    draw();
  });
  draw();
}

// --- cable colours: swap the dyed network's colour ------------------------------------------
async function dyes(root: HTMLElement) {
  const canvas = root.querySelector<HTMLCanvasElement>('canvas')!;
  const still = root.querySelector<HTMLImageElement>('img');
  const view = await SceneView.load(canvas, '/scenes/bus');
  canvas.hidden = false;
  if (still) still.style.visibility = 'hidden';
  const buttons = [...root.querySelectorAll<HTMLButtonElement>('[data-dye]')];
  const pick = (i: number) => {
    view.frames.dye = i;
    buttons.forEach((b, j) => b.setAttribute('aria-pressed', String(i === j)));
    view.draw();
  };
  buttons.forEach((b, i) => b.addEventListener('click', () => pick(i)));
  new ResizeObserver(() => {
    view.resize();
    view.draw();
  }).observe(canvas);
  pick(0);
}

// --- screens: join them, then paint on the pixels -------------------------------------------
async function wall(root: HTMLElement) {
  const canvas = root.querySelector<HTMLCanvasElement>('canvas')!;
  const still = root.querySelector<HTMLImageElement>('img');
  const view = await SceneView.load(canvas, '/scenes/wall');
  canvas.hidden = false;
  if (still) still.style.visibility = 'hidden';
  const COLS = 48 * 4;
  const ROWS = 48 * 3;
  const screen = new FlipScreen(COLS, ROWS);
  const face = view.meta.faces.screen;
  let joined = true;

  // something to look at before anyone draws: a factory line counter
  screen.text('CREATE', 12, 14, 4);
  screen.text('NEW ERA', 12, 52, 4);
  for (let x = 12; x < COLS - 12; x += 3) screen.set(x, 92);
  [10, 14, 9, 18, 22, 17, 25, 28, 24, 31, 34, 30, 36, 38].forEach((h, i) => screen.rect(12 + i * 12, 130 - h, 8, h));
  screen.snap();

  const toggles = [...root.querySelectorAll<HTMLButtonElement>('[data-join]')];
  const setJoined = (on: boolean) => {
    joined = on;
    view.frames.join = on ? 1 : 0;
    toggles.forEach((b) => b.setAttribute('aria-pressed', String((b.dataset.join === 'on') === on)));
    root.toggleAttribute('data-apart', !on);
  };
  toggles.forEach((b) => b.addEventListener('click', () => setJoined(b.dataset.join === 'on')));
  setJoined(true);

  const clear = root.querySelector<HTMLButtonElement>('[data-clear]');
  clear?.addEventListener('click', () => {
    screen.clear();
    screen.commit(0.5);
  });

  // pointer to pixel: invert the face's affine map
  const toPixel = (e: PointerEvent): [number, number] | null => {
    const r = canvas.getBoundingClientRect();
    const sx = ((e.clientX - r.left) / r.width) * view.meta.width;
    const sy = ((e.clientY - r.top) / r.height) * view.meta.height;
    const ux = (face.tr[0] - face.tl[0]) / COLS, uy = (face.tr[1] - face.tl[1]) / COLS;
    const vx = (face.bl[0] - face.tl[0]) / ROWS, vy = (face.bl[1] - face.tl[1]) / ROWS;
    const dx = sx - face.tl[0], dy = sy - face.tl[1];
    const det = ux * vy - uy * vx;
    const i = (dx * vy - dy * vx) / det;
    const j = (ux * dy - uy * dx) / det;
    return i >= 0 && j >= 0 && i < COLS && j < ROWS ? [Math.floor(i), Math.floor(j)] : null;
  };
  let painting: 0 | 1 | null = null;
  let last: [number, number] | null = null;
  const dab = (p: [number, number]) => {
    const steps = last ? Math.max(Math.abs(p[0] - last[0]), Math.abs(p[1] - last[1])) : 0;
    for (let s = 0; s <= steps; s++) {
      const k = steps ? s / steps : 0;
      const x = Math.round(last ? last[0] + (p[0] - last[0]) * k : p[0]);
      const y = Math.round(last ? last[1] + (p[1] - last[1]) * k : p[1]);
      screen.rect(x - 1, y - 1, 3, 3, painting!);
    }
    last = p;
    screen.commit(0);
  };
  canvas.addEventListener('pointerdown', (e) => {
    if (!joined) return;
    const p = toPixel(e);
    if (!p) return;
    canvas.setPointerCapture(e.pointerId);
    painting = screen.get(p[0], p[1]) ? 0 : 1; // start on a lit pixel to erase
    last = null;
    dab(p);
    e.preventDefault();
  });
  canvas.addEventListener('pointermove', (e) => {
    const p = toPixel(e);
    canvas.style.cursor = p && joined ? 'crosshair' : '';
    if (painting !== null && p) dab(p);
  });
  const stop = () => {
    painting = null;
    last = null;
  };
  canvas.addEventListener('pointerup', stop);
  canvas.addEventListener('pointercancel', stop);

  const draw = () =>
    view.draw((ctx) => {
      if (!joined) return;
      screen.paint();
      ctx.save();
      view.faceTransform(face, COLS, ROWS);
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(screen.canvas, 0, 0, COLS, ROWS);
      ctx.restore();
    });
  new ResizeObserver(() => {
    view.resize();
    draw();
  }).observe(canvas);
  whileVisible(canvas, (dt) => {
    screen.update(dt);
    draw();
  });
  draw();
}

// --- quiet motion for the pictures on the home page -----------------------------------------
async function ambient(root: HTMLElement, kind: 'spin' | 'pulse') {
  const canvas = root.querySelector<HTMLCanvasElement>('canvas')!;
  const still = root.querySelector<HTMLImageElement>('img');
  const view = await SceneView.load(canvas, kind === 'spin' ? '/scenes/power' : '/scenes/bus');
  canvas.hidden = false;
  if (still) still.style.visibility = 'hidden';
  if ('computer' in view.frames) view.frames.computer = 1;
  const lines = Object.values(view.meta.cables);
  const lens = lines.map(lineLength);
  let t = 0;
  const draw = () =>
    view.draw((ctx) => {
      if (kind !== 'pulse' || reducedMotion()) return;
      ctx.save();
      ctx.globalCompositeOperation = 'lighter';
      lines.forEach((line, i) => {
        const period = 2.6;
        const run = (((t + i * 0.8) % period) / period) * (lens[i] + 200) - 100;
        if (run < 0 || run > lens[i]) return;
        const [x, y] = along(line, run);
        const g = ctx.createRadialGradient(x, y, 0, x, y, 26);
        g.addColorStop(0, '#ffc94d');
        g.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.fillStyle = g;
        ctx.fillRect(x - 26, y - 26, 52, 52);
        ctx.fillStyle = '#fff6d8';
        ctx.fillRect(x - 3, y - 3, 6, 6);
      });
      ctx.restore();
    });
  new ResizeObserver(() => {
    view.resize();
    draw();
  }).observe(canvas);
  whileVisible(canvas, (dt) => {
    if (reducedMotion()) return;
    t += dt;
    if ('cog' in view.frames) view.frames.cog = (t * 192) / 7.5;
    draw();
  });
  draw();
}

document.querySelectorAll<HTMLElement>('[data-demo=spin]').forEach((el) => ambient(el, 'spin'));
document.querySelectorAll<HTMLElement>('[data-demo=pulse]').forEach((el) => ambient(el, 'pulse'));
document.querySelectorAll<HTMLElement>('[data-demo=power]').forEach(power);
document.querySelectorAll<HTMLElement>('[data-demo=dyes]').forEach(dyes);
document.querySelectorAll<HTMLElement>('[data-demo=wall]').forEach(wall);
