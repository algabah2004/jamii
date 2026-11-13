"""Ce que le travailleur sait faire, par type de tâche : préparer l'appel, lire la réponse, replier.

Ajouter un type de tâche = ajouter une ligne ici (et son module), sans toucher au travailleur.
"""

from collections.abc import Callable
from dataclasses import dataclass
from typing import Any

from jamii_ia import analyse_profil, orientation, plan_projet
from jamii_ia.catalogue import Catalogue
from jamii_ia.fournisseurs import DemandeIA

Entree = dict[str, Any]


@dataclass(frozen=True)
class Traitement:
    demande: Callable[[Entree], DemandeIA]
    lire: Callable[[str, Entree], dict[str, Any]]
    repli: Callable[[Entree], dict[str, Any]]


def traitements(catalogue: Catalogue) -> dict[str, Traitement]:
    return {
        "plan_projet": Traitement(
            plan_projet.demande, plan_projet.lire_plan, plan_projet.plan_de_repli
        ),
        "analyse_profil": Traitement(
            analyse_profil.demande, analyse_profil.lire, analyse_profil.repli
        ),
        "orientation_besoin": Traitement(
            lambda e: orientation.demande(e, catalogue),
            lambda t, e: orientation.lire(t, e, catalogue),
            lambda e: orientation.repli(e, catalogue),
        ),
    }
