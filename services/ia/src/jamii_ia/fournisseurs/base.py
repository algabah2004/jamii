"""Le port `FournisseurIA` : la seule porte du code métier vers un modèle de langage.

Le code métier ne connaît ni Yokk ni aucun autre fournisseur. On change de fournisseur
par la configuration (`IA_PROVIDER`), jamais en réécrivant le métier.
"""

from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class DemandeIA:
    """Ce qu'on envoie au modèle.

    `consigne` est écrite par nous. `donnees` vient des utilisateurs (un besoin de PME, une
    question) : les deux restent séparées pour qu'un texte d'utilisateur ne puisse jamais se
    faire passer pour une consigne (injection de prompt).
    """

    consigne: str
    donnees: str
    max_jetons: int = 2000
    # Type de tâche : sert à l'adaptateur simulé pour choisir sa réponse.
    tache: str = ""


@dataclass(frozen=True)
class ReponseIA:
    texte: str
    fournisseur: str


class FournisseurIndisponibleError(Exception):
    """Le fournisseur ne répond pas (panne, délai, quota). La tâche sera réessayée ou repliée."""


class FournisseurIA(Protocol):
    nom: str

    async def generer(self, demande: DemandeIA) -> ReponseIA:
        """Lève `FournisseurIndisponibleError` si le fournisseur ne répond pas."""
        ...
