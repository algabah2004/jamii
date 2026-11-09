# Les idées de départ de l'équipe

> Avant Jamii, l'équipe a exploré **quatre pistes**. Jamii en est la synthèse, mais **plusieurs idées fortes ne sont pas encore intégrées**. Ce document les garde vivantes : chaque idée « à décider » peut devenir une [proposition](propositions/README.md).

## Les 4 sources

### 1. Catalogue de cas d'usage (document d'exploration, octobre 2026)
25 cas d'usage et 3 axes prioritaires. Résumé dans [cadev.md](cadev.md).

### 2. Tech Tontine : « Ensemble, on construit le projet de chacun »
- **Le principe** : une tontine de **compétences**, pas d'argent. Un cercle de 4 à 6 devs aux profils complémentaires construit **à tour de rôle le projet de chacun**. Un tour dure 2 semaines, et le membre du tour joue le rôle de chef de produit. L'ordre de passage est tiré au sort.
- **La confiance** : **Systalink est le garant** du cercle, et chaque membre a un **score de confiance public** (tâches livrées à temps, présence, avis des pairs). Quitter le cercle après avoir reçu son projet fait chuter le score.
- **Le Griot**, l'agent IA du cercle, a 4 rôles : **organisateur** (l'idée devient un plan de tâches), **recruteur** (il forme les cercles), **arbitre** (il calcule le score, explicable et contestable) et **conteur** (il résume la journée et écrit l'histoire du projet). *Le Griot propose, le cercle décide.*
- **La livraison** : démo, validation par le bénéficiaire, publication dans une **vitrine** avec le label « Né dans une Tech Tontine Systalink ».
- **Pour Systalink** : la marque sur chaque projet, un engagement sur des mois (et pas un week-end), un vivier de talents.

### 3. Mbokk : « La plateforme où ton agent IA réseaute pour toi »
- **Un jumeau IA** par dev, créé depuis GitHub en 30 secondes. Il présente son dev et repère les missions.
- **Les agents se parlent entre eux** et produisent un **brief chaque matin** (« j'ai échangé avec 14 agents… compatibilité 92 % »).
- **Squad Builder** : on décrit une idée, et l'IA assemble une équipe complémentaire (compétences, langue, horaires).
- **Un agent DevOps** : de l'idée à l'architecture, au squelette, puis au **déploiement sur Systalink**. Les produits Systalink deviennent *les outils que les agents utilisent naturellement*, ce qui en fait un canal de distribution pour eux.
- **Pensé pour l'Afrique** : messages vocaux en français, en wolof ou en anglais, et une interface légère en 3G.
- Données de démo : 50 à 100 profils pour que la plateforme paraisse vivante.

### 4. Présentation CADEV de @boubacarsidibe (la plus concrète)
- **Quatre promesses** :
  - **une preuve plutôt qu'un CV** : le profil est rempli par des projets réellement déployés ;
  - **des revenus locaux** : des missions payées en FCFA, avec Wave d'abord, puis Orange Money ;
  - **de l'entraide en français** : un agent propose une réponse, les pairs la valident ;
  - **une mise en ligne simple**.
- **Trois agents avec garde-fous** :
  - **Déploiement** : il détecte la pile, génère le Dockerfile, déploie et vérifie la santé. Validation humaine avant le premier déploiement.
  - **Support de niveau 1** : il répond en français depuis la doc, crée des tickets et escalade. Il ne promet ni prix ni SLA.
  - **Exploitation** : il surveille CPU, RAM, disque et disponibilité, et explique la cause probable. Il n'exécute que des commandes d'une liste approuvée.
  - Règles communes : un humain valide toute action destructive ou coûteuse, tout est journalisé, et chaque agent a un plafond de coût.
- **Pour Systalink** :
  - un **tableau de bord** (inscriptions, projets déployés, conversion vers le payant, tickets, incidents) ;
  - une **page de statut publique**, *la preuve de fiabilité qui leur manque aujourd'hui* ;
  - un parcours qui mène du dev au projet puis à l'hébergement payant.
- **Le contexte du marché** : une pression sur les prix de l'hébergement d'entrée de gamme, de grands concurrents, et une confiance à prouver (pas de SLA public trouvé). *À formuler avec tact en public : ce sont des **opportunités** que Jamii aide à saisir, pas des critiques.*
- **Une PWA légère** avec brouillons hors ligne, pour les connexions lentes.
- **Les risques et leurs parades** :
  - serveur indisponible → un Docker Compose prêt à migrer ;
  - périmètre trop large → une démo limitée au parcours principal ;
  - paiement réel impossible → sandbox Wave, simulation annoncée ;
  - agent en échec pendant la démo → une vidéo et un mode manuel ;
  - consigne malveillante → une liste de commandes approuvées.
- **L'architecture proposée** : Spring Boot + PostgreSQL/Flyway + service d'agents Python, en Docker Compose sur un **serveur cloud Systalink à Dakar**. **C'est un bon plan B si DataCloud n'accepte pas Java.**

## Les décisions (9 novembre 2026)

On a gardé **ce qui fait gagner** : ce qui répond au vrai problème, ce qui montre à Systalink ce qu'il y gagne, et ce qu'on peut livrer solidement. Le concept final est dans [concept.md](concept.md).

| Idée | Source | Décision | Pourquoi |
|---|---|---|---|
| Profil prouvé (projets réellement en ligne) | Boubacar, Mbokk | ✅ **Pilier 1** | La raison n°1 de s'inscrire : une preuve plutôt qu'un CV |
| Indice de fiabilité explicable | Tontine, Boubacar | ✅ Dans le pilier 1 | Répond au « qui va vraiment livrer ? ». Explicable et contestable, sans note globale |
| Projets d'équipe et continuité | Catalogue, Tontine | ✅ **Pilier 2** | Notre différence : le projet survit au concours |
| Assistant IA : plan, suivi, résumé, alerte | Tontine (Griot), Mbokk | ✅ **Pilier 3** | Fait avancer les équipes. « L'IA propose, l'équipe décide » |
| Mise en ligne sur Systalink | Les trois | ✅ **Pilier 4** | Chaque projet devient un client DataCloud |
| Entraide (l'IA propose, les pairs valident) | Boubacar | ✅ **Pilier 5** | Le thème « échange » de la CADEV |
| Tableau de bord Systalink + page de statut | Boubacar | ✅ **Espace Systalink** | La réponse directe à « qu'est-ce que Systalink y gagne ? » |
| Vitrine « Hébergé sur Systalink » | Tontine | ✅ Dans le pilier 5 | Visibilité pour les devs, marque pour Systalink |
| Données de démo réalistes | Mbokk | ✅ Pour la démo | La plateforme paraît vivante |
| Missions payées en FCFA (Wave) | Boubacar | 🕒 Feuille de route | Fort, mais un paiement réel est risqué à livrer à temps |
| Cercles en tontine | Tontine | 🕒 Feuille de route | Un format d'événement à lancer sur la base des projets d'équipe |
| Agents de support et d'exploitation | Boubacar | 🕒 Feuille de route | Utile à Systalink, mais hors du parcours de la démo |
| Messages vocaux, langues locales | Mbokk, UC14 | 🕒 Feuille de route | Pas indispensable pour un public de développeurs |
| Mode 3G / PWA hors ligne | Boubacar, Mbokk | ❌ Écarté | Un dev qui code et déploie a déjà une connexion correcte |
| Brief quotidien des agents qui se parlent | Mbokk | ❌ Écarté | Les agents ne parlent pas à la place des humains. On garde le résumé de la semaine de l'équipe |
| Thème foot (championnat, buts) | Jamii v1, Mbokk | ❌ Écarté | Il perdait ceux qui ne connaissent pas le foot |
