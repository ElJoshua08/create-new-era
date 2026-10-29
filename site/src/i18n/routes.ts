export type Lang = 'en' | 'es';
export type Page = 'home' | 'computer' | 'bus' | 'screens' | 'roadmap' | 'download';

export const routes: Record<Page, Record<Lang, string>> = {
  home: { en: '/', es: '/es/' },
  computer: { en: '/computer/', es: '/es/ordenador/' },
  bus: { en: '/data-bus/', es: '/es/bus-de-datos/' },
  screens: { en: '/screens-and-panels/', es: '/es/pantallas-y-paneles/' },
  roadmap: { en: '/whats-next/', es: '/es/lo-que-viene/' },
  download: { en: '/download/', es: '/es/descargar/' },
};

export const DOCS = 'https://docs.create-new-era.tryberyl.dev';
export const REPO = 'https://github.com/ElJoshua08/create-new-era';
export const FALLBACK = {
  tag: '0.1.0-alpha.1',
  url: 'https://github.com/ElJoshua08/create-new-era/releases/download/v0.1.0-alpha.1/create_new_era-0.1.0-alpha.1.jar',
};

export const ui = {
  en: {
    nav: { computer: 'Computer', bus: 'Data bus', screens: 'Screens & panels', roadmap: "What's next", docs: 'Docs' },
    download: 'Download',
    prerelease: 'Pre-release',
    soon: 'Coming',
    skip: 'Skip to content',
    langName: 'English',
    other: 'Español',
    footer: {
      unofficial: 'An unofficial addon for Create. Not made by, or affiliated with, the Create team.',
      license: 'Public domain under the Unlicense.',
      issues: 'Report a bug',
      source: 'Source on GitHub',
      docs: 'Documentation',
    },
  },
  es: {
    nav: { computer: 'Ordenador', bus: 'Bus de datos', screens: 'Pantallas y paneles', roadmap: 'Lo que viene', docs: 'Docs' },
    download: 'Descargar',
    prerelease: 'Prelanzamiento',
    soon: 'Próximamente',
    skip: 'Saltar al contenido',
    langName: 'Español',
    other: 'English',
    footer: {
      unofficial: 'Un addon no oficial para Create. No lo ha hecho el equipo de Create ni tiene relación con él.',
      license: 'Dominio público bajo la Unlicense.',
      issues: 'Avisar de un fallo',
      source: 'Código en GitHub',
      docs: 'Documentación',
    },
  },
} as const;
