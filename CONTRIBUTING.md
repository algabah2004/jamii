# Comment on travaille

## Les règles

1. **Pas de tâche, pas de code.** Tout travail part d'une **issue** attribuée par le chef de projet. Une idée ? Ouvre une issue « Proposition » : le chef de projet la valide ou non.
2. **On ne pousse jamais directement sur `main`.** On passe toujours par une branche et une Pull Request.
3. **Chaque PR est relue et approuvée par le chef de projet** avant d'être fusionnée.
4. **Aucun secret dans le code** (clés d'API, mots de passe). Ils vont dans `.env`, qui est ignoré par Git, et on documente les variables dans `.env.example`.

## Le cycle d'une tâche

```
À faire ──> En cours ──> En revue ──> Terminé
(attribuée)  (branche)    (PR ouverte)  (PR fusionnée)
```

1. Prends une issue qui t'est attribuée et passe-la en **En cours** dans le tableau.
2. Crée ta branche depuis `main` à jour :
   ```bash
   git switch main && git pull
   git switch -c 12-profil-github     # numéro de l'issue + description courte
   ```
3. Fais des commits petits et clairs :
   ```
   feat: génère le profil depuis l'API GitHub (#12)
   fix: corrige l'affichage mobile du profil (#12)
   docs: ajoute la procédure de déploiement DataCloud
   ```
   Préfixes possibles : `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`.
4. Pousse ta branche et ouvre une PR. Écris `Closes #12` dans la description pour que l'issue se ferme automatiquement.
5. Corrige les remarques de la revue, puis le chef de projet fusionne.

### Peu de branches, toujours depuis `main`
Trop de branches, c'est des conflits en cascade et des revues qui traînent. Donc :
- **2 PR ouvertes maximum par personne.** Une en revue, une en cours. Au-delà, on attend une fusion.
- **Toujours partir de `main` à jour, jamais de la branche d'une autre PR.** Une PR empilée sur une autre doit être reprise à chaque fusion, et un seul conflit bloque toute la pile.
- **Même sujet, même branche** : une correction ou une suite sur la PR encore ouverte s'ajoute en commit sur sa branche, pas dans une nouvelle branche.
- **Une PR vit moins d'un jour.** Si elle grossit, on fusionne ce qui est prêt et on continue ensuite.
- Après la fusion, la branche est supprimée automatiquement. Fais `git switch main && git pull && git fetch --prune` avant de recommencer.

## Les étiquettes

| Type | Domaine | Priorité |
|---|---|---|
| `type: fonctionnalité` | `domaine: frontend` | `priorité: haute` |
| `type: bug` | `domaine: backend` | `priorité: moyenne` |
| `type: doc` | `domaine: ia-systalink` | `priorité: basse` |
| `type: recherche` | `domaine: design` | |
| `type: proposition` | `domaine: démo` | |

Une issue marquée `bloqué` signifie qu'il faut prévenir le chef de projet tout de suite.

## Proposer une idée

Tout le monde peut proposer, et c'est attendu. Voir [docs/propositions/](docs/propositions/README.md) :
- **idée rapide** : onglet *Discussions → Idées* ;
- **proposition écrite** : un fichier `docs/propositions/NNNN-titre.md` (à partir de `TEMPLATE.md`), soumis par PR, que toute l'équipe commente avant la décision du chef de projet.

## Travailler avec un agent IA

Claude, Codex, Cursor… sont les bienvenus. Ils lisent automatiquement [AGENTS.md](AGENTS.md), qui leur demande de **critiquer, comparer des alternatives et terminer par des pistes d'amélioration**. Relis toujours ce que ton agent produit : c'est toi qui signes la PR.
