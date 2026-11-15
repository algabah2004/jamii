# 0005 · Se connecter avec Google, relier GitHub pour sa carte de compétences

| | |
|---|---|
| **Auteur** | @Amar233-ui |
| **Date** | 2026-11-15 |
| **Statut** | En discussion |
| **Décision** | *(remplie par le chef de projet, avec la raison)* |

> Modules concernés : **socle / auth** (@sowibrr) pour la connexion et le rattachement, **profils** (@rev22rse22) pour le bouton dans « Mon profil ». Aucun code n'est écrit tant que la proposition n'est pas acceptée.

## Le problème

Aujourd'hui, on entre dans Jamii de deux façons (vérifié dans `apps/api/.../auth/`) :
- **avec GitHub** (`ConnexionGithub`), pour les développeurs ;
- **par un lien envoyé par e-mail** (`LiensEmail`), pour les PME et les non-développeurs.

Deux freins (des **suppositions**, pas encore d'entretien) :
1. **Beaucoup de développeurs du continent n'ont pas de compte GitHub actif**, ou ne l'utilisent pas sur leur téléphone. Leur compte Google, si. Le lien par e-mail marche, mais il demande d'aller chercher un e-mail à chaque connexion.
2. **La carte de compétences dépend du compte de connexion.** `GenerationProfils.lancer` lit le `login_github` du compte et répond `409 github_non_relie` s'il manque. Quelqu'un entré par e-mail n'a donc **aucun moyen** d'obtenir sa carte, même s'il a un GitHub.

## La proposition

**Séparer l'identité (comment on se connecte) de la preuve (ce qui montre ses compétences).**

1. **Se connecter avec Google**, en plus de GitHub et de l'e-mail : OpenID Connect standard, une inscription `google` dans Spring Security (`scope: openid, email, profile`), à côté de `github`. Rien n'est imposé : les trois portes restent ouvertes.
   **L'écran de connexion met Google et GitHub en avant, l'e-mail en retrait** :
   ```
   [ G  Continuer avec Google ]
   [ ⌥  Continuer avec GitHub ]
   Pas de compte Google ni GitHub ? Recevoir un lien par e-mail
   ```
   On garde le lien par e-mail : une PME ou une association a souvent une adresse Orange, Yahoo, Outlook ou à son nom de domaine, sans compte Google ni GitHub. C'est aussi la porte de secours si Google est en panne ou bloque l'application.
2. **« Relier mon GitHub » dans « Mon profil »** : une personne **déjà connectée** (par Google ou par e-mail) lance l'autorisation GitHub en mode *rattachement*. Au retour, on enregistre `github_id` et `login_github` sur **son** compte, sans créer de compte ni changer de session. Le bouton dit pourquoi : « Relier mon GitHub pour obtenir ma carte de compétences ».
3. **« Délier mon GitHub »** : on retire `github_id` et `login_github`. La carte déjà publiée reste, mais elle n'est plus régénérée.

### Les règles du rattachement (le cœur de la sécurité)

| Cas | Ce qui se passe |
|---|---|
| Le compte GitHub n'est relié à personne | Il est relié au compte connecté |
| Il est déjà relié **au même** compte | Rien ne change |
| Il est déjà relié **à un autre** compte Jamii | **Refus** (`409 github_deja_relie`). Pas de fusion automatique : la personne se connecte avec ce GitHub et supprime l'autre compte si elle veut |
| Connexion Google avec une adresse déjà utilisée par un compte | On ouvre ce compte **seulement si Google dit l'adresse vérifiée** (`email_verified: true`). Sinon : refus, et proposition du lien par e-mail |

- Le mode *rattachement* est porté par un `state` OAuth2 lié à la session (le mécanisme standard de Spring) : un lien de rattachement envoyé à quelqu'un d'autre ne relie rien à son compte.
- Le rattachement demande une **session de moins de 10 minutes** ou une reconnexion, comme tout changement sensible du compte.
- **Données personnelles** : on ne garde de Google que l'identifiant (`sub`), le nom, l'adresse et la photo. On les ajoute à l'export du compte et à sa suppression (`MesDonnees`).

### Ce qui change dans le code (estimation)

- **Base** : une colonne `google_sub VARCHAR(255) UNIQUE` sur `utilisateur` (migration horodatée).
- **auth** (@sowibrr) : `ConnexionGoogle` (`OidcUserService`), `ComptesGoogle`, le mode rattachement dans le retour GitHub, `DELETE /api/moi/github`.
- **Contrat** `socle.yaml` : `Moi` gagne `connexions: { github: boolean, google: boolean, email: boolean }`, plus les routes de rattachement.
- **profils** (@rev22rse22) : le bouton dans « Mon profil » à la place de l'erreur `github_non_relie`.
- **Front, connexion** : un bouton « Continuer avec Google » (le logo officiel, selon les règles de marque de Google).
- **Configuration** : `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` dans `.env.example`. Sans eux, le bouton n'apparaît pas.

## Alternatives envisagées

| Option | Avantages | Inconvénients |
|---|---|---|
| **A. Google + GitHub relié (cette proposition)** | Une connexion simple pour tous, la preuve GitHub gardée ; marche aussi pour les comptes e-mail d'aujourd'hui | Deux morceaux à faire (connexion et rattachement), et les règles de fusion à tester |
| B. Google seul, sans rattachement | Plus rapide | Ceux qui entrent par Google n'ont jamais de carte de compétences : la recommandation des développeurs perd sa preuve |
| C. Fusion automatique des comptes par adresse e-mail | Aucun geste pour l'utilisateur | **Dangereux** : une adresse non vérifiée chez un fournisseur suffirait à prendre le compte d'un autre |
| D. Ne rien faire | Zéro effort | Le lien par e-mail reste la seule porte sans GitHub, et les comptes e-mail n'ont jamais de carte |

## Risques et points faibles

- **Prise de compte par l'adresse e-mail** : réglée par la règle « adresse vérifiée par Google, sinon refus » et par l'absence de fusion automatique. À couvrir par un test pour chaque ligne du tableau.
- **Un rattachement forcé** (CSRF sur le retour OAuth) : réglé par le `state` lié à la session. À tester.
- **Google en panne** : les deux autres portes restent ouvertes, aucun parcours n'en dépend.
- **Conformité** : une donnée de plus (`google_sub`), déclarée dans l'export et la suppression. Google exige un écran de consentement OAuth publié (nom, logo, politique de confidentialité) avant d'ouvrir la connexion à tout le monde (**à vérifier** : le délai de validation par Google).
- **Systalink** : rien ne change, on ne dépend toujours d'aucun fournisseur d'identité en particulier (OIDC standard).

## Coût et impact

- Effort estimé : **M** (environ 1 jour à deux : @sowibrr pour auth, @rev22rse22 pour le bouton du profil).
- Architecture : une inscription OIDC de plus et une colonne. Pas de nouveau service.
- Vocabulaire : « Continuer avec Google », « Relier mon GitHub », « Délier mon GitHub » (à ajouter dans `docs/vocabulaire.md`).
- Planning : **après la CADEV**. C'est déjà rangé dans « Pour plus tard » de `docs/priorites.md`.

## Questions ouvertes

- Faut-il proposer aussi **GitLab** comme source de la carte de compétences, avec le même mécanisme de rattachement ?
- Une personne entrée par GitHub peut-elle **ajouter** Google ensuite, pour se connecter des deux façons ? (Proposé : oui, même mécanisme, plus tard.)
- Qui crée l'application OAuth chez Google, et sous quel compte (Jamii ou Systalink) ?

## Avis de l'équipe

*(Résumé des commentaires de la PR, mis à jour avant la décision.)*
