# Branchement à l'écosystème Systalink

> **Notre point fort.** La solution gagnante de la CADEV **intègre le portefeuille de Systalink**. Jamii doit donc pouvoir être **branché sur leurs services en quelques jours, sans réécriture**. Chaque bloc conçoit son code avec ça en tête.

## Le principe : « Systalink d'abord, et toujours remplaçable »

1. **Si Systalink a le service, on l'utilise** plutôt qu'un équivalent externe : hébergement, base de données, stockage, IA, paiement, e-mail.
2. **Tout service externe passe par un adaptateur** : une interface interne, et une implémentation Systalink. Le code métier ne dépend jamais directement d'un fournisseur. On passe de « simulé » à « réel », ou d'un fournisseur à un autre, **en changeant la configuration**, pas le code.
3. **Uniquement des standards** : OpenAPI, OAuth2 / OpenID Connect, API S3, webhooks signés, conteneurs, variables d'environnement, Postgres. C'est ce que leurs ingénieurs savent intégrer.
4. **Souveraineté** : les données restent hébergées chez Systalink, à Dakar. Aucune donnée personnelle n'est envoyée à un service hors d'Afrique sans le dire clairement à l'utilisateur.

```
Code métier (modules)  ──►  Interface interne (port)  ──►  Adaptateur Systalink   (production)
                                                     └──►  Adaptateur simulé      (développement, tests, démo de secours)
```

## La carte des branchements

| Service Systalink | Utilisé par Jamii pour… | Interface interne | Bloc | Statut |
|---|---|---|---|---|
| **DataCloud (hébergement)** | Héberger Jamii : web, API, IA. Préview par PR | — (configuration) | Socle #17 · IA #20 | ✅ gratuit · ⏳ Java et Python à tester |
| **DataCloud PostgreSQL** | Base de données principale | — (URL standard) | Socle #17 | ⏳ à tester (sauvegardes ?) |
| **Stockage objet (S3)** | Avatars, fichiers des projets | `StockageFichiers` (API S3) | Socle #17 | ⏳ à tester |
| **Yokk** | Cahiers des charges, résumés de profils, traduction | `FournisseurIA` | IA #20 | ⏳ offre gratuite à vérifier |
| **MCP / API Systalink** | Bouton « Déployer sur Systalink » pour les projets des utilisateurs | `Deployeur` | IA #20 (phase 2) | ⚠️ `mcp.systalink.com` ne répond pas, à tester avec une clé |
| **Orion** | Proposer un site généré par IA aux PME qui ont un besoin simple | `GenerateurSite` (lien sortant au départ) | Feuille de route | ✅ offre Free · pas d'API |
| **Paxity / Mobile Money** | Paiement des missions | `Paiement` | Phase 2 | ⚠️ pas de doc publique → simulé |
| **SystaTalk** | Visio pour les communautés et les équipes | `Visio` (lien de salle) | Feuille de route | ⚠️ essai 14 jours |
| **SystaMail / E-mail Pro** | E-mails transactionnels (notifications) | `EnvoiEmail` (SMTP) | Socle #17 | ⏳ à tester |
| **Observabilité DataCloud** | Journaux et métriques de Jamii | — (logs JSON sur stdout) | Socle #17 | ⏳ à configurer |
| **Webhooks** | Être prévenu quand un déploiement réussit | `EvenementsDeploiement` | IA #20 | ⚠️ offre Professionnel → interrogation régulière en attendant |
| **Compte Systalink (OAuth)** | « Se connecter avec Systalink » | `FournisseurIdentite` (OIDC) | Socle #17 | ❌ n'existe pas publiquement → GitHub d'abord, **prévu pour s'ajouter sans réécriture** |

## Ce qui rend Jamii facile à intégrer pour Systalink

- **Configuration 100 % par variables d'environnement** (voir `.env.example`), conteneurisable, déployable sur leur DataCloud.
- **Une API documentée** (contrats OpenAPI dans `contracts/`) : ils peuvent brancher leurs propres outils dessus.
- **Une identité extensible** : la connexion passe par une interface OIDC. Ajouter « Se connecter avec Systalink » le jour où ça existe = un adaptateur de plus.
- **Des événements sortants** (prévus) : Jamii publie des webhooks signés (`projet.deploye`, `demande.creee`…) que Systalink peut consommer (CRM, statistiques, offres DataCloud).
- **Une marque configurable** : nom, logo et couleurs dans le design system. Jamii peut devenir un produit Systalink.
- **Une passerelle vers les clients Systalink** : chaque projet mis en ligne, chaque PME aidée est un utilisateur potentiel de leur cloud. On mesure ces conversions (anonymisées, avec consentement) pour le montrer au jury.

## À montrer dans la démo

1. Jamii **tourne sur DataCloud** (l'URL, la préview de PR).
2. Une PME décrit son besoin → **Yokk** (l'assistant de projet) rédige le plan et propose une équipe.
3. Une équipe met son projet en ligne → **sur DataCloud**, depuis Jamii.
4. Le **tableau de bord Systalink** : projets déployés sur DataCloud, et la page de statut.
5. Une diapo « **Prêt pour Systalink** » : cette carte des branchements, et ce qu'il reste à brancher le jour où on rejoint leur portefeuille.
