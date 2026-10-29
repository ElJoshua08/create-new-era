import { HomeLayout } from 'fumadocs-ui/layouts/home';
import type { ReactNode } from 'react';
import { baseOptions, t } from '@/lib/layout.shared';

export default async function Layout({ params, children }: { params: Promise<{ lang: string }>; children: ReactNode }) {
  const { lang } = await params;
  const base = baseOptions(lang);
  return (
    <HomeLayout
      {...base}
      links={[
        { text: t(lang).guide, url: `/${lang}/guide/`, active: 'nested-url' },
        { text: t(lang).dev, url: `/${lang}/dev/`, active: 'nested-url' },
        ...(base.links ?? []),
      ]}
    >
      {children}
    </HomeLayout>
  );
}
