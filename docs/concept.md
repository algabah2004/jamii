# Jamii · Le concept final

> **Le lieu où les développeurs africains se rencontrent, échangent et construisent ensemble.**
>
> Et de l'échange naissent des projets : **trouve ton équipe, construis un vrai projet, mets-le en ligne sur Systalink, et prouve-le.**

*Jamii veut dire « communauté » en swahili.* Ce document est **la référence produit**. Les choix y sont tranchés : ce qui n'y figure pas, on ne le construit pas pour la CADEV.

**Jamii est d'abord une plateforme d'échange** (c'est le thème de la CADEV) : on s'y rencontre, on discute, on s'entraide, on y trouve des coéquipiers et des PME qui ont de vrais besoins. Ce qui nous distingue d'un forum ou d'un annuaire, c'est que **ces échanges débouchent sur des projets qui vivent et sont mis en ligne**.

## Le problème qu'on résout

Les développeurs africains ont des idées, mais **les projets meurent** :
- ils n'ont pas l'équipe ;
- l'équipe se défait après le hackathon ;
- personne ne sait qui va vraiment livrer ;
- et la mise en ligne bloque.

(Détails et cas d'usage : [cadev.md](cadev.md).)

## Deux principes qui ne se négocient pas

### Ouvert à tous, sans contrainte
Jamii est **un lieu de rassemblement des communautés tech africaines**. N'importe qui doit pouvoir l'utiliser :
- **aucun compte Systalink n'est exigé**, ni pour s'inscrire, ni pour participer ;
- les **développeurs** se connectent avec GitHub, et les **non-développeurs** (PME, associations, designers, étudiants) avec leur **e-mail** (lien de connexion, sans mot de passe) ;
- un projet peut être hébergé **où l'équipe veut** : il apparaît sur le profil et dans la vitrine. Systalink est le choix le plus simple, jamais une obligation ;
- tout ce qui est essentiel est **gratuit**.

### Systalink, le bon outil au bon moment
Jamii est aussi **le meilleur canal de clients pour Systalink**. Pas par de la publicité : en proposant **le bon service Systalink au moment exact où l'utilisateur en a besoin**, en un clic, avec les avantages CADEV.

| Le moment | Ce que Jamii propose | Service Systalink |
|---|---|---|
| L'équipe veut mettre son projet en ligne | « Déployer sur DataCloud » en un clic, avec la réduction CADEV | **DataCloud** |
| Le projet a besoin d'une base de données ou de fichiers | Une base PostgreSQL ou un stockage, déjà configurés | **DataCloud BDD, stockage objet** |
| Une PME a un besoin simple (un site vitrine) | « Un site prêt en quelques minutes » avec Orion, plutôt qu'une équipe | **Orion** |
| L'équipe veut créer un agent IA (chatbot, assistant) | Le construire avec Yokk | **Yokk** |
| L'équipe se réunit | Une salle de visio en un clic | **SystaTalk** |
| Le projet devient sérieux | Un nom de domaine (.sn, .ci…) et des e-mails pro | **Domaines, SystaMail** |
| Une PME doit valider ou signer la livraison | Un formulaire de validation et de signature | **SystaForms** |
| Le projet a besoin de GPU ou de Kubernetes | Les ressources adaptées, proposées par l'assistant | **GPU, Kubernetes** |

Dans la démo, on en montre **2 ou 3** (DataCloud, Orion et Yokk). Les autres sont prévus dans l'architecture (un adaptateur chacun) et s'activent dès que le service est prêt : **aucun n'est exclu**.

## Pourquoi on gagne : 3 arguments pour le jury

1. **On s'attaque au vrai problème, la continuité.** Les autres équipes feront des forums et des annuaires. Nous, on emmène un projet **de l'idée jusqu'à la mise en ligne**, et on le garde vivant après.
2. **Chaque projet réussi devient un client de DataCloud, et Systalink le voit.** Jamii est un canal d'acquisition pour leur cloud, avec un tableau de bord qui le prouve en chiffres.
3. **Une démo qui marche de bout en bout**, sur leur infrastructure, avec une architecture que leurs ingénieurs respecteront.

Et pour **le vote du public** : un dev repart avec un **profil prouvé**, c'est-à-dire des projets réellement en ligne et une fiabilité reconnue. C'est ce qui donne envie de s'inscrire et de partager.

## Le produit : 5 piliers et un espace Systalink

### 1. Le profil prouvé · bloc #19
- **Généré depuis GitHub** en quelques secondes : langages, projets, pays, forces. Pas de note globale.
- **Une preuve plutôt qu'un CV** : on y voit les **projets réellement en ligne** (lien vérifié) et les contributions dans les équipes Jamii.
- **Un indice de fiabilité, explicable** : tâches livrées à temps, avis des coéquipiers, réponses acceptées dans les communautés. Chaque point est justifié et contestable.
- **La recherche de coéquipiers** par compétence, pays et disponibilité.

### 2. Les projets d'équipe · bloc #17
- Un projet naît d'une **idée** (celle d'un dev) ou d'une **demande** (celle d'une PME qui décrit son besoin).
- On publie les **rôles recherchés**, les devs **candidatent**, et l'équipe se forme, de préférence entre plusieurs pays.
- L'équipe a son **espace** : membres, tâches, jalons, statut.
- **La continuité** : un projet qui ralentit est signalé, un membre qui part est remplacé (on republie le rôle), et le projet peut être repris par une autre équipe.

