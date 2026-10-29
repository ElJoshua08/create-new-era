import { SceneView, along, lineLength, reducedMotion, whileVisible, type Pt } from './scene-view';
import { FlipScreen } from './flip';
import { highlight } from './lua';

interface HeroConfig {
  code: string;
  file: string;
  ran: string;
  value: string; // console line for the vault reading, {n} is the count
  screenLabel: string;
  suggest: string[];
  play: string;
  pause: string;
  replay: string;
}

const root = document.querySelector<HTMLElement>('[data-hero]');
if (root) init(root);

async function init(root: HTMLElement) {
  const cfg: HeroConfig = JSON.parse(root.querySelector('[data-hero-config]')!.textContent!);
  const canvas = root.querySelector<HTMLCanvasElement>('[data-hero-canvas]')!;
  const still = root.querySelector<HTMLImageElement>('[data-hero-still]');
  const codeEl = root.querySelector<HTMLElement>('[data-code]')!;
  const gutter = root.querySelector<HTMLElement>('[data-gutter]')!;
  const consoleEl = root.querySelector<HTMLElement>('[data-console]')!;
  const runBtn = root.querySelector<HTMLElement>('[data-run]')!;
  const suggest = root.querySelector<HTMLElement>('[data-suggest]')!;
  const playBtn = root.querySelector<HTMLButtonElement>('[data-play]')!;
  const chapters = [...root.querySelectorAll<HTMLButtonElement>('[data-chapter]')];

  let view: SceneView;
  try {
    view = await SceneView.load(canvas, '/scenes/hero');
  } catch {
    return; // the still stays
  }
  canvas.hidden = false;
  if (still) still.style.visibility = 'hidden';
  const m = view.meta;
  const cables = m.cables;
  const len = Object.fromEntries(Object.entries(cables).map(([k, v]) => [k, lineLength(v)]));
  const SPEED = 820; // scene px per second
  const dur = (k: string) => len[k] / SPEED;

  // --- timeline, in seconds -----------------------------------------------------------------
  const code = cfg.code;
  const T_TYPE = 0.4;
  const CPS = 62;
  const T_TYPED = T_TYPE + code.length / CPS;
  const T_RUN = T_TYPED + 0.45;
  const T_P1 = T_RUN + 1.7;
  const T_P2 = T_P1 + dur('vault') + 0.2;
  const T_MOTOR = T_P2 + dur('motor');
  const T_P3 = T_MOTOR + 0.9;
  const T_SCREEN = T_P3 + dur('screen');
  const T_END = T_SCREEN + 5;
  const marks = [0, T_RUN, T_P1, T_P3, T_END];
  const CYCLE = 1.7;
  const T_CYCLES = T_MOTOR + 0.5;
  const START_IRON = 212;

  const cycleDone = (k: number) => T_CYCLES + k * CYCLE + 0.4 * CYCLE;
  const vaultArrive = (k: number) => cycleDone(k) + 0.05 + dur('vault');
  const screenArrive = (k: number) => vaultArrive(k) + 0.1 + dur('screen');

  /** Number of cycles whose event at time f(k) has happened by t. */
  function countBy(t: number, f: (k: number) => number) {
    if (t < f(0)) return 0;
    let k = Math.floor((t - f(0)) / CYCLE) + 1;
    while (k > 0 && f(k - 1) > t) k--;
    while (f(k) <= t) k++;
    return k;
  }

  type Pulse = { line: Pt[]; from: number; length: number; colour: string };
  function pulses(t: number): Pulse[] {
    const out: Pulse[] = [];
    const add = (k: string, from: number, colour = '#ffc94d') => {
      const d = len[k] / SPEED;
      if (t >= from && t <= from + d + 0.35) out.push({ line: cables[k], from, length: len[k], colour });
    };
    add('vault', T_P1);
    add('motor', T_P2, '#9cf27a');
    add('screen', T_P3, '#9cf27a');
    if (t > T_CYCLES) {
      const now = countBy(t, cycleDone);
      for (let k = Math.max(0, now - 3); k <= now; k++) {
        add('vault', cycleDone(k) + 0.05);
        add('screen', vaultArrive(k) + 0.1, '#9cf27a');
      }
    }
    return out;
  }

  // --- the screen ----------------------------------------------------------------------------
  const screen = new FlipScreen(144, 96);
  let shownKey = '';
  function dashboard(iron: number, history: number[]) {
    screen.clear();
    screen.text(cfg.screenLabel, 8, 8, 2);
    screen.text(String(iron), 8, 30, 4);
    screen.text('RPM', 104, 8, 1);
    screen.text(iron > 64 ? '128' : '32', 104, 20, 2);
    const fill = iron > 64 ? 32 : 8;
    for (let i = 0; i < 32; i += 2) screen.rect(104 + i, 40, 1, 6, i < fill ? 1 : 0);
    for (let x = 8; x < 136; x += 2) screen.set(x, 66);
    history.forEach((v, i) => {
      const h = Math.max(1, Math.round(((v - 150) / (START_IRON - 150)) * 18));
      screen.rect(8 + i * 8, 90 - h, 6, h);
    });
  }

  // --- editor --------------------------------------------------------------------------------
  const lineCount = code.split('\n').length;
  gutter.textContent = Array.from({ length: lineCount }, (_, i) => i + 1).join('\n');
  let typedShown = -1;
  function showCode(n: number) {
    if (n === typedShown) return;
    typedShown = n;
    const text = code.slice(0, n);
    codeEl.innerHTML = highlight(text) + (n < code.length ? '<span class="caret"></span>' : '');
    // the device list pops up while a bus.get("...") name is being typed
    const line = text.split('\n').pop()!;
    const open = line.lastIndexOf('bus.get("');
    if (n < code.length && open >= 0 && !line.slice(open + 9).includes('"')) {
      const typed = line.slice(open + 9);
      const row = text.split('\n').length;
      suggest.hidden = false;
      suggest.style.setProperty('--row', String(row));
      suggest.style.setProperty('--col', String(open + 9));
      suggest.querySelectorAll('li').forEach((li) => {
        li.toggleAttribute('data-hit', !!typed && li.textContent!.startsWith(typed));
      });
    } else suggest.hidden = true;
  }

  let consoleKey = '';
  function showConsole(t: number) {
    const lines: string[] = [];
    if (t >= T_RUN) lines.push(`> ${cfg.ran}`);
    if (t >= T_P1 + dur('vault')) lines.push(cfg.value.replace('{n}', String(START_IRON)));
    if (t > T_CYCLES) {
      const n = countBy(t, vaultArrive);
      for (let k = Math.max(0, n - 3); k < n; k++) lines.push(cfg.value.replace('{n}', String(START_IRON - k - 1)));
    }
    const shown = lines.slice(-4);
    const key = shown.join('|');
    if (key === consoleKey) return;
    consoleKey = key;
    consoleEl.replaceChildren(
      ...shown.map((l, i) => {
        const p = document.createElement('div');
        p.textContent = l;
        if (i === 0 && l.startsWith('>')) p.className = 'ran';
        return p;
      }),
    );
  }

  // --- the world at time t -------------------------------------------------------------------
  let t = 0;
  let playing = !reducedMotion();
  let pressedUntil = -1;

  function stateAt(t: number) {
    view.frames.cog = (t * 192) / 7.5; // 32 RPM
    view.frames.computer = t >= T_RUN ? 1 : 0;
    view.frames.motor = t > T_MOTOR ? ((t - T_MOTOR) * 330) / 11.25 : 0;
    let press = 0;
    let item = 0;
    if (t > T_CYCLES) {
      const p = (((t - T_CYCLES) / CYCLE) % 1 + 1) % 1;
      if (p < 0.35) press = (p / 0.35) ** 2;
      else if (p < 0.45) press = 1;
      else if (p < 0.8) press = 1 - (p - 0.45) / 0.35;
      item = p >= 0.38 && p < 0.95 ? 1 : 0;
    }
    view.frames.press = item * 8 + Math.round(press * 7);

    showCode(Math.max(0, Math.min(code.length, Math.floor((t - T_TYPE) * CPS))));
    showConsole(t);
    const pressing = t >= T_RUN && t < T_RUN + 0.18;
    runBtn.toggleAttribute('data-pressed', pressing || pressedUntil > performance.now());

    if (t >= T_SCREEN) {
      const n = countBy(t, screenArrive);
      const iron = START_IRON - n;
      const history = Array.from({ length: Math.min(16, n + 1) }, (_, i) => iron + Math.min(16, n + 1) - 1 - i).reverse();
      const key = `${iron}`;
      if (key !== shownKey) {
        shownKey = key;
        dashboard(iron, history);
        screen.commit();
      }
    } else if (shownKey !== 'off') {
      shownKey = 'off';
      screen.clear();
      screen.commit(0.1);
    }

    chapters.forEach((c, i) => {
      const p = Math.max(0, Math.min(1, (t - marks[i]) / (marks[i + 1] - marks[i])));
      c.style.setProperty('--p', p.toFixed(3));
      c.toggleAttribute('data-active', t >= marks[i] && (t < marks[i + 1] || i === chapters.length - 1));
    });
  }

  function overlay(ctx: CanvasRenderingContext2D) {
    screen.paint();
    if (t >= T_SCREEN - 0.5) {
      ctx.save();
      view.faceTransform(m.faces.screen, 144, 96);
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(screen.canvas, 0, 0, 144, 96);
      ctx.restore();
    }
    view.sceneSpace();
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    for (const p of pulses(t)) {
      const run = (t - p.from) * SPEED;
      if (run <= p.length) {
        for (let k = 0; k < 4; k++) {
          const [x, y] = along(p.line, Math.max(0, run - k * 14));
          const r = (k ? 14 : 34) - k * 2;
          const g = ctx.createRadialGradient(x, y, 0, x, y, r);
          g.addColorStop(0, p.colour);
          g.addColorStop(1, 'rgba(0,0,0,0)');
          ctx.globalAlpha = k ? 0.55 - k * 0.12 : 1;
          ctx.fillStyle = g;
          ctx.fillRect(x - r, y - r, r * 2, r * 2);
        }
        ctx.globalAlpha = 1;
        const [x, y] = along(p.line, run);
        ctx.fillStyle = '#fff6d8';
        ctx.fillRect(x - 4, y - 4, 8, 8);
      } else {
        const k = (t - p.from - p.length / SPEED) / 0.35;
        const [x, y] = p.line[p.line.length - 1];
        ctx.globalAlpha = 1 - k;
        ctx.strokeStyle = p.colour;
        ctx.lineWidth = 4;
        ctx.strokeRect(x - 8 - k * 18, y - 8 - k * 18, 16 + k * 36, 16 + k * 36);
        ctx.globalAlpha = 1;
      }
    }
    ctx.restore();
  }

  function render() {
    stateAt(t);
    view.draw(overlay);
  }

  const setPlaying = (on: boolean) => {
    playing = on;
    playBtn.setAttribute('aria-pressed', String(!on));
    playBtn.setAttribute('aria-label', on ? cfg.pause : t >= T_END ? cfg.replay : cfg.play);
    playBtn.dataset.state = on ? 'playing' : 'paused';
  };

  playBtn.addEventListener('click', () => {
    if (!playing && reducedMotion()) {
      t = 0;
    }
    setPlaying(!playing);
  });
  chapters.forEach((c, i) =>
    c.addEventListener('click', () => {
      t = marks[i] + (i === 1 ? -0.01 : 0);
      if (i === 1) pressedUntil = performance.now() + 200;
      screen.snap();
      setPlaying(true);
    }),
  );

  const at = Number(new URLSearchParams(location.search).get('t'));
  if (at) {
    t = at;
    playing = false;
    stateAt(t);
    screen.snap();
  } else if (!playing) {
    t = T_END + 2;
    stateAt(t);
    screen.snap();
  }
  setPlaying(playing);
  const ro = new ResizeObserver(() => {
    view.resize();
    render();
  });
  ro.observe(canvas);

  whileVisible(canvas, (dt) => {
    if (playing) t += dt;
    if (screen.update(playing ? dt : dt) || playing) render();
  });
  render();
}
