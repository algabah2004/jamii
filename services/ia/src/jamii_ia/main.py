"""Point d'entrée HTTP du service IA (contrat : contracts/ia.yaml)."""

from fastapi import FastAPI

from jamii_ia.config import Reglages, reglages
from jamii_ia.fournisseurs import creer_fournisseur


def creer_app(config: Reglages | None = None) -> FastAPI:
    config = config or reglages()
    # Échoue au démarrage, pas à la première tâche, si IA_PROVIDER est mal réglé.
    fournisseur = creer_fournisseur(config.fournisseur)

    # Service interne : pas de documentation interactive exposée.
    app = FastAPI(title="Jamii · Service IA", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.fournisseur = fournisseur

    @app.get("/health/liveness")
    def vivant() -> dict[str, str]:
        return {"status": "UP"}

    @app.get("/health/readiness")
    def pret() -> dict[str, str]:
        # La vérification de la base (503 si injoignable) arrive avec la file de tâches.
        return {"status": "UP", "fournisseur": fournisseur.nom}

    return app
