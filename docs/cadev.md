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
| **Inconnus** (ticket #1) | Dates exactes, taille des équipes, livrables, critères et pondération du jury |

### Ce que ça implique pour nous
- **Le jury, ce sont des ingénieurs cloud.** Ils jugeront la solidité : sécurité, architecture, tests, exploitation. Pas seulement la démo.
- **Le gagnant devient un produit Systalink.** Il faut donc un produit **crédible à exploiter**, et pas un prototype jetable. Il doit s'intégrer à leur écosystème : DataCloud, Yokk, Orion…
- **Il y a aussi un vote du public** : il faut que des développeurs aient envie de l'utiliser et de le partager.
- Notre hypothèse, cohérente avec leur communication mais pas officielle : Systalink veut **faire venir des développeurs sur son cloud**. Chaque fonctionnalité qui fait découvrir et utiliser DataCloud est un plus.

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

| Problème | Bloc de Jamii | Cas d'usage |
|---|---|---|
| Former des équipes | Profils et recherche de coéquipiers (#19) | UC01, UC02, UC03 |
| Échanger, s'entraider | Communautés et échanges (#18) | UC04, UC21, UC22, UC24 |
| Vrais besoins locaux | Demandes des PME + IA (#17, #20) | UC11, UC14 |
| Mettre en ligne | Déploiement Systalink (#20) | UC16, UC20 |
| Solidité de l'ensemble | Socle API (#17) | Ce que le jury vérifie |

Ce qui est à renforcer, car c'est peu couvert aujourd'hui : **la continuité après les concours** (UC06, UC07, UC25) et **les beta-testeurs** (UC17). Ce sont de bons sujets pour une proposition dans [propositions/](propositions/README.md).
