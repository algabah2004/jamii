# Jamii · CADEV 2026

> ℹ️ *Mise à jour du 8 novembre 2026 : le thème football a été abandonné. Le produit utilise uniquement des mots simples.*

> **La plateforme d'échange des développeurs africains.**
> L'endroit où les devs de tout le continent se rencontrent, s'entraident et mettent en ligne des projets utiles. Ensemble.

*Jamii* veut dire « communauté » en swahili. C'est un nom provisoire.

---

## 1. Le contexte

Systalink organise la **CADEV 2026** pour entrer dans la communauté tech africaine, où sa présence est encore timide. Le thème officiel est :

> « Construire la plateforme d'échange africaine, pour les devs, par les devs. »

Le jury se demandera si notre produit **fait venir** des devs chez Systalink, s'il **les fait rester** et s'il **les fait payer**.

## 2. Notre principe : brancher ce qui existe déjà

**On ne réinvente rien de ce que Systalink sait déjà faire.** Notre travail consiste à **assembler leurs services** dans une expérience fun et simple.

On a trois bonnes raisons de faire comme ça :

1. **Pour Systalink**, chaque action sur Jamii devient une utilisation réelle de ses produits : déploiements, Yokk, Orion, paiements. On leur sert de vitrine et on leur amène des clients.
2. **Pour nous**, on va plus vite et on est plus crédibles. On construit l'expérience, et Systalink fournit l'infrastructure.
3. **Pour les utilisateurs**, ils découvrent les services Systalink en s'en servant, sans qu'on leur fasse de publicité.

### Les services Systalink que l'on branche

| Service Systalink | Ce qu'il fait | Ce qu'on en fait dans Jamii |
|---|---|---|
| **Web sans serveur (DataCloud)** | Déploie depuis GitHub, avec préview par pull request et offre gratuite | La mise en ligne de chaque projet |
| **Serveur MCP** *(à tester)* | Permet à un agent IA de piloter le cloud | Notre agent configure et déploie pour l'utilisateur |
| **Espace développeurs / OAuth** *(à tester)* | « Se connecter avec Systalink » | Le bouton « Connecter mon Systalink » |
| **Webhooks** *(offre Professionnel)* | Un signal à chaque déploiement | Le badge « En ligne » et la carte d'Afrique des projets |
| **Yokk** ⭐ | Constructeur d'agents IA, publiés en API ou en widget | Création des profils, aide dans les communautés, traduction, cahiers des charges, missions « agent » |
| **Orion** ⭐ | Un site créé par l'IA à partir d'une description | Les besoins simples des PME, les portfolios (en option) |
| **SystaTalk** | Chat et visio jusqu'à 250 personnes | Les lives des communautés, l'espace d'échange des équipes |
| **SystaDrive** | Fichiers hébergés à Dakar | L'espace projet de l'équipe |
| **SystaForms** | Formulaires et signature | Le contrat de mission |
| **Paxity** | Agrégateur de paiement (Mobile Money, carte) | Le paiement des missions, et l'intégration dans les applis des devs |
| **SystaMail + domaines** | E-mail pro, .sn, .ci, .cm… | Le moment « Passer pro » d'un projet |
| **Bases de données, GPU, Kubernetes** | Infrastructure | Proposées par l'agent au déploiement, défis IA |

⭐ = produits stratégiques pour Systalink en ce moment, qu'on met au cœur du projet.

**Limites connues :**
- Aucune API REST publique n'est documentée.
- Les webhooks demandent l'offre Professionnel.
- L'API SystaONE demande l'offre Business.
- Le MCP et l'OAuth restent à **tester de bout en bout en priorité**.

### Une utilisation intelligente

Systalink n'a pas les moyens de faire tourner de l'IA, ou de créer un site, pour chaque inscrit. On applique donc trois règles :

