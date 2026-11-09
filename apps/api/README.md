# API

**Responsable : @sowibrr** ([#17](https://github.com/algabah2004/jamii/issues/17)) · Spring Boot 4.1 · Java 21 · PostgreSQL 16 · Flyway

Un **monolithe modulaire** : chaque sous-paquet de `dev.jamii.api` (auth, projets, communautes…) est un module, et `ModulesTest` refuse qu'un module utilise les classes internes d'un autre. Contrat : [`contracts/socle.yaml`](../../contracts/socle.yaml). Conventions : [`docs/api-conventions.md`](../../docs/api-conventions.md).

## Lancer en 5 minutes

Prérequis : **Java 21 ou plus** et **Docker**. Maven est fourni (`./mvnw`).

```bash
cp .env.example .env              # à la racine du dépôt (une seule fois)
cd apps/api
./mvnw spring-boot:run            # sous Windows (cmd/PowerShell) : mvnw.cmd spring-boot:run
curl localhost:8080/health        # {"status":"UP",…}
```

`spring-boot:run` démarre tout seul le PostgreSQL de `docker-compose.yml` (s'il ne tourne pas déjà) et s'y connecte. Tu n'as rien à configurer côté API. À l'arrêt de l'API, la base reste allumée, car le service IA l'utilise aussi.

- **Port 5432 déjà pris** : change `POSTGRES_PORT` dans `.env`. docker compose le lit, et l'API trouve le bon port toute seule.
- **Port 8080 déjà pris** : `API_PORT=18080 ./mvnw spring-boot:run` (PowerShell : `$env:API_PORT=18080; .\mvnw.cmd spring-boot:run`).
- **Depuis un IDE** : lance `ApiApplication` avec `apps/api` comme dossier de travail, pour qu'il trouve `../../docker-compose.yml`.

## Tester

```bash
./mvnw verify
```

Les tests démarrent une **vraie base PostgreSQL** dans Docker (Testcontainers), avec la même image que `docker-compose.yml`. Docker doit donc tourner. Aucune base locale n'est nécessaire.

## Configuration

Tout passe par des variables d'environnement (voir [`.env.example`](../../.env.example)) :

| Variable | Rôle | Défaut |
|---|---|---|
| `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER` | Connexion à la base | `localhost`, `5432`, `jamii`, `jamii` |
| `POSTGRES_PASSWORD` | Mot de passe de la base | **aucun** : pas de mot de passe dans le code |
| `POSTGRES_SSLMODE` | Chiffrement de la connexion (`require` ou `verify-full` en production) | `prefer` |
| `PORT`, puis `API_PORT` | Port d'écoute. `PORT` est celui qu'impose l'hébergeur, s'il y en a un | `8080` |

En local, `./mvnw spring-boot:run` n'a besoin d'aucune de ces variables : la connexion vient de docker compose. Elles servent au déploiement.

## Santé

| Route | Usage |
|---|---|
| `GET /health` | État global. `503` si la base est injoignable. Aucun détail interne exposé |
| `GET /health/liveness` | Le processus vit. Reste `UP` si la base tombe : inutile de redémarrer l'API pour ça |
| `GET /health/readiness` | Prêt à recevoir du trafic. `503` si la base est injoignable |

Aucun autre endpoint Actuator n'est exposé.

## Connexion et sécurité

Tout le code est dans le module `auth` (`dev.jamii.api.auth`).

- **Connexion GitHub** : `GET /api/auth/github?retour=/chemin`. Pour l'essayer en local, crée une [OAuth App GitHub](https://github.com/settings/developers) avec l'adresse de retour `http://localhost:8080/api/auth/github/retour`, puis remplis `GITHUB_CLIENT_ID` et `GITHUB_CLIENT_SECRET`. Sans ces variables, l'API démarre quand même, mais la connexion GitHub échoue.
- **Session** : cookie `JAMII_SESSION` (HttpOnly, Secure, SameSite=Lax), stockée dans PostgreSQL et expirée après 7 jours sans activité.
- **CSRF** : tout `POST`, `PUT`, `PATCH` ou `DELETE` renvoie la valeur du cookie `XSRF-TOKEN` dans l'en-tête `X-XSRF-TOKEN`.
- **Tout est fermé par défaut.** Une route publique s'ouvre explicitement dans `SecuriteConfig`.
- **Dans ton module**, l'utilisateur connecté s'obtient avec `@AuthenticationPrincipal UtilisateurConnecte moi`, qui donne `moi.id()` et `moi.roles()`.

## Base de données

- Le schéma appartient à **Flyway** : `src/main/resources/db/migration/V<n>__<description>.sql`.
- On ne modifie **jamais** une migration fusionnée : on en ajoute une nouvelle.
- Hibernate est en mode `validate` : il vérifie le schéma, il ne le modifie jamais.
