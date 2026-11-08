# Services Systalink : ce qu'on peut utiliser gratuitement

*Vérification du 8 novembre 2026 : lecture des pages publiques et tests des points d'accès sans compte. Rien n'a encore été testé connecté. À compléter une fois les comptes créés (voir la fin du document).*

## Résumé

| Service | Gratuit ? | Accès développeur | Statut pour nous |
|---|---|---|---|
| **DataCloud** (hébergement d'applis depuis Git) | **Oui, quota inclus** : 750 h d'instance, 500 min de build, 100 Go de bande passante par mois | Préviews par pull request, variables d'environnement | ✅ **Notre hébergement principal**, à tester en premier |
| **Orion** (site généré par IA) | **Oui, offre Free à vie** : 10 crédits IA par jour, 1 site, sous-domaine systalink.com | Aucune API. Git dès l'offre Starter (5 000 FCFA/mois), serveurs MCP dès l'offre Advanced (8 400 FCFA/mois) | ✅ Utilisable pour la démo « besoin simple d'une PME » |
| **Yokk** (constructeur d'agents IA) | Inconnu : le site s'affiche en JavaScript | `https://api.yokk.ai` répond (« yokk.ai Agent Builder API »), mais sans doc publique | ⚠️ Créer un compte pour vérifier |
| **Serveur MCP** (piloter le cloud depuis une IA) | Non précisé | Annoncé à `mcp.systalink.com` avec une clé d'API, mais **le domaine ne répond pas** (DNS introuvable). L'activation passe par `platform.systalink.com/mcp` | ⚠️ À tester connecté. Peut-être une autre URL une fois la clé créée |
| **API Systalink** | — | `api.systalink.com` existe mais renvoie « Authentification requise » (401), sans doc publique | ⚠️ À tester avec un compte |
| **OAuth « Se connecter avec Systalink »** | — | **Introuvable** | ❌ On utilise la connexion GitHub à la place |
| **Webhooks** de déploiement | Non | Offre **Professionnel** minimum | ❌ À simuler (interroger régulièrement l'état du déploiement) |
| **Hébergement web classique** | Non | Starter à 2 525 FCFA/mois | ❌ Inutile, DataCloud suffit |
| **SystaDrive / SystaTalk / SystaForms / SystaMail** (suite SystaONE) | **Essai de 14 jours** seulement. Ensuite 2 000 à 12 000 FCFA par utilisateur et par mois | API et webhooks seulement en offre Business | ⚠️ Seulement pour la démo, pendant l'essai |
| **Paxity** (paiement Mobile Money) | Inconnu | Non documenté publiquement | ⚠️ On simule le paiement dans la démo |

## Avantages CADEV

Source : [page CADEV](https://systalink.com/fr/coupe-afrique-des-developpeurs-cadev)

- **-40 %** sur les produits DataCloud pour les équipes inscrites au challenge.
- **-70 %** sur DataCloud pour tous les participants pendant 2026.
- Compétition **100 % en ligne**. Le classement est fait par le **vote du public et le jury**.
- Inscription : https://platform.systalink.com/sign-up?from=cadev-landing. L'étape 1, « Construis ta team », consiste à créer un compte et inviter ses membres.
- Contact : cadev@systalink.com, WhatsApp +221 76 660 40 41.
- **Non publiés à ce jour** : dates, taille des équipes, critères du jury, livrables. → *Tâche : écrire à cadev@systalink.com pour les obtenir.*

## Conséquences pour notre architecture

1. **On héberge sur DataCloud.** C'est gratuit dans la limite du quota, et ça montre au jury qu'on utilise le cloud Systalink.
2. **On se connecte avec GitHub (OAuth GitHub)**, pas avec Systalink. Le compte Systalink se relie ensuite, en option.
3. **Le « déploiement en un clic »** utilise la clé MCP ou API de l'utilisateur, si on réussit à la faire marcher. Sinon, on guide l'utilisateur pas à pas vers DataCloud.
4. **L'IA** passe par Yokk si son offre gratuite le permet, et sinon par un autre fournisseur, derrière une interface interne qu'on pourra remplacer.
5. **Le paiement et les webhooks sont simulés** dans la démo, et clairement présentés comme tels.

## Tests à faire une fois connecté

Chaque test a son issue GitHub.

- [ ] Créer un compte sur platform.systalink.com (lien CADEV).
- [ ] Déployer une appli « hello world » depuis GitHub sur DataCloud, puis noter les étapes et le temps.
- [ ] Vérifier la préview automatique sur une pull request.
- [ ] Générer une clé MCP sur `platform.systalink.com/mcp` et la brancher à un client MCP.
- [ ] Appeler `api.systalink.com` avec cette clé : quelles routes sont disponibles ?
- [ ] Créer un agent sur yokk.ai : y a-t-il une offre gratuite, une clé d'API, un widget ?
- [ ] Créer un site Orion Free à partir d'une description de PME.
- [ ] Ouvrir un essai SystaONE de 14 jours **seulement quand la démo approche**.
