# Jamii · CADEV 2026

> **Le lieu où les développeurs africains se rencontrent, échangent et construisent ensemble.**

*Jamii* veut dire « communauté » en swahili.

Projet présenté à la **Coupe d'Afrique des Développeurs (CADEV 2026)**, organisée par [Systalink](https://systalink.com), sur le thème : *construire la plateforme d'échange africaine, pour les devs, par les devs*.

## La démo en ligne

**https://jamii.dev**

(adresse de secours : https://87-58-156-18.sslip.io)

Hébergée sur un serveur cloud **DataCloud de Systalink**, en HTTPS. Chaque fusion dans `main` construit les images Docker (`ghcr.io`), et le serveur se met à jour tout seul ([docs/deploiement-datacloud.md](docs/deploiement-datacloud.md)).

On se connecte avec **GitHub** ou **Google** (développeurs), ou par un **lien reçu par e-mail**, sans mot de passe (PME, associations, particuliers). Aucun compte Systalink n'est demandé.

## L'idée en une phrase

Jamii **comprend un besoin et le relie à la bonne réponse** : un service Systalink s'il suffit, sinon les bons développeurs, qui forment un **cercle** (la Tech Tontine) pour le construire, le mettre en ligne et le montrer.

## Ce qu'on peut y faire

| Pour | Ce que Jamii apporte |
|---|---|
| **Se rencontrer et échanger** | Des **communautés** par thème avec leurs salons en direct, une **messagerie** avec contacts et appels, et des notifications |
| **Être reconnu** | Le **profil prouvé** : construit depuis GitHub, avec une **carte de compétences** proposée par Ayo à partir des dépôts publics, que la personne valide avant publication |
| **Construire en équipe** | Les **cercles (Tech Tontine)** : 4 à 6 développeurs, un ordre de passage **tiré au sort par le serveur**, et à chaque tour le projet d'un membre construit par tous. Tâches, salon privé, point du jour, et **Ayo qui propose qui prend quelle tâche** selon les compétences et la charge de chacun |
| **Répondre à un vrai besoin** | Une PME **décrit son besoin avec ses mots**. **Ayo**, le conseiller de Jamii, lui dit honnêtement ce qui suffit (un service Systalink, ou ce qu'elle a déjà), et s'il faut des développeurs : lesquels, avec un plan que l'équipe reprend. Il répond dans la langue de la personne |
| **Travailler en freelance** | Les **missions** (propositions, devis, suivi par jalons, avis) et les **hackathons** (inscription, équipes, rendu, jury, résultats), avec des questionnaires personnalisés |
| **Montrer ce qui est en ligne** | La **vitrine** des projets nés sur Jamii, avec le label « Né dans une Tech Tontine Systalink » |
| *Pour Systalink* | Un **espace réservé à son équipe** : projets en ligne sur DataCloud, cercles actifs, **tendances du marché** (anonymes), services proposés et suivis, et les **clients qui ont demandé à être suivis** (case non cochée par défaut, export CSV, webhook signé) |

## L'IA : seulement là où elle sert

**L'IA pour comprendre, des règles pour décider.**
- **Avec l'IA** : la carte de compétences (lire des dépôts GitHub) et l'orientation d'un besoin (comprendre un texte libre). L'IA ne peut proposer **que** des services du catalogue Systalink (`services/ia/catalogue-systalink.yaml`), et l'humain décide toujours.
- **Sans IA** (calculé, gratuit, explicable, testé) : la recommandation de développeurs, la répartition des tâches d'un cercle, les chiffres de l'espace Systalink. Le tirage au sort d'un cercle reste du hasard, jamais un choix de l'IA.
- **Garde-fous** : un budget par jour, un cache, les e-mails et téléphones retirés avant l'envoi, la réponse vérifiée contre un schéma, un quota par personne, et le site continue sans l'IA si elle tombe.
- **Fournisseur au choix par la configuration** : un adaptateur pour toutes les API au format OpenAI. En production : DeepSeek. Yokk (Systalink) se branche avec un seul fichier.

## Technique

- **Front** : Vue 3, TypeScript, Vite, design system maison, thème clair et sombre, français et anglais.
- **API** : Java 21, Spring Boot, PostgreSQL, Flyway. **Service IA** : Python, FastAPI.
- **Contrats OpenAPI** entre les modules ([contracts/](contracts/README.md)), types du front générés à partir d'eux.
- **CI** à chaque PR : tests (API, IA, front, accessibilité), aucun secret dans le code, titres conventionnels, licences autorisées.
- Architecture et choix : [docs/architecture.md](docs/architecture.md). Lancer en local : [docs/installation.md](docs/installation.md).

## Licences

Jamii est sous licence **Apache 2.0** ([LICENSE](LICENSE)). Toutes les dépendances sont sous licence permissive (MIT, Apache 2.0, BSD…) : le job CI **« Licences autorisées »** bloque toute licence GPL, AGPL ou LGPL, et son résumé liste chaque composant avec sa licence.

## Usage de l'IA pour construire Jamii

Déclaré dans [docs/cadev.md](docs/cadev.md#ce-quon-déclare-usage-de-lia) : les outils utilisés par chaque membre et les parties concernées. Tout a été relu par un humain avant fusion.

## Documentation

- [docs/cadev.md](docs/cadev.md) : **la compétition, Systalink, le problème à résoudre et les cas d'usage.**
- [docs/integration-systalink.md](docs/integration-systalink.md) : **comment Jamii se branche sur l'écosystème Systalink.**
- [docs/concept.md](docs/concept.md) : la vision du produit. [docs/vocabulaire.md](docs/vocabulaire.md) : les mots de l'interface.
- [docs/systalink-services.md](docs/systalink-services.md) : les services Systalink testés.
- [docs/equipe.md](docs/equipe.md) et [CONTRIBUTING.md](CONTRIBUTING.md) : comment l'équipe travaille.
- [docs/propositions/](docs/propositions/README.md) : les propositions et les décisions prises.
- [AGENTS.md](AGENTS.md) : les consignes pour les agents IA, à lire aussi par les humains.

## Équipe

5 personnes, un module chacun : voir [docs/equipe.md](docs/equipe.md).