### 3. L'assistant de projet (IA) · bloc #20
L'IA **propose**, l'équipe **décide**. Elle :
- transforme une idée ou une demande de PME en **plan** : cahier des charges, jalons, tâches réparties selon les compétences ;
- **suggère des coéquipiers** complémentaires ;
- envoie un **résumé de la semaine** à l'équipe, et **alerte** quand le projet décroche ;
- **propose une réponse** aux questions dans les communautés, que les pairs valident.

### 4. La mise en ligne sur Systalink · bloc #20
- Un projet se déploie sur **DataCloud** depuis Jamii : automatiquement si l'API ou le MCP le permet, sinon par un parcours guidé.
- Une fois en ligne, le projet est **vérifié** : il apparaît sur le profil de chaque membre et dans la vitrine.

### 5. Les communautés et l'entraide · bloc #18
- Des communautés **thématiques** (Flutter, Fintech, IA…), ouvertes à tout le continent.
- **Questions-réponses** (l'IA propose une réponse, les pairs la valident), discussions et « Montre ton projet ».
- **La vitrine** : tous les projets en ligne nés sur Jamii, avec la mention « Hébergé sur Systalink ».

### L'espace Systalink · blocs #18 et #17
- Un **tableau de bord** : devs inscrits, projets créés, **projets déployés sur DataCloud**, taux de passage vers une offre payante (données agrégées et anonymes).
- Une **page de statut publique** de Jamii (disponibilité, incidents) : **la preuve de fiabilité** sur l'infrastructure Systalink.

## La démo (3 minutes)

| Temps | Moment |
|---|---|
| 0:00 | Le problème : des idées, des hackathons, et des projets qui meurent |
| 0:20 | Connexion GitHub → **le profil prouvé** se génère |
| 0:50 | Une PME décrit son besoin → **l'assistant** en fait un plan et propose une équipe de 3 pays |
| 1:30 | Dans une communauté : une question, l'IA propose une réponse, un pair la valide |
| 2:00 | **Déploiement sur DataCloud** → le projet est en ligne, il apparaît sur les profils et dans la vitrine |
| 2:30 | **Le tableau de bord Systalink** et la page de statut : « chaque projet est un client DataCloud » |

Une vidéo de secours et des données de démo réalistes (une soixantaine de profils et de projets) sont prêtes, au cas où.

## Ce qu'on ne construit pas maintenant (feuille de route)

On les mentionne sur une diapo « Et après », sans les coder :
- **Missions payées en FCFA** (Wave, Orange Money) : séduisant, mais un paiement réel est risqué à livrer à temps.
- **Cercles en tontine** (chacun son tour, on construit le projet d'un membre) : un format d'événement à lancer sur la base des projets d'équipe.
- **Agents de support et d'exploitation** pour les clients Systalink.
- **Messages vocaux et langues locales** (wolof…).

## Ce qu'on a écarté, et pourquoi

*(Les services Systalink ne sont jamais écartés : voir « Systalink, le bon outil au bon moment ».)*

| Idée | Raison |
|---|---|
| Mode 3G / hors ligne | Un développeur qui code, pousse et déploie a déjà une connexion correcte. Ça coûte cher pour aucun gain réel |
| Des agents IA qui parlent ou s'engagent à la place des devs | Faux contacts et perte de contrôle. On garde « l'IA propose, l'humain décide » |
| Le thème foot (buts, saison, championnat) | Il perdait ceux qui ne connaissent pas le foot |

## Nos règles produit

- **Des mots simples** dans l'interface ([vocabulaire.md](vocabulaire.md)).
- **L'IA propose, l'humain décide.** Chaque suggestion est explicable.
- **Ouvert à tous, Systalink recommandé mais jamais imposé.**
- **Le profil reste privé** jusqu'à ce que l'utilisateur le publie. L'IA ne lit que les données publiques de GitHub et n'écrit jamais.
- **Branchable sur Systalink** ([integration-systalink.md](integration-systalink.md)).
