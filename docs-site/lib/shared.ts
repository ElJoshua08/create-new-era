export const appName = 'Create: New Era';
export const landingUrl = 'https://create-new-era.tryberyl.dev';
// next.config.mjs' basePath. Next adds it to its own links and imported images, not to plain src/href strings.
export const basePath = process.env.NEXT_PUBLIC_BASE_PATH ?? '';
export const siteUrl = `${landingUrl}${basePath}`;
/** A path in public/ or a page, as the browser must ask for it. */
export const withBase = (path: string) => `${basePath}${path}`;

export const gitConfig = {
  user: 'ElJoshua08',
  repo: 'create-new-era',
  branch: 'main',
};

export const repoUrl = `https://github.com/${gitConfig.user}/${gitConfig.repo}`;
export const releasesApi = `https://api.github.com/repos/${gitConfig.user}/${gitConfig.repo}/releases?per_page=5`;
// gradle.properties' mod_version, put in by next.config.mjs; the live release replaces it once fetched
const modVersion = process.env.NEXT_PUBLIC_MOD_VERSION ?? '0.1.0-alpha.3';
export const fallbackRelease = {
  tag: `v${modVersion}`,
  url: `${repoUrl}/releases/download/v${modVersion}/create_new_era-${modVersion}.jar`,
  page: `${repoUrl}/releases/tag/v${modVersion}`,
};
