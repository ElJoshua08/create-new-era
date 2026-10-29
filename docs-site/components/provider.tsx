'use client';
import { RootProvider } from 'fumadocs-ui/provider/next';
import type { ComponentProps, ReactNode } from 'react';
import SearchDialog from '@/components/search';

export function Provider({
  children,
  i18n,
}: {
  children: ReactNode;
  i18n: ComponentProps<typeof RootProvider>['i18n'];
}) {
  return (
    <RootProvider i18n={i18n} search={{ SearchDialog }}>
      {children}
    </RootProvider>
  );
}
