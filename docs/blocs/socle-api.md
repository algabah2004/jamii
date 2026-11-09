# Socle API + Projets d'équipe

> 🧭 **Ce plan est un point de départ, pas une consigne. C'est ton bloc.**
> - **Fixe** : le 🎯 But, le 🏁 « Terminé quand » et les règles communes (`docs/equipe.md`, `AGENTS.md`).
> - **Libre** : tout le reste (étapes, ordre, choix techniques, prompt). **Réécris ce fichier à ta façon** dans une PR, idéalement dans les 48 h.
> - Le suivi au jour le jour se fait dans le ticket GitHub (un commentaire court : fait, prochaine étape, blocage).

**Responsable : @sowibrr** · Ticket de suivi : [#17](https://github.com/algabah2004/jamii/issues/17)

## 🎯 But
Tu poses **les fondations** sur lesquelles tout se branche (API, connexion GitHub, sécurité, base de données), et tu construis **le cœur métier de Jamii : les projets d'équipe**. C'est là qu'une idée ou une demande de PME devient une équipe qui avance. Voir le pilier 2 de [concept.md](../concept.md).

## 📦 À livrer
1. **Le socle** `apps/api` : monolithe modulaire (Spring Boot 3 / Java 21, ou NestJS si Java n'est pas supporté), Flyway, PostgreSQL et `/health`.
2. **La connexion GitHub (OAuth)**, la session sécurisée, `GET /api/me`, les rôles et la limitation des requêtes.
3. **Les conventions de l'API** (`docs/api-conventions.md`) et le **contrat du socle** (`contracts/socle.yaml`).
4. **Les projets d'équipe** (`contracts/projets.yaml`) :
   - créer un projet depuis une **idée** ou une **demande de PME** ;
   - rôles recherchés, candidatures, formation de l'équipe ;
   - espace d'équipe : membres, tâches, jalons, statut ;
   - **continuité** : détection d'un projet inactif, remplacement d'un membre, reprise du projet par une autre équipe.
5. **La page de statut publique** de Jamii (disponibilité et incidents).

## 🚀 Avancer sans attendre personne
- **Jours 1-2** : fusionne `contracts/socle.yaml` et `docs/api-conventions.md`. Les autres blocs codent dessus avec des données factices.
- Le résultat de #20 (Java sur DataCloud ?) ne bloque ni les contrats ni le modèle de données : développe en local avec `docker compose`.
- Les suggestions de l'IA (plan, coéquipiers) arrivent par le bloc #20 via `contracts/ia.yaml`. Simule-les en attendant.

## 🔌 Branchement Systalink
Voir [integration-systalink.md](../integration-systalink.md).
- Interfaces `StockageFichiers` (S3), `EnvoiEmail` (SMTP) et `FournisseurIdentite` (OIDC : GitHub d'abord, « Se connecter avec Systalink » ensuite, sans réécriture).
- PostgreSQL géré et observabilité DataCloud. Toute la configuration par variables d'environnement.

## ✅ Étapes
- [ ] `contracts/socle.yaml` + `docs/api-conventions.md` fusionnés (jours 1-2)
- [ ] Squelette de l'API, Flyway, `/health`, job CI vert
- [ ] OAuth GitHub, session, `GET /api/me`, rôles, tests
- [ ] `contracts/projets.yaml`, puis projets, rôles recherchés, candidatures, équipe
- [ ] Espace d'équipe : tâches, jalons, statut
- [ ] Continuité : détection d'inactivité, remplacement d'un membre, reprise
- [ ] Page de statut publique
- [ ] `apps/api/README.md` : installer, lancer et tester en 5 minutes

## 🤖 Exemple de prompt pour ton agent IA
```
Lis AGENTS.md, docs/concept.md, docs/architecture.md et docs/blocs/socle-api.md.
Je suis responsable du bloc « Socle API + Projets d'équipe ».
1. Propose contracts/socle.yaml (OpenAPI 3.1) et docs/api-conventions.md
   (erreurs RFC 7807, pagination, droits). Critique tes choix comme un ingénieur
   senior de Systalink.
2. Puis le modèle de données des projets d'équipe (projet, rôle recherché,
   candidature, membre, tâche, jalon) avec ses cas limites.
Petites PR (moins de 400 lignes), chacune avec ses tests.
```

## 🏁 Terminé quand
On se connecte avec GitHub, on crée un projet (depuis une idée ou une demande de PME), une équipe se forme sur candidatures, et l'espace d'équipe suit les tâches. Un projet inactif est signalé. La page de statut est en ligne. La CI est verte et le chef de projet a validé en revue.
