# IA et déploiement Systalink

> 🧭 **Ce plan est un point de départ, pas une consigne. C'est ton bloc.**
> - **Fixe** : le 🎯 But, le 🏁 « Terminé quand » et les règles communes (`docs/equipe.md`, `AGENTS.md`).
> - **Libre** : tout le reste (étapes, ordre, choix techniques, prompt). **Réécris ce fichier à ta façon** dans une PR, idéalement dans les 48 h.
> - Le suivi au jour le jour se fait dans le ticket GitHub (un commentaire court : fait, prochaine étape, blocage).

**Responsable : @boubacarsidibe** · Ticket de suivi : [#20](https://github.com/algabah2004/jamii/issues/20)

## 🎯 But
Tu rends Jamii **intelligent** et tu le **branches sur le cloud Systalink**. C'est ce qui fera la différence devant le jury. L'IA transforme le besoin d'une PME en cahier des charges et génère les profils. Le déploiement met les projets en ligne sur DataCloud.

## 📦 À livrer
1. **Le rapport de tests Systalink**, d'abord et vite, car il débloque toute l'équipe : DataCloud accepte-t-il Java et Python ? Le MCP et l'API marchent-ils ? Yokk est-il gratuit ? Résultats dans `docs/systalink-services.md`.
2. **Le service IA** `services/ia` (Python + FastAPI) :
   - une interface interne `FournisseurIA` (Yokk ou autre, remplaçable) ;
   - **besoin → cahier des charges** (pour le module Demandes) ;
   - **GitHub → résumé du profil** (pour le module Profils) ;
   - une file de tâches PostgreSQL, un cache des résultats, et un repli si l'IA est en panne.
3. **Le module Déploiement** : contrat `contracts/deploiement.yaml`, et la mise en ligne d'un projet sur DataCloud (automatique via MCP ou API si c'est possible, sinon guidée pas à pas).
4. **Le déploiement de Jamii lui-même** sur DataCloud, avec une préview par PR.

## 🚀 Avancer sans attendre personne
- **Jours 1 à 3** : les tests Systalink. Publie un résultat partiel dès que tu sais si Java est accepté : @sowibrr en a besoin.
- Le service IA est **indépendant** : il a sa propre API (`contracts/ia.yaml`), et tu le testes seul avec des exemples.
- Le contrat de déploiement peut être écrit avant même que le MCP fonctionne.

## 🔌 Branchement Systalink
Voir [integration-systalink.md](../integration-systalink.md).
- Tu es le **référent Systalink** de l'équipe : tu tiens `docs/integration-systalink.md` à jour (statut de chaque service).
- Interfaces `FournisseurIA` (Yokk), `Deployeur` (MCP/API DataCloud), `GenerateurSite` (Orion) et `EvenementsDeploiement` (webhooks), chacune avec un adaptateur simulé.

## ✅ Étapes
- [ ] Compte Systalink créé, hello world **Spring Boot** et **FastAPI** déployés sur DataCloud, résultat publié (jour 1-3)
- [ ] Préview par PR vérifiée, base PostgreSQL gérée testée (sauvegardes ?)
- [ ] Clé MCP et API Systalink testées : ce qui est faisable ou non
- [ ] Yokk testé : offre gratuite, API, quotas
- [ ] `services/ia` : squelette, `contracts/ia.yaml`, interface FournisseurIA, job CI
- [ ] Besoin → cahier des charges, et GitHub → résumé du profil, avec tests sur des exemples réels
- [ ] Module Déploiement et déploiement de Jamii sur DataCloud

## 🤖 Prompt de démarrage pour ton agent IA
```
Lis AGENTS.md, docs/architecture.md, docs/systalink-services.md et docs/blocs/ia-systalink.md.
Je suis responsable du bloc « IA et déploiement Systalink ».
1. Aide-moi à tester DataCloud : prépare 2 hello world minimaux (Spring Boot 3 /
   Java 21, et FastAPI) prêts à déployer depuis GitHub, et la liste précise de ce
   qu'il faut noter (étapes, temps, limites, préview PR).
2. Puis le squelette services/ia : FastAPI, une interface FournisseurIA remplaçable,
   une file de tâches PostgreSQL (SELECT … FOR UPDATE SKIP LOCKED), pytest.
N'invente aucune API Systalink : ce qui n'est pas vérifié est marqué « à tester ».
```

## 🏁 Terminé quand
Jamii tourne sur DataCloud. Une demande de PME produit un cahier des charges, et un projet peut être mis en ligne sur Systalink depuis Jamii. Le chef de projet a validé en revue.

