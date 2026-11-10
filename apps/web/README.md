# Interface web

**Responsables : @Amar233-ui (lead technique) · @rev22rse22 (design et UI)** · Ossature posée par le chef de projet le 10/11 pour ne pas bloquer la démo : reprenez-la librement.

Vue 3 + TypeScript + Vite, vue-router. Les types de l'API sont **générés depuis `contracts/`** (`npm run contrats`), donc le front ne peut pas diverger du back. Textes : uniquement ceux de [docs/vocabulaire.md](../../docs/vocabulaire.md).

## Lancer en 2 minutes
```bash
cd apps/web
npm ci
npm run dev          # http://localhost:5173
```
Le front appelle l'API via le proxy de Vite (`/api` → `http://localhost:8080`, modifiable avec `API_URL`). Lance l'API à côté (`apps/api`, `./mvnw spring-boot:run`) pour la connexion GitHub. Sans API, le site reste visitable et affiche un message.

| Commande | Rôle |
|---|---|
| `npm run dev` | Serveur de développement |
| `npm test` | Tests (Vitest) |
| `npm run build` | Vérification des types puis build de production dans `dist/` |
| `npm run contrats` | Régénère les types depuis `contracts/socle.yaml` et `contracts/projets.yaml` |

## Ce qui est en place
- **Accueil** : Jamii présenté comme un lieu d'échange, avec les 4 espaces.
- **Connexion GitHub** : bouton vers `/api/auth/github?retour=…` (chemin relatif uniquement).
- **Profil** : lit `GET /api/me`, déconnexion avec le jeton CSRF (`X-XSRF-TOKEN`).
- **Session** : un visiteur non connecté reste un visiteur ; si l'API ne répond pas, un message clair s'affiche, sans planter.
- Pages **Communautés**, **Demandes** et **Projets** : en construction.
- Jetons de couleur clair et sombre dans `src/style.css`, à remplacer par le design system (#19).

## Production
Le front est un **site statique** (`dist/`). Il doit être servi **sur le même domaine que l'API** (le cookie de session est `SameSite=Lax`). Sinon, définir `VITE_API_URL` au build et ouvrir CORS côté API. **À décider au déploiement sur DataCloud.**
