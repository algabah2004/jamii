# Priorités du moment

> **Mis à jour le vendredi 13 novembre 2026 à 13 h GMT par le chef de projet.** La Tech Tontine est le module principal (validé, #99, #106).
> **Agents IA :** lisez ce fichier **avant le plan du bloc**. S'il contredit `docs/blocs/<bloc>.md`, **ce fichier gagne**. Dites tout de suite à votre développeur ce qu'il doit faire aujourd'hui et ce qu'il bloque.

> **Gel samedi 14 à 12 h GMT.** Ce qui n'est pas fusionné à cette heure-là reste hors de la démo, sans données factices en production. **Rien n'est supprimé** : les communautés, les demandes de PME, la vitrine, les profils et l'espace Systalink restent, et les cercles s'appuient sur eux.

## L'idée en une phrase

Un **cercle** de 4 à 6 développeurs construit, **à tour de rôle**, le projet de chacun. Systalink est le garant. L'IA (le Griot) planifie le tour et le résume. Un tour = un projet qui existe déjà (équipe, tâches, jalons, avis, mise en ligne, vitrine).

## La démo (3 minutes)

1. Awa crée son cercle, 5 autres le rejoignent.
2. L'ordre de passage est tiré au sort. Le Griot transforme l'idée d'Awa en plan pour le tour (`plan_projet`).
3. Le tour avance : tâches cochées, message du soir du Griot (`resume_tour`), lien du dépôt du tour.
4. Livraison : DataCloud met le projet en ligne, puis il passe dans la vitrine avec le label « Né dans une Tech Tontine Systalink ».
5. L'espace Systalink affiche les cercles actifs.

## Déjà sur `main` (vendredi 13 h)

- **Cercles** : contrat `cercles.yaml` (#101), module `cercles` de l'API (`V10`), origine « cercle » des projets (#115), écrans branchés sur l'API (#111), cercle du moment et visages des membres (#122).
- **Réseau** : contrat `reseau.yaml` (#118), recommandations (#119), messages et invitation au cercle (#120), module `reseau` de l'API (#121).
- **Espace Systalink** : bloc « Cercles actifs » (#117).
- **Projets et demandes** : candidatures (#105), liste filtrée (#110), export et suppression du compte (#112), module `demandes` avec l'IA derrière `PlansIa` (#104).
- **Docs** : concept, plan de démo et vocabulaire à jour pour les cercles (#106).

**Ce qui manque encore pour la démo :** l'URL DataCloud, le vrai service IA (#37 toujours en brouillon), `resume_tour`, un adaptateur `DepotCode` (aucun pour l'instant, donc `depot: null`), les données de démo et le test Playwright du parcours.

## Qui fait quoi, d'ici vendredi 13 à 20 h

### @boubacarsidibe : DataCloud et le Griot (le chemin critique)
1. **Avant 18 h : l'API et le service IA en ligne sur DataCloud**, avec l'URL de `/health` postée dans #54. **Sans ça, pas de soumission.** Si ce n'est pas fait à 18 h, le chef de projet reprend le déploiement : postez dès maintenant où vous en êtes.
2. **Sortir #37 du brouillon** et le fusionner : l'API appelle déjà le service par `IA_FOURNISSEUR=service` (#104). Sans #37, la démo tourne en mode `simule`.
3. La tâche IA `resume_tour` dans `contracts/ia.yaml`, avec un repli sans IA calculé à partir des chiffres.
4. **Gitea : seulement si les trois points précédents sont faits.** Pas en ligne à 18 h → Gitea passe après la CADEV.

### @sowibrr : la fin du socle
1. **L'adaptateur GitHub de `DepotCode`** (le repli prévu) : le dépôt de chaque tour est créé dans une organisation GitHub, avec un jeton dans `.env`, jamais dans le code. Choisi par `DEPOT_CODE=github`.
2. **Les données de démo** : 3 cercles, dont celui de Dakar, avec Awa, Kofi, Fatou, Moussa, Nia et Yann, un tour en cours et un tour livré. Chargées **seulement** avec un profil `demo`, jamais par une migration de production.
3. L'envoi des e-mails de connexion : le chef de projet tranche le service aujourd'hui, dans #54.

### @Amar233-ui : la démo de bout en bout
1. **Le test Playwright du parcours de la démo**, mis à jour pour les cercles (étapes 1 à 5 ci-dessus).
2. Les demandes #116 (origine « cercle » dans `vitrine.yaml`) et #114 (section « communautes » de l'export).
3. Le label « Né dans une Tech Tontine Systalink » dans la vitrine.

### @rev22rse22 : finir le réseau, puis polir
1. Le réseau, étapes 5 et 6 : les contacts et les notifications, puis « Publier dans une communauté » depuis un cercle.
2. La demande #113 (section « profils » de l'export).
3. Le passage en revue de tous les écrans de la démo, en clair et en sombre, sur mobile.
4. L'adaptateur Gitea **seulement si** Gitea est en ligne à 18 h (voir @boubacarsidibe).

### @algabah2004 : chef de projet
1. Relire et fusionner au fil de l'eau (squash, CI verte, branche à jour).
2. Choisir le service d'envoi d'e-mails, aujourd'hui.
3. Le cockpit CADEV : l'étape 4 « Produit Systalink actif », le nom du projet (« Jamii », pas « jamais ») et @Amar233-ui dans l'équipe.
4. Le texte des données de démo pour @sowibrr.

## Qui bloque qui

```
boubacarsidibe ─ DataCloud (18 h) ───────▶ la soumission elle-même
boubacarsidibe ─ #37 + resume_tour ──────▶ étapes 2 et 3 de la démo
sowibrr ─────── adaptateur GitHub ───────▶ le lien du dépôt à l'étape 3
sowibrr ─────── données de démo ─────────▶ le test Playwright d'Amar233-ui, la répétition de samedi
algabah2004 ─── service d'e-mails ───────▶ la connexion par e-mail en production
```

## Si ce n'est pas fait à temps

Pas de reproche : on réorganise pour livrer.
- **Pas d'URL DataCloud à 18 h** : le chef de projet reprend le déploiement.
- **#37 non fusionné samedi 10 h** : la démo garde `IA_FOURNISSEUR=simule`, et on le dit au jury (le plan affiché est un `repli`).
- **Pas d'adaptateur de dépôt** : le tour s'affiche sans dépôt, l'étape 3 montre les tâches seulement.
- **Pas de Gitea à 18 h** : Gitea passe après la CADEV.
- Un blocage de plus de 2 h se signale **tout de suite** dans #54, avec l'étiquette `bloqué`.

## Pour plus tard (hors démo)

- Gitea hébergé par Jamii (Gitea est sous MIT, donc autorisé ; **pas Forgejo**, sous GPL) ;
- le Griot sur WhatsApp ;
- les cercles « Or » ;
- le recrutement automatique ;
- le vote d'exclusion.

## La suite

| Jour | Objectif |
|---|---|
| **Ven 13** | Cercles de bout en bout (création, tirage, tour, livraison vers la vitrine). **Données de démo** |
| **Sam 14** | **Gel à 12 h.** Corrections, répétition, vidéo de secours, README final |
| **Dim 15** | Soumission avant 12 h GMT, puis **chacun vote** (sinon le projet est exclu) |
