# Assistant de projet IA + mise en ligne Systalink

> 🧭 **Ce plan est un point de départ, pas une consigne. C'est ton bloc.**
> - **Fixe** : le 🎯 But, le 🏁 « Terminé quand » et les règles communes (`docs/equipe.md`, `AGENTS.md`).
> - **Libre** : tout le reste (étapes, ordre, choix techniques, prompt). **Réécris ce fichier à ta façon** dans une PR, idéalement dans les 48 h.
> - Le suivi au jour le jour se fait dans le ticket GitHub (un commentaire court : fait, prochaine étape, blocage).

**Responsable : @boubacarsidibe** · Ticket de suivi : [#20](https://github.com/algabah2004/jamii/issues/20)

## 🎯 But
Tu rends Jamii **intelligent** et tu le **branches sur Systalink** : l'**assistant de projet** fait avancer les équipes, et la **mise en ligne sur DataCloud** transforme leurs projets en produits, et en clients Systalink. C'est ce qui fera la différence devant le jury. Voir les piliers 3 et 4 de [concept.md](../concept.md).

## 📦 À livrer
1. **Le rapport de tests Systalink**, d'abord et vite, car il débloque l'équipe : DataCloud accepte-t-il Java et Python ? Le MCP et l'API ? Yokk est-il gratuit ? (`docs/systalink-services.md`)
2. **L'assistant de projet** `services/ia` (Python + FastAPI, `contracts/ia.yaml`) :
   - idée ou demande de PME → **plan** (cahier des charges, jalons, tâches réparties selon les compétences) ;
   - **suggestion de coéquipiers** complémentaires ;
   - **résumé de la semaine** et **alerte** quand un projet décroche ;
   - **réponse proposée** aux questions des communautés ;
   - résumé du profil GitHub (pour #19).
   Le tout derrière une interface `FournisseurIA` (Yokk ou autre), avec une file de tâches PostgreSQL, un cache et un repli si l'IA tombe. **L'IA propose, l'humain décide.**
3. **La mise en ligne sur DataCloud** (`contracts/deploiement.yaml`) : automatique via l'API ou le MCP si c'est possible, sinon un parcours guidé. Un projet en ligne est **vérifié**.
4. **Jamii lui-même déployé sur DataCloud**, avec une préview par PR (plan B : serveur cloud Systalink + Docker Compose).

## 🚀 Avancer sans attendre personne
- **Jours 1 à 3** : les tests Systalink. Publie un résultat partiel dès que tu sais si Java est accepté : @sowibrr en a besoin.
- Le service IA est **indépendant** : sa propre API, testable seul sur des exemples réels.
- Le contrat de mise en ligne peut être écrit avant que le MCP fonctionne.

## 🔌 Branchement Systalink
Voir [integration-systalink.md](../integration-systalink.md).
- Tu es le **référent Systalink** : tu tiens `docs/integration-systalink.md` à jour.
- Interfaces `FournisseurIA` (Yokk), `Deployeur` (API/MCP DataCloud) et `EvenementsDeploiement` (webhooks), chacune avec un adaptateur simulé.

## ✅ Étapes
- [ ] Hello world Spring Boot et FastAPI sur DataCloud, résultat publié (jours 1-3)
- [ ] Préview par PR, PostgreSQL géré, MCP/API et Yokk testés
- [ ] `contracts/ia.yaml`, squelette `services/ia`, FournisseurIA, CI
- [ ] Plan de projet depuis une idée ou une demande de PME, testé sur des exemples réels
- [ ] Suggestion de coéquipiers, résumé de la semaine, alerte de décrochage
- [ ] Réponse proposée aux questions, résumé du profil GitHub
- [ ] Mise en ligne d'un projet sur DataCloud depuis Jamii, et déploiement de Jamii lui-même

## 🤖 Exemple de prompt pour ton agent IA
```
Lis AGENTS.md, docs/concept.md, docs/systalink-services.md et docs/blocs/ia-systalink.md.
Je suis responsable du bloc « Assistant de projet IA + mise en ligne Systalink ».
1. Prépare 2 hello world minimaux (Spring Boot 3 / Java 21, et FastAPI) à déployer
   sur DataCloud depuis GitHub, et la liste de ce qu'il faut noter.
2. Puis le squelette services/ia : FastAPI, une interface FournisseurIA remplaçable,
   une file de tâches PostgreSQL, pytest. Le premier cas : demande de PME → plan.
N'invente aucune API Systalink : ce qui n'est pas vérifié est marqué « à tester ».
```

## 🏁 Terminé quand
Jamii tourne sur DataCloud. Une demande de PME devient un plan d'équipe proposé par l'IA, l'assistant résume la semaine et alerte en cas de décrochage, et un projet d'équipe se met en ligne sur Systalink depuis Jamii. Le chef de projet a validé en revue.
