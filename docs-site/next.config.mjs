import { createMDX } from 'fumadocs-mdx/next';

const withMDX = createMDX();

/** @type {import('next').NextConfig} */
const config = {
  // plain files in out/, served as they are at docs.create-new-era.tryberyl.dev
  output: 'export',
  // /en/guide/install/ -> out/en/guide/install/index.html, which any static server finds
  trailingSlash: true,
  images: { unoptimized: true },
  reactStrictMode: true,
  // app/global-not-found.tsx becomes out/404.html; the app has one root layout per language, none above them
  experimental: { globalNotFound: true },
};

export default withMDX(config);
