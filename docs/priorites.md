# Priorités du moment

> **Mis à jour le vendredi 13 novembre 2026 à 20 h GMT par le chef de projet.** @algabah2004 reprend le bloc #20 de @boubacarsidibe (sans nouvelles depuis le 11/11).
> **Agents IA :** lisez ce fichier **avant le plan du bloc**. S'il contredit `docs/blocs/<bloc>.md`, **ce fichier gagne**. Dites tout de suite à votre développeur ce qu'il doit faire aujourd'hui et ce qu'il bloque.

> **Gel samedi 14 à 12 h GMT.** Ce qui n'est pas fusionné à cette heure-là reste hors de la démo, sans données factices en production. **Rien n'est supprimé.**

## L'idée en une phrase

Jamii **comprend un besoin et le relie à la bonne réponse** : un service Systalink s'il suffit, sinon les bons développeurs, qui forment un **cercle** (la Tech Tontine) pour le construire.

## L'IA : seulement là où elle sert

**L'IA pour comprendre, les règles pour décider.** L'IA lit du texte libre et le transforme en données. Tout le reste (recommandation, classement, chiffres pour Systalink) se fait **sans IA** : gratuit, instantané, explicable et testable.

| Où | IA ? | Pourquoi |
|---|---|---|
| **Carte de compétences** d'un développeur (`analyse_profil`) | ✅ un appel à l'inscription, au plus un par jour ensuite | Lire des dépôts GitHub et en tirer des compétences **avec leurs preuves**. Le développeur valide avant publication |
| **Orientation d'un besoin** (`orientation_besoin`) | ✅ un appel par besoin | Comprendre un texte libre (« cabinet d'avocats, on perd nos dossiers ») et le relier au **catalogue Systalink** et aux compétences nécessaires |
| Recommandation des développeurs, des cercles, des projets | ❌ score calculé | Chaque recommandation dit **pourquoi** (« React, 3 dépôts, disponible, Dakar ») |
| Point du soir d'un tour | ❌ calculé à partir des chiffres | Déjà fait, l'IA n'ajouterait rien |
| Réponses dans les communautés | ❌ abandonné (#69) | Une discussion entre développeurs reste humaine |

- **Fournisseur au choix, par la configuration** : un seul adaptateur pour toutes les API au format OpenAI (DeepSeek, OpenAI, Mistral…). Yokk ou un autre se branche avec **un seul fichier**, sans toucher au reste.
- **Le catalogue Systalink** est un fichier de données (`services/ia/catalogue-systalink.yaml`) : Systalink le modifie sans toucher au code. L'IA ne peut proposer **que** ce qui y figure.
- **Garde-fous** : budget par jour (`IA_BUDGET_JOUR`), cache (un même texte n'est jamais analysé deux fois), e-mails et téléphones retirés avant l'envoi, réponse vérifiée contre un schéma, et le site continue sans l'IA si elle tombe.

## Ce que Systalink y gagne

1. **Ses services, au bon moment, sous les yeux de la personne.** Le résultat de l'orientation montre « Ce dont vous avez besoin existe chez Systalink » : le service, à quoi il sert pour **ce** besoin, le prix indicatif et le lien. Même chose dans l'espace d'un développeur dont le projet est prêt à être mis en ligne. **Aucune donnée ne sort de Jamii pour ça.**
2. **Les tendances du marché**, anonymes : besoins par secteur et par pays, services les plus demandés, compétences qui manquent.
3. **Des clients qui ont demandé à être suivis.** Une case formulée comme un avantage, **non cochée par défaut** : « Recevoir les offres Systalink adaptées à mon projet ». Seulement alors, Systalink voit le contact et le besoin résumé, avec un statut (nouveau, contacté, client), un export CSV et un webhook signé vers son outil de suivi.

> Pourquoi une case : transmettre un contact ou cibler quelqu'un sans son accord est interdit par la loi sénégalaise sur les données personnelles, et le jury le verrait tout de suite. Formulée comme un avantage, elle est **honnête et efficace**.

## La démo (3 minutes)

1. **Un cabinet d'avocats décrit son besoin.** L'agent répond : un site et un espace de stockage Systalink pour une partie, des développeurs pour le reste, avec 3 profils recommandés et la raison de chaque choix.
2. **Les développeurs forment un cercle**, et l'ordre de passage est tiré au sort.
3. **Le tour avance** : tâches cochées, point du soir, lien du dépôt.
4. **Livraison** : DataCloud met le projet en ligne, puis il passe dans la vitrine avec le label « Né dans une Tech Tontine Systalink ».
5. **L'espace Systalink** : cercles actifs, tendances du marché et clients qui ont demandé à être suivis.

## Qui fait quoi, d'ici samedi 14 à 12 h

### @algabah2004 : bloc #20 (repris), mise en ligne et IA
1. **La mise en ligne sur DataCloud** (`docs/deploiement-datacloud.md`), ce soir. Sans elle, pas de soumission.
2. Avec Claude Code, dans cet ordre, chaque étape fusionnable seule :
   1. le contrat : `analyse_profil` et `orientation_besoin` dans `contracts/ia.yaml`, et le catalogue Systalink ;
   2. le service IA : l'adaptateur au format OpenAI, le budget, les deux tâches ;
   3. l'API : le module `orientation` (besoin, résultat, case d'accord, clients suivis) et le score de recommandation.
3. Brancher la clé DeepSeek ou OpenAI sur DataCloud.

### @rev22rse22 : les écrans de l'orientation et de la carte de compétences
Voir la demande qui lui est adressée. Sur données factices (MSW) d'abord, branchés quand l'API arrive.

### @sowibrr : les tâches des tours
#126 (les tâches et les jalons dans l'API), puis le relais de l'orientation si @algabah2004 le demande.

### @Amar233-ui : la vitrine et la démo de bout en bout
#129 (le module vitrine de l'API), puis le test Playwright du parcours de la démo.

## Qui bloque qui

```
algabah2004 ── DataCloud (ce soir) ──────────▶ la soumission elle-même
algabah2004 ── contrat ia.yaml + orientation ─▶ les écrans de rev22rse22
sowibrr ────── #126 tâches et jalons ─────────▶ étape 3 de la démo
Amar233-ui ─── #129 vitrine ───────────────────▶ étape 4 de la démo
```

## Si ce n'est pas fait à temps

Pas de reproche : on réorganise pour livrer.
- **Pas de clé IA** : l'IA simulée tourne, annoncée comme telle (`origine: repli`).
- **Pas d'orientation fusionnée samedi 10 h** : la démo commence directement au cercle (étape 2).
- **On coupe dans cet ordre** : le webhook et l'export CSV, puis les tendances du marché, puis la recommandation « pour un développeur ».
- Un blocage de plus de 2 h se signale **tout de suite** dans #54, avec l'étiquette `bloqué`.

## Pour plus tard (hors démo)

- Yokk comme fournisseur d'IA, dès que son API est documentée ;
- Gitea hébergé par Jamii (sous MIT ; **pas Forgejo**, sous GPL) ;
- la connexion avec Google ;
- le Griot sur WhatsApp, les cercles « Or », le vote d'exclusion.

## La suite

| Jour | Objectif |
|---|---|
| **Ven 13** | Cercles de bout en bout (création, tirage, tour, livraison vers la vitrine). **Données de démo** |
| **Sam 14** | **Gel à 12 h.** Corrections, répétition, vidéo de secours, README final |
| **Dim 15** | Soumission avant 12 h GMT, puis **chacun vote** (sinon le projet est exclu) |
