import { defineI18n } from 'fumadocs-core/i18n';

export const i18n = defineI18n({
  defaultLanguage: 'en',
  languages: ['en', 'es'],
  // static export has no middleware to hide or guess the locale, so every URL carries it
  hideLocale: 'never',
  parser: 'dir',
});

export type Lang = (typeof i18n.languages)[number];
