# Services Systalink : ce qu'on peut utiliser gratuitement

*Vérification du 8 novembre 2026 : lecture des pages publiques et tests des points d'accès sans compte.*
*Mise à jour du 10 novembre 2026 : premiers tests **connecté** à la console DataCloud (voir « Tests du 10 novembre 2026 »). Ce qui n'y figure pas reste à tester.*

## Résumé

| Service | Gratuit ? | Accès développeur | Statut pour nous |
|---|---|---|---|
| **DataCloud** (hébergement d'applis depuis Git) | ⚠️ **À confirmer** : une offre « Free » existe (0 FCFA/mois affiché), mais sa commande ouvre une page de paiement de 800 FCFA (voir plus bas) | **Java et Python acceptés en natif** (vérifié), Docker, monorepo, variables d'environnement, réseau privé | ✅ **Notre hébergement principal**. Java et Python : vérifié le 10/11 |
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
- Le **règlement officiel** est dans l'espace CADEV de la console (« Conditions générales »), lu le 10/11/2026 :
  - soumission jusqu'au **15 novembre 2026 à 17 h 00 GMT** ; vote des pairs les 15 et 16 (obligatoire pour **chaque** membre, sinon le projet est exclu) ; vote du public du 17 au 20 ; jury les 21 et 22 ; résultats le 23 ;
  - équipes de 2 à 5 membres ; livrables : un dépôt git et une démo en ligne ;
  - **article 6** : le projet doit être déployé avec **au moins un produit Datacloud payant**, acheté avec la remise de 40 % (code promo de l'équipe, visible dans l'espace CADEV, **à ne pas publier ici**). Sinon le projet n'est pas recevable ;
  - **article 6** : les composants sous licence **GPL, AGPL ou LGPL** rendent le projet irrecevable. Licences permissives uniquement (MIT, Apache 2.0, BSD). Tous les composants et leurs licences sont déclarés à la soumission ;
  - l'usage de l'IA est libre mais **doit être déclaré** à la soumission (outils, parties concernées) ;
  - grille du jury : qualité technique 30 %, pertinence 25 %, expérience utilisateur 20 %, innovation 15 %, vote du public 10 %.

## Tests du 10 novembre 2026 (connecté à la console)

Test fait par @boubacarsidibe avec deux dépôts hello world (Spring Boot 4 / Java 21 et FastAPI), jusqu'à l'écran de paiement. **Aucun service n'a encore été réellement déployé** : durée de build, démarrage, URL publique et variables fournies restent à mesurer.

| Question | Réponse | Statut |
|---|---|---|
| Java est-il accepté ? | **Oui.** Environnements proposés : Docker, Node, Python, Go, Ruby, Elixir, Rust, PHP, **Java** | ✅ vérifié (liste vue dans le formulaire) |
| Python est-il accepté ? | **Oui** (même liste) | ✅ vérifié |
| D'où vient le code ? | Fournisseur Git (GitHub, GitLab, Bitbucket), dépôt Git public, image existante, ou auto-hébergé | ✅ vérifié |
| Un monorepo est-il possible ? | Oui : champ « Répertoire racine ». Les commandes s'exécutent depuis ce dossier, et seuls ses changements déclenchent un déploiement | ✅ vérifié (formulaire) |
| Où est-ce hébergé ? | Région **Dakar** | ✅ vérifié |
| Les services se parlent-ils en privé ? | Le formulaire annonce un **réseau privé** entre les services d'une même région | ⚠️ annoncé, pas testé |
| Que peut-on régler ? | Commande de build, commande de démarrage, variables d'environnement (saisie ou import d'un `.env`), fichiers secrets (`/etc/secrets/<fichier>`), chemin de contrôle de santé, commande de pré-déploiement, déploiement automatique à chaque commit avec filtres de chemins | ✅ vérifié (formulaire) |
| Que donne l'offre Free ? | 0,1 vCPU et 0,5 Go. **Mise en veille après inactivité**. Pas d'accès SSH, de mise à l'échelle, de tâches ponctuelles ni de disque persistant | ✅ vérifié (texte de l'offre) |
| L'offre Free est-elle gratuite ? | Affichée à 0 FCFA/mois, mais « Déployer » ouvre une page Facturation puis Paiement : 667 FCFA HT + TVA 20 % = **800 FCFA**. Nature du montant inconnue | ❓ à demander à cadev@systalink.com |
| Combien coûtent les offres payantes ? | Starter (0,5 Go, 0,5 cœur) 4 667 FCFA ; Standard (2 Go, 1 cœur) 16 667 FCFA ; Pro (4 Go, 2 cœurs) 56 667 FCFA. Disque persistant : 2 500 FCFA par Go et par mois | ✅ vérifié (prix affichés, avant remise) |
| Quel accès GitHub est demandé ? | La connexion GitHub liste **tous** les dépôts du compte, privés compris | ✅ vérifié |

### Ce que ça change pour nous

- **On garde Spring Boot** pour l'API : pas besoin du plan NestJS ni d'un Dockerfile.
- **Le dépôt Jamii peut être déployé tel quel** : un service par dossier (`apps/api`, `services/ia`, `apps/web`) grâce au répertoire racine.
- **0,5 Go de RAM est serré pour Spring Boot** : à mesurer. Régler `-XX:MaxRAMPercentage` et prévoir une offre au-dessus de Free pour l'API.
- **La mise en veille de l'offre Free est incompatible avec une démo jugée** (premier accès lent) et avec la file de tâches du service IA, qui doit tourner en continu.
- **Le service IA n'a pas besoin d'être exposé sur Internet** si le réseau privé fonctionne comme annoncé. À tester.
- **Les migrations** peuvent passer par la commande de pré-déploiement.

## Conséquences pour notre architecture

1. **On héberge sur DataCloud.** C'est gratuit dans la limite du quota, et ça montre au jury qu'on utilise le cloud Systalink.
2. **On se connecte avec GitHub (OAuth GitHub)**, pas avec Systalink. Le compte Systalink se relie ensuite, en option.
3. **Le « déploiement en un clic »** utilise la clé MCP ou API de l'utilisateur, si on réussit à la faire marcher. Sinon, on guide l'utilisateur pas à pas vers DataCloud.
4. **L'IA** passe par Yokk si son offre gratuite le permet, et sinon par un autre fournisseur, derrière une interface interne qu'on pourra remplacer.
5. **Le paiement et les webhooks sont simulés** dans la démo, et clairement présentés comme tels.

## Tests à faire une fois connecté

Chaque test a son issue GitHub.

- [x] Créer un compte sur platform.systalink.com (lien CADEV). *(10/11)*
- [x] Vérifier que Java et Python sont acceptés. *(10/11 : oui, les deux)*
- [ ] Déployer une appli « hello world » depuis GitHub sur DataCloud, puis noter les étapes et le temps. *(10/11 : formulaire rempli, arrêté à la page de paiement)*
- [ ] Tester PostgreSQL géré : URL unique ou variables séparées, SSL, sauvegardes.
- [ ] Vérifier la préview automatique sur une pull request.
- [ ] Générer une clé MCP sur `platform.systalink.com/mcp` et la brancher à un client MCP.
- [ ] Appeler `api.systalink.com` avec cette clé : quelles routes sont disponibles ?
- [ ] Créer un agent sur yokk.ai : y a-t-il une offre gratuite, une clé d'API, un widget ?
- [ ] Créer un site Orion Free à partir d'une description de PME.
- [ ] Ouvrir un essai SystaONE de 14 jours **seulement quand la démo approche**.
