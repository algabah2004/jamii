# ADR 0011 · L'accueil est pré-rendu au build, pas rendu par un serveur

- **Statut :** accepté
- **Date :** 19/11/2026
- **Concerne :** `apps/web` (front Vue 3 et Vite), `apps/web/Caddyfile`

## Contexte

Le front est une application à page unique (SPA). Avant ce changement, `index.html` ne contenait qu'un `<div id="app">`
vide, et rien ne s'affichait avant que le JavaScript soit téléchargé **et** que `GET /api/me` ait répondu
(`main.ts` attend la session avant de monter l'application).

Conséquences, surtout sur l'accueil, la page que voient les visiteurs, le jury et les aperçus de liens :

- écran vide pendant le chargement, plus long sur une connexion mobile lente ;
- les robots d'indexation et les aperçus (WhatsApp, LinkedIn, Slack) ne voient pas le contenu ;
- si l'API redémarre (à chaque mise à jour, voir `deploiement/README.md`), l'accueil reste vide le temps des réessais.

## Options étudiées

| Option | Pour | Contre |
|---|---|---|
| **A. SSR à chaque requête** (serveur Node, ou Nuxt) | HTML toujours à jour, même pour les pages connectées | Un service Node de plus à exploiter, surveiller et mettre à jour en production ; migration de tout le front vers Nuxt ; le cookie de session devrait être relayé au serveur de rendu. Tout ça pour une page **identique pour tous les visiteurs sans compte** |
| **B. Pré-rendu au build (SSG) de l'accueil, puis hydratation** | Même bénéfice que A pour l'accueil ; zéro composant de plus en production (Caddy sert un fichier) ; rendu déterministe testé en CI | Le HTML est figé au build : il montre l'accueil sans compte, en français |
| C. Garder la SPA et soigner un squelette de chargement | Rien à changer | Ne règle ni les aperçus de liens ni l'indexation |

## Décision

**Option B.** `npm run build` construit le front, puis `scripts/prerendre.mjs` :

1. construit `src/entree-prerendu.ts` pour Node avec Vite (mode SSR) ;
2. rend l'accueil avec `vue/server-renderer`, session fixée à « non connecté » et langue à `fr`, sans appeler l'API ;
3. écrit `dist/index.html` (accueil rendu, marqué `data-prerendu`) et `dist/app.html` (coquille vide).

Caddy sert `index.html` pour `/` et `app.html` pour toute autre route du routeur, pour qu'aucune autre page ne montre
l'accueil une fraction de seconde.

Dans le navigateur, `main.ts` décide (`peutHydrater`, `src/application.ts`) :

- **visiteur sans compte, en français, sur `/`** : `createSSRApp` **hydrate** le HTML existant, sans le redessiner ;
- **tous les autres cas** (connecté, anglais, API injoignable) : `createApp` remplace le HTML pré-rendu. On évite ainsi
  une hydratation incohérente, que Vue signale comme erreur dans la console.

Une seule fabrique (`creerApplication`) sert aux deux rendus, pour qu'ils produisent le même arbre.

## Garde-fous

- Toute erreur pendant le rendu fait **échouer le build** (`app.config.errorHandler` dans `entree-prerendu.ts`) : on ne
  publie jamais un accueil à moitié dessiné.
- `application.test.ts` vérifie le contenu rendu, son caractère déterministe (deux rendus identiques) et chaque cas de
  `peutHydrater`.
- Le code exécuté au rendu ne doit pas toucher au navigateur (`window`, `document`, `localStorage`) dans `setup()` :
  ce qui en a besoin va dans `onMounted` ou derrière `if (!import.meta.env.SSR)`, comme les suivis d'activité de `App.vue`.

## Conséquences

- L'accueil s'affiche dès le premier octet HTML, avant le JavaScript et avant la réponse de l'API.
- Une personne connectée ou en anglais voit brièvement l'accueil sans compte en français, puis sa page. Si on veut
  supprimer ce passage, l'étape suivante serait de pré-rendre aussi `/en` ou de passer certaines routes en SSR (option A).
- Les autres pages publiques (`/vitrine`, `/missions`, `/p/:id`) restent rendues côté client : elles dépendent de
  données vivantes, ce qui relève de l'option A si un jour leur indexation compte.
