"""Lecture prudente d'une réponse de modèle : on ne fait jamais confiance à ce qu'il renvoie."""

import json
from typing import Any


class ReponseInvalideError(Exception):
    """La réponse du fournisseur n'est pas conforme au contrat."""


def objet_json(texte: str) -> dict[str, Any]:
    debut, fin = texte.find("{"), texte.rfind("}")
    if debut == -1 or fin <= debut:
        raise ReponseInvalideError("aucun objet JSON dans la réponse")
    try:
        valeur = json.loads(texte[debut : fin + 1])
    except json.JSONDecodeError:
        raise ReponseInvalideError("JSON illisible") from None
    if not isinstance(valeur, dict):
        raise ReponseInvalideError("la réponse n'est pas un objet")
    return valeur
