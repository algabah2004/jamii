"""Adaptateur simulé : pour le développement, les tests et la démo de secours.

Aucun appel réseau, réponse toujours identique pour une même demande.
"""

from collections.abc import Callable

from jamii_ia.fournisseurs.base import DemandeIA, FournisseurIndisponibleError, ReponseIA


def _echo(demande: DemandeIA) -> str:
    return f"[simulé] {len(demande.donnees)} caractères reçus"


class FournisseurSimule:
    nom = "simule"

    def __init__(
        self,
        repondre: Callable[[DemandeIA], str] = _echo,
        en_panne: bool = False,
    ) -> None:
        self._repondre = repondre
        self._en_panne = en_panne

    async def generer(self, demande: DemandeIA) -> ReponseIA:
        if self._en_panne:
            raise FournisseurIndisponibleError("panne simulée")
        return ReponseIA(texte=self._repondre(demande), fournisseur=self.nom)
