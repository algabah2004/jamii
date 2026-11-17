# Plan de vérification de la responsivité

Le but : chaque écran de Jamii doit être juste et confortable de 320 px à 1920 px de large, et d'abord sur téléphone.
Beaucoup de développeurs et de commerçants du continent n'ont que leur téléphone pour ouvrir Jamii.

Ce plan fixe la méthode : un module à la fois, dans l'ordre ci-dessous.
Pour chaque module, on mesure, on corrige, puis **le développeur valide avant qu'on passe au suivant**.

## 1. Les largeurs testées

| Largeur × hauteur | Ce qu'elle représente |
|---|---|
| **320 × 640** | Le plus petit téléphone encore courant. C'est aussi la largeur exigée par WCAG 1.4.10 (zoom à 400 % sur 1280 px). |
| **360 × 780** | Android d'entrée et de milieu de gamme. Supposé être la largeur la plus répandue sur le continent : à vérifier sur StatCounter. |
| **390 × 844** | iPhone récent |
| **812 × 375** | Téléphone tenu à l'horizontale (clavier ouvert : il reste environ 150 px de haut) |
| **768 × 1024** | Tablette tenue à la verticale |
| **1024 × 768** | Tablette tenue à l'horizontale, petit portable |
| **1280 × 800** et **1440 × 900** | Portables |
| **1920 × 1080** | Grand écran : le contenu ne doit pas s'étirer sans fin |

## 2. La grille de contrôle (pour chaque écran et chaque largeur)

**Mise en page**
1. Rien ne dépasse à droite et rien n'est rogné. `#app` a `overflow-x: clip` : la page ne défile jamais en largeur, si bien qu'un débordement **cache** du contenu au lieu de le signaler. Le script du §4 le détecte.
2. Les rangées qui défilent à l'horizontale (onglets, filtres) le montrent : un bord coupé ou un fondu, et l'onglet actif est visible au chargement.
3. Au-delà de 1440 px, la largeur de lecture reste bornée.

**Toucher**

4. Les cibles tactiles mesurent **au moins 24 × 24 px**, ce qui est le minimum WCAG 2.5.8 (AA). Pour les actions principales, on vise **44 × 44 px**. Deux cibles voisines ne se touchent pas.
5. Ce qui n'apparaît qu'au survol (réactions, menus `…`) est accessible sans souris (`@media (hover: none)`).

**Texte**

6. Rien n'est en dessous de 14 px (`--type-body-sm-size`), sauf les pastilles de compteur.
7. Les titres passent à la ligne et ne se coupent pas.
8. Un nom long, un lien long ou un code ne fait pas exploser la largeur (`overflow-wrap: anywhere`).

**Formulaires**

9. Les champs ont au moins 16 px de texte sur mobile ; en dessous, iOS zoome tout seul.
10. Les champs ont le bon `inputmode` et `autocomplete`.
11. Le bouton d'envoi reste atteignable quand le clavier est ouvert.

**Fenêtres, tiroirs et éléments fixes**

12. Une fenêtre tient dans `100dvh`, son contenu défile à l'intérieur, et « Fermer » reste visible.
13. Les barres fixes en bas d'écran respectent `env(safe-area-inset-bottom)`.
14. L'en-tête collant ne prend pas plus d'environ 20 % de la hauteur sur téléphone.

**Accessibilité et thèmes**

15. Le focus clavier reste visible et n'est pas rogné par un conteneur qui défile.
16. Le zoom à 200 % ne casse rien.
17. Le thème sombre et le thème clair sont tous deux vérifiés. Le clair n'a jamais été vérifié sur le profil.

**Ergonomie sur téléphone**

18. L'action principale de l'écran est visible sans défiler, ou dans la zone du pouce.
19. Une colonne latérale passe **sous** le contenu principal ou dans un tiroir, jamais à côté en 150 px.

## 3. Le cycle pour chaque module

1. **Mesurer** : lancer le script du §4 et faire des captures aux 9 largeurs, en mode factice (`npm run dev:factice`), sans toucher la production.
2. **Lister** les défauts, chacun rattaché à un point de la grille, avec sa capture. On montre la liste au développeur.
3. **Corriger** dans les fichiers du module. Un défaut commun (en-tête, composant du design system) se corrige au module 0, pas dans chaque page.
4. **Vérifier** :
   - relancer le script, qui doit sortir à zéro ;
   - captures avant/après ;
   - `vue-tsc` et `vitest` verts ;
   - un test quand un comportement change (par exemple, un tiroir qui remplace une colonne).
