import { i18nProvider } from 'fumadocs-ui/i18n';
import type { Metadata, Viewport } from 'next';
import type { ReactNode } from 'react';
import { jetbrains, monocraft, plex } from '@/components/fonts';
import { Provider } from '@/components/provider';
import { i18n } from '@/lib/i18n';
import { translations } from '@/lib/layout.shared';
import { appName, siteUrl } from '@/lib/shared';
import '../global.css';

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: { default: `${appName} docs`, template: `%s · ${appName}` },
  icons: { icon: '/img/items/create_new_era_brass_computer.png' },
};

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#faf7f1' },
    { media: '(prefers-color-scheme: dark)', color: '#14110d' },
  ],
};

export default async function Layout({ params, children }: { params: Promise<{ lang: string }>; children: ReactNode }) {
  const { lang } = await params;
  return (
    <html
      lang={lang}
      className={`${plex.variable} ${jetbrains.variable} ${monocraft.variable}`}
      suppressHydrationWarning
    >
      <body className="flex flex-col min-h-screen font-sans">
        <Provider i18n={i18nProvider(translations, lang)}>{children}</Provider>
      </body>
    </html>
  );
}

export function generateStaticParams() {
  return i18n.languages.map((lang) => ({ lang }));
}
