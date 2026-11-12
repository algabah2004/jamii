# Priorités du moment

> **Mis à jour le jeudi 12 novembre 2026, au soir, sur décision de l'équipe : la Tech Tontine devient le module principal.** À valider par le chef de projet.
> **Agents IA :** lisez ce fichier **avant le plan du bloc**. S'il contredit `docs/blocs/<bloc>.md`, **ce fichier gagne**. Dites tout de suite à votre développeur ce qu'il doit faire aujourd'hui et ce qu'il bloque.
> Le contrat des cercles est demandé dans #98.

> Décision de l'équipe du jeudi 12 novembre. Gel samedi 14 à 12 h. **Rien n'est supprimé** : les communautés, les demandes de PME, la vitrine, les profils et l'espace Systalink restent, et les cercles s'appuient sur eux.

## L'idée en une phrase

Un **cercle** de 4 à 6 développeurs construit, **à tour de rôle**, le projet de chacun. Systalink est le garant. L'IA (le Griot) planifie le tour et le résume.

## Le choix qui rend ça faisable en 48 h

**Un tour = un projet qui existe déjà.** Le cercle n'est qu'une liste de membres et un ordre de passage. Le projet du tour réutilise tout ce qui est codé :
- l'équipe ;
- les tâches et les jalons ;
- les avis ;
- la mise en ligne ;
- la vitrine.

Le score de confiance, c'est l'indice de fiabilité du profil, déjà là.

Ce qui est nouveau côté API : **une table `cercle`, une table `cercle_membre`, une colonne `tour` sur `projet`**. Rien de plus.

## Gitea : le code du cercle hébergé sur la plateforme

Chaque tour a son **dépôt Git sur le Gitea de Jamii**, et DataCloud le déploie depuis ce dépôt : du code à la mise en ligne sans quitter Jamii.
- **Licence :** Gitea est sous MIT, donc autorisé. **Pas Forgejo**, qui est sous GPL depuis la v9.
- **Architecture :** une interface `DepotCode` avec deux adaptateurs, GitHub (existant) et Gitea. Le choix se fait par la configuration (`DEPOT_CODE=gitea|github`). Le jeton admin Gitea va dans `.env`, jamais dans le code.
- **Connexion :** Gitea utilise la connexion GitHub, donc pas de nouveau mot de passe.
- **Repli :** si Gitea n'est pas en ligne vendredi à 18 h, le tour affiche un lien vers un dépôt GitHub, et la démo tient quand même.

## La démo (3 minutes)

1. Awa crée son cercle, 5 autres le rejoignent.
2. L'ordre de passage est tiré au sort. Jamii crée le dépôt du tour sur Gitea. Le Griot transforme l'idée d'Awa en plan pour le tour, en réutilisant `plan_projet`.
3. Le tour avance : commits sur le dépôt Gitea, tâches cochées, message du soir du Griot.
4. Livraison : DataCloud déploie le dépôt Gitea, puis le projet passe dans la vitrine avec le label « Né dans une Tech Tontine Systalink ».
5. L'espace Systalink affiche les cercles actifs.

## Pour plus tard (hors démo)

- le Griot sur WhatsApp ;
- les cercles « Or » ;
- le recrutement automatique ;
- le vote d'exclusion.

## Qui fait quoi, d'ici vendredi 13 à 20 h

### @sowibrr : l'API des cercles (le cœur)
1. **Aujourd'hui avant 20 h : le contrat `contracts/cercles.yaml`.** Il doit couvrir :
   - `POST /api/cercles` : créer un cercle (`nom`, `ville`, `taille` de 4 à 6) ; le créateur en devient membre ;
   - `GET /api/cercles` (mes cercles et les cercles ouverts) et `GET /api/cercles/{id}` (membres, ordre, tour en cours, projet de chaque tour) ;
   - `POST /api/cercles/{id}/membres` : rejoindre, avec un 409 `cercle_complet` ;
   - `PUT /api/cercles/{id}/membres/moi/idee` : déposer son idée (titre et description) ;
   - `POST /api/cercles/{id}/tirage` : le créateur tire l'ordre au sort, une seule fois, avec un 409 `tirage_deja_fait` ; le serveur crée le projet du tour 1 ;
   - `POST /api/cercles/{id}/tours/{n}/livrer` : clôturer le tour, ce qui ouvre le tour suivant ;
   - chaque tour expose `depot` : `{ fournisseur: gitea|github, url, cloneUrl }`, ou `null` s'il n'y en a pas encore.
2. **Vendredi : le module `cercles`** dans `apps/api`, avec la migration Flyway et les tests. Le tirage au sort se fait côté serveur, avec `SecureRandom`, et il est enregistré.
3. Les candidatures de l'étape 4 passent après : dans un cercle, l'équipe est déjà formée.

