"""Choix du fournisseur IA par la configuration (`IA_PROVIDER`).

Pour brancher un autre fournisseur (Yokk…) : écrire un adaptateur qui respecte `FournisseurIA`
(`base.py`) et l'ajouter à `_FABRIQUES`. Rien d'autre ne change.
"""

from collections.abc import Callable
from typing import TYPE_CHECKING

from jamii_ia.fournisseurs.base import (
    DemandeIA,
    FournisseurIA,
    FournisseurIndisponibleError,
    ReponseIA,
)
from jamii_ia.fournisseurs.compatible_openai import FournisseurCompatibleOpenAI
from jamii_ia.fournisseurs.simule import FournisseurSimule

if TYPE_CHECKING:
    from jamii_ia.config import Reglages

__all__ = [
    "DemandeIA",
    "FournisseurIA",
    "FournisseurInconnuError",
    "FournisseurIndisponibleError",
    "ReponseIA",
    "creer_fournisseur",
]

_FABRIQUES: dict[str, Callable[["Reglages | None"], FournisseurIA]] = {
    "simule": lambda _: FournisseurSimule(),
    "compatible_openai": lambda r: FournisseurCompatibleOpenAI(
        url=r.url_fournisseur if r else "", modele=r.modele if r else "", cle=r.cle if r else ""
    ),
}


class FournisseurInconnuError(ValueError):
    pass


def creer_fournisseur(nom: str, config: "Reglages | None" = None) -> FournisseurIA:
    fabrique = _FABRIQUES.get(nom)
    if fabrique is None:
        connus = ", ".join(sorted(_FABRIQUES))
        raise FournisseurInconnuError(
            f"IA_PROVIDER={nom!r} n'est pas branché. Valeurs possibles : {connus}"
        )
    return fabrique(config)
