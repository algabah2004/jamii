"""Point d'entrée HTTP du service IA (contrat : contracts/ia.yaml)."""

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Response

from jamii_ia.base_donnees import migrer, ouvrir_pool
from jamii_ia.config import Reglages, reglages
from jamii_ia.erreurs import brancher_erreurs
from jamii_ia.fournisseurs import creer_fournisseur
from jamii_ia.routes_taches import creer_routes_taches
from jamii_ia.taches import DepotTaches
from jamii_ia.travailleur import Travailleur

_LONGUEUR_MIN_JETON = 16


class ConfigurationError(ValueError):
    pass


def creer_app(config: Reglages | None = None) -> FastAPI:
    config = config or reglages()
    # On échoue au démarrage, pas à la première tâche, si la configuration est mauvaise.
    fournisseur = creer_fournisseur(config.fournisseur)
    if len(config.jeton_interne) < _LONGUEUR_MIN_JETON:
        raise ConfigurationError(
            f"IA_JETON_INTERNE doit faire au moins {_LONGUEUR_MIN_JETON} caractères"
        )
    if not config.database_url:
        raise ConfigurationError("DATABASE_URL est obligatoire")

    @asynccontextmanager
    async def cycle_de_vie(app: FastAPI) -> AsyncIterator[None]:
        await migrer(config.database_url)
        pool = await ouvrir_pool(config.database_url)
        app.state.depot = DepotTaches(pool, config.limite_par_heure)
        boucle = None
        if config.travailleur_actif:
            boucle = asyncio.create_task(Travailleur(app.state.depot, fournisseur).tourner())
        yield
        if boucle is not None:
            boucle.cancel()
            await asyncio.gather(boucle, return_exceptions=True)
        await pool.close()

    # Service interne : pas de documentation interactive exposée.
    app = FastAPI(
        title="Jamii · Service IA",
        lifespan=cycle_de_vie,
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )
    app.state.fournisseur = fournisseur
    brancher_erreurs(app)
    app.include_router(creer_routes_taches(config.jeton_interne))

    @app.get("/health/liveness")
    def vivant() -> dict[str, str]:
        return {"status": "UP"}

    @app.get("/health/readiness")
    async def pret(reponse: Response) -> dict[str, str]:
        # Seule la base est indispensable : une panne du fournisseur IA ne rend pas le
        # service indisponible, les tâches restent acceptées.
        joignable = await app.state.depot.base_joignable()
        if not joignable:
            reponse.status_code = 503
        return {"status": "UP" if joignable else "DOWN", "fournisseur": fournisseur.nom}

    return app
