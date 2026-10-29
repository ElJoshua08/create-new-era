export const appName = 'Create: New Era';
export const siteUrl = 'https://docs.create-new-era.tryberyl.dev';
export const landingUrl = 'https://create-new-era.tryberyl.dev';

export const gitConfig = {
  user: 'ElJoshua08',
  repo: 'create-new-era',
  branch: 'main',
};

export const repoUrl = `https://github.com/${gitConfig.user}/${gitConfig.repo}`;
export const releasesApi = `https://api.github.com/repos/${gitConfig.user}/${gitConfig.repo}/releases?per_page=5`;
export const fallbackRelease = {
  tag: 'v0.1.0-alpha.1',
  url: `${repoUrl}/releases/download/v0.1.0-alpha.1/create_new_era-0.1.0-alpha.1.jar`,
  page: `${repoUrl}/releases/tag/v0.1.0-alpha.1`,
};
