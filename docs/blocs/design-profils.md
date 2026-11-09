# Design system + Profils et coéquipiers

> 🧭 **Ce plan est un point de départ, pas une consigne. C'est ton bloc.**
> - **Fixe** : le 🎯 But, le 🏁 « Terminé quand » et les règles communes (`docs/equipe.md`, `AGENTS.md`).
> - **Libre** : tout le reste (étapes, ordre, choix techniques, prompt). **Réécris ce fichier à ta façon** dans une PR, idéalement dans les 48 h.
> - Le suivi au jour le jour se fait dans le ticket GitHub (un commentaire court : fait, prochaine étape, blocage).

**Responsable : @rev22rse22** · Ticket de suivi : [#19](https://github.com/algabah2004/jamii/issues/19)

## 🎯 But
Tu donnes **son identité visuelle** à Jamii. C'est la première chose que le jury verra. Tu construis aussi le module **Profils et recherche de coéquipiers** : un profil généré depuis GitHub, et la recherche de partenaires dans tout le continent.

## 📦 À livrer
1. **Le design system** : couleurs, typographie, espacements, thème clair et sombre, et les composants Vue de base (bouton, champ, carte, badge, avatar, modale, liste, état vide, chargement). Documentation dans `apps/web/src/design-system/README.md`.
2. **Les maquettes** des écrans clés : accueil, profil, communauté, demande, en mobile et sur ordinateur.
3. **Le module Profils, front et back** : contrat `contracts/profils.yaml`, tables, endpoints dans `apps/api` (package `profils`), écrans, tests.
   - un profil généré depuis GitHub : langages, projets, pays, et uniquement les forces, sans note globale ;
   - le profil est privé jusqu'à sa publication ;
   - la recherche de coéquipiers par compétence, pays et disponibilité.

## 🚀 Avancer sans attendre personne
- **Tout de suite** : maquettes et choix visuels (Figma ou Penpot), sans aucune dépendance.
- Dès que l'ossature de `apps/web` est fusionnée (Amar, jour 1-2), tu y ajoutes le design system.
- Tes écrans de profil tournent sur des **données factices (MSW)** générées depuis ton contrat. La génération par IA viendra du bloc de @boubacarsidibe, à simuler en attendant.

## 🔌 Branchement Systalink
Voir [integration-systalink.md](../integration-systalink.md).
- Le design system est **brandable** (les tokens pilotent nom, logo et couleurs) : Systalink doit pouvoir appliquer sa charte en changeant les tokens.
- Le profil peut afficher « Projets en ligne sur Systalink » (données du bloc #20).

## ✅ Étapes
- [ ] Maquettes des écrans clés, validées par le chef de projet (lien en commentaire)
- [ ] Design tokens et composants de base dans `apps/web`, page de démonstration des composants
- [ ] `contracts/profils.yaml` validé avec @sowibrr
- [ ] Écrans Profil et Recherche de coéquipiers
- [ ] Backend du module Profils et tests
- [ ] Accessibilité vérifiée (contrastes, clavier) et rendu mobile

## 🤖 Prompt de démarrage pour ton agent IA
```
Lis AGENTS.md, docs/concept.md, docs/vocabulaire.md et docs/blocs/design-profils.md.
Je suis responsable du bloc « Design system + Profils ».
1. Propose une identité visuelle pour une plateforme panafricaine de développeurs :
   moderne, sobre, lisible en 3G et sur mobile, sans cliché. Donne 2 directions
   avec leurs palettes accessibles (contraste AA), puis recommande-en une.
2. Implémente les design tokens et les composants Vue de base, avec une page de démo.
Petites PR. Termine par « Pistes d'amélioration ».
```

## 🏁 Terminé quand
Toute l'appli utilise le design system, et un dev peut générer son profil depuis GitHub, le publier et trouver des coéquipiers. Le chef de projet a validé en revue.

