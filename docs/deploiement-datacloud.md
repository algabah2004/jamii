# Mettre Jamii en ligne sur DataCloud

> Repris par le chef de projet le 13 novembre 2026 (échéance de 18 h dépassée, voir `docs/priorites.md`).
> Ce qui est **vérifié** l'a été en local, avec les trois images ci-dessous lancées ensemble. Ce qui touche à la console DataCloud est **supposé** d'après le formulaire vu le 10/11 (`docs/systalink-services.md`) : à cocher en le faisant.

## Ce qu'on déploie

```
navigateur ──https──▶ jamii-web (Caddy) ──/api/*──▶ jamii-api (Java) ──▶ jamii-ia (Python)
                            │ pages                      │                    │
                                                          └──── PostgreSQL ◀───┘
```

- **Une seule adresse publique, celle de `jamii-web`.** Caddy sert les pages et relaie `/api/*` vers l'API. Le navigateur ne voit qu'une origine, ce qui est indispensable pour le cookie de session et le jeton CSRF (`apps/web/README.md`).
- L'API et le service IA n'ont pas besoin d'être visibles du public, s'il existe un réseau privé (annoncé par DataCloud, pas testé). Sinon, on utilise leur adresse publique, et rien d'autre ne change.

| Service | Répertoire racine | Construction | Port | Contrôle de santé |
|---|---|---|---|---|
| `jamii-api` | `apps/api` | `apps/api/Dockerfile` | `PORT` (8080) | `/health/readiness` |
| `jamii-ia` | `services/ia` | `services/ia/Dockerfile` | `PORT` (8000) | `/health/readiness` |
| `jamii-web` | `.` (racine, le front lit `docs/design`) | `apps/web/Dockerfile` | `PORT` (8080) | `/` |

Les trois images sont **vérifiées en local** : elles se construisent, démarrent, et le parcours passe par le relais (pages, `/cercles` en accès direct, `/api/me` → 401, demande de lien e-mail avec le jeton CSRF → 202).

## Étape par étape

### 0. Avant de commencer (10 min)
- [ ] Acheter **un produit DataCloud payant** avec le code promo de l'équipe (espace CADEV). **Ne jamais l'écrire ici ni dans une issue.** C'est l'article 6 du règlement : sans cet achat, le projet n'est pas recevable.
- [ ] Générer deux secrets : `openssl rand -hex 32` pour `IA_JETON_INTERNE`, un autre pour le mot de passe de la base.

### 1. La base PostgreSQL
- [ ] Menu **Base de données** → PostgreSQL 16, région Dakar.
- [ ] Noter l'hôte, le port, le nom, l'utilisateur et le mot de passe.

### 2. Le service IA (`jamii-ia`)
- [ ] **Application web** → GitHub → `algabah2004/jamii`, branche `main`, répertoire racine `services/ia`, environnement **Docker**.
- [ ] Variables :
  ```
  DATABASE_URL=postgresql://<utilisateur>:<mot de passe>@<hôte>:<port>/<base>
  IA_JETON_INTERNE=<secret 1>
  IA_PROVIDER=simule
  ```
- [ ] Contrôle de santé : `/health/readiness`. Noter son adresse (privée si possible).

### 3. L'API (`jamii-api`)
- [ ] **Application web** → même dépôt, répertoire racine `apps/api`, environnement **Docker**.
- [ ] Variables (**n'en mettez aucune vide** : une variable vide écrase la valeur par défaut, et `GITHUB_CLIENT_ID=` vide empêche l'API de démarrer) :
  ```
  POSTGRES_HOST=<hôte>
  POSTGRES_PORT=<port>
  POSTGRES_DB=<base>
  POSTGRES_USER=<utilisateur>
  POSTGRES_PASSWORD=<mot de passe>
  POSTGRES_SSLMODE=require
  WEB_URL=https://<adresse publique de jamii-web>
  IA_FOURNISSEUR=service
  IA_URL=<adresse de jamii-ia>
  IA_JETON_INTERNE=<secret 1>
  SPRING_PROFILES_ACTIVE=demo
  ```
  - `SPRING_PROFILES_ACTIVE=demo` charge les 3 cercles et les 6 comptes de démo (#125). À retirer après la CADEV.
  - Si la base de DataCloud refuse `require`, essayer `prefer` et le noter ici.
- [ ] Contrôle de santé : `/health/readiness`. La première fois, compter 30 à 60 s (Flyway crée les tables).

### 4. Le site (`jamii-web`)
- [ ] **Application web** → même dépôt, répertoire racine **`.`**, Dockerfile **`apps/web/Dockerfile`**, environnement **Docker**.
- [ ] Variable : `API_URL=<adresse de jamii-api>` (avec `http://` ou `https://`).
- [ ] Ouvrir l'adresse publique : la page d'accueil s'affiche, et `/api/systalink/tableau-de-bord` répond en JSON.

### 5. Brancher ce qui vient ensuite (sans redéployer le code)
| Quoi | Variables à ajouter sur `jamii-api` | Qui |
|---|---|---|
| Connexion GitHub | `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` d'une OAuth App dont l'adresse de retour est `https://<jamii-web>/api/auth/github/retour` | chef de projet |
| Connexion par e-mail (#127) | `EMAIL_FOURNISSEUR=brevo`, `BREVO_API_KEY`, `EMAIL_EXPEDITEUR` | chef de projet |
| Dépôt GitHub de chaque tour (#124) | `DEPOT_CODE=github`, `GITHUB_DEPOTS_ORGANISATION`, `GITHUB_DEPOTS_JETON` | chef de projet |
| Profils générés depuis GitHub | `GITHUB_TOKEN` (jeton sans aucune permission) | chef de projet |

### 6. Pour le cockpit CADEV
- [ ] Poster l'adresse publique de `jamii-web` et `/health` de l'API dans #54.
- [ ] Étape 4 du cockpit, « Produit Systalink actif ».

## Si quelque chose casse

| Symptôme | Cause probable |
|---|---|
| L'API redémarre en boucle, « Client id of registration 'github' must not be empty » | Une variable `GITHUB_CLIENT_ID` vide : la supprimer |
| L'API ne trouve pas la base | `POSTGRES_SSLMODE`, ou le réseau privé ne marche pas : essayer l'adresse publique de la base |
| Le site s'affiche mais tout appel répond 502 | `API_URL` faux sur `jamii-web` |
| Après la connexion GitHub, retour sur une mauvaise adresse | `WEB_URL` faux, ou l'adresse de retour de l'OAuth App |
| Plus de mémoire (512 Mo) | Voir « Déployer avec 512 Mo » dans `apps/api/README.md` |

## Plan B : un seul serveur

Si DataCloud bloque, les trois images tournent sur n'importe quel serveur avec Docker (menu **Serveurs**). Les mêmes variables, et Caddy publie le site sur le port 8080.
