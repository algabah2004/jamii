"""Choix du fournisseur IA par la configuration."""

from jamii_ia.fournisseurs.base import (
    DemandeIA,
    FournisseurIA,
    FournisseurIndisponibleError,
    ReponseIA,
)
from jamii_ia.fournisseurs.simule import FournisseurSimule

__all__ = [
    "DemandeIA",
    "FournisseurIA",
    "FournisseurInconnuError",
    "FournisseurIndisponibleError",
    "ReponseIA",
    "creer_fournisseur",
]

# Yokk sera ajouté ici quand son API aura été testée (docs/systalink-services.md).
_FOURNISSEURS = {"simule": FournisseurSimule}


class FournisseurInconnuError(ValueError):
    pass


def creer_fournisseur(nom: str) -> FournisseurIA:
    try:
        return _FOURNISSEURS[nom]()
    except KeyError:
        connus = ", ".join(sorted(_FOURNISSEURS))
        raise FournisseurInconnuError(
            f"IA_PROVIDER={nom!r} n'est pas branché. Valeurs possibles : {connus}"
        ) from None