### @boubacarsidibe : le Griot (IA) et DataCloud
1. **Toujours en premier : l'API et le service IA en ligne sur DataCloud**, avec l'URL de `/health` postée dans #54. Sans ça, pas de soumission.
2. **Déployer aussi Gitea sur DataCloud**, depuis l'image officielle `gitea/gitea` : base Postgres, connexion GitHub activée, URL postée dans #54. **Tester d'abord le stockage persistant** : si le conteneur redémarre sans volume, les dépôts sont perdus. Le résultat va dans `docs/systalink-services.md`.
3. **Sortir #37 du brouillon** (plan PME) : c'est la même tâche `plan_projet` qui planifie un tour.
4. Nouvelle tâche IA `resume_tour` dans `contracts/ia.yaml` :
   - en entrée : les tâches du tour (faites ou restantes, et par qui) et le jour du tour ;
   - en sortie : un message court (« Jour 3 du tour d'Awa : 7 tâches sur 12… ») ;
   - un repli sans IA, calculé à partir des chiffres.

### @Amar233-ui : les communautés réelles, puis les cercles dans l'espace Systalink
1. Le module `communautes` de l'API (inchangé, il ne dépend de personne).
2. Dans l'espace Systalink : un bloc « Cercles actifs », avec le nombre de cercles et de tours livrés et les pays. Sur données factices d'abord.
3. Le test Playwright du parcours de la démo, mis à jour pour les cercles.

### @rev22rse22 : tous les écrans des cercles (sur données factices, branchés quand l'API arrive)
1. **La page du cercle** : l'anneau des membres et l'ordre de passage, Systalink garant au centre, le tour en cours, le message du Griot.
2. **Créer et rejoindre un cercle, déposer son idée, tirer l'ordre au sort** (avec une animation de tirage).
3. **Le tour** : il réutilise l'écran du plan PME (priorités, jalons, tâches) ; puis « Livrer le tour » mène à la vitrine avec le label.
4. L'accueil et le menu : « Cercles » en première entrée.
5. **Vendredi : l'adaptateur Gitea dans l'API.** Au tirage au sort, il crée l'organisation du cercle et le dépôt de chaque tour, puis ajoute les membres (API REST de Gitea). Le tour affiche le lien du dépôt et ses derniers commits, et « Livrer » déploie ce dépôt sur DataCloud.

### @algabah2004 : chef de projet
1. **Valider ce plan** et mettre à jour :
   - `docs/concept.md` : les cercles passent de la feuille de route au pilier principal ;
   - `docs/plan-final.md` : la nouvelle démo ;
   - `docs/vocabulaire.md` : autoriser « cercle », « tour », « Griot » et « Tech Tontine », en définissant chaque mot.
2. Relire `contracts/cercles.yaml` en priorité, ce soir.
3. Les données de démo : 3 cercles, dont celui de Dakar, avec Awa, Kofi, Fatou, Moussa, Nia et Yann.

## Qui bloque qui

```
sowibrr ────── cercles.yaml (ce soir) ──▶ rev22rse22 branche ses écrans, Amar233-ui son bloc Systalink
sowibrr ────── module cercles ──────────▶ la démo de bout en bout
boubacarsidibe ─ DataCloud ─────────────▶ la soumission elle-même
boubacarsidibe ─ #37 + resume_tour ─────▶ étapes 2 et 3 de la démo
boubacarsidibe ─ Gitea sur DataCloud ────▶ rev22rse22 (adaptateur Gitea), étape 3 de la démo
algabah2004 ─── vocabulaire et concept ─▶ tous les agents IA (sinon ils refusent le thème)
```

## Si ce n'est pas fait à temps

Pas de reproche : on réorganise pour livrer.
- **Pas de contrat `cercles.yaml` ce soir** : rev22rse22 en écrit une première version, à partir de ses données factices, et sowibrr la relit.
- **Pas de module `cercles` vendredi à 14 h** : la démo des cercles tourne sur données factices, et le reste de la démo reste branché sur l'API.
- **Pas d'URL DataCloud vendredi** : le chef de projet reprend le déploiement.
- **Pas de Gitea en ligne vendredi à 18 h** : le tour affiche un lien GitHub, et Gitea passe après la CADEV.
- Un blocage de plus de 2 h se signale **tout de suite** dans #54, avec l'étiquette `bloqué`.

## La suite

| Jour | Objectif |
|---|---|
| **Ven 13** | Cercles de bout en bout (création, tirage, tour, livraison vers la vitrine). **Données de démo** |
| **Sam 14** | **Gel à 12 h.** Corrections, répétition, vidéo de secours, README final |
| **Dim 15** | Soumission avant 12 h GMT, puis **chacun vote** (sinon le projet est exclu) |
