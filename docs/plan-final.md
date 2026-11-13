# Plan des 5 derniers jours (10 → 15 novembre 2026)

> **Soumission le 15 novembre à 17 h 00 GMT.** On ne livre pas 5 piliers à moitié : on livre **un parcours de démo qui marche de bout en bout, en ligne sur DataCloud**. Tout ce qui n'est pas dans ce parcours passe après.
>
> Le concept est dans [concept.md](concept.md) : ce plan fixe seulement **l'ordre** et **ce qu'on montre**.

## Le parcours à livrer (la démo)

> **Mis à jour le 12 novembre 2026 : la Tech Tontine devient le module principal** (décision de l'équipe, validée par le chef de projet ; détail dans [priorites.md](priorites.md) et [concept.md](concept.md)). Rien n'est supprimé.

| # | Étape | Bloc | Indispensable ? |
|---|---|---|---|
| 1 | Se connecter avec GitHub | #17 (fait) | ✅ |
| 2 | Voir son **profil prouvé** : langages et projets publics GitHub, projets Jamii en ligne, indice de fiabilité | #19 | ✅ |
| 3 | **Créer un cercle**, le rejoindre, déposer son idée | #98 (API @sowibrr, écrans @rev22rse22) | ✅ |
| 4 | **Tirer l'ordre au sort** (serveur) → le projet du tour 1 est créé, le Griot en fait le plan (`plan_projet`) | #98 + #20 | ✅ |
| 5 | Le tour avance : tâches et jalons du projet, **message du soir du Griot** (`resume_tour`, avec repli sans IA) | #17 + #20 | ✅ |
| 6 | **Livrer le tour** → mis en ligne sur DataCloud, entre dans la **vitrine** avec « Né dans une Tech Tontine Systalink » | #20 + #18 | ✅ |
| 7 | **L'espace Systalink** : cercles actifs, tours livrés, projets sur DataCloud | #18 | ✅ (chiffres simples) |
| 8 | Demande de PME → plan ; rôles et candidatures ; communautés | #17, #18, #20 | 🟡 déjà en cours, montrés si le temps le permet |
| 9 | Dépôt du tour sur un **Gitea de Jamii** hébergé sur DataCloud | #20 + @rev22rse22 | 💡 **bonus seulement** : sans Gitea vendredi 18 h, le tour pointe vers GitHub et la démo tient |

**Et Jamii lui-même tourne sur DataCloud** (offre payante, remise CADEV) : c'est une condition du règlement, pas une option. **Pour @boubacarsidibe, le déploiement de l'API et du service IA passe avant tout le reste, Gitea compris** : sans URL DataCloud, il n'y a pas de soumission.

## Jour par jour

| Jour | Objectif de la journée | Qui |
|---|---|---|
| **Mar 10** | Fusionner les PR prêtes (#29, #30, #31, #32, #33, #35, #36, #38). Acheter le service DataCloud payant. Relancer Amar et Mohamed | Chef de projet, tous |
| **Mer 11** | API et service IA **déployés sur DataCloud** (URL publique de `/health`). Fusion #34, #37, #39. **Squelette du front en ligne** (Vue, connexion GitHub, page profil vide) | Socle : @sowibrr · IA : @boubacarsidibe · Front : @Amar233-ui |
| **Jeu 12** | Étapes 2 et 3 dans l'interface : profil généré depuis GitHub ; formulaire de besoin PME → plan affiché | Profil : @rev22rse22 · Projets + IA : @sowibrr, @boubacarsidibe |
| **Ven 13** | **Cercles de bout en bout** : création, tirage, tour, livraison vers la vitrine, espace Systalink. **Données de démo** : 3 cercles, dont celui de Dakar (Awa, Kofi, Fatou, Moussa, Nia, Yann), et une soixantaine de profils et de projets | Tous (détail dans [priorites.md](priorites.md)) |
| **Sam 14** | **Gel des fonctionnalités à 12 h.** Corrections, répétition de la démo, **vidéo de secours**, README final, déclaration de l'IA et liste des licences (résumé du job CI « Licences autorisées ») | Tous |
| **Dim 15** | Soumission **avant 12 h GMT** (marge de 5 h). Puis **vote des pairs : chaque membre vote**, sinon le projet est exclu | Chef de projet soumet, **chacun vote** |

## Les règles de ces 5 jours

- **Une PR fusionnée vaut mieux que trois PR parfaites ouvertes.** Petites PR, revue dans la demi-journée.
- **Un blocage de plus de 2 heures se signale** dans le ticket du bloc, avec l'étiquette `bloqué`.
- **Pas de nouvelle idée** avant le 15 : on la note dans `docs/propositions/` pour « Et après ».
- **Données de démo** réalistes et variées (plusieurs pays, des PME, des communautés), jamais de vraies données personnelles.

## Les risques et ce qu'on fait

| Risque | Parade | Décision le |
|---|---|---|
| Le front n'avance pas (#18 sans activité au 10/11) | Si rien n'est poussé **le 11 à 20 h**, le front est repris par le chef de projet et @sowibrr, réduit aux écrans du parcours | 11/11 |
| Le profil n'avance pas (#19 sans activité au 10/11) | Même échéance : version minimale (lecture GitHub publique + projets Jamii), sans indice de fiabilité, reprise par @sowibrr | 11/11 |
| Pas de vraie IA disponible (Yokk non testé) | Le plan de repli et le fournisseur simulé existent déjà. On l'annonce honnêtement : « fournisseur branchable, simulé pour la démo » | 12/11 |
| Le déploiement DataCloud bloque | Plan B : serveur cloud Systalink + `docker compose` ([architecture.md](architecture.md)) | 11/11 |
| L'API Spring Boot manque de mémoire | Offre Standard (2 Go) pour l'API, `-XX:MaxRAMPercentage=75` | 11/11 |
| Le module `cercles` n'est pas fusionné vendredi 14 h | Les écrans des cercles tournent sur données factices (MSW), le reste de la démo reste branché sur l'API | 13/11 |
| Gitea n'est pas en ligne, ou perd ses dépôts au redémarrage | Le tour affiche un lien GitHub (`DEPOT_CODE=github`), Gitea passe après la CADEV | 13/11, 18 h |
| La démo plante devant le jury | Vidéo de secours enregistrée le 14 | 14/11 |
| Un membre oublie le vote des pairs | Rappel le 15 au soir et le 16 au matin, chacun confirme dans le ticket #1 | 15-16/11 |