5. **Faire valider** : le développeur regarde les captures et, s'il veut, ouvre le mode factice sur son téléphone (`npm run dev:factice -- --host`, même Wi-Fi). Le module est fermé seulement après sa validation.
6. **Livrer** : une PR par module, d'environ 400 lignes au plus. La branche part de `main` à jour et la PR est fusionnée avant d'ouvrir la suivante (pas de PR empilées). La case du module est cochée ici même.

**Paliers de largeur.**
- Le code en utilise aujourd'hui une quinzaine (480, 560, 600, 640, 680, 700, 720, 760, 860, 900, 959/960, 1000, 1100, 1180…).
- On ne les réécrit pas tous d'un coup. **Quand on touche un module, on ramène ses paliers à quatre : 480, 720, 960 et 1180 px.** Ce sont ceux de l'en-tête.
- Les variables CSS ne marchent pas dans `@media` : la liste vit dans `design-system/tokens.css`, en commentaire.

## 4. L'outil de mesure

Le script parcourt toutes les routes dans une `iframe` à chaque largeur, et relève trois choses :
- les éléments qui dépassent de l'écran, en précisant s'ils sont **rognés** (défaut) ou dans une rangée qui **défile** (voulu) ;
- le nombre de cibles tactiles de moins de 32 px ;
- la route finale, ce qui révèle une redirection.

Il a servi au premier état des lieux ci-dessous. Au module 0, on le range dans `apps/web/scripts/audit-responsive.js`, à coller dans la console du mode factice. On n'y ajoute aucune dépendance : Playwright ne tourne pas sur les postes Windows de l'équipe.

## 5. État des lieux du 17/11 (mode factice, connecté)

Rien ne défile en largeur : c'est l'effet de `overflow-x: clip`. Mais du contenu est **rogné** à trois endroits :

| Écran | Largeur | Défaut constaté | Gravité |
|---|---|---|---|
| En-tête (toutes les pages) | 320 px | « Se déconnecter » sort de l'écran (bord droit à 339 px) : le bouton est **invisible et inaccessible** | 🔴 |
| Conversation `/messages/:id` | 768 px | L'en-tête de la conversation et le fil sont rognés à droite (777 et 791 px) | 🔴 |
| Espace Systalink `/systalink` | 320 px | Les cartes dépassent de 28 px et leur bord droit est coupé | 🟠 |
| Cercle, communauté, notifications, hackathon | 320–768 px | Les onglets défilent à l'horizontale, ce qui est voulu, mais il faut vérifier qu'on voit qu'il y en a d'autres (point 2) | 🟡 |
| Toutes les pages | toutes | De 6 à 27 cibles de moins de 32 px par page, dont les liens du menu (36 px) et les icônes de l'en-tête (40 px) : point 4 à revoir écran par écran | 🟡 |

