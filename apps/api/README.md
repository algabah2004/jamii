# API

**Responsable : @sowibrr** ([#17](https://github.com/algabah2004/jamii/issues/17)) · Spring Boot 4.1 · Java 21 · PostgreSQL 16 · Flyway

Un **monolithe modulaire** : chaque sous-paquet de `dev.jamii.api` (auth, projets, communautes…) est un module, et `ModulesTest` refuse qu'un module utilise les classes internes d'un autre. Contrat : [`contracts/socle.yaml`](../../contracts/socle.yaml). Conventions : [`docs/api-conventions.md`](../../docs/api-conventions.md).

## Lancer en 5 minutes

Prérequis : **Java 21 ou plus** et **Docker**. Maven est fourni (`./mvnw`).

```bash
cp .env.example .env              # à la racine du dépôt (une seule fois)
docker compose up -d --wait postgres
cd apps/api
./mvnw spring-boot:run            # sous Windows (cmd/PowerShell) : mvnw.cmd spring-boot:run
curl localhost:8080/health        # {"status":"UP",…}
```

L'API lit le `.env` de la racine du dépôt. Si un port est déjà pris sur ta machine, change `POSTGRES_PORT` ou `API_PORT` dans `.env`.

## Tester

```bash
./mvnw verify
```

Les tests démarrent une **vraie base PostgreSQL 16** dans Docker (Testcontainers). Docker doit donc tourner. Aucune base locale n'est nécessaire.

## Configuration

Tout passe par des variables d'environnement (voir [`.env.example`](../../.env.example)) :

| Variable | Rôle | Défaut |
|---|---|---|
| `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER` | Connexion à la base | `localhost`, `5432`, `jamii`, `jamii` |
| `POSTGRES_PASSWORD` | Mot de passe de la base | **aucun** : l'API refuse de démarrer sans |
| `API_PORT` | Port d'écoute | `8080` |

## Santé

| Route | Usage |
|---|---|
| `GET /health` | État global. `503` si la base est injoignable. Aucun détail interne exposé |
| `GET /health/liveness` | Le processus vit. Reste `UP` si la base tombe : inutile de redémarrer l'API pour ça |
| `GET /health/readiness` | Prêt à recevoir du trafic |

Aucun autre endpoint Actuator n'est exposé.

## Base de données

- Le schéma appartient à **Flyway** : `src/main/resources/db/migration/V<n>__<description>.sql`.
- On ne modifie **jamais** une migration fusionnée : on en ajoute une nouvelle.
- Hibernate est en mode `validate` : il vérifie le schéma, il ne le modifie jamais.
