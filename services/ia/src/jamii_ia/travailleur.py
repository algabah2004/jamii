"""Le travailleur : il prend les tâches en file, appelle le fournisseur IA et range le résultat.

L'IA n'est appelée que si c'est utile et permis :
- même entrée déjà traitée en 24 h → même résultat (`cache`), sans appel ;
- budget du jour atteint (`IA_BUDGET_JOUR`) → repli sans IA, sans appel ;
- fournisseur en panne → nouvel essai, puis repli : l'utilisateur n'a jamais une erreur sèche.
"""

import asyncio
import logging

from jamii_ia.catalogue import Catalogue
from jamii_ia.fournisseurs import FournisseurIA, FournisseurIndisponibleError
from jamii_ia.reponse import ReponseInvalideError
from jamii_ia.taches import ESSAIS_MAX, DepotTaches
from jamii_ia.traitements import traitements

journal = logging.getLogger(__name__)

__all__ = ["ESSAIS_MAX", "Travailleur"]


class Travailleur:
    def __init__(
        self,
        depot: DepotTaches,
        fournisseur: FournisseurIA,
        catalogue: Catalogue | None = None,
        budget_jour: int = 0,
    ) -> None:
        self._depot = depot
        self._fournisseur = fournisseur
        self._traitements = traitements(catalogue or Catalogue.charger())
        self._budget_jour = budget_jour

    async def _budget_atteint(self) -> bool:
        return self._budget_jour > 0 and await self._depot.jetons_du_jour() >= self._budget_jour

    async def traiter_une(self) -> bool:
        """Traite au plus une tâche. Renvoie faux si la file est vide."""
        tache = await self._depot.prendre_suivante()
        if tache is None:
            return False
        entree, essais = tache["entree"], tache["essais"]
        traitement = self._traitements.get(tache["type"])
        if traitement is None:
            await self._depot.echouer(tache, "entree_refusee")
            return True

        en_cache = await self._depot.resultat_en_cache(tache["empreinte"])
        if en_cache is not None:
            await self._depot.terminer(tache, en_cache, "cache")
            return True

        if await self._budget_atteint():
            journal.warning("budget IA du jour atteint : repli pour une tâche %s", tache["type"])
            await self._depot.terminer(tache, traitement.repli(entree), "repli")
            return True

        try:
            reponse = await self._fournisseur.generer(traitement.demande(entree))
            await self._depot.ajouter_jetons(tache["id"], reponse.jetons)
            resultat = traitement.lire(reponse.texte, entree)
        except FournisseurIndisponibleError as erreur:
            journal.warning("fournisseur indisponible (essai %s) : %s", essais, erreur)
            if essais < ESSAIS_MAX:
                await self._depot.remettre(tache, delai_secondes=30 * essais)
            else:
                await self._depot.terminer(tache, traitement.repli(entree), "repli")
        except ReponseInvalideError as erreur:
            journal.warning("réponse non conforme (essai %s) : %s", essais, erreur)
            if essais < ESSAIS_MAX:
                await self._depot.remettre(tache, delai_secondes=5)
            elif tache["type"] == "plan_projet":
                await self._depot.echouer(tache, "resultat_invalide")
            else:
                await self._depot.terminer(tache, traitement.repli(entree), "repli")
        else:
            await self._depot.terminer(tache, resultat, "ia")
        return True

    async def tourner(self, pause_secondes: float = 1.0) -> None:
        """Boucle de fond. Une erreur inattendue ne l'arrête pas : la tâche sera reprise."""
        while True:
            try:
                if not await self.traiter_une():
                    await asyncio.sleep(pause_secondes)
            except asyncio.CancelledError:
                raise
            except Exception:
                journal.exception("erreur du travailleur, nouvelle tentative")
                await asyncio.sleep(pause_secondes)
