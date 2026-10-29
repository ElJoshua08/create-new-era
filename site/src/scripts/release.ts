// Points every download link at the newest release's jar, pre-releases included.
// The pages ship with 0.1.0-alpha.1 baked in, so they work if GitHub can't be reached.

const API = 'https://api.github.com/repos/ElJoshua08/create-new-era/releases?per_page=5';
const KEY = 'cne-release';
const MAX_AGE = 10 * 60 * 1000;

interface Release {
  tag: string;
  url: string;
  prerelease: boolean;
  published: string;
}

interface GhRelease {
  tag_name: string;
  draft: boolean;
  prerelease: boolean;
  published_at: string;
  assets: { name: string; browser_download_url: string }[];
}

function cached(): Release | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const { at, release } = JSON.parse(raw);
    return Date.now() - at < MAX_AGE ? release : null;
  } catch {
    return null;
  }
}

async function fetchLatest(): Promise<Release | null> {
  const hit = cached();
  if (hit) return hit;
  const res = await fetch(API, { headers: { Accept: 'application/vnd.github+json' } });
  if (!res.ok) return null;
  const list: GhRelease[] = await res.json();
  const newest = list
    .filter((r) => !r.draft)
    .sort((a, b) => Date.parse(b.published_at) - Date.parse(a.published_at))[0];
  if (!newest) return null;
  const jar = newest.assets.find((a) => a.name.endsWith('.jar') && !/sources|javadoc/.test(a.name));
  if (!jar) return null;
  const release = {
    tag: newest.tag_name.replace(/^v/, ''),
    url: jar.browser_download_url,
    prerelease: newest.prerelease,
    published: newest.published_at,
  };
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ at: Date.now(), release }));
  } catch {
    /* private mode */
  }
  return release;
}

function apply(r: Release) {
  const lang = document.documentElement.lang === 'es' ? 'es-ES' : 'en-GB';
  document.querySelectorAll<HTMLAnchorElement>('[data-download]').forEach((a) => (a.href = r.url));
  document.querySelectorAll('[data-version]').forEach((el) => (el.textContent = r.tag));
  document.querySelectorAll<HTMLElement>('[data-prerelease]').forEach((el) => (el.hidden = !r.prerelease));
  document.querySelectorAll('[data-published]').forEach((el) => {
    el.textContent = new Date(r.published).toLocaleDateString(lang, { day: 'numeric', month: 'long', year: 'numeric' });
  });
}

fetchLatest()
  .then((r) => r && apply(r))
  .catch(() => {});
