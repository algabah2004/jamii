# 0003 · Des communautés vivantes : l'échange qui fait naître les projets

| | |
|---|---|
| **Auteur** | @rev22rse22 |
| **Date** | 2026-11-10 |
| **Statut** | Brouillon |
| **Décision** | *(remplie par le chef de projet, avec la raison)* |

> Bloc concerné : **#18 (@Amar233-ui)**, qui reste propriétaire des communautés. Cette proposition apporte le **regard design et profil** (#19) et des idées à trancher. Elle ne remplace pas la spec `0002-communautes.md` d'Amar : elle l'alimente.

## Le problème

Une communauté « classique » (fils de discussion + questions-réponses) a trois défauts :

1. **Elle paraît vide.** Un forum sans activité visible fait fuir dès la première visite (supposé, mais c'est le mal connu de tous les forums lancés de zéro).
2. **On n'y gagne rien de concret.** Une bonne réponse disparaît dans le fil. Elle ne se voit ni sur le profil, ni ailleurs.
3. **Elle est déconnectée du reste.** La proposition 0001 l'a déjà dit : un parcours centré sur la Q&R « ressemble à Discord ou Stack Overflow et montre peu le cloud Systalink ».

Pour qu'une communauté soit **fun**, il faut que l'utilisateur **sente qu'il se passe quelque chose**, que **sa contribution compte tout de suite** et que **l'échange le mène quelque part** : une équipe, un projet, une mise en ligne.

**Contrainte de [vocabulaire.md](../vocabulaire.md) :** on ne rend pas l'expérience fun avec un thème (pas de points, de niveaux ou de « quêtes »). Le fun vient du **rythme**, de la **réactivité**, des **rencontres** et de **petites célébrations au texte explicite**.

## La proposition : la communauté, point de passage entre tous les piliers

La communauté n'est pas un 6ᵉ module isolé. C'est **là où l'on voit tout Jamii bouger**, et d'où l'on part vers un projet.

```
            ┌──────────── Profil prouvé (#19) ◄── réponse acceptée, badge
            │
Question ──► Communauté (#18) ──► « Transformer en projet » ──► Projet d'équipe (#17)
            │        ▲                                                   │
            │        └──── fil « Ça bouge » ◄── projet en ligne ◄── DataCloud (#20)
            └──────────── Défi du mois ──► Vitrine « Hébergé sur Systalink »
```

### 1. Le fil « Ça bouge » : la plateforme paraît vivante 🔥
En haut de chaque communauté, un fil court des **événements réels** de Jamii :
- « ✅ *Plateforme de tontine digitale* est en ligne · équipe Sénégal · Kenya · Bénin »
- « 🙌 Aïcha (Abidjan) a eu sa réponse acceptée sur *Riverpod et la mise en cache* »
- « 👥 L'équipe *Agri-Alertes* cherche un dev Flutter »

Chaque ligne **mène quelque part** (le projet, la réponse, le rôle). C'est le meilleur antidote à l'effet « forum vide ». Pour la démo, il tourne sur les données de démo.

### 2. « Questions pour toi » : se rendre utile en 1 minute
À partir des langages du **profil généré depuis GitHub**, Jamii montre 3 questions ouvertes que le dev sait probablement résoudre : « *Tu as 14 dépôts en Dart : 3 questions Flutter attendent une réponse.* »
- C'est la première action proposée après l'inscription, plutôt qu'une page vide.
- L'IA (#20) classe les questions, mais **ne répond jamais à la place du dev** dans cet encart.

### 3. La question qui se débloque en direct ⚡
Le moment fort de la démo (1:30) :
1. Le dev pose sa question. Il voit tout de suite « *2 devs de ta communauté sont en ligne* ».
2. **L'IA propose une réponse en quelques secondes**, clairement étiquetée « Proposée par l'IA, à valider ».
3. Un pair la **valide ou la corrige**. La réponse passe en « **Réponse acceptée** », avec une petite animation.
4. Sur le profil du pair, la ligne s'ajoute à l'**indice de fiabilité** : « *Réponses acceptées : 7, voir lesquelles* ».

### 4. Des réactions qui font quelque chose
Pas de « like » vide. Sous un « Montre ton projet », trois boutons explicites :
- **« Je veux l'utiliser »** → compteur visible par l'équipe : un premier signal d'utilisateurs ;
- **« Je veux contribuer »** → ouvre la candidature au rôle publié (#17) ;
- **« Je l'ai testé »** → un retour court, que l'équipe voit dans son espace.

### 5. « Transformer en projet » : l'échange devient une équipe 🚀
Une discussion « *Qui veut faire une appli de covoiturage pour Dakar ?* » a un bouton **« Transformer en projet »**. Il crée le projet (#17) avec les participants comme premiers candidats, et l'assistant (#20) propose le plan.
**C'est la phrase du pitch :** *« sur Jamii, une conversation devient un projet en ligne ».*

### 6. Le Défi du mois (déjà prévu dans le vocabulaire)
Une fois par mois, un **thème posé par une PME ou par Systalink** (« *Une appli pour les commerçants du marché Sandaga* »).
- Les équipes se forment dans la communauté et ont **un mois** pour mettre en ligne.
- Une jauge **collective**, pas individuelle : « *12 équipes · 5 projets en ligne · 7 pays* ».
- Les projets finissent dans la **vitrine**, avec le badge du défi et « Hébergé sur Systalink ».
- **Pour Systalink :** chaque défi est une vague de déploiements DataCloud, mesurée dans le tableau de bord.

### 7. Les antennes locales et l'impact par pays 🌍
- Une **carte du continent** où s'allument les pays actifs cette semaine (questions résolues, projets en ligne).
- **« Impact par pays »** (mot validé), toujours **collectif** : on célèbre un pays, jamais un classement de personnes.
- Une antenne locale (Dakar, Abidjan, Nairobi…) peut proposer un rendez-vous en visio **SystaTalk** en un clic : un « moment Systalink » mesurable.

### 8. Le rendez-vous de la semaine
- **Le résumé de la semaine** de la communauté, écrit par l'IA (le rôle de « conteur » de la Tech Tontine) : 3 questions résolues marquantes, les projets mis en ligne, qui a aidé qui.
- **La démo du vendredi** : 30 minutes en visio, 5 minutes par projet. Elle donne une raison de revenir chaque semaine. C'est la question posée dans le plan d'Amar : « *qu'est-ce qui fera revenir un dev chaque semaine ?* »

### 9. Des célébrations explicites
On respecte la règle de [vocabulaire.md](../vocabulaire.md) : une petite animation, un texte clair.
- « 🎉 *Ta première réponse acceptée !* Elle apparaît maintenant sur ton profil. »
- « ✅ *Ton projet est en ligne.* 23 personnes l'ont vu dans le fil. »
- Badges : *Première réponse acceptée*, *Premier projet en ligne*, *A participé au Défi du mois*, *Équipe de 3 pays*.

### Ce qu'on refuse volontairement
| Mécanique | Pourquoi non |
|---|---|
| Points, niveaux, réputation chiffrée | Contraire au principe « pas de note globale ». On garde un indice explicable, point par point |
| Classement des personnes | Décourage les débutants et pousse à la triche. On célèbre le collectif (pays, défi, communauté) |
| Une IA qui répond et compte comme un humain | « L'IA propose, l'humain décide » : une réponse IA n'entre dans l'indice de personne |

## Ce qu'on fait avant le 15 novembre, et ce qui attend

Le [plan des 5 derniers jours](../plan-final.md) dit : « pas de nouvelle idée avant le 15 », et les communautés sont 🟡. On ne garde donc que **ce qui coûte peu et rend la démo vivante** :

| Élément | Avant le 15 ? | Qui | Coût |
|---|---|---|---|
| Fil « Ça bouge » sur **données de démo** | ✅ | composant @rev22rse22, données @Amar233-ui | S |
| Célébrations (animation + badge) dans le design system | ✅ | @rev22rse22 | S |
| Question → réponse IA → validation par un pair → profil | ✅ (étape 7 de la démo, déjà prévue) | #18 + #20, ligne de profil #19 | M |
| Bouton « Transformer en projet » | 🟡 si l'API Projets est prête | #18 → `contracts/projets.yaml` (#17) | S |
| Jauge collective « Défi du mois » (données de démo) | 🟡 | #18, maquette #19 | S |
| Questions pour toi, réactions utiles, carte d'impact | 🕒 Et après | #18 + #20 | M |
| Résumé de la semaine, démo du vendredi (SystaTalk) | 🕒 Et après (diapo « Et après ») | #20, #18 | M |

## Alternatives envisagées *(obligatoire)*

| Option | Avantages | Inconvénients |
|---|---|---|
| A. **Communauté comme point de passage** (cette proposition) | Montre tout Jamii vivant, relie les 5 piliers, renforce l'histoire « l'échange devient un projet en ligne », chaque élément est découpable | Demande des contrats entre blocs (événements, création de projet) |
| B. Gamification classique (points, niveaux, classement) | Connue, rapide à coder | Contredit « pas de note globale » et vocabulaire.md, encourage la triche, et le jury y verra un clone |
| C. Communauté minimale (Q&R seule) | Le moins de travail | Effet « forum vide », ressemble à Stack Overflow (déjà écarté par 0001) |

**Recommandation : A**, limitée avant le 15 aux 3 lignes ✅ du tableau.

## Risques et points faibles *(obligatoire)*

- 🔴 **Délai.** Le bloc #19 doit montrer de l'activité **avant le 11/11 à 20 h**, sinon il est repris. Le **profil prouvé passe avant** cette proposition.
- 🔴 **Triche sur l'indice.** On se valide entre amis. Parades : l'auteur de la réponse ne valide jamais sa propre réponse ; validation par l'auteur de la question **ou** par 2 pairs qui ne sont pas dans la même équipe ; limite quotidienne ; chaque point reste **contestable**.
- 🟡 **Le fil d'activité et la vie privée.** Le profil est privé jusqu'à sa publication, donc le fil **n'affiche jamais** un utilisateur au profil non publié (« un dev de Dakar »). Il ne montre que des actions publiques.
- 🟡 **Architecture du fil.** Le fil ne doit pas lire les tables des autres modules ([modele-donnees.md](../modele-donnees.md)). Le modèle de données prévoit déjà des événements entre modules (`TacheLivree`, `ReponseAcceptee`, `ProjetMisEnLigne`, [modele-donnees.md](../modele-donnees.md) §6). Le fil n'a qu'à les écouter, en plus de `profils` et de la vitrine.
- 🟡 **Charge.** Le « en ligne maintenant » demande du temps réel. Avant le 15 : un rafraîchissement simple toutes les 30 s, sans WebSocket.
- 🟡 **Spam de « Transformer en projet ».** Il faut une limite par utilisateur et une modération identique aux publications.

## Coût et impact

- Effort : **S** pour la partie d'avant le 15 (sur données de démo), **L** pour tout le reste.
- Vocabulaire : nouveaux libellés à ajouter à [vocabulaire.md](../vocabulaire.md) : « Ça bouge », « Questions pour toi », « Transformer en projet », « Je veux l'utiliser / contribuer / Je l'ai testé ». À valider.
- Démo : la minute 1:30 devient vivante (présence, IA, validation, célébration, profil mis à jour), et le fil fait le lien avec la mise en ligne à 2:00.
- Jury Systalink : le Défi du mois et le rendez-vous SystaTalk sont des **moments Systalink** mesurables dans le tableau de bord.

## Questions ouvertes

- ❓ @Amar233-ui : intègres-tu ces idées dans ta spec 0002, ou on garde 0003 à part pour « Et après » ?
- ❓ @sowibrr : `contracts/projets.yaml` peut-il accepter une création de projet depuis une publication (`publication_id` d'origine) ?
- ❓ @boubacarsidibe : le service IA peut-il classer des questions par langage pour « Questions pour toi » sans coût par requête trop élevé ?
- ❓ @algabah2004 : « Ça bouge » est-il assez simple pour le vocabulaire ? Alternative plus sobre : « En ce moment ».

## Avis de l'équipe

*(Résumé des commentaires de la PR, mis à jour avant la décision.)*
