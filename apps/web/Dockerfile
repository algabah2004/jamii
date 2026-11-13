# Interface web de Jamii, servie par Caddy (licence Apache 2.0), qui relaie /api vers l'API sur la même origine
# (indispensable pour le cookie de session et le jeton CSRF, voir apps/web/README.md).
# Construire depuis la RACINE du dépôt (le front lit docs/design) :  docker build -f apps/web/Dockerfile -t jamii-web .
FROM node:22-alpine AS compilation
WORKDIR /src/apps/web
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY docs/design /src/docs/design
COPY apps/web ./
RUN npm run build

FROM caddy:2-alpine
COPY apps/web/Caddyfile /etc/caddy/Caddyfile
COPY --from=compilation /src/apps/web/dist /srv
EXPOSE 8080
