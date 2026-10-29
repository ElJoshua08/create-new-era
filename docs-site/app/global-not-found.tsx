import type { Metadata } from 'next';
import { jetbrains, monocraft, plex } from '@/components/fonts';
import './global.css';

export const metadata: Metadata = { title: 'Not found · Create: New Era docs' };

const theme = `try{var t=localStorage.getItem('theme');if(t==='dark'||(t!=='light'&&matchMedia('(prefers-color-scheme: dark)').matches))document.documentElement.classList.add('dark')}catch(e){}`;

// static hosts serve this for any missing path, in either language, so it speaks both
export default function GlobalNotFound() {
  return (
    <html lang="en" className={`${plex.variable} ${jetbrains.variable} ${monocraft.variable}`}>
      <body className="flex min-h-screen items-center justify-center px-4 font-sans">
        {/* biome-ignore lint/security/noDangerouslySetInnerHtml: fixed script, picks the theme the docs would use */}
        <script dangerouslySetInnerHTML={{ __html: theme }} />
        <main className="max-w-md text-center">
          {/* biome-ignore lint/performance/noImgElement: pixel-art icon in a static export */}
          <img
            src="/img/blocks/brass_computer_off.png"
            alt=""
            width={120}
            height={120}
            className="pixelated mx-auto w-[120px]"
          />
          <h1 className="mt-6 text-2xl font-semibold">No page here</h1>
          <p className="mt-2 text-fd-muted-foreground">Esta página no existe.</p>
          <p className="mt-6 flex justify-center gap-3 text-sm">
            <a className="press rounded-lg border px-3.5 py-2 hover:bg-fd-accent" href="/en/">
              English docs
            </a>
            <a className="press rounded-lg border px-3.5 py-2 hover:bg-fd-accent" href="/es/">
              Documentación en español
            </a>
          </p>
        </main>
      </body>
    </html>
  );
}
