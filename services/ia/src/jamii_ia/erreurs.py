"""Erreurs au format Problem Details (RFC 9457), schéma `Erreur` de contracts/socle.yaml."""

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

# Codes de validation de Pydantic → codes stables du contrat.
_CODES_CHAMP = {
    "missing": "obligatoire",
    "string_too_short": "trop_court",
    "string_too_long": "trop_long",
    "too_long": "trop_long",
    "extra_forbidden": "champ_inconnu",
}


class ErreurApi(Exception):
    def __init__(
        self, status: int, code: str, titre: str, entetes: dict[str, str] | None = None
    ) -> None:
        self.status = status
        self.code = code
        self.titre = titre
        self.entetes = entetes


def _probleme(
    requete: Request,
    status: int,
    code: str,
    titre: str,
    erreurs: list[dict[str, str]] | None = None,
    entetes: dict[str, str] | None = None,
) -> JSONResponse:
    corps = {
        "type": f"urn:jamii:erreur:{code}",
        "title": titre,
        "status": status,
        "code": code,
        "instance": requete.url.path,
    }
    if erreurs:
        corps["erreurs"] = erreurs
    return JSONResponse(
        corps, status_code=status, headers=entetes, media_type="application/problem+json"
    )


def brancher_erreurs(app: FastAPI) -> None:
    @app.exception_handler(ErreurApi)
    def erreur_api(requete: Request, erreur: ErreurApi) -> JSONResponse:
        return _probleme(requete, erreur.status, erreur.code, erreur.titre, entetes=erreur.entetes)

    @app.exception_handler(RequestValidationError)
    def invalide(requete: Request, erreur: RequestValidationError) -> JSONResponse:
        # On renvoie le chemin du champ et un code, jamais la valeur reçue.
        erreurs = [
            {
                "champ": ".".join(str(morceau) for morceau in e["loc"][1:]) or str(e["loc"][0]),
                "code": _CODES_CHAMP.get(e["type"], "format_invalide"),
            }
            for e in erreur.errors()
        ]
        return _probleme(requete, 400, "validation", "Certains champs sont invalides", erreurs)
