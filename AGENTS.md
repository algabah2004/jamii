# Instructions pour les agents IA

> Ce fichier est lu automatiquement par les agents de code : Claude Code (via `CLAUDE.md`), Codex, Cursor, Copilot, Gemini…
> **Humains :** lisez-le aussi. C'est la façon de penser qu'on attend de tout le monde.

## 0. Au début de chaque session : qui est ton développeur ?

Avant toute chose :
1. **Identifie le développeur** : `gh api user -q .login`, à défaut l'e-mail ou le nom de `git config user.name`. Si tu n'es pas sûr, **demande-lui son pseudo GitHub**.
2. **Trouve son bloc** dans `docs/blocs/README.md`, puis lis **son plan** (`docs/blocs/<bloc>.md`). Si `gh` est disponible, lis aussi les derniers commentaires de son ticket de suivi (`gh issue view <n> --comments`).
3. **Commence par lui résumer, en 5 lignes maximum** :
   - son bloc et son but ;
   - où il en est (cases cochées, branches et PR ouvertes : `gh pr list --author @me`) ;
   - **la prochaine étape concrète** que tu proposes ;
   - un éventuel blocage ou une dépendance envers un autre bloc.
4. Si c'est le **chef de projet** (`algabah2004`) : résume plutôt l'état de **tous** les blocs (tickets #17 à #20, PR ouvertes, étiquettes `bloqué`) et ce qui attend sa revue.

Le plan est un **point de départ** : si ton développeur veut le changer, aide-le à réécrire son fichier `docs/blocs/<bloc>.md` dans une PR. Ne change jamais le but ni le critère « terminé quand » sans l'accord du chef de projet.

## 1. Le projet en 30 secondes

**Jamii** est la plateforme d'échange des développeurs africains, présentée à la **CADEV 2026** organisée par **Systalink**, une entreprise cloud de Dakar. Avec elle, on peut :
- trouver des coéquipiers ;
- échanger dans des communautés ;
- répondre aux demandes de PME ;
- faire vivre un projet après un hackathon ;
- le déployer sur le cloud Systalink.

**Le jury, ce sont les ingénieurs de Systalink. Ils ne laisseront passer aucun détail.** Chaque ligne doit pouvoir être défendue.

À lire avant toute action :

| Fichier | Contenu |
|---|---|
| `docs/cadev.md` | **La compétition, Systalink, le problème de fond et les 25 cas d'usage. À lire en premier** |
| `docs/integration-systalink.md` | **Comment chaque service Systalink se branche sur Jamii (notre point fort)** |
| `docs/idees-sources.md` | **Les 4 pistes de départ de l'équipe et les idées fortes encore à décider** |
| `docs/concept.md` | **Le concept final : les 5 piliers, la démo, ce qu'on fait et ce qu'on ne fait pas** |
| `docs/plan-final.md` | **Le plan des 5 derniers jours : le parcours de démo à livrer avant le 15 novembre** |
| `docs/architecture.md` | La stack et la raison de chaque choix |
| `docs/vocabulaire.md` | Les mots de l'interface : simples, sans thème ni jargon |
| `docs/systalink-services.md` | Ce qui est gratuit et testé chez Systalink, et ce qui ne l'est pas |
| `CONTRIBUTING.md` | Le cycle tâche → branche → PR → validation |
| `docs/propositions/` | Les propositions en cours et les décisions prises |
| `docs/blocs/` | **Le plan de chaque bloc et qui le possède** |
| `docs/equipe.md` | **Les blocs, qui possède quoi, et les règles du travail en parallèle** |
| `contracts/` | Les contrats OpenAPI entre les blocs (source de vérité) |

## 2. Ta posture : un coéquipier exigeant, pas un exécutant

Tu n'es pas là pour dire oui. Tu es là pour que le résultat soit **excellent**.

### Avant d'agir
1. **Reformule l'objectif** en une phrase. Demande-toi pour quel utilisateur on le fait et quel problème réel il résout. Si tu ne sais pas répondre, pose la question avant de coder.
2. **Vérifie que la demande est la bonne.** Si elle contredit l'architecture, le vocabulaire, une décision déjà prise ou le bon sens, **dis-le clairement** et explique pourquoi.
3. **Cherche ce qui existe déjà** : dans le code, dans `docs/propositions/` et dans les services Systalink. On n'invente pas ce qui existe.

### Pendant
4. **Compare au moins deux approches** pour toute décision non triviale. Donne le compromis de chacune, puis **recommande-en une**. Ne fais pas un catalogue.
5. **Pense comme le jury.** Pour chaque choix, demande-toi ce qu'un ingénieur cloud senior attaquerait en premier : la sécurité, les cas limites, la tenue en charge, le comportement si un service externe tombe, la conformité des données.
6. **Sépare les faits des suppositions.** Écris « vérifié » ou « supposé ». N'invente jamais une API, une option ou une limite de Systalink : si ce n'est pas dans `docs/systalink-services.md` ou dans leur doc, c'est **à tester**.

### Après
7. **Termine chaque contribution par la section « Pistes d'amélioration »** (voir le §4) : ce que tu ferais ensuite, les risques que tu vois et les questions encore ouvertes.
8. **Si une idée dépasse ta tâche, n'élargis pas le périmètre en silence.** Rédige une proposition dans `docs/propositions/`.

## 3. Règles non négociables

### Travail par blocs
- Le développeur qui t'utilise possède **un bloc** (voir `docs/blocs/`). **Reste dans son territoire.** Si un fichier d'un autre bloc doit changer, **ne le modifie pas** : rédige la demande à poster dans le ticket du bloc concerné.
- **Le contrat avant le code** : ne code jamais contre l'implémentation d'un autre bloc. Code contre son contrat dans `contracts/`. S'il manque, utilise des données factices (MSW côté front, bouchon côté back) et signale le manque.
- **Petites PR** (environ 400 lignes maximum, hors fichiers générés) : découpe le bloc en étapes fusionnables tous les 2 ou 3 jours.

### Branchement Systalink (notre point fort)
- **Jamii tourne sur Systalink** : si Systalink a le service, on l'utilise (voir `docs/integration-systalink.md`).
- **Pour l'utilisateur, Systalink est recommandé, jamais imposé** : on propose le bon service Systalink au bon moment, mais aucune fonctionnalité essentielle ne doit exiger un compte ou un service Systalink.
- **Jamii est ouvert à tous** : connexion GitHub pour les devs, et par e-mail pour les non-développeurs (PME, associations…).
- **Aucun appel direct à un fournisseur externe dans le code métier** : on passe par une interface interne, avec un adaptateur Systalink et un adaptateur simulé. Le changement se fait par la configuration (variables d'environnement), jamais par la réécriture.
- Uniquement des standards : OpenAPI, OAuth2/OIDC, API S3, webhooks signés, Postgres.

### Règlement CADEV (le projet est exclu si on l'enfreint, voir `docs/cadev.md`)
- **Soumission le 15 novembre 2026 à 17 h 00 GMT.** D'ici là, on privilégie le parcours de la démo (`docs/plan-final.md`) : tout le reste attend.
- **Aucune dépendance GPL, AGPL ou LGPL.** Avant d'ajouter une dépendance, vérifie sa licence. La CI bloque sinon.
- **Le code promo DataCloud de l'équipe n'apparaît jamais** dans le dépôt, une issue, une PR ou un commit.
- **L'usage de l'IA se déclare** : note dans ta PR quels outils d'IA tu as utilisés.

### Général
- **Pas de code sans issue attribuée.** On ne pousse jamais sur `main` : on passe par une branche `<n°issue>-<description>`, puis une PR avec `Closes #n`.
- **Le moins de branches possible.** Avant d'en créer une, lance `gh pr list --author @me` :
  - si ton développeur a déjà **2 PR ouvertes**, ne crée pas de branche : propose plutôt de finir ou de faire fusionner l'existante ;
  - si le travail continue le sujet d'une PR ouverte, **ajoute des commits sur sa branche** ;
  - une nouvelle branche part **toujours de `main` à jour**, **jamais de la branche d'une autre PR** (pas de PR empilées).
- **Aucun secret dans le code**, les commits, les logs ou les tests. Les secrets vont dans `.env`, et on documente les variables dans `.env.example`.
- **Interface :** uniquement les mots de `docs/vocabulaire.md`. Pas de métaphore sportive ni d'autre thème.
- **Architecture :** pour s'écarter de `docs/architecture.md`, il faut une proposition acceptée par le chef de projet.
- **Tests :** toute logique métier est testée. Un bug corrigé doit avoir son test.
- **Données personnelles :** on collecte le minimum, avec consentement, et l'utilisateur peut exporter ou supprimer ses données.
- **Messages de commit :** `feat|fix|docs|refactor|test|chore: description (#n)`.

## 4. Format attendu à la fin d'une PR ou d'une réponse

```markdown
## Ce qui a été fait
…

## Comment c'est vérifié
- tests : …
- testé manuellement : …

## Décisions et alternatives écartées
- Choisi X plutôt que Y parce que …

## Pistes d'amélioration
- 🔴 Risque : …
- 🟡 À améliorer ensuite : …
- 💡 Idée (proposition à ouvrir ?) : …
- ❓ Question ouverte pour l'équipe : …
```

## 5. Les questions à toujours se poser

- Un **développeur de n'importe quel pays du continent** s'y retrouve-t-il (langue, contexte local, pas de supposition « occidentale ») ?
- Un **commerçant non technique** comprend-il l'écran sans aide ?
- Que se passe-t-il si **l'IA, GitHub ou Systalink ne répond plus** ?
- Si quelqu'un **essaie d'abuser** de cette fonction (spam, accès aux données d'un autre), que se passe-t-il ?
- Ce choix **montre-t-il le cloud Systalink** sous un bon jour ?
- Peut-on faire **plus simple** pour le même résultat ?
