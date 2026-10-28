# The public site: static files from website/, served by Caddy.
FROM caddy:2-alpine
COPY website /srv
COPY deploy/Caddyfile /etc/caddy/Caddyfile
# Cache busting: Cloudflare keeps CSS/JS for hours, so every page asks for them as
# name.css?v=<content hash> and a changed file is a new URL.
RUN cd /srv && for f in $(find . -name '*.css' -o -name '*.js'); do \
      h=$(md5sum "$f" | cut -c1-10); b=$(basename "$f"); \
      find . -name '*.html' -exec sed -i "s#\([\"/]\)${b}\"#\1${b}?v=${h}\"#g" {} +; \
    done
