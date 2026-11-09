# Socle API

> 🧭 **Ce plan est un point de départ, pas une consigne. C'est ton bloc.**
> - **Fixe** : le 🎯 But, le 🏁 « Terminé quand » et les règles communes (`docs/equipe.md`, `AGENTS.md`).
> - **Libre** : tout le reste (étapes, ordre, choix techniques, prompt). **Réécris ce fichier à ta façon** dans une PR, idéalement dans les 48 h.
> - Le suivi au jour le jour se fait dans le ticket GitHub (un commentaire court : fait, prochaine étape, blocage).

**Responsable : @sowibrr** · Ticket de suivi : [#17](https://github.com/algabah2004/jamii/issues/17)

## 🎯 But
Tu poses **les fondations sur lesquelles tous les autres blocs se branchent** : l'API, la base de données, la connexion GitHub, la sécurité et les règles communes. C'est la partie que les ingénieurs de Systalink examineront le plus.

## 📦 À livrer
1. `apps/api` : le squelette de l'API (Spring Boot 3 / Java 21, ou NestJS si #3 dit que Java n'est pas supporté) avec un **monolithe modulaire** (un package par module), Flyway, PostgreSQL et `/health`.
2. **La connexion GitHub (OAuth)** de bout en bout : se connecter, recevoir une session sécurisée, appeler `GET /api/me`.
3. **Les conventions de l'API** dans `docs/api-conventions.md` : format des erreurs, pagination, nommage, versionnement, contrôle des droits.
4. **Le contrat OpenAPI** du socle dans `contracts/socle.yaml`.
5. Une étape simple, **Demandes** : une PME publie un besoin et des devs y répondent (`contracts/demandes.yaml`, endpoints, tests). La version enrichie par l'IA (cahier des charges) est branchée par @boubacarsidibe.
6. Le job CI du backend : build et tests, avec Testcontainers.

## 🚀 Avancer sans attendre personne
- **Jour 1** : écris `contracts/socle.yaml` et `docs/api-conventions.md`, puis fusionne-les. Les autres peuvent alors brancher leurs données factices dessus.
- En attendant le résultat de #3 (Java sur DataCloud ?), développe en local avec `docker compose`. Le choix Spring ou NestJS ne bloque ni le contrat ni le modèle de données.

## ✅ Étapes
- [ ] `contracts/socle.yaml` et `docs/api-conventions.md` fusionnés (jour 1-2)
- [ ] Squelette de l'API, migrations Flyway, `/health`, job CI vert
- [ ] OAuth GitHub, session sécurisée, `GET /api/me` et tests
- [ ] Gestion des rôles (membre, modérateur, admin), limitation des requêtes sur l'authentification
- [ ] Demandes (version simple) : publier un besoin, y répondre, avec ses tests
- [ ] `apps/api/README.md` : installer, lancer et tester en 5 minutes

## 🤖 Prompt de démarrage pour ton agent IA
```
Lis AGENTS.md, docs/architecture.md, docs/systalink-services.md et docs/blocs/socle-api.md.
Je suis responsable du bloc « Socle API ».
1. Propose d'abord contracts/socle.yaml (OpenAPI 3.1) et docs/api-conventions.md
   (erreurs RFC 7807, pagination, droits). Critique tes choix comme un ingénieur
   senior de Systalink.
2. Ensuite, le squelette Spring Boot 3 / Java 21 en monolithe modulaire, avec
   Flyway, Testcontainers, OAuth2 GitHub et une session en cookie HttpOnly.
Avance par petites PR (moins de 400 lignes), chacune avec ses tests.
```

## 🏁 Terminé quand
On peut se connecter avec GitHub et appeler l'API de façon sécurisée, et les autres blocs se branchent dessus sans friction. Une demande simple peut être publiée et recevoir des réponses. La CI est verte, et le chef de projet a validé en revue.

