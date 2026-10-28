(function () {
  'use strict';
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)');

  // ---------- hero board ----------
  const canvas = document.getElementById('board');
  let board = null, timer = null;
  const status = document.getElementById('board-status');
  const playBtn = document.getElementById('board-play');

  function setPlaying(on) {
    clearInterval(timer);
    timer = on ? setInterval(() => board && board.next(), 4200) : null;
    if (playBtn) {
      playBtn.setAttribute('aria-pressed', String(on));
      playBtn.querySelector('span').textContent = on ? 'Pause' : 'Play';
    }
  }

  if (canvas && window.FlipBoard) {
    board = new FlipBoard(canvas, {
      cols: 132, rows: 84, frames: window.FLIP_FRAMES,
      onchange: (s) => {
        if (status) status.textContent = (s.index + 1) + '/' + s.count + ' · ' + s.changed.toLocaleString('en') + ' flips';
      }
    });
    board.start().then(() => setPlaying(!reduce.matches));
    document.getElementById('board-next').addEventListener('click', () => { setPlaying(false); board.next(); });
    playBtn.addEventListener('click', () => setPlaying(playBtn.getAttribute('aria-pressed') !== 'true'));
  }

  // ---------- pixel shape, shared by the hero board and the close-up ----------
  const plates = document.getElementById('plates');
  document.querySelectorAll('[data-shape]').forEach((b) => b.addEventListener('click', () => {
    const shape = b.dataset.shape;
    document.querySelectorAll('[data-shape]').forEach((o) => o.setAttribute('aria-pressed', String(o.dataset.shape === shape)));
    if (board) board.setShape(shape);
    if (plates) plates.classList.toggle('disc', shape === 'disc');
  }));

  // ---------- Create-style tooltips: hover, tap or hold Shift ----------
  const scope = document.getElementById('blocks');
  const key = document.getElementById('shift-key');
  if (scope) {
    const setShift = (on) => {
      scope.classList.toggle('shift', on);
      if (key) key.setAttribute('aria-pressed', String(on));
    };
    const ckey = document.getElementById('ctrl-key');
    const setCtrl = (on) => {
      scope.classList.toggle('ctrl', on);
      if (ckey) ckey.setAttribute('aria-pressed', String(on));
    };
    let held = false, cheld = false;
    window.addEventListener('keydown', (e) => {
      if (e.key === 'Shift' && !held) { held = true; setShift(true); }
      if (e.key === 'Control' && !cheld) { cheld = true; setCtrl(true); }
    });
    window.addEventListener('keyup', (e) => {
      if (e.key === 'Shift') { held = false; setShift(false); }
      if (e.key === 'Control') { cheld = false; setCtrl(false); }
    });
    window.addEventListener('blur', () => { held = cheld = false; setShift(false); setCtrl(false); });
    if (key) key.addEventListener('click', () => setShift(key.getAttribute('aria-pressed') !== 'true'));
    if (ckey) ckey.addEventListener('click', () => setCtrl(ckey.getAttribute('aria-pressed') !== 'true'));

    scope.querySelectorAll('[data-tip]').forEach((item) => {
      const tip = document.getElementById(item.dataset.tip);
      const open = (on) => { tip.classList.toggle('open', on); item.classList.toggle('open', on); item.setAttribute('aria-expanded', String(on)); };
      item.addEventListener('pointerenter', (e) => { if (e.pointerType === 'mouse') open(true); });
      item.addEventListener('pointerleave', (e) => { if (e.pointerType === 'mouse') open(false); });
      item.addEventListener('click', () => open(!tip.classList.contains('open')));
      item.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(!tip.classList.contains('open')); } });
    });
  }

  // ---------- editor mock-up: plays the default program in game time ----------
  const editor = document.getElementById('editor');
  if (editor) {
    const out = editor.querySelector('[data-side="back"] .out');
    const lines = editor.querySelectorAll('.ln');
    const state = document.getElementById('ed-state');
    const consoleEl = document.getElementById('ed-console');
    let timer = 0, on = false, running = false;
    const log = (text) => {
      const li = document.createElement('li');
      li.className = 'sys'; li.textContent = text;
      consoleEl.appendChild(li);
      while (consoleEl.children.length > 3) consoleEl.firstElementChild.remove();
    };
    const mark = (n) => lines.forEach((l) => l.classList.toggle('cur', Number(l.dataset.line) === n));
    // each half of the loop: set the output, then wait on the sleep(0.5) below it
    const step = () => { on = !on; out.classList.toggle('on', on); mark(on ? 5 : 7); };
    const run = () => {
      clearInterval(timer);
      running = true; on = false;
      state.textContent = 'Running'; state.classList.remove('off');
      step();
      timer = setInterval(step, 500);
    };
    const stop = () => {
      if (!running) return;
      clearInterval(timer);
      running = false; on = false;
      out.classList.remove('on'); mark(0);
      state.textContent = 'Stopped'; state.classList.add('off');
      log('Stopped');
    };
    document.getElementById('ed-run').addEventListener('click', () => { log('Running'); run(); });
    document.getElementById('ed-stop').addEventListener('click', stop);
    // blinking costs nothing while it's off screen, but there's no reason to run it then
    new IntersectionObserver((entries) => {
      if (!running) return;
      if (entries[0].isIntersecting) { if (!timer) run(); }
      else { clearInterval(timer); timer = 0; }
    }).observe(editor);
    run();
  }

  // ---------- close-up: one patch of plates you can brush ----------
  if (plates) {
    const COLS = 15, ROWS = 9;
    const glyphs = {
      4: ['...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'],
      8: ['.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.']
    };
    const base = new Array(COLS * ROWS).fill(false);
    [[4, 2], [8, 8]].forEach(([d, ox]) => glyphs[d].forEach((row, y) => [...row].forEach((c, x) => {
      if (c === '#') base[(y + 1) * COLS + ox + x] = true;
    })));
    plates.style.setProperty('--cols', COLS);
    const cells = [];
    for (let i = 0; i < COLS * ROWS; i++) {
      const el = document.createElement('span');
      el.className = 'plate' + (base[i] ? ' on' : '');
      el.innerHTML = '<i></i><i></i>';
      plates.appendChild(el);
      cells.push({ el, timer: 0 });
    }
    const flip = (i, hold) => {
      const c = cells[i];
      if (c.timer) return;
      c.el.classList.toggle('on', !base[i]);
      c.timer = setTimeout(() => { c.el.classList.toggle('on', base[i]); c.timer = 0; }, hold);
    };
    const at = (e) => {
      const el = document.elementFromPoint(e.clientX, e.clientY);
      const plate = el && el.closest('.plate');
      return plate ? cells.findIndex((c) => c.el === plate) : -1;
    };
    plates.addEventListener('pointermove', (e) => { const i = at(e); if (i >= 0) flip(i, 1100); });
    plates.addEventListener('pointerdown', (e) => { const i = at(e); if (i >= 0) flip(i, 1100); });

    const sweep = document.getElementById('plates-sweep');
    if (sweep) sweep.addEventListener('click', () => {
      cells.forEach((c, i) => {
        const x = i % COLS, y = (i / COLS) | 0;
        const d = reduce.matches ? 0 : x * 45 + y * 12;
        setTimeout(() => flip(i, reduce.matches ? 1600 : 1400 + (COLS - x) * 30), d);
      });
    });
  }
})();
