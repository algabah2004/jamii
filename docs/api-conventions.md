# Conventions de l'API

**Responsable : @sowibrr (bloc Socle, #17).** Ces règles s'appliquent à **tous** les contrats de `contracts/` et à toute l'API. Les schémas communs (erreur, pagination) sont dans [`contracts/socle.yaml`](../contracts/socle.yaml) : on les **référence** avec `$ref`, on ne les copie pas.

## 1. Chemins et nommage

| Élément | Règle | Exemple |
|---|---|---|
| Préfixe | Toute l'API métier est sous `/api` | `/api/projets` |
| Ressources | Noms français, au pluriel, en minuscules, sans accent, mots séparés par `-` | `/api/roles-recherches` |
| Identifiants | UUID dans le chemin | `/api/projets/{projetId}` |
| Champs JSON | `camelCase` français, sans accent | `creeLe`, `loginGithub` |
| Valeurs d'énumération | `snake_case` en minuscules | `en_cours`, `moderateur` |
| Dates | ISO 8601 en UTC (`format: date-time`) | `2026-11-09T14:30:00Z` |
| Actions qui ne sont pas du CRUD | Sous-ressource avec un verbe | `POST /api/projets/{id}/candidatures/{cid}/accepter` |

- Un champ absent et un champ `null` ne veulent pas dire la même chose : on n'envoie `null` que si la valeur est **connue et vide**.
- Les textes affichés à l'utilisateur ne viennent jamais de l'API (sauf les contenus écrits par les utilisateurs) : l'API renvoie des **codes**, le front les traduit. Ça permet le français puis l'anglais sans toucher au back.

## 2. Méthodes et codes HTTP

| Cas | Réponse |
|---|---|
| Lecture | `200` |
| Création | `201` + en-tête `Location` + la ressource créée |
| Action acceptée mais traitée plus tard (IA, e-mail) | `202` |
| Modification partielle | `PATCH` (JSON Merge Patch, RFC 7396) → `200` |
| Suppression, déconnexion | `204` |
| Entrée invalide | `400` avec la liste des champs en erreur |
| Pas connecté | `401` |
| Connecté mais pas le droit | `403` |
| Ressource inconnue **ou privée** | `404` (voir §5) |
| Conflit (doublon, modification concurrente, état incompatible) | `409` |
| Trop de requêtes | `429` + `Retry-After` |

## 3. Les erreurs : Problem Details (RFC 9457)

Toute erreur renvoie `Content-Type: application/problem+json` avec le schéma `Erreur` de `socle.yaml`. La RFC 9457 remplace la RFC 7807 et reste compatible avec elle. Spring Boot 3 la produit nativement (`ProblemDetail`).

```json
{
  "type": "urn:jamii:erreur:validation",
  "title": "Certains champs sont invalides",
  "status": 400,
  "code": "validation",
  "instance": "/api/projets",
  "traceId": "4bf92f3577b34da6",
  "erreurs": [{ "champ": "titre", "code": "trop_court" }]
}
```

- **`type`** est une URN (`urn:jamii:erreur:<code>`) : on n'a pas encore de nom de domaine, et une URN n'oblige pas à héberger une page par erreur.
- **`code`** est stable et c'est **lui** que le front utilise. `title` et `detail` servent au débogage : ils peuvent changer.
- **`traceId`** relie l'erreur aux journaux. Un utilisateur peut le donner au support.
- **Jamais** de trace de pile, de requête SQL ou de nom de classe dans une réponse, même en `500`.

## 4. Pagination par curseur

Toute liste est paginée, même courte : `?limite=20&curseur=…` (limite de 1 à 100, 20 par défaut).

```json
{ "elements": [ … ], "curseurSuivant": "eyJjIjoiMjAyNi0xMS0wOV…" }
```

- `curseurSuivant` vaut `null` à la dernière page. Le curseur est **opaque** : le front le renvoie tel quel, sans le lire.
- Le tri par défaut est le plus récent d'abord. Un autre tri se demande avec `?tri=` sur une liste fermée de valeurs, documentée dans le contrat.
- Pourquoi un curseur plutôt qu'un numéro de page : la liste reste juste quand des éléments arrivent pendant qu'on la parcourt (fils de discussion, demandes), et la requête reste rapide sur une grosse table. On renonce à « page 7 sur 12 », dont nos écrans n'ont pas besoin.

## 5. Connexion, session et droits

- **Session côté serveur** (Spring Session, stockée dans PostgreSQL) dans un cookie `JAMII_SESSION` `HttpOnly`, `Secure`, `SameSite=Lax`. Aucun jeton n'est lisible par le JavaScript du navigateur. Une session peut être révoquée immédiatement (déconnexion, compte suspendu).
- **CSRF** : le serveur pose un cookie `XSRF-TOKEN` lisible par le front. Toute requête `POST`, `PUT`, `PATCH` ou `DELETE` doit renvoyer sa valeur dans l'en-tête `X-XSRF-TOKEN`, sinon `403`.
- **Rôles globaux** : `membre` (tout le monde), `moderateur`, `admin`. Les rôles **dans un projet** (porteur, membre de l'équipe) appartiennent au module concerné. Tous les droits sont vérifiés **côté serveur, à chaque route**. Le front masque des boutons, mais ce n'est jamais une protection.
- **Ne pas révéler ce qui est privé** : si l'utilisateur n'a pas le droit de **savoir** qu'une ressource existe (un projet privé, le compte d'un autre), on répond `404`, pas `403`.
- **Ne pas révéler qui est inscrit** : la demande de lien de connexion répond toujours `202`, que l'e-mail existe ou non.

## 6. Limitation des requêtes

Réponse `429` avec `Retry-After` (en secondes) et les en-têtes `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`. Limites de départ, à ajuster après mesure :

| Route | Limite |
|---|---|
| Demande de lien de connexion | 5 par heure par e-mail, 20 par heure par adresse IP |
| Routes qui appellent l'IA | Fixée par le bloc #20 dans `ia.yaml` |
| Toute autre route, par utilisateur connecté | 300 par minute |

## 7. Évolution des contrats

- Pas de `/v1` dans les chemins : le front et l'API sont dans le même dépôt et déployés ensemble. On fait évoluer les contrats **sans casser** : ajouter un champ ou une route, oui ; renommer, supprimer ou rendre obligatoire, non.
- Le front doit **ignorer les champs qu'il ne connaît pas**.
- Un changement cassant se propose dans le ticket des blocs qui consomment le contrat, avant le code.
- Modification concurrente : une ressource modifiable par plusieurs personnes porte un champ `version` (entier). Le client le renvoie ; s'il a changé entre-temps, la réponse est `409` avec le code `modification_concurrente`.

## 8. Données personnelles

- On ne renvoie que ce dont l'écran a besoin. L'e-mail d'un utilisateur n'apparaît que dans **sa propre** réponse `/api/me`, jamais dans le profil vu par les autres.
- Aucune donnée personnelle dans les URL (paramètres de requête compris), car elles finissent dans les journaux. Seule exception : le jeton du lien de connexion, à usage unique, valable 15 minutes et retiré de l'adresse par le front dès la lecture.
- Chaque module qui stocke des données personnelles doit les ajouter à l'export (`GET /api/me/export`) et à la suppression du compte (`DELETE /api/me`).
