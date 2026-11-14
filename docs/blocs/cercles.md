# 🔁 Cercles et collaboration sur la plateforme

**Responsable :** @Amar233-ui · **Suivi :** #157 · **Repris de @sowibrr le 14 novembre** (les modules `cercles` et `projets`).

## But
Des développeurs forment un **cercle** (la Tech Tontine) et construisent, à tour de rôle, le projet de chacun. Ils collaborent **sur Jamii même** : un dépôt Gitea par tour, les tâches et les jalons. Le projet livré passe ensuite dans la vitrine.

## Territoire
- API : `cercles`, `projets`, `vitrine` ;
- écrans : `cercles`, `vitrine` ;
- contrats : `cercles.yaml`, `projets.yaml`, `vitrine.yaml` ;
- l'ossature du front : `router.ts`, `App.vue` et les traductions.

## Ce qui existe déjà
- Créer et rejoindre un cercle, déposer son idée, tirer l'ordre au sort, livrer un tour.
- Le dépôt GitHub de chaque tour (`DepotCode`, #124).
- Les tâches et les jalons d'un projet.
- Le bloc « Cercles actifs » de l'espace Systalink (#117), en attente des vrais chiffres.

## Avant la soumission (dimanche 15, 12 h GMT)
1. **#129 : le module vitrine de l'API**, avec l'origine `cercle` (#116).
2. **Le bean `ChiffresDesModules.Cercles`**, pour que l'espace Systalink affiche les vrais chiffres.
3. **L'adaptateur Gitea de `DepotCode`**, choisi par `DEPOT_CODE=gitea`, avec un repli sur GitHub. Il dépend de Gitea sur DataCloud (@algabah2004).
4. **L'ossature découpée par module** : les routes et les traductions, pour que chacun travaille sans conflit.
5. Le test Playwright du parcours de la démo.

## Terminé quand
Pendant la démo, le cercle né d'une demande travaille sur le dépôt de son tour, livre le tour, et le projet apparaît dans la vitrine avec le label « Né dans une Tech Tontine Systalink ».
