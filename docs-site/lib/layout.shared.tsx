import { uiTranslations } from 'fumadocs-ui/i18n';
import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { Logo } from '@/components/logo';
import { i18n } from './i18n';
import { landingUrl, repoUrl } from './shared';

export const translations = i18n
  .translations()
  .extend(uiTranslations())
  .add({
    en: { displayName: 'English' },
    es: {
      displayName: 'Español',
      'Search(search trigger)': 'Buscar',
      'Search(search dialog)': 'Buscar',
      'Open Search(search trigger)(aria-label)': 'Abrir la búsqueda',
      'Close Search(search dialog)(aria-label)': 'Cerrar la búsqueda',
      'No results found(search dialog)': 'Nada encontrado',
      'Search failed(search dialog)': 'La búsqueda ha fallado',
      'Search failed, showing results for {query}(search dialog)': 'La búsqueda ha fallado, resultados para {query}',
      'On this page(table of contents)': 'En esta página',
      'No Headings(table of contents)': 'Sin apartados',
      'Table of Contents(inline table of contents)': 'Índice',
      'Next Page(pagination)': 'Siguiente',
      'Previous Page(pagination)': 'Anterior',
      'Choose a language(language switcher)': 'Elige idioma',
      'Choose a language(language switcher)(aria-label)': 'Elige idioma',
      'Language(language switcher)': 'Idioma',
      'Theme(site menu)': 'Tema',
      'Toggle Theme(theme switcher)(aria-label)': 'Cambiar tema',
      'Light(theme switcher)(aria-label)': 'Claro',
      'Dark(theme switcher)(aria-label)': 'Oscuro',
      'System(theme switcher)(aria-label)': 'Sistema',
      'Open Sidebar(aria-label)': 'Abrir el menú lateral',
      'Close Sidebar(aria-label)': 'Cerrar el menú lateral',
      'Open Sidebar(sidebar)(aria-label)': 'Abrir el menú lateral',
      'Close Sidebar(sidebar)(aria-label)': 'Cerrar el menú lateral',
      'Collapse Sidebar(sidebar)(aria-label)': 'Plegar el menú lateral',
      'Hide Sidebar(sidebar)': 'Ocultar el menú',
      'Show Sidebar(sidebar)': 'Mostrar el menú',
      'Toggle Menu(home layout header)(aria-label)': 'Abrir o cerrar el menú',
      'Copy Text(code block)(aria-label)': 'Copiar',
      'Copied Text(code block)(aria-label)': 'Copiado',
      'Copy Anchor Link(heading anchor)(aria-label)': 'Copiar enlace',
      'Copied Anchor Link(heading anchor)(aria-label)': 'Enlace copiado',
      'Edit on GitHub(edit page)': 'Editar en GitHub',
      'Last updated on(page footer)': 'Actualizado el',
      'Page Not Found(404 not found page)': 'No existe esa página',
      'Back to Home(404 not found page)': 'Volver al inicio',
      'The page you are looking for might have been removed, had its name changed, or is temporarily unavailable.(404 not found page)':
        'Puede que la hayan quitado o cambiado de nombre.',
      'Options(aria-label)': 'Opciones',
    },
  });

const labels = {
  en: { website: 'Website', guide: 'Guide', dev: 'Development' },
  es: { website: 'Web', guide: 'Guía', dev: 'Desarrollo' },
};

export function t(lang: string) {
  return labels[lang as keyof typeof labels] ?? labels.en;
}

export function baseOptions(lang: string): BaseLayoutProps {
  return {
    nav: {
      title: <Logo />,
      url: `/${lang}`,
    },
    githubUrl: repoUrl,
    links: [
      {
        text: t(lang).website,
        url: landingUrl,
        external: true,
        active: 'none',
      },
    ],
  };
}
