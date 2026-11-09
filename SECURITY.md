# Sécurité

## Signaler une faille

**N'ouvre pas d'issue publique.** Utilise l'onglet **Security → Report a vulnerability** du dépôt, ou contacte le chef de projet en privé.

## Règles de l'équipe

- Aucun secret (clé, jeton, mot de passe) dans le code, les commits, les issues, les captures d'écran ou le chat. La CI le vérifie avec gitleaks.
- Si un secret a fuité, même une seconde, **révoque-le immédiatement** puis préviens le chef de projet. Le supprimer du code ne suffit pas, car il reste dans l'historique Git.
- Jetons GitHub : permissions minimales et expiration courte (90 jours maximum).
- Les secrets de production vivent uniquement dans les variables d'environnement DataCloud.
