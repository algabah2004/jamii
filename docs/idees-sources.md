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

## Ce qui est déjà dans Jamii

| Idée | Source | Où |
|---|---|---|
| Profil généré depuis GitHub | Mbokk, Boubacar | Bloc #19 |
| Entraide et questions-réponses | Boubacar, Jamii | Bloc #18 |
| Demandes des PME, cahier des charges par IA | Jamii | Blocs #17 et #20 |
| Déploiement sur Systalink | Les trois | Bloc #20 |
| Pensé pour l'Afrique (3G, langues) | Mbokk, Boubacar | AGENTS.md, bloc #18 |
| Agents avec garde-fous, humain dans la boucle | Tontine, Boubacar | Règles de concept.md |

## Les idées fortes encore à décider

| # | Idée | Source | Pourquoi c'est fort | Bloc concerné |
|---|---|---|---|---|
| A | **Score de confiance / réputation vérifiable** | Tontine, Boubacar | Répond au « qui va vraiment livrer ? ». C'est une preuve concrète pour les recruteurs | #19 Profils |
| B | **Cercles à tour de rôle (tontine)** comme format de projet | Tontine | **Couvre la continuité après les concours** (UC06, UC07, UC25), aujourd'hui le point le plus faible. Très africain, très mémorable | À attribuer |
| C | **Tableau de bord Systalink + page de statut publique** | Boubacar | Donne à Systalink des chiffres et une preuve de fiabilité. Énorme pour le jury | #20 ou #17 |
| D | **Agents de support et d'exploitation** | Boubacar | Réduit la charge de Systalink, et montre leur cloud sous un bon jour | #20 (phase 2) |
| E | **Missions payées en FCFA (Wave, sandbox)** | Boubacar | Des revenus locaux, le frein n°1 des freelances africains | Phase 2 |
| F | **Brief quotidien de l'IA** (opportunités, coéquipiers) | Mbokk | Le moment « wow », qui donne envie de revenir chaque jour | #19 ou #20 |
| G | **Vitrine des projets + label « Né sur Jamii · hébergé par Systalink »** | Tontine | De la visibilité pour les devs, et la marque Systalink sur chaque réussite | #18 ou #19 |
| H | **PWA hors ligne** (brouillons) | Boubacar | Les connexions lentes sont la réalité de nos utilisateurs | #18 ossature |
| I | **Messages vocaux** (wolof, français) → texte | Mbokk, cas UC14 | Inclut les non-techniciens et les langues locales | #20 |
| J | **50 à 100 profils de démo** | Mbokk | La plateforme paraît vivante dès la démo | Démo |

**Écarté volontairement** : des agents qui parlent ou s'engagent **à la place** des humains (Mbokk, piliers 1 et 2), pour éviter les faux profils et la perte de contrôle. On garde « l'IA propose, l'humain décide ». Le thème foot (Championnat) a lui aussi été abandonné.
