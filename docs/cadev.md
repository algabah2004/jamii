# La CADEV 2026 : le contexte à connaître

> **À lire en premier**, humains comme agents IA. Pourquoi ce projet existe, pour qui, et sur quoi on sera jugés.

## L'organisateur : Systalink

- Entreprise technologique de **Dakar (Sénégal)**. Elle fait du **cloud souverain** (hébergé localement, dans ses datacenters Stellarix à Dakar), du DevOps et des outils collaboratifs pour l'Afrique de l'Ouest.
- Ses produits :
  - **DataCloud** : hébergement d'applications depuis Git, bases de données, Kubernetes, GPU, stockage objet ;
  - **Orion** : sites générés par IA ;
  - **Yokk** : constructeur d'agents IA ;
  - **SystaONE / SystaDrive** : fichiers, e-mail, visio, formulaires et signature ;
  - **SystaIQ** : IA pour les entreprises ;
  - paiement **Mobile Money** (Wave, Orange Money, via Paxity).
- Ce qu'elle met en avant : **la souveraineté numérique**, l'accessibilité des infrastructures, la valorisation des talents locaux et un écosystème tech africain.
- Site : https://systalink.com · Détail de ce qui est gratuit ou testé : [systalink-services.md](systalink-services.md)

## La compétition

| | |
|---|---|
| **Nom** | CADEV, la Coupe d'Afrique des Développeurs (2026) |
| **Thème** | « Construire la plateforme d'échange africaine, pour les devs, par les devs » : une plateforme d'échange et de collaboration pour les talents tech du continent |
| **Format** | 100 % en ligne. On construit, on soumet, puis le **public et le jury** désignent le meilleur projet |
| **Prix** | **5 000 000 FCFA**, et **la solution gagnante intègre le portefeuille technologique de Systalink** |
| **Avantages** | -40 % sur DataCloud pour les équipes, -70 % pour tous les participants en 2026 |
| **Page officielle** | https://systalink.com/fr/coupe-afrique-des-developpeurs-cadev · contact : cadev@systalink.com |
| **Règlement** | Lu dans l'espace CADEV de la console DataCloud le 10/11/2026 : voir ci-dessous |

### Ce que ça implique pour nous
- **Le jury, ce sont des ingénieurs cloud.** Ils jugeront la solidité : sécurité, architecture, tests, exploitation. Pas seulement la démo.
- **Le gagnant devient un produit Systalink.** Il faut donc un produit **crédible à exploiter**, et pas un prototype jetable. Il doit s'intégrer à leur écosystème : DataCloud, Yokk, Orion…
- **Il y a aussi un vote du public** : il faut que des développeurs aient envie de l'utiliser et de le partager.
- **Notre point fort : Jamii est conçu pour se brancher directement sur l'écosystème Systalink** (voir [integration-systalink.md](integration-systalink.md)).
- Notre hypothèse, cohérente avec leur communication mais pas officielle : Systalink veut **faire venir des développeurs sur son cloud**. Chaque fonctionnalité qui fait découvrir et utiliser DataCloud est un plus.

## Le règlement (lu le 10 novembre 2026)

> Source : « Conditions générales » de l'espace CADEV dans la console DataCloud (relevé par @boubacarsidibe, PR #36). **Un manquement rend le projet irrecevable.**

### Le calendrier
| Date | Étape |
|---|---|
| **15 novembre 2026, 17 h 00 GMT** | **Fin des soumissions** |
| 15 et 16 novembre | **Vote des pairs : obligatoire pour chaque membre de l'équipe, sinon le projet est exclu** |
| 17 au 20 novembre | Vote du public |
| 21 et 22 novembre | Délibération du jury |
| 23 novembre | Résultats |

### Les conditions
- **Équipe** de 2 à 5 membres.
- **Livrables** : un dépôt Git et une **démo en ligne**.
- **Article 6, hébergement** : le projet est déployé avec **au moins un produit DataCloud payant**, acheté avec la remise de 40 % de l'équipe. **Le code promo ne doit jamais apparaître dans le dépôt** (ni dans une issue, une PR ou un commit).
- **Article 6, licences** : aucun composant sous licence **GPL, AGPL ou LGPL**. Licences permissives uniquement (MIT, Apache 2.0, BSD…). La CI le vérifie (job « Licences autorisées »), et son résumé donne la liste complète des composants et de leurs licences pour la soumission. Jamii est lui-même sous licence Apache 2.0 (`LICENSE`).
- **L'usage de l'IA est libre mais doit être déclaré** à la soumission : les outils utilisés et les parties concernées. Voir « Ce qu'on déclare » ci-dessous.

### La grille du jury
| Critère | Poids | Ce qui le nourrit chez nous |
|---|---|---|
| Qualité technique | **30 %** | Tests, CI, sécurité, contrats OpenAPI, architecture modulaire, déploiement sur DataCloud |
| Pertinence | **25 %** | Le problème de fond (continuité, équipes, besoins des PME) et la valeur pour Systalink |
| Expérience utilisateur | **20 %** | Un parcours de démo fluide, des mots simples, l'interface en ligne |
| Innovation | **15 %** | Le profil prouvé, l'assistant de projet, les moments Systalink mesurés |
| Vote du public | **10 %** | Que chaque membre partage le projet du 17 au 20 novembre |

