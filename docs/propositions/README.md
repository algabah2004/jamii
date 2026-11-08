# Propositions

C'est l'endroit où **tout le monde peut proposer, critiquer et améliorer des idées** : fonctionnalités, architecture, design, démo, organisation.

## Deux niveaux

| Niveau | Où | Pour quoi |
|---|---|---|
| 💬 **Idée rapide** | Onglet [**Discussions → Idées**](https://github.com/algabah2004/jamii/discussions/categories/ideas) du dépôt | Lancer une idée en 3 lignes et recueillir des avis |
| 📄 **Proposition écrite** | Un fichier dans ce dossier, via une PR | Une idée qui mérite qu'on décide : elle change le produit, l'architecture ou le planning |

Une bonne idée rapide devient une proposition écrite.

## Le cycle d'une proposition

```
Brouillon ──> En discussion ──> Acceptée ──> Tâches créées
                    │
                    └──> Refusée / Reportée   (on garde le fichier : la décision et ses raisons servent de mémoire)
```

1. Copie [`TEMPLATE.md`](TEMPLATE.md) sous le nom `NNNN-titre-court.md`, en prenant le numéro suivant.
2. Remplis-le. **Les sections « Alternatives » et « Risques » sont obligatoires** : une proposition sans alternative n'est pas une réflexion.
3. Ouvre une PR. Toute l'équipe commente dans la PR.
4. Le chef de projet tranche et met à jour le statut. Si la proposition est acceptée, il crée les issues.

## Utiliser son agent IA (Claude, Codex, Cursor…)

C'est encouragé, mais **pour réfléchir mieux, pas pour réfléchir moins**. Prompts utiles :

- *« Lis AGENTS.md et docs/. Critique cette proposition comme un ingénieur senior de Systalink : quelles sont ses 3 faiblesses principales ? »*
- *« Propose deux alternatives à cette approche, avec leurs compromis, puis recommande-en une. »*
- *« Quels cas limites et quels risques de sécurité cette fonctionnalité n'a-t-elle pas prévus ? »*
- *« Rédige une proposition avec docs/propositions/TEMPLATE.md à partir de cette idée : … »*

**Règle :** relis et assume ce que ton agent écrit. Le nom sur la proposition, c'est le tien.

## Index

| N° | Titre | Auteur | Statut |
|---|---|---|---|
| 0001 | [Réduire le périmètre au parcours « d'une demande à une appli en ligne »](0001-perimetre-demo.md) | @algabah2004 | En discussion |
