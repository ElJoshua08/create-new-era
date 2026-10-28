(function () {
  'use strict';
  const root = document.documentElement;
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* private mode: the toggle still works for this page */ } }
  };

  // ---------- theme: follows the system until the toggle is used ----------
  const isDark = () => root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
  document.querySelectorAll('.theme-toggle').forEach((b) => b.addEventListener('click', () => {
    const next = isDark() ? 'light' : 'dark';
    root.dataset.theme = next;
    store.set('cne-theme', next);
  }));

  // ---------- mobile drawer ----------
  const body = document.body;
  const menuBtn = document.querySelector('.menu-btn');
  const setDrawer = (open) => {
    body.classList.toggle('drawer-open', open);
    if (menuBtn) menuBtn.setAttribute('aria-expanded', String(open));
    if (open) { const a = document.querySelector('.sidebar a[aria-current]') || document.querySelector('.sidebar a'); if (a) a.focus({ preventScroll: true }); }
  };
  if (menuBtn) menuBtn.addEventListener('click', () => setDrawer(!body.classList.contains('drawer-open')));
  document.querySelectorAll('.scrim, .drawer-close').forEach((el) => el.addEventListener('click', () => setDrawer(false)));
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && body.classList.contains('drawer-open')) setDrawer(false); });

  // ---------- "On this page" scroll-spy ----------
  const tocLinks = [...document.querySelectorAll('.toc a')];
  if (tocLinks.length) {
    const targets = tocLinks.map((a) => document.getElementById(decodeURIComponent(a.hash.slice(1)))).filter(Boolean);
    const setActive = (id) => tocLinks.forEach((a) => a.classList.toggle('active', a.hash.slice(1) === id));
    const pick = () => {
      // the last heading that has scrolled past the top bar is the section being read
      const line = 96;
      let current = targets[0];
      for (const t of targets) { if (t.getBoundingClientRect().top - line <= 0) current = t; else break; }
      if (innerHeight + scrollY >= document.documentElement.scrollHeight - 4) current = targets[targets.length - 1];
      if (current) setActive(current.id);
    };
    let raf = 0;
    addEventListener('scroll', () => { if (!raf) raf = requestAnimationFrame(() => { raf = 0; pick(); }); }, { passive: true });
    pick();
  }

  // ---------- copy buttons ----------
  document.querySelectorAll('.copy-btn').forEach((btn) => btn.addEventListener('click', async () => {
    const code = btn.closest('.codeblock').querySelector('pre').innerText;
    try {
      await navigator.clipboard.writeText(code);
    } catch (e) {
      const ta = document.createElement('textarea');
      ta.value = code; document.body.appendChild(ta); ta.select();
      try { document.execCommand('copy'); } catch (err) { /* nothing else to try */ }
      ta.remove();
    }
    btn.classList.add('done');
    btn.querySelector('.label').textContent = 'Copied';
    setTimeout(() => { btn.classList.remove('done'); btn.querySelector('.label').textContent = 'Copy'; }, 1600);
  }));

  // ---------- search over page titles and headings ----------
  const dialog = document.getElementById('search');
  const input = document.getElementById('search-input');
  const list = document.getElementById('search-results');
  const index = window.DOCS_SEARCH || [];
  const base = document.body.dataset.root || './';
  let sel = 0, hits = [];
  const esc = (s) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const render = () => {
    const q = input.value.trim().toLowerCase();
    const words = q.split(/\s+/).filter(Boolean);
    hits = (words.length ? index.filter((e) => words.every((w) => (e.title + ' ' + e.page).toLowerCase().includes(w))) : index.filter((e) => !e.hash)).slice(0, 12);
    sel = 0;
    list.innerHTML = hits.length
      ? hits.map((e, i) => `<li><a href="${base}${e.url}" role="option" aria-selected="${i === sel}">${esc(e.title)}${e.hash ? `<small>${esc(e.page)}</small>` : ''}</a></li>`).join('')
      : '<li class="search-empty">No results for “' + esc(input.value) + '”</li>';
  };
  const move = (d) => {
    const links = list.querySelectorAll('a');
    if (!links.length) return;
    sel = (sel + d + links.length) % links.length;
    links.forEach((a, i) => a.setAttribute('aria-selected', String(i === sel)));
    links[sel].scrollIntoView({ block: 'nearest' });
  };
  const open = () => { if (!dialog.open) { dialog.showModal(); input.value = ''; render(); input.focus(); } };
  document.querySelectorAll('.search-btn').forEach((b) => b.addEventListener('click', open));
  document.addEventListener('keydown', (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); open(); }
    else if (e.key === '/' && !/input|textarea/i.test(document.activeElement.tagName)) { e.preventDefault(); open(); }
  });
  if (input) {
    input.addEventListener('input', render);
    input.addEventListener('keydown', (e) => {
      if (e.key === 'ArrowDown') { e.preventDefault(); move(1); }
      else if (e.key === 'ArrowUp') { e.preventDefault(); move(-1); }
      else if (e.key === 'Enter') { const a = list.querySelectorAll('a')[sel]; if (a) { e.preventDefault(); location.href = a.href; dialog.close(); } }
    });
    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.close(); });
    list.addEventListener('click', (e) => { if (e.target.closest('a')) dialog.close(); });
  }
})();
