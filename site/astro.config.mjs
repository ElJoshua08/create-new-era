import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://create-new-era.tryberyl.dev',
  output: 'static',
  trailingSlash: 'always',
  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'es'],
    routing: { prefixDefaultLocale: false },
  },
  build: { format: 'directory' },
});
