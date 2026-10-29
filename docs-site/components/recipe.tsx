import data from '@/lib/recipes.json';

type Item = { en: string; es: string; icon: string; mod: string };
type Grid = {
  type: 'shaped' | 'mechanical';
  grid: (string | null)[][];
  result: string;
  resultIcon?: string;
  count: number;
  mirrored: boolean;
};
type Shapeless = { type: 'shapeless'; ingredients: string[]; result: string; count: number };
type Application = { type: 'application'; base: string; held: string; result: string; count: number };
type RecipeData = Grid | Shapeless | Application;

const items = data.items as Record<string, Item>;
const recipes = data.recipes as unknown as Record<string, RecipeData>;

const mods: Record<string, string> = {
  minecraft: 'Minecraft',
  create: 'Create',
  create_new_era: 'Create: New Era',
};

const text = {
  en: {
    shaped: 'Crafting Table',
    shapeless: 'Crafting Table, any arrangement',
    mechanical: 'Mechanical Crafting',
    application: 'Item Application',
    makes: 'Makes',
    onto: 'onto',
    hold: 'Hold',
    notMirrored: 'Lay it out on Mechanical Crafters in exactly this shape; it can’t be mirrored.',
    applicationHow:
      'Right-click a placed {base} while holding the {held}, or let a Deployer do it. The held item is used up.',
    dyed: 'Blue Cable Spool',
  },
  es: {
    shaped: 'Mesa de crafteo',
    shapeless: 'Mesa de crafteo, en cualquier orden',
    mechanical: 'Ensamblaje mecánico',
    application: 'Aplicación de objeto',
    makes: 'Da',
    onto: 'sobre',
    hold: 'En la mano',
    notMirrored: 'Colócalo en ensambladores mecánicos con esta forma exacta; no vale la versión en espejo.',
    applicationHow:
      'Haz clic derecho en un {base} colocado con el {held} en la mano, o deja que lo haga un desplegador. El objeto de la mano se gasta.',
    dyed: 'Bobina de cable (azul)',
  },
};

function name(id: string, lang: 'en' | 'es') {
  return items[id]?.[lang] ?? id;
}

function Slot({
  id,
  lang,
  icon,
  count,
  big,
}: {
  id: string | null;
  lang: 'en' | 'es';
  icon?: string;
  count?: number;
  big?: boolean;
}) {
  const size = big ? 'size-14' : 'size-11';
  if (!id) return <span className={`${size} rounded-[3px] recipe-slot`} aria-hidden />;
  const label = name(id, lang);
  return (
    <span className={`${size} relative grid place-items-center rounded-[3px] recipe-slot`} title={label}>
      {/* biome-ignore lint/performance/noImgElement: tiny pixel-art icons in a static export */}
      <img src={icon ?? items[id].icon} alt={label} className="pixelated size-[78%]" loading="lazy" />
      {count && count > 1 ? (
        <span className="pixel absolute right-0.5 bottom-0 text-[13px] leading-none text-white [text-shadow:1px_1px_0_#3f3f3f]">
          {count}
        </span>
      ) : null}
    </span>
  );
}

function Arrow() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="size-6 shrink-0 text-fd-muted-foreground"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      aria-hidden
    >
      <path d="M4 12h15M13 6l6 6-6 6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

export function Recipe({ id, lang: rawLang }: { id: string; lang?: string }) {
  const lang = rawLang === 'es' ? 'es' : 'en';
  const t = text[lang];
  const r = recipes[id];
  if (!r) throw new Error(`no recipe ${id}`);

  let kind: string;
  let input: React.ReactNode;
  let used: string[];
  let note: string | null = null;

  if (r.type === 'shaped' || r.type === 'mechanical') {
    kind = r.type === 'mechanical' ? t.mechanical : t.shaped;
    const width = r.grid[0].length;
    input = (
      <span className="grid gap-1" style={{ gridTemplateColumns: `repeat(${width}, auto)` }}>
        {r.grid.flat().map((cell, i) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: grid cells have no identity beyond their place
          <Slot key={i} id={cell} lang={lang} />
        ))}
      </span>
    );
    used = [...new Set(r.grid.flat().filter((c): c is string => c !== null))];
    if (!r.mirrored) note = t.notMirrored;
  } else if (r.type === 'shapeless') {
    kind = t.shapeless;
    input = (
      <span className="grid grid-cols-3 gap-1">
        {Array.from({ length: Math.max(3, Math.ceil(r.ingredients.length / 3) * 3) }, (_, i) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: grid cells have no identity beyond their place
          <Slot key={i} id={r.ingredients[i] ?? null} lang={lang} />
        ))}
      </span>
    );
    used = [...new Set(r.ingredients)];
  } else {
    const a = r as Application;
    kind = t.application;
    input = (
      <span className="flex items-center gap-2">
        <span className="flex flex-col items-center gap-1 text-[11px] text-fd-muted-foreground">
          <Slot id={a.held} lang={lang} />
          {t.hold}
        </span>
        <span className="text-xs text-fd-muted-foreground">{t.onto}</span>
        <Slot id={a.base} lang={lang} />
      </span>
    );
    used = [a.held, a.base];
    note = t.applicationHow.replace('{base}', name(a.base, lang)).replace('{held}', name(a.held, lang));
  }

  const resultIcon = 'resultIcon' in r ? r.resultIcon : undefined;
  const resultName = resultIcon ? t.dyed : name(r.result, lang);

  return (
    <figure className="not-prose my-6 overflow-hidden rounded-xl border bg-fd-card">
      <figcaption className="flex items-baseline justify-between gap-3 border-b px-4 py-2.5 text-sm">
        <span className="font-medium text-fd-foreground">{kind}</span>
        <span className="text-fd-muted-foreground">
          {t.makes} {r.count} × {resultName}
        </span>
      </figcaption>
      <div className="flex flex-wrap items-center gap-4 px-4 py-5">
        {input}
        <Arrow />
        <Slot id={r.result} icon={resultIcon} count={r.count} lang={lang} big />
      </div>
      <ul className="flex flex-col gap-1.5 border-t px-4 py-3 text-sm">
        {used.map((u) => (
          <li key={u} className="flex items-center gap-2">
            {/* biome-ignore lint/performance/noImgElement: tiny pixel-art icons in a static export */}
            <img src={items[u].icon} alt="" className="pixelated size-5" loading="lazy" />
            <span className="text-fd-foreground">{name(u, lang)}</span>
            <span className="text-xs text-fd-muted-foreground">{mods[items[u].mod]}</span>
          </li>
        ))}
      </ul>
      {note ? <p className="border-t px-4 py-3 text-sm text-fd-muted-foreground">{note}</p> : null}
    </figure>
  );
}
