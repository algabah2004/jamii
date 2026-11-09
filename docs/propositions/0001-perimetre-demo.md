# 0001 · Réduire le périmètre au parcours « d'une demande à une appli en ligne »

| | |
|---|---|
| **Auteur** | @algabah2004 |
| **Date** | 2026-11-08 |
| **Statut** | En discussion |
| **Décision** | — |

## Le problème

Le concept (`docs/concept.md`) contient beaucoup de choses : profils, communautés, équipes, missions, paiement, traduction, Orion, impact par pays, défis mensuels. À 5, dans le délai d'un concours, on risque d'arriver avec **dix fonctionnalités à moitié faites**. Le jury, des ingénieurs, juge sur ce qui **marche vraiment de bout en bout**.

## La proposition

On construit en priorité, et à fond, **un seul parcours complet**. Il reprend les cas d'usage que le catalogue CADEV juge les plus intéressants (UC11, UC14, UC01 et UC16) :

1. **Une PME décrit son besoin** avec ses mots. L'IA en fait un cahier des charges, que la PME vérifie.
2. **Des devs de plusieurs pays** voient la demande, forment une équipe et échangent dans l'espace de la demande.
3. **L'équipe déploie** sur DataCloud (Systalink). La PME teste la préview et valide.

Le reste (communautés thématiques complètes, impact par pays, paiement, défis) est soit **réduit à sa version la plus simple**, soit placé **« après la CADEV »**.

## Alternatives envisagées

| Option | Avantages | Inconvénients |
|---|---|---|
| A. Un parcours complet (cette proposition) | Démo solide, chaque brique est testée, l'histoire est claire pour le jury | On montre moins de fonctionnalités |
| B. Toutes les fonctionnalités en version légère | Paraît plus riche | Fragile, rien n'est vraiment fini, risque de bug en pleine démo |
| C. Parcours centré sur la communauté (Q&R, échanges) | Proche du thème « plateforme d'échange » | Ressemble à ce qui existe déjà (Discord, Stack Overflow), et montre peu le cloud Systalink |

## Risques et points faibles

- Le thème officiel parle de « plateforme d'**échange** ». Il faut que l'échange entre devs reste visible dans le parcours (étape 2), sinon on risque d'être hors sujet.
- Ça dépend du test de déploiement DataCloud (issue #3). Si le déploiement automatique est impossible, l'étape 3 devient guidée plutôt qu'automatique.

## Coût et impact

- Effort : M. Ça réduit le travail total.
- Le planning S2 et S3 serait à réorganiser autour de ce parcours.

## Questions ouvertes

- Faut-il garder les communautés thématiques en version minimale (une liste et des fils de discussion) pour illustrer le mot « échange » ?
- Le règlement de la CADEV (issue #1) impose-t-il des fonctionnalités ?

## Avis de l'équipe

*(à compléter)*