### Ce qu'on déclare (usage de l'IA)
- **Pour construire Jamii** : des agents de code (Claude Code, et ceux de chaque membre) ont aidé à écrire la documentation, les contrats et le code. Tout a été relu et validé en revue par un humain (une PR, une approbation).
- **Dans Jamii** : l'assistant de projet (`services/ia`) génère des plans, des suggestions et des réponses proposées, via l'interface `FournisseurIA` (Yokk ou un fournisseur simulé). L'IA propose, l'humain décide.
- Chaque membre complète cette liste avec ses propres outils avant le 15 novembre.
  - **@rev22rse22 (bloc #19 : design system, profil, cercles, réseau, orientation)** : Claude Code (Claude Opus 5.5) pour le code du front et du module `reseau` de l'API, les tests, les contrats `reseau.yaml` et la revue visuelle des écrans (captures dans le navigateur, en clair, en sombre et sur mobile). Chaque PR le mentionne ; chaque écran a été relu à l'œil avant fusion.

## Le problème de fond

> **Comment permettre à des développeurs africains qui ne se connaissent pas de former des équipes, de créer des solutions utiles et de transformer leurs prototypes en produits réels ?**

Les difficultés constatées :
1. Il est difficile de constituer des équipes **aux compétences complémentaires**.
2. Les projets sont **abandonnés après les hackathons**.
3. Il manque de contacts avec des **utilisateurs et des organisations ayant de vrais besoins**.
4. Le **déploiement, la maintenance et le financement** sont difficiles.
5. Les contributions techniques locales sont **peu visibles**.
6. Les communautés et les dispositifs d'accompagnement sont **fragmentés**.

**Ce qu'il ne faut pas faire :** reproduire un réseau social ou une plateforme de hackathons qui existe déjà. **La bonne direction** : faciliter le passage **d'une idée à une équipe, d'une équipe à un prototype, puis d'un prototype à une solution réellement utilisée**.

## Le catalogue des cas d'usage

C'est notre document d'exploration (« UC » pour *use case*). Les cas en **gras** sont ceux jugés les plus intéressants (5/5).

| Catégorie | Cas d'usage |
|---|---|
| **A. Équipes** | UC01 trouver des coéquipiers complémentaires · UC02 remplacer un membre qui part · UC03 trouver des devs proches de chez soi · UC04 relier des projets similaires · UC05 trouver un associé non technique |
| **B. Après le hackathon** | **UC06 relancer un projet abandonné** · UC07 planifier les 90 jours après un concours · UC08 détecter un projet qui devient inactif · UC09 vérifier qu'on est prêt à lancer · UC10 transmettre un projet à une autre équipe |
| **C. Besoins locaux** | **UC11 une PME soumet un vrai problème** · UC12 valider un besoin avant de coder · **UC13 tester avec des utilisateurs locaux** · UC14 décrire un besoin en langue locale (wolof…) · **UC15 le bénéficiaire valide la solution** |
| **D. Lancement** | **UC16 déployer sur un cloud africain** · **UC17 trouver ses premiers beta-testeurs** · UC18 transformer les retours en tâches · UC19 constituer un dossier pour un incubateur · UC20 estimer les coûts du cloud |
| **E. Écosystème** | UC21 contribuer à l'open source africain · UC22 obtenir un mentorat ponctuel · UC23 valoriser ses contributions · UC24 organiser une collaboration panafricaine · **UC25 suivre les projets après le concours** |

Trois axes prioritaires :
- **la continuité après les concours** (UC06, UC07, UC25) ;
- **la connexion aux besoins locaux** (UC11, UC13, UC14, UC15) ;
- **le passage à une application exploitable** (UC16, UC17, UC18, UC20).

## Comment Jamii y répond

| Problème | Pilier de Jamii | Cas d'usage |
|---|---|---|
| Former des équipes | Projets d'équipe (#17) + suggestions de l'assistant (#20) | UC01, UC02, UC03, UC05 |
| Les projets meurent après le concours | Continuité des projets (#17) + résumé et alerte de l'assistant (#20) | **UC06, UC07, UC08, UC10, UC25** |
| Vrais besoins locaux | Demandes de PME → projets (#17) + plan par l'IA (#20) | UC11, UC12 |
| Échanger, s'entraider | Communautés et entraide (#18) | UC04, UC21, UC22, UC24 |
| Mettre en ligne | Mise en ligne sur DataCloud (#20) | UC16 |
| Visibilité, confiance | Profil prouvé (#19) + vitrine (#18) | UC23, UC17 |
| Ce que Systalink y gagne | Tableau de bord et page de statut (#18, #17) | UC25 |

