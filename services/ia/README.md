# Service IA

**Responsable : @boubacarsidibe** · Bloc [#20](https://github.com/algabah2004/jamii/issues/20) · Contrat : [`contracts/ia.yaml`](../../contracts/ia.yaml)

L'assistant de projet de Jamii. Python 3.12 + FastAPI. Service **interne** : il est appelé par l'API, jamais par le navigateur. **L'IA propose, l'humain décide.**

## Lancer

Il faut [uv](https://docs.astral.sh/uv/) (il installe Python 3.12 tout seul).

```bash
cd services/ia
uv sync                      # installe les dépendances
uv run python -m jamii_ia    # http://localhost:8000/health/readiness
uv run pytest                # les tests
uv run ruff check . && uv run ruff format --check .
```

## Variables d'environnement

| Variable | Défaut | Rôle |
|---|---|---|
| `IA_PROVIDER` | `simule` | Le fournisseur IA. Une valeur inconnue **bloque le démarrage** |
| `PORT`, sinon `IA_PORT` | `8000` | Port d'écoute. `PORT` (imposé par l'hébergeur) l'emporte |
| `DATABASE_URL` | aucun, **obligatoire** | PostgreSQL. Le service y crée son schéma `ia` au démarrage |
| `IA_JETON_INTERNE` | aucun, **obligatoire** | Secret partagé avec l'API, 16 caractères minimum |
| `IA_TRAVAILLEUR` | `true` | Faux : le service reçoit les tâches mais ne les traite pas |
| `IA_LIMITE_PAR_HEURE` | `100` | Dernier rempart : tâches acceptées par heure, au total |

Les tests ont besoin de PostgreSQL : `docker compose up -d postgres` à la racine. Ils créent leur propre base, `jamii_ia_test`, et ne touchent jamais à la base de développement.

## La file de tâches

- `POST /taches` dépose une tâche (`202`), `GET /taches/{id}` lit son état. Les deux exigent `Authorization: Bearer <IA_JETON_INTERNE>`.
- L'en-tête `Idempotency-Key` est obligatoire : rejouer la même requête renvoie la même tâche (`200`), sans doublon.

Les tables vivent dans le schéma `ia` : l'API de Jamii ne les lit jamais, elle passe par `contracts/ia.yaml`. Les migrations (`src/jamii_ia/migrations/*.sql`) sont jouées une seule fois, sous verrou, dans une transaction. Une clé d'idempotence empêche qu'un nouvel essai réseau crée deux tâches.

## Le fournisseur IA est remplaçable

```
code métier ──► FournisseurIA (fournisseurs/base.py) ──► simule             (développement, tests, démo de secours)
                                                    ├──► compatible_openai  (DeepSeek, OpenAI, Mistral… : même format d'API)
                                                    └──► yokk               (un fichier à ajouter quand son API sera documentée)
```

- Le code métier ne connaît que `FournisseurIA`. On change de fournisseur avec `IA_PROVIDER`, sans réécrire.
- **DeepSeek ou OpenAI** : `IA_PROVIDER=compatible_openai`, puis `IA_URL_FOURNISSEUR`, `IA_MODELE` et `IA_CLE` (voir `.env.example`). La clé ne sort jamais du service et n'apparaît dans aucun journal.
- Une demande sépare la **consigne** (écrite par nous) des **données** (écrites par un utilisateur), dans deux messages distincts : un texte d'utilisateur ne peut pas se faire passer pour une consigne.
- Ajouter un fournisseur : une classe avec `nom` et `generer()`, puis une ligne dans `_FABRIQUES` (`fournisseurs/__init__.py`).

## L'IA seulement là où elle sert

| Tâche | Ce qu'elle fait | Quand |
|---|---|---|
| `plan_projet` | Une idée ou une demande devient un plan | À la demande |
| `analyse_profil` | Les faits GitHub deviennent une carte de compétences, **chacune prouvée par un dépôt** | À la génération du profil |
| `orientation_besoin` | Un besoin libre est relié au **catalogue Systalink** et aux compétences nécessaires, avec le plan si besoin | Un appel par besoin |

Ajouter une tâche : un module (`demande`, `lire`, `repli`) et une ligne dans `traitements.py`.

**On ne fait jamais confiance à la réponse d'un modèle** : elle est validée contre le contrat. Une compétence sans preuve réelle est retirée, un service absent du catalogue aussi, un membre inventé est refusé. Le nom, le prix et le lien d'un service viennent toujours du catalogue.

**Le catalogue Systalink** (`catalogue-systalink.yaml`) est un fichier de données : Systalink le modifie sans toucher au code (`IA_CATALOGUE` pour un autre chemin).

**Le coût est maîtrisé** :
- même entrée dans les 24 h → résultat en cache, sans appel ;
- `IA_BUDGET_JOUR` (jetons par jour, UTC) → au-delà, repli sans IA ;
- `IA_LIMITE_PAR_HEURE` → dernier rempart sur le nombre de tâches ;
- un fournisseur en panne → 3 essais, puis repli : l'utilisateur n'a jamais une erreur sèche.

## État

- [x] Squelette, sondes de santé, `FournisseurIA` et adaptateur simulé, tests, CI
- [x] File de tâches PostgreSQL : dépôt, lecture, idempotence, jeton interne, limite globale
- [x] Travailleur qui traite la file, cache de 24 h et repli si l'IA tombe
- [x] Premier cas : idée ou demande de PME → plan (avec le fournisseur simulé)
- [x] Adaptateur `compatible_openai` (DeepSeek, OpenAI…), budget du jour
- [x] `analyse_profil` (carte de compétences) et `orientation_besoin` (catalogue Systalink)
- [ ] Adaptateur Yokk (après le test de son API, voir `docs/systalink-services.md`)
