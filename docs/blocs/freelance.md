# 🧾 Freelance : du besoin au cahier des charges publié

**Responsable :** @sowibrr · **Suivi :** #158 · Il garde le socle de l'API (`auth`, `socle.yaml`).

## But
Une personne (une PME, une association…) arrive avec un besoin. **L'agent IA l'aide à le structurer et à rédiger son cahier des charges.** Puis elle **publie sa demande**, et la communauté des développeurs la voit.

## Territoire
- API : `demandes`, `auth` ;
- écrans : `demandes` et la connexion ;
- contrats : `demandes.yaml`, `socle.yaml`.

## Ce qui existe déjà
- Les demandes de PME et leur plan proposé.
- L'orientation d'un besoin vers les services Systalink et les développeurs recommandés.
- « Former un cercle avec eux » (#150).

## Avant la soumission (dimanche 15, 12 h GMT)
1. **Le parcours** : décrire son besoin, l'agent pose les bonnes questions, puis le cahier des charges est rédigé, relu par la personne et publié. Les agents viennent de @algabah2004, par `ia.yaml`.
2. **L'événement public `DemandePubliee`**, que le module des communautés affiche dans un salon.
3. L'appui à la mise en ligne, avec @algabah2004.

## Terminé quand
Pendant la démo, une PME passe de son texte libre à un cahier des charges publié, visible dans une communauté.

## La suite : hackathons et missions (proposition [0004](../propositions/0004-hackathons-et-missions.md), en discussion)

Suivi : #166. Une tâche par ticket, dans cet ordre :

| Ordre | Tickets | Ce que ça livre |
|---|---|---|
| 1 | #167 | La proposition acceptée (cette PR) |
| 2 | #175, #176, #177 | Missions : le contrat, publier, candidater et fixer le devis |
| 3 | #178, #179, #180 | Missions : livrer et accepter, annuler ou trancher, avis et données |
| 4 | #168, #169, #170 | Hackathons : le contrat, créer et publier, les écrans |
| 5 | #171, #172, #173, #174 | Hackathons : équipes, rendus, jury, résultats |
| 6 | #181 | Données de démo, accessibilité, documentation |

**Territoire en plus, si la proposition est acceptée :** le module `hackathons` (API, écrans `features/hackathons`, `contracts/hackathons.yaml`) et `contracts/missions.yaml`. Les routes du front (`router.ts`), `projets`, `reseau` et `profils` restent à leurs responsables : on passe par une **demande**.

**Terminé quand :** une PME publie une mission, choisit un développeur sur devis et la mène jusqu'aux avis ; un organisateur crée un hackathon, des équipes rendent, le jury note et les résultats sont publiés, sur l'environnement de test.

### Où en est le module (15 novembre)

Les API et les écrans des étapes 2 à 5 sont fusionnés. Les écrans s'ouvrent depuis la page d'une mission (`features/missions/MissionPage.vue`) ou d'un hackathon (`features/hackathons/HackathonPage.vue`). Ce que chacun voit dépend de `moi.actions`, calculé par l'API, qui revérifie chaque action.

| Qui | Mission | Hackathon |
|---|---|---|
| Client / organisateur | publier, retenir une proposition, accepter le devis, accepter ou demander une retouche, déclarer un paiement, annuler, ouvrir un désaccord, donner un avis | publier, trier les candidatures, inviter le jury (par le lien du profil), suivre la notation, exporter les notes en CSV, confirmer les gagnants |
| Développeur / participant | proposer ses services, envoyer le devis, démarrer, livrer un jalon, donner un avis | s'inscrire seul ou en équipe, inviter, rendre le projet |
| Juré | — | noter chaque projet de 1 à 5 par critère, avec un commentaire |
| Tout le monde | lire une mission publique | lire un hackathon publié, ses équipes et ses résultats |

**Pour la démo**, le profil `demo` (`SPRING_PROFILES_ACTIVE=demo`) charge aussi :
- « Dakar Cloud Hack », terminé, avec ses résultats (`DemoHackathonMission.HACKATHON`) ;
- la mission des tisserandes de Thiès, en cours. Son 2e jalon attend qu'Awa l'accepte.

**Configuration** (`.env.example`) :
- `PAIEMENTS=declares` : Jamii n'encaisse rien, chacun déclare le paiement d'un jalon.
- `VERIFICATION_LIEN=aucune|simulee` : vérifie si le lien du produit d'un rendu répond.

Chaque fournisseur passe par une interface interne, avec un adaptateur simulé ; on le change par la configuration.

**Encore ouvert :**
- les routes et le lien de menu (#191, demande à @Amar233-ui) ;
- un projet Jamii créé depuis un hackathon (#211) ;
- la preuve « mission terminée » sur le profil (#212).
