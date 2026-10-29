const KEYWORDS = new Set([
  'local', 'function', 'end', 'if', 'then', 'else', 'elseif', 'and', 'or', 'not', 'return',
  'while', 'do', 'for', 'in', 'repeat', 'until', 'true', 'false', 'nil', 'break',
]);

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Lua to HTML spans, coloured like the in-game editor. */
export function highlight(src: string): string {
  const re = /(--[^\n]*)|("(?:[^"\\\n]|\\.)*"?)|(\b\d+(?:\.\d+)?\b)|([A-Za-z_][A-Za-z0-9_]*)(\s*\()?|([\s\S])/g;
  let out = '';
  for (const m of src.matchAll(re)) {
    if (m[1]) out += `<span class="c">${esc(m[1])}</span>`;
    else if (m[2]) out += `<span class="s">${esc(m[2])}</span>`;
    else if (m[3]) out += `<span class="n">${m[3]}</span>`;
    else if (m[4]) {
      if (KEYWORDS.has(m[4])) out += `<span class="k">${m[4]}</span>`;
      else if (m[5]) out += `<span class="f">${m[4]}</span>${esc(m[5])}`;
      else out += m[4];
    } else out += esc(m[6]);
  }
  return out;
}
