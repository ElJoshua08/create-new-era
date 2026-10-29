'use client';
import { useEffect, useState } from 'react';
import { fallbackRelease, releasesApi } from '@/lib/shared';

type Release = { tag: string; url: string; page: string; prerelease: boolean };

type GithubRelease = {
  tag_name: string;
  html_url: string;
  prerelease: boolean;
  draft: boolean;
  assets: { name: string; browser_download_url: string }[];
};

const text = {
  en: { download: 'Download', pre: 'pre-release', notes: 'Release notes' },
  es: { download: 'Descargar', pre: 'versión previa', notes: 'Notas de la versión' },
};

// newest release first, pre-releases included, as long as it has a jar attached
async function latest(): Promise<Release | null> {
  const res = await fetch(releasesApi, { headers: { Accept: 'application/vnd.github+json' } });
  if (!res.ok) return null;
  const list = (await res.json()) as GithubRelease[];
  for (const r of list) {
    if (r.draft) continue;
    const jar = r.assets.find((a) => a.name.endsWith('.jar') && !a.name.endsWith('-sources.jar'));
    if (jar) return { tag: r.tag_name, url: jar.browser_download_url, page: r.html_url, prerelease: r.prerelease };
  }
  return null;
}

export function Download({ lang }: { lang?: string }) {
  const t = text[lang === 'es' ? 'es' : 'en'];
  const [release, setRelease] = useState<Release>({ ...fallbackRelease, prerelease: true });

  useEffect(() => {
    let cancelled = false;
    latest()
      .then((r) => {
        if (r && !cancelled) setRelease(r);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <span className="not-prose inline-flex flex-wrap items-center gap-x-4 gap-y-2">
      <a
        href={release.url}
        className="press inline-flex items-center gap-2 rounded-lg bg-fd-primary px-4 py-2.5 text-sm font-medium text-fd-primary-foreground shadow-sm hover:brightness-110"
      >
        <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden>
          <path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19h14" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        {t.download} {release.tag.replace(/^v/, '')}
      </a>
      <span className="text-sm text-fd-muted-foreground">
        {release.prerelease ? `${t.pre} · ` : ''}
        <a href={release.page} className="underline underline-offset-4 hover:text-fd-foreground">
          {t.notes}
        </a>
      </span>
    </span>
  );
}
