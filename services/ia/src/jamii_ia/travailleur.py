"""Le travailleur : il prend les tâches en file, appelle le fournisseur IA et range le résultat."""

import asyncio
import logging

from jamii_ia import plan_projet
from jamii_ia.fournisseurs import FournisseurIA, FournisseurIndisponibleError
from jamii_ia.taches import DepotTaches

journal = logging.getLogger(__name__)

ESSAIS_MAX = 3


class Travailleur:
    def __init__(self, depot: DepotTaches, fournisseur: FournisseurIA) -> None:
        self._depot = depot
        self._fournisseur = fournisseur

    async def traiter_une(self) -> bool:
        """Traite au plus une tâche. Renvoie faux si la file est vide."""
        tache = await self._depot.prendre_suivante()
        if tache is None:
            return False
        identifiant, entree, essais = tache["id"], tache["entree"], tache["essais"]

        # On n'appelle l'IA que si c'est utile : même entrée déjà traitée → même résultat.
        en_cache = await self._depot.resultat_en_cache(tache["empreinte"])
        if en_cache is not None:
            await self._depot.terminer(identifiant, en_cache, "cache")
            return True

        try:
            reponse = await self._fournisseur.generer(plan_projet.demande(entree))
            plan = plan_projet.lire_plan(reponse.texte, entree)
        except FournisseurIndisponibleError:
            if essais < ESSAIS_MAX:
                await self._depot.remettre(identifiant, delai_secondes=30 * essais)
            else:
                # L'IA est tombée : l'utilisateur reçoit un modèle à compléter, pas une erreur.
                await self._depot.terminer(identifiant, plan_projet.plan_de_repli(entree), "repli")
        except plan_projet.PlanInvalideError as erreur:
            journal.warning("plan non conforme (essai %s) : %s", essais, erreur)
            if essais < ESSAIS_MAX:
                await self._depot.remettre(identifiant, delai_secondes=5)
            else:
                await self._depot.echouer(identifiant, "resultat_invalide")
        else:
            await self._depot.terminer(identifiant, plan, "ia")
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
