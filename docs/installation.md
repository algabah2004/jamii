# Installer le projet en local

## Prérequis

- Git, et **Docker Desktop** (sous Windows, activer l'intégration WSL)
- Sous Windows, travaille **dans WSL** (`~/…`), pas dans `/mnt/c/…`. C'est plus rapide, et ça évite les problèmes de fins de ligne.
- Les outils de chaque application (Node, Java, Python) seront précisés quand la stack sera validée (#6).

## Démarrer

```bash
git clone https://github.com/algabah2004/jamii.git
cd jamii
cp .env.example .env          # puis remplis ce dont tu as besoin
docker compose up -d          # lance PostgreSQL
docker compose ps             # postgres doit être « healthy »
```

## Structure du dépôt

```
jamii/
├── apps/
│   ├── web/        Interface web (Vue 3 + TypeScript)       · @rev22rse22
│   └── api/        API (Spring Boot, ou NestJS selon #3/#6)  · @sowibrr
├── services/
│   └── ia/         Service IA (Python FastAPI)               · @boubacarsidibe
├── docs/           Concept, architecture, propositions…
├── .github/        CI, modèles d'issues et de PR, CODEOWNERS
└── docker-compose.yml
```

## Avant d'ouvrir une PR

- La CI doit être verte : aucun secret, pas de `.env` commité, titre de PR au format `feat: …` / `fix: …` / `docs: …`
- Voir [CONTRIBUTING.md](../CONTRIBUTING.md)