**Pas encore mesurés :**
- `/connexion` (le mode factice est connecté et redirige vers l'accueil) ;
- `/demandes/:id` et `/publications/:id` (aucun lien dans les données factices) ;
- toutes les fenêtres et tiroirs (ils demandent un clic).

On les ouvre à la main dans leur module.

## 6. Les modules, dans l'ordre

L'ordre suit le parcours d'un visiteur : d'abord ce qui est partout, puis l'arrivée, puis chaque espace.

### Module 0 — Le socle commun ⬜
Ce que tous les écrans partagent. Corrigé ici une fois, pour ne pas l'être dix fois.

**Fichiers :**
- `App.vue` (en-tête, menu, pied, marges de `main`) ;
- `design-system/` (`base.css`, `tokens.css`, `JBouton`, `JChamp`, `JFiltres`, `JDeplier`, `JAvatar`) ;
- `components/` (`BoutonLangue`, `BoutonTheme`, `ReglagesAffichage`, `LienRetour`, `exploration/*`, `creation/AssistantCreation`) ;
- `components/texte/*` (le composeur, l'enregistreur vocal, les réactions et les pièces jointes, utilisés par les messages, les salons et les cercles).

**À faire :**
- en-tête à 320 px : « Se déconnecter » doit rester accessible, par exemple dans le menu « Moi » ou sous forme d'icône ;
- hauteur de l'en-tête sur téléphone (point 14) ;
- cibles de l'en-tête et du menu à 44 px ;
- champs à 16 px sur mobile (point 9) ;
- fondu au bord des rangées qui défilent (point 2), une seule classe utilitaire réutilisée par tous les onglets ;
- composeur au-dessus du clavier, avec `safe-area` ;
- liste des paliers dans `tokens.css` ;
- script de mesure rangé.

### Module 1 — L'arrivée ⬜
**Écrans :**
- `/` : l'accueil et ses sections (`pages/accueil/*`) ;
- `/connexion` et `/connexion/email` ;
- la page introuvable.

**Attention :** c'est le premier écran du jury et des visiteurs. La scène et les illustrations doivent rester légères sur un téléphone d'entrée de gamme.

### Module 2 — Le profil ⬜
**Écrans :**
- `/profil` et `/p/:id` (`FicheProfil`, `MonProfilPage`, `ProfilPublicPage`) ;
- la fenêtre « Partager ma carte » ;
- `CarteCompetences`.

**À regarder :**
- les blocs de modification ouverts au clavier mobile ;
- l'anneau de preuves à 320 px ;
- la carte en petit dans sa fenêtre.

### Module 3 — Les communautés ⬜
**Écrans :**
- `/communautes` ;
- `/communautes/nouvelle` ;
- `/communautes/:slug` : l'espace, la bannière, la barre des salons, les membres, « En direct » ;
- `/communautes/:slug/salons/nouveau` ;
- `/publications/:id` ;
- la fenêtre « Signaler » et la suppression d'un salon.

**À regarder :**
- l'espace a trois colonnes sur ordinateur, et il faut décider ce que devient chacune sur téléphone ;
- un salon avec le composeur et le clavier ouvert.

### Module 4 — Les cercles ⬜
**Écrans :**
- `/cercles` ;
- `/cercles/nouveau` ;
- `/cercles/:id` : le QG, le tour et sa colline, les tâches du tour, le salon ;
- les outils : tâches, calendrier, décisions, points ;
- la fenêtre d'une tâche, l'appel du cercle, le partage, la préparation d'une idée.

**À regarder :**
- le tableau des tâches (des colonnes sur ordinateur) et le calendrier sur 320 px : ce sont les écrans les plus denses de l'application.

### Module 5 — Messages et réseau ⬜
**Écrans :**
- `/messages` et `/messages/:id` ;
- `/notifications` et `/contacts` ;
- la fenêtre d'appel ;
- le réglage de présence.

**À regarder :**
- la conversation rognée à 768 px ;
- liste ou conversation sur téléphone : on en affiche une seule à la fois, et le retour est évident ;
- le message vocal, les réactions au toucher, les pièces jointes.

### Module 6 — Les demandes (Ayo) ⬜
**Écrans :**
- `/demandes` ;
- `/demandes/:id` : la conversation avec Ayo, la publication de la demande, la confirmation de suppression.

**À regarder :** c'est l'écran du **commerçant non technique**, souvent sur téléphone. Il doit pouvoir tout faire d'une main.

### Module 7 — Les missions ⬜
**Écrans :**
- `/missions` et `/missions/nouvelle` ;
- `/missions/:id` : le suivi, les jalons, le devis proposé ou à envoyer, le tiroir des échanges, l'avis, le désaccord, l'annulation, la livraison d'un jalon.

**À regarder :**
- les montants et les dates sur 320 px ;
- le tiroir des échanges au-dessus du clavier.

### Module 8 — Les hackathons ⬜
**Écrans :**
- `/hackathons` et `/hackathons/nouveau` ;
- `/hackathons/:id` et ses onglets : les équipes, la galerie, la discussion, le rendu d'une équipe, le tri des candidatures, le jury, la notation, les résultats et leur confirmation, le pilotage ;
- les questionnaires : l'éditeur, le remplissage, les réponses.

**À regarder :**
- la grille de notation du jury sur téléphone ;
- les tableaux de candidatures et de réponses, qui doivent devenir des cartes et non défiler en largeur.

### Module 9 — Vitrine et Espace Systalink ⬜
**Écrans :**
- `/vitrine` (`CarteProjet`) ;
- `/systalink` : le tableau de bord, les clients suivis, la liste des pays.

**À regarder :** les cartes de `/systalink` rognées à 320 px.

### Module 10 — La passe finale ⬜
**Sur tous les écrans, d'un bout à l'autre :**
- téléphone à l'horizontale ;
- zoom à 200 % ;
- thème clair et thème sombre ;
- clavier seul (ordre du focus, focus jamais rogné).

**Sur un vrai téléphone Android d'entrée de gamme :** l'appareil d'un membre de l'équipe, sur `jamii.dev` après le déploiement.

**Conclusion :** une capture de chaque écran à 360 px, réunies dans la PR.

## 7. Ce que ce plan ne fait pas
- **Pas de refonte visuelle.** On rend juste et confortable ce qui existe. Une idée de refonte devient une proposition à part.
- **Pas de changement d'API ni de contrat.**
- **Pas de test sur la production avec de fausses données :** tout se mesure en mode factice, et `jamii.dev` sert seulement à la passe finale, en lecture.
