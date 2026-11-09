# Communautés et entraide + ossature du front + espace Systalink

> 🧭 **Ce plan est un point de départ, pas une consigne. C'est ton bloc.**
> - **Fixe** : le 🎯 But, le 🏁 « Terminé quand » et les règles communes (`docs/equipe.md`, `AGENTS.md`).
> - **Libre** : tout le reste (étapes, ordre, choix techniques, prompt). **Réécris ce fichier à ta façon** dans une PR, idéalement dans les 48 h.
> - Le suivi au jour le jour se fait dans le ticket GitHub (un commentaire court : fait, prochaine étape, blocage).

**Responsable : @Amar233-ui** · Ticket de suivi : [#18](https://github.com/algabah2004/jamii/issues/18)

## 🎯 But
Tu es **lead technique du front** : tu poses l'ossature de l'appli que tout le monde utilise. Et tu construis **l'échange**, le cœur du thème CADEV : les communautés, l'entraide, la **vitrine** des projets en ligne et le **tableau de bord Systalink**, c'est-à-dire ce que le jury de Systalink verra de « son » côté. Voir le pilier 5 et l'espace Systalink de [concept.md](../concept.md).

## 📦 À livrer
1. **L'ossature `apps/web`** : Vue 3 + TypeScript + Vite, routage, mise en page, client API généré depuis `contracts/`, données factices (MSW), traduction FR/EN, job CI.
2. **Les communautés** (`contracts/communautes.yaml`, front et back) :
   - communautés thématiques ouvertes à tout le continent ;
   - questions-réponses avec réponse acceptée : **l'IA propose une réponse, les pairs la valident** (via #20) ;
   - discussions et « Montre ton projet » ;
   - modération (signalement, modérateurs).
3. **La vitrine** : les projets en ligne nés sur Jamii, avec la mention « Hébergé sur Systalink ».
4. **Le tableau de bord Systalink** : devs inscrits, projets créés, projets déployés sur DataCloud, passage vers une offre payante (agrégé et anonyme).
5. **Les tests de bout en bout** (Playwright) du parcours de la démo.

## 🚀 Avancer sans attendre personne
- **Jours 1-2** : fusionne l'ossature `apps/web`. @rev22rse22 y branchera le design system.
- Le front tourne sur des **données factices (MSW)** générées depuis les contrats.
- Ton backend vit dans son package `communautes`. L'utilisateur connecté est simulé tant que le socle n'est pas prêt.

## 🔌 Branchement Systalink
Voir [integration-systalink.md](../integration-systalink.md).
- La vitrine et le tableau de bord mettent en avant **DataCloud** : chaque projet affiché est hébergé chez Systalink.
- Le front est **brandable** (nom, logo et couleurs depuis la configuration), pour devenir un produit Systalink.

## ✅ Étapes
- [ ] Ossature `apps/web` fusionnée (jours 1-2)
- [ ] Spec `docs/propositions/0002-communautes.md` : étude de Stack Overflow, Discord et GitHub Discussions, parcours, cas limites
- [ ] `contracts/communautes.yaml` validé avec @sowibrr
- [ ] Communautés et questions-réponses (front et back), réponse proposée par l'IA
- [ ] Vitrine des projets en ligne
- [ ] Tableau de bord Systalink
- [ ] Tests Playwright du parcours de la démo

## 🤖 Exemple de prompt pour ton agent IA
```
Lis AGENTS.md, docs/concept.md, docs/vocabulaire.md et docs/blocs/communautes.md.
Je suis lead technique front et responsable du bloc « Communautés et entraide ».
1. Crée l'ossature apps/web : Vue 3 + TS + Vite, vue-router, Pinia, vue-i18n,
   MSW, client API généré depuis contracts/*.yaml, Vitest, ESLint.
2. Étudie Stack Overflow, Discord et GitHub Discussions, puis propose la spec 0002 :
   qu'est-ce qui fera revenir un dev africain chaque semaine ?
Petites PR. Termine par « Pistes d'amélioration ».
```

## 🏁 Terminé quand
Un dev pose une question dans une communauté, l'IA propose une réponse et un pair la valide. La vitrine affiche les projets en ligne, et le tableau de bord Systalink montre les vrais chiffres. Le tout sur la vraie API, avec les tests Playwright verts et la validation du chef de projet.