- **L'IA n'intervient que si** l'utilisateur la demande, si le moment peut rapporter un client, ou si le résultat est réutilisé (calculé une fois, puis gardé en cache).
- **Notre propre code calcule tout le reste**, sans IA : les profils, la carte d'Afrique et les statistiques.
- **Chacun paie sa part.** Systalink paie ce qui fait venir les gens (offres gratuites, un appel IA à l'inscription). L'utilisateur paie son usage réel. Le client paie ses missions.

---

## 3. L'ambition : réunir l'Afrique

On ne veut pas d'une communauté par ville. **Tout est sans frontières par défaut :**

- Les communautés, les équipes et les missions sont ouverts à tout le continent.
- **La traduction fait tomber le mur des langues** entre le français, l'anglais, le portugais, l'arabe et le swahili.
- **Les projets multi-pays sont mis en avant :** un projet qui réunit 3 pays apparaît sur la carte de chacun des trois.
- Les suggestions de coéquipiers vont chercher au-delà de ta ville.

## 4. Les 5 piliers

1. **Ton profil (Yokk).** Ton profil se construit tout seul depuis GitHub, en 30 secondes. Il montre tes forces, ton style et ton pays, et chaque stat renvoie au code qui la prouve.
2. **Les communautés (Yokk, SystaTalk).**
   - Les communautés thématiques (Flutter Afrique, Fintech Afrique…) sont continentaux.
   - Les villes et les pays en sont les **antennes** locales.
   - Des **jumelages** relient des villes, par exemple Dakar ↔ Nairobi.
   - Une communauté devient officielle avec des membres d'au moins 3 pays.
   - On y échange de six façons : questions-réponses, discussions, « Montre ton projet », annonces, lives et événements.
3. **Équipes et missions (Orion, Yokk, SystaForms, Paxity).** Un client décrit son besoin, et l'agent l'oriente vers la bonne option :
   - Orion si c'est simple ;
   - une mission Yokk pour un agent ;
   - une mission de développement sur DataCloud pour une vraie application.
4. **Mettre en ligne (DataCloud, MCP).** On connecte son compte Systalink et l'agent lit le dépôt. On valide, puis Systalink déploie. Le projet est en ligne.
5. **L'impact du continent.**
   - Une carte d'Afrique montre, pays par pays, les projets mis en ligne et l'entraide (réponses acceptées).
   - Un défi multi-pays est proposé chaque mois.
   - L'esprit de la CADEV continue toute l'année.

### Le savoir du continent

On propose des guides qu'on ne trouve nulle part ailleurs :
- intégrer le mobile money (Wave, Orange Money, MTN MoMo, M-Pesa, Paxity) ;
- construire un service USSD ou SMS ;
- faire des applis légères qui marchent en 3G ;
- utiliser l'IA avec les langues africaines ;
- respecter les lois sur les données de chaque pays.

Ces guides sont écrits par la communauté et traduits pour tout le continent.

---

## 5. Nos règles

**Personne n'a honte de son profil :**
- il n'y a pas de note globale ;
- le profil ne montre que tes forces, et un style plutôt qu'un niveau ;
- les débutants ont un profil « Débutant motivé » ;
- le profil reste privé jusqu'à ce que tu la publies.

**L'agent ne touche pas à ton code :**
- il ne lit que les données publiques et n'écrit jamais rien ;
- il exclut les fichiers .env et les secrets ;
- il ne garde pas le code et ne s'entraîne pas dessus ;
- tu peux voir le journal de ce qu'il a lu et tout supprimer.

**Des mots simples :**
- les boutons disent ce qu'ils font (« Déployer », « Rejoindre ») ;
- aucun jargon ni métaphore à apprendre (voir vocabulaire.md).

**Les données partagées avec Systalink** sont agrégées, anonymes et soumises à l'accord de l'utilisateur.

**Ce qu'on a mis de côté :** WhatsApp, les agents qui parlent à ta place et les faux profils.

## 6. Parcours type

**Aminata, développeuse à Abidjan :**
1. Elle voit passer le profil d'un ami.
2. Elle crée son profil depuis GitHub.
3. Elle rejoint la communauté Laravel Afrique.
4. Elle aide Chidi, de Lagos, chacun dans sa langue.
5. Elle forme une équipe avec Bamako et Nairobi.
6. Elle met le projet en ligne sur Systalink.
7. Une boutique de Dakar lui confie une mission.
8. Elle revient pour le défi du mois.

**Moussa, commerçant à Dakar :**
1. Il décrit son besoin avec ses mots.
2. Yokk en fait un cahier des charges.
3. Son besoin part vers Orion ou devient une mission.
4. Il teste la préview, signe et paie via Paxity.
5. Son appli tourne sur Systalink : il devient client sans l'avoir cherché.

## 7. La démo du jury (3 minutes)

| Temps | Moment |
|---|---|
| 0:00 | Le problème |
| 0:20 | Le profil se crée en direct |
| 0:50 | Une communauté panafricaine avec traduction |
| 1:20 | Une mission rédigée par Yokk |
| 2:00 | Le déploiement en un clic |
| 2:30 | La carte de l'Afrique s'allume, puis le tableau de bord Systalink |

## 8. À décider ensemble

- **Le nom :** Jamii, ou autre chose ?
- **Le paiement des missions via Paxity :** réel, simulé ou en crédits ? Paxity peut-il bloquer l'argent jusqu'à la livraison ?
- **La Tontine :** on la garde comme format d'événement, ou on la retire ?
- **Le seuil d'une communauté officielle :** 3 pays ?

**Prochaines étapes :**
1. Tester le MCP et l'OAuth Systalink de bout en bout.
2. Confirmer le règlement (format, taille des équipes).
3. Répartir les rôles.
4. Maquetter le profil et une communauté.
5. Écrire la démo seconde par seconde.

---

## Ouvrir la présentation

La présentation animée est dans le fichier **`Jamii_CADEV2026.html`**.

- **Sur ordinateur :** ouvre-le avec Chrome, Edge ou Firefox. Avance avec les flèches ← →. **F** passe en plein écran et **N** affiche les notes d'orateur.
- **Sur téléphone :** ouvre-le avec Chrome (Android) ou Safari (iPhone), en mode paysage. Glisse le doigt pour changer de diapo.
- Il faut internet pour charger les polices. Hors ligne, tout fonctionne quand même.

> « Seul on va plus vite. Ensemble, on va plus loin. »
