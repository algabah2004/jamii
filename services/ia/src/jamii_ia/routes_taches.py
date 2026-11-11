"""Routes /taches de contracts/ia.yaml : déposer une tâche, lire son état."""

import hmac
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Header, Request, Response

from jamii_ia.erreurs import ErreurApi
from jamii_ia.modeles import NouvelleTache, Tache
from jamii_ia.taches import CleReutiliseeError, DepotTaches, LimiteAtteinteError


def creer_routes_taches(jeton_interne: str) -> APIRouter:
    attendu = f"Bearer {jeton_interne}".encode()

    def exiger_jeton(authorization: Annotated[str | None, Header()] = None) -> None:
        # compare_digest : temps constant, pour ne pas laisser deviner le jeton peu à peu.
        if authorization is None or not hmac.compare_digest(authorization.encode(), attendu):
            raise ErreurApi(401, "non_connecte", "Jeton interne absent ou incorrect")

    def depot(requete: Request) -> DepotTaches:
        return requete.app.state.depot

    routes = APIRouter(prefix="/taches", dependencies=[Depends(exiger_jeton)])

    @routes.post("", response_model=Tache, response_model_exclude_none=True)
    async def deposer(
        corps: NouvelleTache,
        reponse: Response,
        depot: Annotated[DepotTaches, Depends(depot)],
        cle: Annotated[UUID, Header(alias="Idempotency-Key")],
    ) -> Tache:
        entree = corps.entree.model_dump(mode="json", exclude_none=True)
        try:
            ligne, creee = await depot.deposer(cle, corps.type, entree)
        except CleReutiliseeError:
            raise ErreurApi(
                409, "cle_reutilisee", "Cette clé a déjà servi pour une autre entrée"
            ) from None
        except LimiteAtteinteError:
            raise ErreurApi(
                429, "trop_de_requetes", "Limite de tâches atteinte", {"Retry-After": "60"}
            ) from None
        if creee:
            reponse.status_code = 202
            reponse.headers["Location"] = f"/taches/{ligne['id']}"
        return Tache.depuis_ligne(ligne)

    @routes.get("/{tache_id}", response_model=Tache, response_model_exclude_none=True)
    async def lire(tache_id: UUID, depot: Annotated[DepotTaches, Depends(depot)]) -> Tache:
        ligne = await depot.lire(tache_id)
        if ligne is None:
            raise ErreurApi(404, "introuvable", "Tâche inconnue")
        return Tache.depuis_ligne(ligne)

    return routes
