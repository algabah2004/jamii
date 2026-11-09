# API

**Responsable : @sowibrr** · Statut : ⏳ en attente de #3 (test DataCloud) et #6 (validation de l'architecture)

Monolithe modulaire (auth, profils, communautés, demandes, projets, déploiement). Spring Boot 3 / Java 21 si DataCloud le supporte, sinon NestJS. Migrations Flyway, contrat OpenAPI, endpoint de santé. Voir [docs/architecture.md](../../docs/architecture.md).

## À faire au démarrage
- [ ] Initialiser le projet avec la stack validée
- [ ] Un endpoint ou une page « hello world » et un test qui passe
- [ ] Les commandes d'installation, de lancement et de test documentées ici
- [ ] Le job correspondant ajouté dans `.github/workflows/ci.yml`
