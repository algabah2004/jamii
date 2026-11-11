# Interface web

**Responsables : @Amar233-ui (lead technique, bloc #18) · @rev22rse22 (design system et profil, bloc #19)**

Vue 3 + TypeScript + Vite. Textes : uniquement ceux de [docs/vocabulaire.md](../../docs/vocabulaire.md), en français puis en anglais.

## Lancer

```bash
cd apps/web
npm ci
npm run dev           # http://localhost:5173, relaie /api vers l'API (http://localhost:8080 ou $API_URL)
npm run dev:factice   # sans API : les réponses viennent des données factices (MSW)
npm test              # tests Vitest
npm run build         # vérification des types + build dans dist/
```

Node 22 ou plus.

## Organisation

| Dossier | Contenu | Bloc |
|---|---|---|
| `src/api/` | Client typé (`openapi-fetch`) et jeton CSRF | #18 |
| `src/api/generated/` | Types **générés** depuis `contracts/` : ne pas modifier à la main | — |
| `src/mocks/` | Données factices MSW, partagées par le mode `factice` et les tests | chacun ajoute les siennes |
| `src/i18n/` | Traductions `fr.json` et `en.json` (mêmes clés, vérifié par un test) | chacun ajoute les siennes |
| `src/pages/` | Pages de l'ossature : accueil, profil vide, page introuvable | #18 |
| `src/features/<bloc>/` | Une fonctionnalité par dossier (`communautes`, `vitrine`, `systalink`, `profils`…) | le bloc concerné |
| `src/design-system/` | Composants et tokens ([docs/design/tokens.json](../../docs/design/tokens.json)) | #19 |

## Les contrats

Le client est généré depuis `contracts/` : le front ne peut pas diverger de l'API.

```bash
npm run api:types   # après chaque changement de contracts/socle.yaml
```

La CI régénère les types et **échoue s'ils ne sont pas à jour**. Pour brancher un nouveau contrat (par exemple `projets.yaml`), ajoute sa ligne dans le script `api:types`.

## Connexion et sécurité

- Le front appelle **toujours `/api` en relatif**. Le cookie de session (`JAMII_SESSION`, HttpOnly) suit tout seul. Il n'y a ni jeton dans le navigateur, ni CORS.
- Les requêtes `POST`, `PUT`, `PATCH` et `DELETE` renvoient le cookie `XSRF-TOKEN` dans l'en-tête `X-XSRF-TOKEN`. C'est ce qu'attend `csrf.spa()` côté API.
- Au démarrage, `GET /api/me` décide de l'écran : `200` connecté, `401` accueil non connecté, autre chose « Jamii ne répond pas » avec un bouton « Réessayer ».
- Le chemin de retour après la connexion GitHub est toujours relatif. Le front le vérifie, et l'API le revérifie (pas de redirection ouverte).

## Mise en ligne (à décider avec #17 et #20)

**Contrainte :** le front et l'API doivent être servis sur **la même origine**, par exemple `https://jamii…/` pour le front et `https://jamii…/api` relayé vers l'API. Sinon, le cookie de session et le jeton CSRF ne passent pas.

- **Option recommandée** : un site statique (`dist/`) avec une règle qui relaie `/api/*` vers le service API. Il faut **tester** si DataCloud le permet (rien dans `docs/systalink-services.md`).
- **Plan B** (`docs/architecture.md`) : Nginx sert `dist/` et relaie `/api` dans le `docker compose` sur un serveur Systalink.
- Toutes les routes inconnues doivent renvoyer `index.html` (routeur en mode historique).
