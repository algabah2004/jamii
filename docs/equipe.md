# Équipe, blocs et règles de travail

On travaille **par gros blocs de bout en bout** : chacun possède une fonctionnalité entière (front, back, base de données, tests) et avance avec son agent IA, **sans attendre les autres**.

## Qui fait quoi

> **Depuis le samedi 14 novembre 2026 : un module par personne** (décision de l'équipe). Les packages de l'API sont dans `apps/api/src/main/java/dev/jamii/api/`, les écrans dans `apps/web/src/features/`.

| Module | Responsable | Ticket | Territoire dans le code |
|---|---|---|---|
| 💬 **Communautés façon Discord** (salons, appels, profils) | `@rev22rse22` | [#155](https://github.com/algabah2004/jamii/issues/155) | API `communautes`, `reseau`, `profils` · écrans `communautes`, `reseau`, `profils`, `design-system` · contrats `communautes`, `reseau`, `profils` |
| 🤖 **Agents IA et Systalink** | `@algabah2004` | [#156](https://github.com/algabah2004/jamii/issues/156) | `services/ia` · API `systalink`, `deploiement`, `demo` · écran `systalink` · contrats `ia`, `deploiement`, `systalink` · DataCloud et CI |
| 🔁 **Cercles et collaboration (Gitea)** | `@Amar233-ui` | [#157](https://github.com/algabah2004/jamii/issues/157) | API `cercles`, `projets`, `vitrine` · écrans `cercles`, `vitrine` · contrats `cercles`, `projets`, `vitrine` · ossature du front (`router.ts`, `App.vue`, traductions) |
| 🧾 **Freelance** (besoin → cahier des charges → publication) | `@sowibrr` | [#158](https://github.com/algabah2004/jamii/issues/158) | API `demandes`, `auth` (socle) · écrans `demandes` et connexion · contrats `demandes`, `socle` |
| 🧭 **Chef de projet, produit et démo** | `@algabah2004` | #1, #10 | Revue de toutes les PR, intégration entre les modules, démo |

### Travailler sans se bloquer

- **Entre deux modules, seulement un contrat ou un événement public.** Un module expose une interface publique (comme `systalink.ChiffresDesModules`) ou publie un événement (comme `communautes.ReponseAcceptee`, ou `demandes.DemandePubliee`). L'autre module l'utilise sans jamais lire ses tables ni ses classes internes. `ModulesTest` (Spring Modulith) fait échouer la CI sinon.
- **Les agents IA ne s'appellent que par `contracts/ia.yaml`.** Le module freelance ne code pas d'IA : il demande une tâche à @algabah2004.
- **Les migrations SQL sont horodatées** (`V20261114_1530__salons.sql`, l'heure GMT à laquelle tu l'écris) : deux modules ne prennent jamais le même numéro. Si une migration plus récente que la tienne est fusionnée avant toi, renomme la tienne avec l'heure actuelle avant de fusionner (Flyway refuse une migration plus ancienne que la dernière appliquée).
- **`CODEOWNERS`** demande la relecture du propriétaire du module touché.

Le **plan de chaque bloc** est dans [`docs/blocs/`](blocs/README.md). C'est un **point de départ** que son responsable réécrit à sa façon. Seuls le **but** et le critère **« terminé quand »** sont fixés par le chef de projet. Les tickets GitHub servent au suivi au jour le jour.

## Les 5 règles du travail en parallèle

1. **Le contrat avant le code.** Chaque bloc écrit d'abord son contrat OpenAPI dans `contracts/` et le fait fusionner. Les autres codent contre ce contrat, jamais contre l'implémentation de quelqu'un d'autre.
2. **Des données factices pour ne jamais attendre.** Le front tourne sur des données simulées (MSW) générées depuis les contrats, et le back simule l'utilisateur connecté tant que le socle n'est pas prêt.
3. **Chacun son territoire.** Tu modifies ton territoire (voir le tableau). Pour toucher à celui d'un autre, tu le préviens dans son ticket, ou c'est lui qui fait la modification. Les fichiers communs (`contracts/socle.yaml`, ossature du front, CI) passent par leur responsable.
4. **Gros bloc, petites PR.** On fusionne **au moins tous les 2 ou 3 jours**, avec des PR de **moins de 400 lignes** environ. Une énorme PR générée par IA est impossible à relire.
5. **Relire ce que son IA produit.** Tu dois pouvoir expliquer chaque ligne en revue. « C'est l'IA qui l'a écrit » n'est pas une réponse devant le jury de Systalink.

## Ordre de démarrage

| Jours | Ce qui se passe | Pourquoi |
|---|---|---|
| 1-2 | @sowibrr fusionne `contracts/socle.yaml` et les conventions de l'API · @Amar233-ui fusionne l'ossature `apps/web` · @boubacarsidibe teste Java et Python sur DataCloud | Ce sont les 3 seuls points dont les autres dépendent. **Ils passent en priorité, avec des PR courtes** |
| En parallèle dès le jour 1 | @rev22rse22 : maquettes et identité visuelle · tout le monde : la spec et le contrat de son bloc | Aucune dépendance |
| Ensuite | Chacun déroule son bloc | Chacun dans son territoire, sur les contrats |

## Rituels

- **Chaque jour, par écrit** : un commentaire court dans ton ticket de bloc : fait, prochaine étape, blocage éventuel. Si tu es bloqué, ajoute l'étiquette `bloqué`.
- **Vendredi (30 min), intégration** : chacun fusionne ce qui est prêt, on teste le parcours complet sur `main`, et on ajuste les priorités.
