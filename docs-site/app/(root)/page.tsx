import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Create: New Era docs',
};

// Static hosting has no server to read Accept-Language, so the browser picks the language.
const pick = `(function(){var l=(navigator.languages&&navigator.languages[0])||navigator.language||'';location.replace(/^es\\b/i.test(l)?'/es/':'/en/');})();`;

export default function RootRedirect() {
  return (
    <main style={{ padding: 24 }}>
      {/* without JavaScript the English docs open after a moment */}
      <meta httpEquiv="refresh" content="1;url=/en/" />
      {/* biome-ignore lint/security/noDangerouslySetInnerHtml: a fixed redirect script, no user input */}
      <script dangerouslySetInnerHTML={{ __html: pick }} />
      <p>
        <a href="/en/" style={{ color: '#e4b763' }}>
          English
        </a>{' '}
        ·{' '}
        <a href="/es/" style={{ color: '#e4b763' }}>
          Español
        </a>
      </p>
    </main>
  );
}
