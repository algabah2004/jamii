# Design system + Profil prouvé

> 🧭 **Ce plan est un point de départ, pas une consigne. C'est ton bloc.**
> - **Fixe** : le 🎯 But, le 🏁 « Terminé quand » et les règles communes (`docs/equipe.md`, `AGENTS.md`).
> - **Libre** : tout le reste (étapes, ordre, choix techniques, prompt). **Réécris ce fichier à ta façon** dans une PR, idéalement dans les 48 h.
> - Le suivi au jour le jour se fait dans le ticket GitHub (un commentaire court : fait, prochaine étape, blocage).

**Responsable : @rev22rse22** · Ticket de suivi : [#19](https://github.com/algabah2004/jamii/issues/19)

## 🎯 But
Tu donnes à Jamii **son identité visuelle**, la première chose que le jury et le public verront. Et tu construis **le profil prouvé** : ce qu'un dev montre fièrement, et la raison n°1 de s'inscrire. Voir le pilier 1 de [concept.md](../concept.md).

## 📦 À livrer
1. **Le design system** : tokens (couleurs, typographie, espacements, clair et sombre) et composants Vue de base, avec une page de démonstration. Il est **brandable** (Systalink peut appliquer sa charte en changeant les tokens).
2. **Les maquettes** des écrans de la démo : profil, projet d'équipe, communauté, vitrine, tableau de bord Systalink.
3. **Le profil prouvé** (`contracts/profils.yaml`, front et back) :
   - généré depuis GitHub (avec le résumé fait par l'IA du bloc #20) ;
   - **projets réellement en ligne** (lien vérifié) et contributions dans les équipes Jamii ;
   - **un indice de fiabilité explicable** : tâches livrées, avis des coéquipiers, réponses acceptées. Chaque point est justifié et contestable, sans note globale ;
   - privé jusqu'à sa publication ;
   - recherche de coéquipiers par compétence, pays et disponibilité.

## 🚀 Avancer sans attendre personne
- **Tout de suite** : maquettes et identité visuelle (Figma ou Penpot), sans aucune dépendance.
- Dès que l'ossature de `apps/web` est fusionnée (Amar, jours 1-2) : le design system.
- Le profil tourne sur des **données factices (MSW)**. Les données de fiabilité viendront des projets (#17) et des communautés (#18) via leurs contrats.

## 🔌 Branchement Systalink
Voir [integration-systalink.md](../integration-systalink.md).
- Le badge « En ligne sur Systalink » sur chaque projet du profil.
- Le design system brandable.

## ✅ Étapes
- [ ] Maquettes des écrans de la démo, validées par le chef de projet
- [ ] Tokens et composants de base, page de démonstration
- [ ] `contracts/profils.yaml` validé avec @sowibrr
- [ ] Profil généré depuis GitHub, publication, recherche de coéquipiers
- [ ] Projets en ligne vérifiés et indice de fiabilité explicable
- [ ] Accessibilité (contrastes, clavier) et rendu mobile

## 🤖 Exemple de prompt pour ton agent IA
```
Lis AGENTS.md, docs/concept.md, docs/vocabulaire.md et docs/blocs/design-profils.md.
Je suis responsable du bloc « Design system + Profil prouvé ».
1. Propose 2 directions d'identité visuelle pour une plateforme panafricaine
   de développeurs (moderne, sobre, sans cliché, contraste AA), puis recommande-en une.
2. Conçois un indice de fiabilité juste et explicable (sans note globale) :
   ses sources, son calcul, comment le contester, comment éviter la triche.
Petites PR. Termine par « Pistes d'amélioration ».
```

## 🏁 Terminé quand
Toute l'appli utilise le design system. Un dev génère son profil depuis GitHub, voit ses projets en ligne vérifiés et son indice de fiabilité expliqué, le publie et trouve des coéquipiers. Le chef de projet a validé en revue.
