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

## Le fournisseur IA est remplaçable

```
code métier ──► FournisseurIA (fournisseurs/base.py) ──► simule   (développement, tests, démo de secours)
                                                    └──► yokk     (à brancher après le test de son API)
```

- Le code métier ne connaît que `FournisseurIA`. On change de fournisseur avec `IA_PROVIDER`, sans réécrire.
- Une demande sépare la **consigne** (écrite par nous) des **données** (écrites par un utilisateur) : un texte d'utilisateur ne peut pas se faire passer pour une consigne.
- Ajouter un fournisseur : une classe avec `nom` et `generer()`, puis une ligne dans `fournisseurs/__init__.py`.

## État

- [x] Squelette, sondes de santé, `FournisseurIA` et adaptateur simulé, tests, CI
- [ ] File de tâches PostgreSQL (`POST /taches`, `GET /taches/{id}`), jeton interne, cache, repli
- [ ] Premier cas : idée ou demande de PME → plan
- [ ] Adaptateur Yokk (après le test de son API, voir `docs/systalink-services.md`)
