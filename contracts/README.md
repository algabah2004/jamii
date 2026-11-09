# Contrats d'API (OpenAPI 3.1)

**La source de vérité entre les blocs.** Un contrat est écrit et fusionné **avant** son implémentation.

| Fichier | Responsable | Contenu |
|---|---|---|
| `socle.yaml` | @sowibrr | Authentification, utilisateur connecté (`/api/me`), schémas communs (erreurs, pagination) |
| `projets.yaml` | @sowibrr | Projets d'équipe (depuis une idée ou une demande de PME), rôles, candidatures, tâches, continuité |
| `communautes.yaml` | @Amar233-ui | Communautés, questions-réponses, discussions, modération |
| `profils.yaml` | @rev22rse22 | Profil prouvé, indice de fiabilité, recherche de coéquipiers |
| `deploiement.yaml` | @boubacarsidibe | Mise en ligne des projets sur DataCloud |
| `systalink.yaml` | @Amar233-ui | Statistiques agrégées du tableau de bord Systalink |
| `ia.yaml` | @boubacarsidibe | API interne du service IA (appelée par l'API, jamais par le navigateur) |

## Règles

- Respecter `docs/api-conventions.md` (fourni par le socle) : erreurs RFC 7807, pagination, nommage.
- Les schémas communs se référencent depuis `socle.yaml` (`$ref: './socle.yaml#/components/schemas/Erreur'`) : pas de copie.
- Modifier le contrat d'un autre bloc : on le propose dans son ticket, et c'est lui qui le modifie.
- Un changement qui casse la compatibilité doit être annoncé dans le ticket des blocs qui consomment ce contrat.
- Le front génère ses types et ses données factices (MSW) depuis ces fichiers, et l'API est testée contre eux.
