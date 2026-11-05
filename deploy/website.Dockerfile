# The landing page (site/, Astro) and the docs (docs-site/, Fumadocs), both built to
# static files and served by one Caddy: the landing at /, the docs under /docs/.
FROM node:24-alpine AS build
RUN corepack enable && corepack prepare pnpm@10.34.5 --activate
WORKDIR /build/site
COPY site/package.json site/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY site/ ./
RUN pnpm build
# the docs read the mod's version from gradle.properties
COPY gradle.properties /build/
WORKDIR /build/docs-site
COPY docs-site/package.json docs-site/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY docs-site/ ./
RUN pnpm build

FROM caddy:2-alpine
COPY --from=build /build/site/dist /srv/site
COPY --from=build /build/docs-site/out /srv/docs
COPY deploy/Caddyfile /etc/caddy/Caddyfile
