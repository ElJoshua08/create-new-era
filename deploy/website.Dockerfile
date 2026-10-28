# The public site: static files from website/, served by Caddy.
FROM caddy:2-alpine
COPY website /srv
COPY deploy/Caddyfile /etc/caddy/Caddyfile
