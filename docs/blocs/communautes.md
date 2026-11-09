# Communautés et échanges + ossature du front

> 🧭 **Ce plan est un point de départ, pas une consigne. C'est ton bloc.**
> - **Fixe** : le 🎯 But, le 🏁 « Terminé quand » et les règles communes (`docs/equipe.md`, `AGENTS.md`).
> - **Libre** : tout le reste (étapes, ordre, choix techniques, prompt). **Réécris ce fichier à ta façon** dans une PR, idéalement dans les 48 h.
> - Le suivi au jour le jour se fait dans le ticket GitHub (un commentaire court : fait, prochaine étape, blocage).

**Responsable : @Amar233-ui** · Ticket de suivi : [#18](https://github.com/algabah2004/jamii/issues/18)

## 🎯 But
Tu construis **le cœur du thème de la CADEV, la « plateforme d'échange »**. Les développeurs de tout le continent s'y retrouvent par sujet, posent des questions, discutent et montrent leurs projets. Tu es aussi **lead technique du front** : tu poses l'ossature de l'appli web que tout le monde utilisera.

## 📦 À livrer
1. **L'ossature du front** `apps/web` : Vue 3 + TypeScript + Vite, routage, mise en page (en-tête, navigation : Accueil, Communautés, Demandes, Projets, Profil), client API **généré depuis `contracts/`**, données factices avec MSW, traduction FR/EN, job CI.
2. **La spec Communautés** : `docs/propositions/0002-communautes.md`.
3. **Le module Communautés, front et back** : contrat `contracts/communautes.yaml`, tables, endpoints dans `apps/api` (package `communautes`), écrans, tests.
   - communautés thématiques (Flutter Afrique, Fintech…) et antennes locales ;
   - questions-réponses avec réponse acceptée, discussions, « Montre ton projet » ;
   - modération : signalement, rôle modérateur.
4. Les tests de bout en bout (Playwright) du parcours Communautés.

## 🚀 Avancer sans attendre personne
- **Jour 1** : crée l'ossature de `apps/web` et fusionne-la vite. rev22rse22 y branchera le design system.
- Le front tourne **sur des données factices (MSW)** générées depuis ton contrat. Tu n'attends pas l'API.
- Ton backend vit dans son propre package `communautes`. Il ne dépend du socle de @sowibrr que pour l'utilisateur connecté, à simuler en attendant.

## ✅ Étapes
- [ ] Ossature `apps/web` fusionnée (jour 1-2) : routage, mise en page, i18n, MSW, CI
- [ ] Spec 0002 : étude de Discord, Stack Overflow, Dev.to et GitHub Discussions, parcours, cas limites (spam, langue, abus)
- [ ] `contracts/communautes.yaml` validé avec @sowibrr (respect des conventions de l'API)
- [ ] Écrans Communautés avec les composants du design system (@rev22rse22)
- [ ] Backend du module Communautés et tests
- [ ] Tests Playwright, performance vérifiée en 3G simulée

## 🤖 Prompt de démarrage pour ton agent IA
```
Lis AGENTS.md, docs/architecture.md, docs/vocabulaire.md et docs/blocs/communautes.md.
Je suis lead technique front et responsable du bloc « Communautés et échanges ».
1. Crée l'ossature apps/web : Vue 3 + TS + Vite, vue-router, Pinia, vue-i18n,
   MSW, client API généré depuis contracts/*.yaml (openapi-typescript), Vitest, ESLint.
2. Étudie Discord, Stack Overflow, Dev.to et GitHub Discussions, puis propose
   la spec 0002 avec docs/propositions/TEMPLATE.md : ce qui manque à un dev africain
   (3G, langues, mobile money…).
Petites PR, chacune avec ses tests. Termine par « Pistes d'amélioration ».
```

## 🏁 Terminé quand
Un dev peut rejoindre une communauté, poser une question, recevoir une réponse et la marquer comme acceptée, depuis l'appli, sur la vraie API. Les tests Playwright passent et le chef de projet a validé en revue.

