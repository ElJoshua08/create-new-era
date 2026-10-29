import type { ReactNode } from 'react';

export default function RootRedirectLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body style={{ background: '#14110d', color: '#efe4cc', fontFamily: 'system-ui, sans-serif' }}>{children}</body>
    </html>
  );
}
