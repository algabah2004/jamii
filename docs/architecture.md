# Architecture

**Statut : proposition, à valider par l'équipe (issue « Valider l'architecture »). Date : 8 novembre 2026.**

## Principe directeur : branchable sur Systalink

La solution gagnante **intègre le portefeuille de Systalink**. Tout service externe passe donc par une **interface interne** (port) avec un **adaptateur Systalink**, et un adaptateur simulé pour le développement. On utilise uniquement des standards (OpenAPI, OIDC, S3, webhooks, Postgres). Détails et carte des branchements : [integration-systalink.md](integration-systalink.md).

## Les critères de choix, dans l'ordre

1. **Solide et défendable devant les ingénieurs de Systalink** : sécurité, tests, données, exploitation.
2. **Déployable sur DataCloud**, le cloud de Systalink. C'est leur produit, on doit le montrer.
3. **Maîtrisé par l'équipe** : on ne découvre pas un framework pendant un concours.
4. **Simple** : 5 personnes et un délai court. Chaque composant en plus est un risque en plus.

## Ce qu'on sait de DataCloud (doc publique, 08/11/2026)

- Services web déployés **depuis Git**, avec une commande d'installation, de build et de démarrage. Chaque build part d'un environnement neuf.
- Un **projet** regroupe plusieurs services : front, API, tâches de fond.
- **Préview automatique** pour chaque pull request.
- **Base de données gérée : PostgreSQL ou MySQL.**
- Stockage objet compatible S3, groupes de variables d'environnement (secrets), envoi des journaux vers un outil d'observabilité, webhooks (offre Professionnel).
- **Inconnu** : les langages supportés (Java ? Python ?), Docker, Redis, les tâches planifiées. → **Premier test à faire** (issue de @boubacarsidibe).

Un indice : `api.systalink.com` renvoie le message d'erreur par défaut de **Spring Security** (« Full authentication is required to access this resource »). Systalink utilise donc très probablement Spring Boot en interne.

## La proposition

```
                ┌────────────────────────── DataCloud (Systalink) ──────────────────────────┐
 Navigateur ──► │  web        Vue 3 + TypeScript (site statique)                            │
                │    │ HTTPS / JSON (contrat OpenAPI)                                        │
                │    ▼                                                                       │
                │  api        Spring Boot 3 · Java 21  ── monolithe modulaire                │
                │    │   modules : auth · profils · communautés · demandes · projets ·        │
                │    │             déploiement · notifications                                │
                │    ├──► PostgreSQL (géré)   ◄── table « jobs » (file de tâches) ──┐         │
                │    └──► Stockage objet S3 (avatars, fichiers)                      │         │
                │  ia         Python · FastAPI (worker) ─────────────────────────────┘         │
                │               └──► Yokk / fournisseur IA, derrière une interface interne    │
                └────────────────────────────────────────────────────────────────────────────┘
   Services externes : GitHub (OAuth et lecture des dépôts publics) · API/MCP Systalink (déploiement)
```

### Pourquoi ces choix

| Choix | Pourquoi | Alternative écartée |
|---|---|---|
| **Monolithe modulaire** (une seule API, découpée en modules aux frontières strictes) | Solide sans la complexité des microservices : une seule base, un seul déploiement, des transactions simples. Un module pourra devenir un service plus tard | Microservices : trop de pièces mobiles pour 5 personnes, et le jury y verrait de la sur-ingénierie |
| **Spring Boot 3 / Java 21** | Mature, typé, sécurité intégrée (Spring Security, OAuth2), probablement la stack de Systalink. @sowibrr l'a déjà utilisé (`pet-clinic`) | Node/NestJS : valable si l'équipe le maîtrise mieux (voir « À valider ») |
| **Vue 3 + TypeScript + Vite** | @Amar233-ui maîtrise Vue et TypeScript. Le client API est **généré depuis le contrat OpenAPI**, donc le front et le back ne peuvent pas diverger | React : personne ne le pratique dans l'équipe |
| **Service IA séparé en Python** | L'écosystème IA est en Python et @boubacarsidibe le maîtrise. Il est isolé : une panne ou une lenteur de l'IA ne bloque jamais l'API | IA dans l'API Java : mélange les rythmes (des appels lents bloqueraient les requêtes) |
| **PostgreSQL** | Recommandé par la doc DataCloud. Transactions, JSON, recherche plein texte. Il sert aussi de **file de tâches** (`SELECT … FOR UPDATE SKIP LOCKED`), ce qui évite d'ajouter Redis ou RabbitMQ | MongoDB : nos données sont relationnelles (membres ↔ équipes ↔ projets) |
| **Connexion avec GitHub (OAuth)** | Pas d'OAuth Systalink public. Tous nos utilisateurs ont GitHub, et le profil est généré à partir de GitHub | Inscription par e-mail et mot de passe : plus de risques et de code |

## Les garanties de solidité (ce que le jury va vérifier)

**Sécurité**
- OAuth GitHub avec les **permissions minimales** (`read:user`, et seulement les dépôts publics). Les jetons sont chiffrés en base et jamais envoyés au navigateur.
- Session en cookie `HttpOnly` + `Secure` + `SameSite`, protection CSRF, en-têtes de sécurité (CSP, HSTS).
- Rôles (membre, modérateur, admin) vérifiés **côté serveur**, à chaque route.
- Validation de toutes les entrées (Bean Validation) et limitation du nombre de requêtes sur l'authentification et l'IA.
- Secrets uniquement dans les variables d'environnement DataCloud. Scan automatique des secrets et des dépendances vulnérables dans la CI.
- Les contenus créés par les utilisateurs sont échappés à l'affichage (protection XSS), et on peut les signaler à un modérateur.

**Données**
- Schéma versionné avec **Flyway** : aucune modification manuelle en base.
- Conformité à la **loi sénégalaise 2008-12** (CDP) et aux équivalents dans les autres pays : consentement, export et suppression de son compte, journal de ce que l'IA a lu.
- Sauvegardes de la base : à vérifier sur DataCloud, sinon une sauvegarde quotidienne en script vers le stockage objet.

**Qualité**
- CI GitHub Actions obligatoire avant toute fusion : build, lint, tests unitaires, **tests d'intégration sur une vraie base PostgreSQL (Testcontainers)** et vérification du contrat OpenAPI.
- Une préview DataCloud par PR : le chef de projet teste avant de valider.
- Des tests de bout en bout (Playwright) sur le parcours de la démo, pour qu'elle ne casse jamais.

**Exploitation**
- Endpoints de santé (`/actuator/health`), journaux structurés en JSON, envoi vers l'observabilité DataCloud.
- Si l'IA tombe, l'appli reste utilisable : la tâche est remise en file, et l'utilisateur peut écrire son texte lui-même en attendant.
- Les résultats de l'IA sont mis en cache pour maîtriser les coûts (règle « on n'appelle l'IA que si c'est utile »).

## À valider avant de coder

1. **DataCloud accepte-t-il Java et Python ?** C'est le test n°1 : déployer un hello world Spring Boot et un hello world FastAPI. **Si Java n'est pas supporté**, on passe l'API en **NestJS (TypeScript)**, avec la même architecture.
2. **Qui maîtrise vraiment quoi ?** Les rôles viennent des dépôts GitHub publics. Un tour de table de 10 minutes peut changer le choix entre Java et TypeScript.
3. Le règlement de la CADEV : impose-t-il une technologie, un format de livrable, un dépôt public ?
