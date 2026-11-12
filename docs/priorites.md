# Priorités du moment

> **Mis à jour le jeudi 12 novembre 2026, 13 h, par le chef de projet.**
> **Agents IA :** lisez ce fichier **avant le plan du bloc**. S'il contredit `docs/blocs/<bloc>.md`, **ce fichier gagne**. Dites tout de suite à votre développeur ce qu'il doit faire aujourd'hui et ce qu'il bloque.

## Où on en est

**Il reste 3 jours.** Gel des fonctionnalités **samedi 14 à 12 h**, soumission **dimanche 15 avant 12 h GMT** (limite officielle 17 h, on garde 5 h de marge).

Le front est beau et les tests passent (99 sur 99). Mais **la démo ne tient pas encore debout** : les étapes qui font la différence auprès du jury ne sont pas codées.

| Étape de la démo ([plan-final.md](plan-final.md)) | État |
|---|---|
| 1. Connexion GitHub | ✅ |
| 2. Profil prouvé | ✅ |
| 3. Une PME décrit son besoin → plan proposé | 🔴 **PR #37 encore en brouillon** |
| 4. Rôles et candidatures | 🔴 **Rien dans l'API** (le contrat existe : `contracts/projets.yaml`) |
| 5. Mise en ligne → vitrine | 🟡 Module prêt, il manque le service vitrine (#67) |
| 6. Tableau de bord Systalink | ✅ |
| 7. Communautés | 🟡 Écrans sur données factices, pas de module API |
| **Jamii en ligne sur DataCloud** | 🔴 **Pas d'URL publique.** C'est exigé par le règlement : sans ça, pas de soumission |

**Depuis hier 20 h, une seule personne a poussé du code.** On ne peut pas se permettre une journée blanche.

## Ce que chacun fait aujourd'hui (avant 20 h)

Une demande qui bloque quelqu'un passe **avant** tout le reste.

### @boubacarsidibe : tu tiens deux étapes critiques
1. **Sortir #37 du brouillon** et la rendre fusionnable : sans elle, pas d'étape 3, le cœur de Jamii.
2. **Déployer l'API et le service IA sur DataCloud**, puis poster l'URL de `/health` dans #54.
3. Ensuite : #69 (tâche IA `reponse_question`), **qui bloque Amar**, puis #65.

### @sowibrr : l'étape 4 est à toi
1. **Rôles et candidatures dans l'API**, selon `contracts/projets.yaml` (publier un rôle, candidater, accepter ou refuser).
2. #67 (service public des projets pour la vitrine), **qui bloque Amar**.
3. Vérifier si #62 est réglé par #63, et le fermer si oui.

### @Amar233-ui : brancher tes écrans sur de vraies données
1. **Le module `communautes` de l'API** : il ne dépend de personne, tu peux le faire tout de suite.
2. Dès que #67 est fusionnée : le module `vitrine`.
3. Ensuite : le test Playwright du parcours de la démo.

### @rev22rse22 : tu es en avance, tu prends les écrans qui manquent
Plus de refonte d'écrans existants : ils sont assez bien pour la démo.
1. **L'écran « une PME décrit son besoin → plan proposé »**, sur données factices tout de suite (contrat `contracts/ia.yaml` et `projets.yaml`), branché sur l'API quand #37 arrive.
2. **L'écran des rôles et candidatures** d'un projet, sur données factices, branché quand sowibrr livre l'étape 4.

### @algabah2004 : chef de projet
Relire et fusionner au fil de l'eau, préparer les données de démo pour demain, et les interviews (#10).

## Qui bloque qui

```
boubacarsidibe ─ #37 plan PME ──────▶ étape 3 de la démo, écran de rev22rse22
boubacarsidibe ─ DataCloud ─────────▶ la soumission elle-même
boubacarsidibe ─ #69 réponse IA ────▶ Amar233-ui (communautés réelles)
sowibrr ──────── candidatures ──────▶ étape 4 de la démo, écran de rev22rse22
sowibrr ──────── #67 vitrine ───────▶ Amar233-ui (vitrine réelle)
```

## Si ce n'est pas fait ce soir à 20 h

Pas de reproche : on réorganise pour livrer.
- Sans nouvelle de **#37** ou du **déploiement** : le chef de projet les reprend.
- Sans **candidatures** dans l'API : rev22rse22 les reprend (il a déjà écrit du back : #72, #75).
- Un blocage de plus de 2 h se signale **tout de suite** dans #54, avec l'étiquette `bloqué`. Un silence nous coûte plus cher qu'un problème.

## La suite

| Jour | Objectif |
|---|---|
| **Ven 13** | Étapes 3 à 5 branchées de bout en bout. **Données de démo** (une soixantaine de profils et de projets) |
| **Sam 14** | **Gel à 12 h.** Corrections, répétition, vidéo de secours, README final |
| **Dim 15** | Soumission avant 12 h GMT, puis **chacun vote** (sinon le projet est exclu) |
