# Plan des 5 derniers jours (10 → 15 novembre 2026)

> **Soumission le 15 novembre à 17 h 00 GMT.** On ne livre pas 5 piliers à moitié : on livre **un parcours de démo qui marche de bout en bout, en ligne sur DataCloud**. Tout ce qui n'est pas dans ce parcours passe après.
>
> Le concept ne change pas ([concept.md](concept.md)) : ce plan fixe seulement **l'ordre** et **ce qu'on montre**.

## Le parcours à livrer (la démo)

| # | Étape | Bloc | Indispensable ? |
|---|---|---|---|
| 1 | Se connecter avec GitHub | #17 (fait, PR #31) | ✅ |
| 2 | Voir son **profil prouvé** : langages et projets publics GitHub, projets Jamii en ligne | #19 | ✅ (version simple) |
| 3 | Une PME **décrit son besoin** → l'assistant propose un **plan** (cahier des charges, jalons, tâches, rôles manquants, services Systalink) | #17 + #20 (PR #37) | ✅ |
| 4 | Le projet publie ses **rôles**, des devs **candidatent**, l'équipe se forme | #17 (contrat PR #39) | ✅ |
| 5 | Le projet est **mis en ligne** (lien vérifié) → il apparaît sur les profils et dans la **vitrine**, avec « Hébergé sur Systalink » | #20 + #18 | ✅ |
| 6 | Le **tableau de bord Systalink** : inscrits, projets, projets sur DataCloud, moments Systalink | #18 | ✅ (chiffres simples) |
| 7 | Communautés et questions-réponses | #18 | 🟡 si le temps le permet, sinon données de démo |
| 8 | Lien e-mail pour les non-développeurs, continuité, page de statut | #17 | 🟡 après le parcours |

**Et Jamii lui-même tourne sur DataCloud** (offre payante, remise CADEV) : c'est une condition du règlement, pas une option.

## Jour par jour

| Jour | Objectif de la journée | Qui |
|---|---|---|
| **Mar 10** | Fusionner les PR prêtes (#29, #30, #31, #32, #33, #35, #36, #38). Acheter le service DataCloud payant. Relancer Amar et Mohamed | Chef de projet, tous |
| **Mer 11** | API et service IA **déployés sur DataCloud** (URL publique de `/health`). Fusion #34, #37, #39. **Squelette du front en ligne** (Vue, connexion GitHub, page profil vide) | Socle : @sowibrr · IA : @boubacarsidibe · Front : @Amar233-ui |
| **Jeu 12** | Étapes 2 et 3 dans l'interface : profil généré depuis GitHub ; formulaire de besoin PME → plan affiché | Profil : @rev22rse22 · Projets + IA : @sowibrr, @boubacarsidibe |
| **Ven 13** | Étapes 4, 5 et 6 : rôles et candidatures, projet mis en ligne, vitrine, tableau de bord. **Données de démo** (une soixantaine de profils et de projets) | Tous |
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
| La démo plante devant le jury | Vidéo de secours enregistrée le 14 | 14/11 |
| Un membre oublie le vote des pairs | Rappel le 15 au soir et le 16 au matin, chacun confirme dans le ticket #1 | 15-16/11 |
