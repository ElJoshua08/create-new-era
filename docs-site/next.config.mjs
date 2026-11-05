import { readFileSync } from 'node:fs';
import { createMDX } from 'fumadocs-mdx/next';

// the mod's version as gradle.properties has it, for when GitHub can't be asked for the latest release
const modVersion = readFileSync(new URL('../gradle.properties', import.meta.url), 'utf8')
  .match(/^mod_version=(.+)$/m)?.[1]
  .trim();

const withMDX = createMDX();

// The docs live under the landing page's domain, at /docs/; Caddy serves out/ there.
const basePath = '/docs';

/** @type {import('next').NextConfig} */
const config = {
  // plain files in out/, served at create-new-era.tryberyl.dev/docs/
  output: 'export',
  basePath,
  // /en/guide/install/ -> out/en/guide/install/index.html, which any static server finds
  trailingSlash: true,
  images: { unoptimized: true },
  reactStrictMode: true,
  env: { NEXT_PUBLIC_MOD_VERSION: modVersion, NEXT_PUBLIC_BASE_PATH: basePath },
  // app/global-not-found.tsx becomes out/404.html; the app has one root layout per language, none above them
  experimental: { globalNotFound: true },
};

export default withMDX(config);
