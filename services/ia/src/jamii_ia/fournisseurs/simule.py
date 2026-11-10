"""Adaptateur simulé : pour le développement, les tests et la démo de secours.

Aucun appel réseau, réponse toujours identique pour une même demande.
"""

import json
from collections.abc import Callable

from jamii_ia.fournisseurs.base import DemandeIA, FournisseurIndisponibleError, ReponseIA

_ETAPES = [
    (
        "Cadrage",
        1,
        "Besoin validé avec le demandeur",
        ["Interroger le demandeur", "Lister les écrans"],
    ),
    (
        "Première version",
        3,
        "Parcours principal utilisable",
        ["Créer le modèle de données", "Construire le parcours principal"],
    ),
    (
        "Mise en ligne",
        4,
        "Version en ligne, testée par le demandeur",
        ["Tester avec le demandeur", "Mettre en ligne"],
    ),
]
_COMPETENCES = [["produit"], ["design"], ["backend"], ["frontend"], ["test"], ["devops"]]


def _plan(demande: DemandeIA) -> str:
    """Un plan plausible et conforme au contrat, construit sans IA à partir du besoin."""
    besoin = json.loads(demande.donnees)
    membres = besoin.get("membres", [])
    presentes = {c.lower() for m in membres for c in m["competences"]}
    taches = []
    for position, (_, _, _, titres) in enumerate(_ETAPES):
        for titre in titres:
            voulues = _COMPETENCES[len(taches)]
            tache = {"titre": titre, "jalon": position, "competences": voulues}
            choisi = next(
                (m for m in membres if voulues[0] in map(str.lower, m["competences"])), None
            )
            if choisi:
                tache["membreSuggere"] = choisi["membreId"]
                tache["raison"] = f"Possède la compétence « {voulues[0]} »."
            taches.append(tache)
    manquants = [c[0] for c in _COMPETENCES if membres and c[0] not in presentes]
    services = [{"service": "datacloud", "raison": "Pour mettre le projet en ligne à la fin."}]
    if besoin["source"] == "demande_pme" and "site" in besoin["description"].lower():
        services.append(
            {"service": "orion", "raison": "Un site simple peut être généré avec Orion."}
        )
    return json.dumps(
        {
            "cahierDesCharges": {
                "resume": f"{besoin['titre']} : {besoin['description'][:300]}",
                "objectifs": [
                    "Répondre au besoin décrit",
                    "Livrer une version utilisable en ligne",
                ],
                "fonctionnalites": [
                    {
                        "titre": "Parcours principal",
                        "description": besoin["titre"],
                        "priorite": "indispensable",
                    }
                ],
                "horsPerimetre": ["Paiement en ligne"],
            },
            "jalons": [
                {"titre": titre, "semaine": semaine, "livrable": livrable}
                for titre, semaine, livrable, _ in _ETAPES
            ],
            "taches": taches,
            "rolesManquants": [
                {"role": c, "competences": [c], "raison": "Aucun membre n'a cette compétence."}
                for c in manquants
            ],
            "servicesSuggeres": services,
        },
        ensure_ascii=False,
    )


def _repondre(demande: DemandeIA) -> str:
    if demande.tache == "plan_projet":
        return _plan(demande)
    return f"[simulé] {len(demande.donnees)} caractères reçus"


class FournisseurSimule:
    nom = "simule"

    def __init__(
        self,
        repondre: Callable[[DemandeIA], str] = _repondre,
        en_panne: bool = False,
    ) -> None:
        self._repondre = repondre
        self._en_panne = en_panne

    async def generer(self, demande: DemandeIA) -> ReponseIA:
        if self._en_panne:
            raise FournisseurIndisponibleError("panne simulée")
        return ReponseIA(texte=self._repondre(demande), fournisseur=self.nom)
