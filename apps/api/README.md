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
| `DEPOT_CODE` | Dépôt Git de chaque tour de cercle : `aucun` (le tour n'a pas de dépôt) ou `github` | `aucun` |
| `GITHUB_DEPOTS_ORGANISATION`, `GITHUB_DEPOTS_JETON` | Avec `DEPOT_CODE=github` : l'organisation qui reçoit les dépôts, et un jeton qui peut y créer des dépôts et inviter des membres. L'API refuse de démarrer s'il en manque un | aucun |
| `GITHUB_DEPOTS_PRIVES` | `true` : dépôts privés | `false` |
| `JAVA_TOOL_OPTIONS` | Réglages de la JVM, lus par Java lui-même (voir ci-dessous) | aucun |

En local, `./mvnw spring-boot:run` n'a besoin d'aucune de ces variables : la connexion vient de docker compose. Elles servent au déploiement.

### Déployer avec 512 Mo de mémoire (offre Free de DataCloud, cf. #36)

Mettre `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` : sans ce réglage, Java ne prend qu'un quart de la mémoire pour ses objets. Sur DataCloud, il faut aussi `POSTGRES_SSLMODE=require`, à vérifier avec leur PostgreSQL géré.

Mesuré en local dans un conteneur limité à 512 Mo et 1 CPU, avec la connexion GitHub et la session en base. Ce n'est pas une mesure sur DataCloud :

| Réglage | Démarrage | Au repos | Pic (3 000 requêtes) |
|---|---|---|---|
| Défaut de Java | 24 s | 304 Mo | 321 Mo |
| `-XX:+UseSerialGC -XX:MaxRAMPercentage=60 -XX:TieredStopAtLevel=1 -Xss512k -XX:ReservedCodeCacheSize=48m -XX:MaxMetaspaceSize=128m` | 23 s | 252 Mo | 266 Mo |

L'API tient dans 512 Mo. En revanche, l'offre Free se met en veille : la première requête après une veille attend le démarrage, plus de 20 s.

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
- **Connexion par lien e-mail** : `POST /api/auth/lien-email` puis `POST /api/auth/lien-email/confirmation`. Le lien vaut 15 minutes et ne sert qu'une fois ; seule son empreinte est en base. Limites : 5 demandes par heure et par adresse (en base), 20 par heure et par IP (en mémoire : une seule instance). L'envoi passe par `EnvoiEmail`. Pour l'instant, seul l'adaptateur simulé existe : en local, mets `EMAIL_AFFICHER_LIEN=true` dans `.env` pour voir le lien dans la console de l'API ; sans cela, rien n'est écrit.
- **Session** : cookie `JAMII_SESSION` (HttpOnly, Secure, SameSite=Lax), stockée dans PostgreSQL et expirée après 7 jours sans activité.
- **CSRF** : tout `POST`, `PUT`, `PATCH` ou `DELETE` renvoie la valeur du cookie `XSRF-TOKEN` dans l'en-tête `X-XSRF-TOKEN`.
- **Tout est fermé par défaut.** Une route publique s'ouvre explicitement dans `SecuriteConfig`, en GET seulement et avec le chemin exact. Aujourd'hui : `/health`, `/api/auth/**`, `GET /api/systalink/tableau-de-bord` et `GET /api/vitrine`. Pour en ouvrir une autre, il faut `security: []` dans son contrat et un test dans `SecuriteTest`.
- **Lire un compte depuis un autre module** : injecte `dev.jamii.api.auth.Annuaire` (`lire(UUID)`, `lire(Collection<UUID>)`). Il renvoie un `Compte(id, nom, avatarUrl, loginGithub)`, jamais l'e-mail. Ne lis pas la table `utilisateur` : `ModulesTest` te le refuserait. `compterInscrits(Instant)` sert au tableau de bord Systalink.
- **Dans ton module**, l'utilisateur connecté s'obtient avec `@AuthenticationPrincipal UtilisateurConnecte moi`, qui donne `moi.id()` et `moi.roles()`.

## Base de données

- Le schéma appartient à **Flyway** : `src/main/resources/db/migration/V<n>__<description>.sql`.
- On ne modifie **jamais** une migration fusionnée : on en ajoute une nouvelle.
- Hibernate est en mode `validate` : il vérifie le schéma, il ne le modifie jamais.
